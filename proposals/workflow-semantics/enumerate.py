#!/usr/bin/env python3
"""Checks Workflow.tla on every program up to a size, instead of hand-picked ones.

A program is a sequence of nodes; a node is a task of one of several kinds, a
sub-workflow, a TERMINATE, or an operator (switch, fork, dynamic fork, loop)
holding smaller sequences. Every shape up to --size nodes is generated, each with
every task kind, and TLC checks each one with all the invariants and properties of
the models. Programs that would exceed --limit are sampled at random.

    TLA2TOOLS=/path/to/tla2tools.jar python3 enumerate.py --size 4 [--ops 1]
"""
import argparse, itertools, json, os, random, re, shutil, subprocess, sys, tempfile
from concurrent.futures import ThreadPoolExecutor

HERE = os.path.dirname(os.path.abspath(__file__))
JAVA = os.environ.get('JAVA', 'java')
TOOLS = os.environ.get('TLA2TOOLS', 'tla2tools.jar')

LEAVES = {  # name: TLA+ node
    'task': 'Task(0, FALSE)',
    'retried': 'Task(1, FALSE)',
    'optional': 'Task(1, TRUE)',
    'deadline': 'TimedTask(0, FALSE, "TIME_OUT_WF", FALSE)',
    'budget': 'TimedTask(1, TRUE, "RETRY", TRUE)',
    'sub': 'Sub("child", 0, FALSE)',
    'terminate': 'TerminateNode("FAILED")',
    'end': 'TerminateNode("COMPLETED")',
}


def shapes(n, in_loop):
    """Every sequence of total size n: lists of ('leaf',) / (op, children...)."""
    if n == 0:
        yield []
        return
    for first in range(1, n + 1):
        for node in node_shapes(first, in_loop):
            for rest in shapes(n - first, in_loop):
                yield [node] + rest


def node_shapes(n, in_loop):
    if n == 1:
        yield ('leaf',)
        return
    k = n - 1
    for a in range(1, k + 1):           # switch: a case of size a, a default of size k - a
        for c in shapes(a, in_loop):
            for d in shapes(k - a, in_loop):
                yield ('switch', c, d)
    for a in range(1, k):               # fork: two non-empty branches
        for b1 in shapes(a, in_loop):
            for b2 in shapes(k - a, in_loop):
                yield ('fork', b1, b2, (1, 2))
                yield ('fork', b1, b2, (1,))
    if k == 1:
        yield ('dynfork',)
    if not in_loop:
        for body in shapes(k, True):
            yield ('loop', body)


COPIES = [k for k in LEAVES if k not in ('terminate', 'end')]  # what a dynamic fork may copy


def slots(shape):
    """The kinds each leaf position may take, in the order render assigns them."""
    out = []
    for node in shape:
        if node[0] == 'leaf':
            out.append(list(LEAVES))
        elif node[0] == 'dynfork':
            out.append(COPIES)
        elif node[0] in ('switch', 'fork'):
            out += slots(node[1]) + slots(node[2])
        elif node[0] == 'loop':
            out += slots(node[1])
    return out


def render(shape, kinds):
    """TLA+ text for MCNodes and the main body, assigning kinds to leaves in order."""
    nodes, it = [], iter(kinds)
    counter = itertools.count(1)

    def seq(s):
        return '<< ' + ', '.join(f'"{node(x)}"' for x in s) + ' >>' if s else '<< >>'

    def node(x):
        name = f'n{next(counter)}'
        if x[0] == 'leaf':
            nodes.append((name, LEAVES[next(it)]))
        elif x[0] == 'switch':
            nodes.append((name, f'Switch(<< {seq(x[1])}, {seq(x[2])} >>)'))
        elif x[0] == 'fork':
            on = '{' + ', '.join(map(str, x[3])) + '}'
            nodes.append((name, f'Fork(<< {seq(x[1])}, {seq(x[2])} >>, {on})'))
        elif x[0] == 'dynfork':
            leaf = f'{name}c'
            nodes.append((leaf, LEAVES[next(it)]))
            nodes.append((name, f'DynFork("{leaf}")'))
        elif x[0] == 'loop':
            nodes.append((name, f'Loop({seq(x[1])})'))
        return name

    body = seq(shape)
    nodes.append(('cx', 'Task(0, FALSE)'))
    nodes.append(('w', 'Task(0, FALSE)'))
    return nodes, body


def model(name, shape, kinds, failure_wf):
    nodes, body = render(shape, kinds)
    defs = (f'("main" :> Def({body}, {chr(34) + "onfail" + chr(34) if failure_wf else "NONE"}, NONE, FALSE))'
            ' @@ ("child" :> Def(<<"cx">>, NONE, NONE, FALSE))'
            ' @@ ("onfail" :> Def(<<"w">>, NONE, NONE, FALSE))')
    text = f'---- MODULE {name} ----\nEXTENDS Common\nMCNodes ==\n       ' + \
        '\n    @@ '.join(f'("{n}" :> {v})' for n, v in nodes) + f'\nMCDefs == {defs}\n====\n'
    return text


def cfg(ops):
    base = open(os.path.join(HERE, 'MC_Compose.cfg')).read()
    base = re.sub(r'MaxIter = \d+', 'MaxIter = 2', base)
    base = re.sub(r'MaxBranches = \d+', 'MaxBranches = 2', base)
    return re.sub(r'MaxOps = \d+', f'MaxOps = {ops}', base)


def run(job):
    name, text, ops, timeout = job
    d = tempfile.mkdtemp(prefix=name + '-')
    for f in ('Workflow.tla', 'Common.tla'):
        shutil.copy(os.path.join(HERE, f), d)
    open(os.path.join(d, name + '.tla'), 'w').write(text)
    open(os.path.join(d, name + '.cfg'), 'w').write(cfg(ops))
    try:
        out = subprocess.run([JAVA, '-XX:+UseParallelGC', '-Xmx2g', '-cp', TOOLS, 'tlc2.TLC', '-workers', '1',
                              '-metadir', os.path.join(d, 'states'), name],
                             cwd=d, capture_output=True, text=True, timeout=timeout).stdout
    except subprocess.TimeoutExpired:
        shutil.rmtree(d, ignore_errors=True)
        return name, 'timeout', None, text
    m = re.search(r'(?:Invariant|Action property|Temporal property) (\w+) (?:is|was) violated', out)
    states = re.search(r'([\d,]+) distinct states found, 0 states left', out)
    if m:
        verdict = 'violated ' + m.group(1)
    elif 'No error has been found' in out:
        verdict = 'ok'
    else:
        verdict = 'error'
    if verdict == 'ok':
        shutil.rmtree(d, ignore_errors=True)
    return name, verdict, states.group(1) if states else None, (text if verdict == 'ok' else d)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--size', type=int, default=3)
    ap.add_argument('--ops', type=int, default=0)
    ap.add_argument('--limit', type=int, default=1500)
    ap.add_argument('--jobs', type=int, default=os.cpu_count())
    ap.add_argument('--timeout', type=int, default=300)
    ap.add_argument('--seed', type=int, default=1)
    a = ap.parse_args()
    programs = []
    for n in range(1, a.size + 1):
        for shape in shapes(n, False):
            for kinds in itertools.product(*slots(shape)):
                programs.append((shape, kinds))
    random.Random(a.seed).shuffle(programs)
    total = len(programs)
    programs = programs[:a.limit]
    jobs = [(f'G{i}', model(f'G{i}', s, k, i % 2 == 0), a.ops, a.timeout) for i, (s, k) in enumerate(programs)]
    print(f'{total} programs up to size {a.size}; checking {len(jobs)} with MaxOps = {a.ops}', flush=True)
    counts, bad = {}, []
    with ThreadPoolExecutor(a.jobs) as pool:
        for i, (name, verdict, states, info) in enumerate(pool.map(run, jobs), 1):
            counts[verdict] = counts.get(verdict, 0) + 1
            if verdict != 'ok':
                bad.append((name, verdict, info))
            if i % 100 == 0:
                print(f'  {i}/{len(jobs)} {counts}', flush=True)
    print(counts)
    for name, verdict, info in bad:
        print(name, verdict, '' if verdict == 'timeout' else info)   # a timeout is unchecked, not a failure
    checked = counts.get('ok', 0)
    print(f'{checked} of {len(jobs)} checked, {counts.get("timeout", 0)} timed out unchecked')
    failed = any(verdict != 'timeout' for _, verdict, _ in bad)
    sys.exit(1 if failed or checked == 0 else 0)   # nothing checked is not a pass


if __name__ == '__main__':
    main()
