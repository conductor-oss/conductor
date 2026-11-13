#!/usr/bin/env python3
"""Tests a Conductor server against Workflow.tla.

Each test is a generated program (see enumerate.py) with the world's choices
fixed in advance: the answer to every task attempt, the case each switch takes,
how many iterations each loop runs, and the width of each dynamic fork. The
program runs on the server as an ordinary workflow, with this script answering
every task as planned; some tests then retry or rerun the ended workflow. TLC
then checks that the server's final state -- each task's status and number of
attempts, the workflow's status, how many failure workflows started -- is a
state the spec reaches under the same choices.

    TLA2TOOLS=/path/to/tla2tools.jar python3 server_check.py --server http://localhost:8099/api --tests 100
"""
import argparse, hashlib, itertools, json, os, random, re, shutil, subprocess, sys, tempfile, threading, time
import urllib.error, urllib.parse, urllib.request
from concurrent.futures import ThreadPoolExecutor

import enumerate as gen

HERE = os.path.dirname(os.path.abspath(__file__))
JAVA = os.environ.get('JAVA', 'java')
TOOLS = os.environ.get('TLA2TOOLS', 'tla2tools.jar')
KINDS = ['task', 'retried', 'optional', 'sub', 'terminate', 'end']   # no timeouts: the test cannot time them
RETRIES = {'task': 0, 'retried': 1, 'optional': 1, 'sub': 0}
OPTIONAL = {'optional'}
WORKER = 'spec-check'
SETTLE = 2.0   # seconds of quiet after a workflow ends before reading its final state


# ---------------------------------------------------------------------------- the program

class Program:
    """A generated program, named as enumerate.render names it, in both forms."""

    def __init__(self, tid, shape, kinds, failure_wf, rng, name=None):
        # tid: the test's identity, from the seed: it decides every answer and choice, so a seed
        # reproduces the test. name: unique per run, for the server's definitions.
        self.tid, self.prefix = tid, f'spec{name or tid}_'
        self.failure_wf = failure_wf
        self.nodes = {}          # name -> (kind, info)
        self.leaf_defs = set()   # task definition names a worker answers
        it, counter = iter(kinds), itertools.count(1)
        self.cases, self.iters, self.widths = {}, {}, {}

        def seq(s, loop):
            return [node(x, loop) for x in s]

        def node(x, loop):
            name = f'n{next(counter)}'
            if x[0] == 'leaf':
                self.nodes[name] = ('leaf', next(it), loop)
            elif x[0] == 'switch':
                case, default = seq(x[1], loop), seq(x[2], loop)
                self.nodes[name] = ('switch', [case, default], loop)
                self.cases[name] = rng.choice([1, 2])
            elif x[0] == 'fork':
                b1, b2 = seq(x[1], loop), seq(x[2], loop)
                self.nodes[name] = ('fork', [b1, b2], loop, x[3])
            elif x[0] == 'dynfork':
                self.nodes[f'{name}c'] = ('leaf', next(it), loop)
                self.nodes[name] = ('dynfork', f'{name}c', loop)
                self.widths[name] = rng.choice([0, 1, 2])
            elif x[0] == 'loop':
                self.nodes[name] = ('loop', seq(x[1], name), loop)
                self.iters[name] = rng.choice([1, 2])
            return name

        self.body = seq(shape, None)

    # TLA+
    def tla_node(self, name):
        n = self.nodes[name]
        q = lambda s: '<< ' + ', '.join(f'"{x}"' for x in s) + ' >>' if s else '<< >>'
        if n[0] == 'leaf':
            return {'task': 'Task(0, FALSE)', 'retried': 'Task(1, FALSE)', 'optional': 'Task(1, TRUE)',
                    'sub': 'Sub("child", 0, FALSE)', 'terminate': 'TerminateNode("FAILED")',
                    'end': 'TerminateNode("COMPLETED")'}[n[1]]
        if n[0] == 'switch':
            return f'Switch(<< {q(n[1][0])}, {q(n[1][1])} >>)'
        if n[0] == 'fork':
            return f'Fork(<< {q(n[1][0])}, {q(n[1][1])} >>, {{{", ".join(map(str, n[3]))}}})'
        if n[0] == 'dynfork':
            return f'DynFork("{n[1]}")'
        return f'Loop({q(n[1])})'

    # Conductor
    def leaf_task(self, name, ref, kind):
        if kind in ('terminate', 'end'):
            return {'name': 'terminate', 'taskReferenceName': ref, 'type': 'TERMINATE',
                    'inputParameters': {'terminationStatus': 'FAILED' if kind == 'terminate' else 'COMPLETED'}}
        if kind == 'sub':
            return {'name': self.prefix + 'sub', 'taskReferenceName': ref, 'type': 'SUB_WORKFLOW',
                    'retryCount': 0, 'optional': False,
                    'subWorkflowParam': {'name': self.prefix + 'child', 'version': 1}}
        self.leaf_defs.add(self.prefix + name)
        return {'name': self.prefix + name, 'taskReferenceName': ref, 'type': 'SIMPLE',
                'retryCount': RETRIES[kind], 'optional': kind in OPTIONAL}

    def last_ref(self, seq_):
        last = seq_[-1]
        return f'{last}_join' if self.nodes[last][0] in ('fork', 'dynfork') else last

    def conductor(self, seq_):
        out = []
        for name in seq_:
            n = self.nodes[name]
            if n[0] == 'leaf':
                out.append(self.leaf_task(name, name, n[1]))
            elif n[0] == 'switch':
                out.append({'name': 'switch', 'taskReferenceName': name, 'type': 'SWITCH',
                            'evaluatorType': 'value-param', 'expression': 'switchCaseValue',
                            'inputParameters': {'switchCaseValue': '${workflow.input.' + name + '}'},
                            'decisionCases': {'1': self.conductor(n[1][0])},
                            'defaultCase': self.conductor(n[1][1])})
            elif n[0] == 'fork':
                out.append({'name': 'fork', 'taskReferenceName': name, 'type': 'FORK_JOIN',
                            'forkTasks': [self.conductor(b) for b in n[1]]})
                out.append({'name': 'join', 'taskReferenceName': f'{name}_join', 'type': 'JOIN',
                            'joinOn': [self.last_ref(n[1][b - 1]) for b in n[3]]})
            elif n[0] == 'dynfork':
                out.append({'name': 'dynfork', 'taskReferenceName': name, 'type': 'FORK_JOIN_DYNAMIC',
                            'dynamicForkTasksParam': 'dynamicTasks', 'dynamicForkTasksInputParamName': 'dynamicTasksInput',
                            'inputParameters': {'dynamicTasks': '${workflow.input.' + name + '}',
                                                'dynamicTasksInput': '${workflow.input.' + name + '_in}'}})
                out.append({'name': 'join', 'taskReferenceName': f'{name}_join', 'type': 'JOIN', 'joinOn': []})
            elif n[0] == 'loop':
                out.append({'name': 'loop', 'taskReferenceName': name, 'type': 'DO_WHILE',
                            'inputParameters': {'n': '${workflow.input.' + name + '}'},
                            'loopCondition': f"if ($.{name}['iteration'] < $.n) {{ true; }} else {{ false; }}",
                            'loopOver': self.conductor(n[1])})
        return out

    def input(self):
        inp = {}
        for name, c in self.cases.items():
            inp[name] = '1' if c == 1 else 'other'
        for name, n in self.iters.items():
            inp[name] = n
        for name, w in self.widths.items():
            leaf = self.nodes[name][1]
            kind = self.nodes[leaf][1]
            inp[name] = [self.leaf_task(leaf, f'{leaf}_{j}', kind) for j in range(1, w + 1)]
            inp[name + '_in'] = {f'{leaf}_{j}': {} for j in range(1, w + 1)}
        return inp

    def definitions(self):
        own = 'spec@example.com'
        wf = lambda name, tasks, extra={}: dict({'name': name, 'version': 1, 'schemaVersion': 2, 'tasks': tasks,
                                                  'ownerEmail': own, 'timeoutSeconds': 0,
                                                  'timeoutPolicy': 'ALERT_ONLY'}, **extra)
        main = wf(self.prefix + 'main', self.conductor(self.body),
                  {'failureWorkflow': self.prefix + 'onfail'} if self.failure_wf else {})
        for leaf, n in self.nodes.items():   # copies of dynamic forks are defined by the input
            if n[0] == 'leaf' and n[1] not in ('terminate', 'end', 'sub'):
                self.leaf_defs.add(self.prefix + leaf)
        self.leaf_defs |= {self.prefix + 'cx', self.prefix + 'w'}
        child = wf(self.prefix + 'child', [{'name': self.prefix + 'cx', 'taskReferenceName': 'cx', 'type': 'SIMPLE',
                                             'retryCount': 0}])
        onfail = wf(self.prefix + 'onfail', [{'name': self.prefix + 'w', 'taskReferenceName': 'w', 'type': 'SIMPLE',
                                              'retryCount': 0}])
        return [child, onfail, main]


# ---------------------------------------------------------------------------- the world's answers

def answer(tid, key):
    """The planned answer to one attempt, the same for the server run and for TLC."""
    h = int(hashlib.sha256(json.dumps([tid, key]).encode()).hexdigest()[:8], 16) % 10
    return 'COMPLETED' if h < 6 else 'FAILED' if h < 9 else 'FAILED_WITH_TERMINAL_ERROR'


def key_tla(key):
    return '<<' + ', '.join(f'"{x}"' if isinstance(x, str) else str(x) for x in key) + '>>'


# ---------------------------------------------------------------------------- the server

class Server:
    def __init__(self, base):
        self.base = base.rstrip('/')

    def call(self, method, path, body=None, text=False):
        req = urllib.request.Request(self.base + path, method=method,
                                     data=None if body is None else json.dumps(body).encode(),
                                     headers={'Content-Type': 'application/json'})
        with urllib.request.urlopen(req, timeout=30) as r:
            data = r.read().decode()
        return data if text else (json.loads(data) if data else None)


def parse_ref(ref):
    """Server reference name -> (node, iteration suffix or None, copy)."""
    m = re.match(r'^(.*?)(?:__(\d+))?$', ref)
    base, it = m.group(1), m.group(2)
    c = re.match(r'^(n\d+c)_(\d+)$', base)
    if c:
        return c.group(1), it, int(c.group(2))
    return base, it, 0


def spec_key(task):
    node, it, copy = parse_ref(task['referenceTaskName'])
    return node, int(it) if it else 0, copy


def drive(srv, prog, wid, epoch, deadline, child_parent):
    """Answers every task of the test until the main workflow ends and nothing is left."""
    quiet_since = None
    while time.time() < deadline:
        busy = False
        for d in sorted(prog.leaf_defs):
            tasks = srv.call('GET', f'/tasks/poll/batch/{d}?workerid={WORKER}&count=10&timeout=50')
            for t in tasks or []:
                busy = True
                attempt = t['retryCount'] + 1
                if t['workflowType'] == prog.prefix + 'onfail':
                    status = 'COMPLETED'
                elif t['workflowType'] == prog.prefix + 'child':
                    parent = child_parent(t['workflowInstanceId'])
                    status = answer(prog.tid, ['child', *parent, attempt]) if parent else 'COMPLETED'
                else:
                    status = answer(prog.tid, ['main', *spec_key(t), attempt, epoch])
                srv.call('POST', '/tasks', {'workflowInstanceId': t['workflowInstanceId'], 'taskId': t['taskId'],
                                            'status': status, 'workerId': WORKER, 'outputData': {},
                                            'reasonForIncompletion': None if status == 'COMPLETED' else 'planned'},
                         text=True)
        wf = srv.call('GET', f'/workflow/{wid}?includeTasks=true')
        ended = wf['status'] in ('COMPLETED', 'FAILED', 'TIMED_OUT', 'TERMINATED')
        if ended and not busy:
            quiet_since = quiet_since or time.time()
            if time.time() - quiet_since > SETTLE:
                return wf
        else:
            quiet_since = None
            if not busy:
                time.sleep(0.2)
    return None


def run_test(srv, prog, op, deadline_s):
    defs = prog.definitions()
    # retries come from each workflow task's retryCount; the definitions only allow them
    taskdefs = [{'name': d, 'retryCount': 1, 'retryLogic': 'FIXED', 'retryDelaySeconds': 0, 'timeoutSeconds': 0,
                 'responseTimeoutSeconds': 3600, 'timeoutPolicy': 'ALERT_ONLY', 'ownerEmail': 'spec@example.com'}
                for d in sorted(prog.leaf_defs)]
    srv.call('POST', '/metadata/taskdefs', taskdefs, text=True)
    srv.call('PUT', '/metadata/workflow', defs, text=True)
    wid = srv.call('POST', '/workflow', {'name': prog.prefix + 'main', 'version': 1, 'input': prog.input()}, text=True)
    parents = {}

    def child_parent(cid):
        if cid not in parents:
            c = srv.call('GET', f'/workflow/{cid}?includeTasks=false')
            p = srv.call('GET', f'/workflow/{wid}?includeTasks=true')
            t = next(x for x in p['tasks'] if x['taskId'] == c.get('parentWorkflowTaskId'))
            parents[cid] = [*spec_key(t), t['retryCount'] + 1, state['epoch']]
        return parents[cid]

    state = {'epoch': 0}
    end = time.time() + deadline_s
    wf = drive(srv, prog, wid, 0, end, child_parent)
    if wf is None:
        return wid, None, 'server did not settle'
    done_op = None
    if op:
        # the latest attempt of each task: the spec has no older attempts to rerun from
        latest = {}
        for t in sorted(wf['tasks'], key=lambda t: t['seq']):
            if t['taskType'] in prog.leaf_defs or t['taskType'] == 'SUB_WORKFLOW':
                latest[spec_key(t)] = t
        leaves = [latest[k] for k in sorted(latest)]
        kind = op
        try:
            if kind == 'retry':
                if wf['status'] == 'COMPLETED':
                    kind = None
                else:
                    srv.call('POST', f'/workflow/{wid}/retry', text=True)
            elif kind == 'restart':
                srv.call('POST', f'/workflow/{wid}/rerun', {'reRunFromWorkflowId': wid}, text=True)
            elif kind == 'rerun':
                if not leaves:
                    kind = None
                else:
                    t = random.Random(prog.tid).choice(leaves)
                    srv.call('POST', f'/workflow/{wid}/rerun', {'reRunFromWorkflowId': wid, 'reRunFromTaskId': t['taskId']}, text=True)
                    kind = ('rerun', spec_key(t))
        except urllib.error.HTTPError as e:
            if e.code == 409:
                kind = None   # refused as not allowed in the workflow's state: the spec allows doing nothing
            else:
                # anything else (a missing endpoint or task, no permission, throttling, a server
                # error) is not an answer from the workflow's semantics: the test fails
                return wid, None, f'operator call {op} failed: HTTP {e.code} {e.read()[:200]!r}'
        if kind:
            state['epoch'] = 1
            done_op = kind
            wf = drive(srv, prog, wid, 1, end, child_parent)
            if wf is None:
                return wid, None, 'server did not settle after ' + str(kind)
    return wid, (wf, done_op), None


def failure_started(srv, prog):
    """How many failure workflows the test's workflow started."""
    q = urllib.parse.quote(f"workflowType='{prog.prefix}onfail'")
    return srv.call('GET', f'/workflow/search?query={q}&size=5')['totalHits']


def observe(prog, wf, failure):
    obs = {}
    for t in sorted(wf['tasks'], key=lambda t: t['seq']):
        if not (t['taskType'] in prog.leaf_defs or t['taskType'] in ('SUB_WORKFLOW', 'TERMINATE')):
            continue
        k = spec_key(t)
        prev = obs.get(k)
        obs[k] = (t['status'], (prev[1] + 1) if prev else 1, t['retryCount'])
    return obs, wf['status'], failure


# ---------------------------------------------------------------------------- the trace

TRACE_DIR = None
TASK_STATUS = {'IN_PROGRESS': 'SCHEDULED'}


def trace_views(prog, wid):
    """What each decide left the main workflow in, as TraceView shows it; None without a trace."""
    path = os.path.join(TRACE_DIR, wid + '.jsonl')
    if not os.path.exists(path):
        return None
    views = []
    for raw in open(path):
        line = json.loads(raw)
        latest, count = {}, {}
        for ref, typ, seq, status, _ in sorted(line['tasks'], key=lambda r: r[2]):
            latest[ref] = (typ, seq, status)
            count[ref] = count.get(ref, 0) + 1
        order, tasks = [], {}
        for ref, (typ, seq, status) in sorted(latest.items(), key=lambda kv: kv[1][1]):
            node, it, copy = parse_ref(ref)
            key = (node, int(it) if it else 0, copy)
            if typ == 'JOIN':
                order.append(('join', (node[:-len('_join')], key[1], key[2])))
                continue
            order.append(key)
            if typ in prog.leaf_defs or typ in ('SUB_WORKFLOW', 'TERMINATE'):
                tasks[key] = (TASK_STATUS.get(status, status), count[ref])
        view = (line['status'], tuple(order), tuple(sorted(tasks.items())))
        if not views or views[-1] != view:
            views.append(view)
    return views


def view_tla(view):
    status, order, tasks = view
    def spot(x):
        return f'<<"join", {key_tla(list(x[1]))}>>' if x[0] == 'join' else key_tla(list(x))
    o = '<< ' + ', '.join(spot(x) for x in order) + ' >>'
    t = ' @@ '.join(f'({key_tla(list(k))} :> <<"{st}", {n}>>)' for k, (st, n) in tasks) or '[x \\in {} |-> <<>>]'
    return f'[status |-> "{status}", order |-> {o}, tasks |-> {t}]'


# ---------------------------------------------------------------------------- TLC

def mc_module(name, prog, obs, status, failure, epoch, op, views=None):
    lines = [f'---- MODULE {name} ----', 'EXTENDS Common', 'MCNodes ==']
    entries = [f'("{n}" :> {prog.tla_node(n)})' for n in prog.nodes] + ['("cx" :> Task(0, FALSE))', '("w" :> Task(0, FALSE))']
    lines.append('       ' + '\n    @@ '.join(entries))
    fw = '"onfail"' if prog.failure_wf else 'NONE'
    body = '<< ' + ', '.join(f'"{x}"' for x in prog.body) + ' >>'
    lines.append(f'MCDefs == ("main" :> Def({body}, {fw}, NONE, FALSE)) @@ ("child" :> Def(<<"cx">>, NONE, NONE, FALSE))'
                 ' @@ ("onfail" :> Def(<<"w">>, NONE, NONE, FALSE))')
    # planned answers, by attempt and epoch for the main workflow and by parent task for children
    table = []
    leaves = [n for n, v in prog.nodes.items() if v[0] == 'leaf' and v[1] not in ('terminate', 'end', 'sub')]
    subs = [n for n, v in prog.nodes.items() if v[0] == 'leaf' and v[1] == 'sub']
    for n in leaves + subs:
        its = [1, 2] if prog.nodes[n][2] else [0]
        copies = [1, 2] if n.endswith('c') else [0]
        for it, c, a, e in itertools.product(its, copies, [1, 2, 3], [0, 1]):
            if n in leaves:
                table.append((['main', n, it, c, a, e], answer(prog.tid, ['main', n, it, c, a, e])))
            else:
                table.append((['child', n, it, c, a, e, 1], answer(prog.tid, ['child', n, it, c, a, e, 1])))
    lines.append('Plan == ' + ('\n    @@ '.join(f'({key_tla(k)} :> "{v}")' for k, v in table) if table else '[x \\in {} |-> NONE]'))
    lines.append('''MCAnswers(i, k) ==
    IF inst[i].wf = "onfail" THEN {"COMPLETED"}
    ELSE IF inst[i].wf = "child"
    THEN LET p == inst[i].parent[1]
             pk == inst[i].parent[2]
             key == <<"child", pk[1], pk[2], pk[3], inst[p].act[pk].attempt, inst[p].epoch, inst[i].act[k].attempt>>
         IN IF key \\in DOMAIN Plan THEN {Plan[key]} ELSE {"COMPLETED"}
    ELSE LET key == <<"main", k[1], k[2], k[3], inst[i].act[k].attempt, inst[i].epoch>>
         IN IF key \\in DOMAIN Plan THEN {Plan[key]} ELSE {"COMPLETED"}''')
    cases = ' @@ '.join(f'("{n}" :> {c})' for n, c in prog.cases.items()) or '[x \\in {} |-> 0]'
    iters = ' @@ '.join(f'("{n}" :> {c})' for n, c in prog.iters.items()) or '[x \\in {} |-> 0]'
    widths = ' @@ '.join(f'("{n}" :> {c})' for n, c in prog.widths.items()) or '[x \\in {} |-> 0]'
    lines.append(f'CaseOf == {cases}\nItersOf == {iters}\nWidthOf == {widths}')
    lines.append('MCCases(i, k) == {CaseOf[k[1]]}\nMCWidths(i, k) == {WidthOf[k[1]]}')
    lines.append('MCGoesOn(i, k) == {inst[i].act[k].iter < ItersOf[k[1]]}')
    # a rerun from a task is from that task only, as the server was asked
    target = f'{{{key_tla(list(op[1]))}}}' if isinstance(op, tuple) and op[0] == 'rerun' else 'DOMAIN inst[i].act'
    lines.append(f'MCRerunTargets(i) == {target}')
    o = ' @@ '.join(f'({key_tla(list(k))} :> [status |-> "{s}", attempt |-> {a}])' for k, (s, a, _) in obs.items()) \
        or '[x \\in {} |-> NONE]'
    lines.append(f'Obs == {o}')
    how = 'TRUE' if not op else 'inst[1].how = "%s"' % (op if isinstance(op, str) else op[0])
    lines.append(f'''Seen(i) == {{k \\in DOMAIN inst[i].act : IsLeaf(k[1]) \\/ Kind(k[1]) = "terminate"}}
Match ==
    /\\ inst[1].status = "{status}"
    /\\ inst[1].epoch = {epoch}
    /\\ {how}
    /\\ Seen(1) = DOMAIN Obs
    /\\ \\A k \\in DOMAIN Obs : inst[1].act[k].status = Obs[k].status /\\ inst[1].act[k].attempt = Obs[k].attempt
    /\\ Cardinality({{j \\in 1..Len(inst) : inst[j].wf = "onfail"}}) = {failure}
Witness == ~Match''')
    if views is not None:
        # the run, step by step: the spec goes through every state a decide left, in order
        lines[-1] = lines[-1].replace('Witness == ~Match', '''VARIABLE l
Trace == << ''' + ',\n    '.join(view_tla(v) for v in views) + ''' >>
TInit == Init /\\ l = 0
TNext == \\/ Next /\\ UNCHANGED l
         \\/ l < Len(Trace) /\\ TraceView(1) = Trace[l + 1] /\\ l' = l + 1 /\\ UNCHANGED vars
TSpec == TInit /\\ [][TNext]_<<vars, l>>
\\* between two decides the spec only adds what the next one shows (activations go only when an
\\* operator reopens an ended workflow, and its end was traced): other states lead nowhere
SpotSet(o) == {o[j] : j \\in 1..Len(o)}
TConstraint == SpotSet(TraceOrder(1)) \\subseteq
    (IF l = 0 THEN {} ELSE SpotSet(Trace[l].order)) \\cup (IF l < Len(Trace) THEN SpotSet(Trace[l + 1].order) ELSE {})
Witness == ~(l = Len(Trace) /\\ Match)''')
    lines.append('====')
    return '\n'.join(lines) + '\n'


def mc_cfg(ops, traced=False):
    return f'''CONSTANTS
    Nodes <- MCNodes
    Defs <- MCDefs
    Main = "main"
    MaxIter = 2
    MaxBranches = 2
    MaxOps = {ops}
    Answers <- MCAnswers
    Cases <- MCCases
    Widths <- MCWidths
    GoesOn <- MCGoesOn
    RerunTargets <- MCRerunTargets
SPECIFICATION {'TSpec' if traced else 'Spec'}
{'CONSTRAINT TConstraint' if traced else ''}
INVARIANT Witness
CHECK_DEADLOCK FALSE
'''


def tlc_reaches(name, text, ops, d):
    traced = 'TSpec ==' in text
    for f in ('Workflow.tla', 'Common.tla'):
        shutil.copy(os.path.join(HERE, f), d)
    open(os.path.join(d, name + '.tla'), 'w').write(text)
    open(os.path.join(d, name + '.cfg'), 'w').write(mc_cfg(ops, traced))
    try:
        out = subprocess.run([JAVA, '-XX:+UseParallelGC', '-cp', TOOLS, 'tlc2.TLC', '-workers', '2',
                              '-metadir', os.path.join(d, 'states'), name],
                             cwd=d, capture_output=True, text=True, timeout=600).stdout
    except subprocess.TimeoutExpired:
        return 'unchecked (TLC timeout)', ''   # not a pass: counted apart
    if 'Invariant Witness is violated' in out:
        return 'reached', out
    if 'No error has been found' in out:
        return 'not reached', out
    return 'error', out


# ---------------------------------------------------------------------------- main

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--server', default='http://localhost:8099/api')
    ap.add_argument('--tests', type=int, default=50)
    ap.add_argument('--size', type=int, default=3)
    ap.add_argument('--seed', type=int, default=7)
    ap.add_argument('--jobs', type=int, default=6)
    ap.add_argument('--settle', type=float, default=2.0)
    ap.add_argument('--deadline', type=int, default=120, help='seconds before a test counts as not settling')
    ap.add_argument('--ops', type=float, default=0.4, help='share of tests that retry or rerun at the end')
    ap.add_argument('--keep', default=None, help='directory for the models of tests that did not match')
    ap.add_argument('--trace-dir', default=None,
                    help="the server's conductor.trace.dir: each run is then checked step by step, not by its end alone")
    a = ap.parse_args()
    global SETTLE, TRACE_DIR
    SETTLE = a.settle
    TRACE_DIR = a.trace_dir
    rng = random.Random(a.seed)
    shapes = []
    for n in range(1, a.size + 1):
        for s in gen.shapes(n, False):
            if not has_loop_dynfork(s):
                shapes.append(s)
    run_id = time.strftime('%H%M%S')
    tests = []
    for i in range(a.tests):
        shape = rng.choice(shapes)
        kinds = [rng.choice([k for k in KINDS if k in slot]) for slot in gen.slots(shape)]
        op = rng.choice(['retry', 'restart', 'rerun']) if rng.random() < a.ops else None
        tests.append((f's{a.seed}x{i}', f'{run_id}x{i}', shape, kinds, rng.random() < 0.5, op))
    srv = Server(a.server)
    results, lock = [], threading.Lock()

    def one(t):
        tid, name, shape, kinds, fw, op = t
        prog = Program(tid, shape, kinds, fw, random.Random(tid), name)
        try:
            wid, res, err = run_test(srv, prog, op, a.deadline)
        except Exception as e:  # noqa
            return tid, 'driver error', repr(e), None
        if err:
            label = 'operator call failed' if err.startswith('operator call') else 'server did not settle'
            return tid, label, f'{wid}: {err}', None
        wf, done_op = res
        obs, status, failure = observe(prog, wf, failure_started(srv, prog))
        module = 'S' + tid.replace('x', '_')
        views = None
        if TRACE_DIR:
            views = trace_views(prog, wid)
            if not views:
                return tid, 'no trace', f'{wid}: no trace in {TRACE_DIR}', None
        text = mc_module(module, prog, obs, status, failure, 1 if done_op else 0, done_op, views)
        d = tempfile.mkdtemp(prefix=module + '-')
        verdict, out = tlc_reaches(module, text, 1 if done_op else 0, d)
        detail = f'{wid} op={done_op} status={status} failure_wf={failure} tasks=' + \
            ', '.join(f'{k[0]}/{k[1]}.{k[2]}:{s}#{n}' for k, (s, n, _) in sorted(obs.items()))
        if verdict == 'reached':
            shutil.rmtree(d, ignore_errors=True)
            # control: an observation the spec cannot reach must not match, or a match proves
            # nothing. Each is out of reach by construction: no TERMINATE in these programs ends
            # as TERMINATED and no operator terminates, no task is retried more than twice, and
            # nothing is skipped.
            r = random.Random(tid)
            choice = r.choice(['status', 'attempts', 'skipped'] if obs else ['status'])
            bad_obs, bad_status = obs, status
            if choice == 'status':
                bad_status = 'TERMINATED' if status != 'TERMINATED' else 'COMPLETED'
            else:
                k = r.choice(sorted(obs))
                st, cnt, rc = obs[k]
                bad_obs = {**obs, k: (st, cnt + 5, rc) if choice == 'attempts' else ('SKIPPED', cnt, rc)}
            d2 = tempfile.mkdtemp(prefix=module + '-control-')
            v2, _ = tlc_reaches(module, mc_module(module, prog, bad_obs, bad_status, failure, 1 if done_op else 0, done_op, views),
                                1 if done_op else 0, d2)
            shutil.rmtree(d2, ignore_errors=True)
            if v2 != 'not reached':
                return tid, 'control ' + v2, detail, d
        elif a.keep:
            shutil.copytree(d, os.path.join(a.keep, module), dirs_exist_ok=True)
        return tid, verdict, detail, d

    with ThreadPoolExecutor(a.jobs) as pool:
        for tid, verdict, detail, d in pool.map(one, tests):
            with lock:
                results.append((tid, verdict, detail))
                print(f'{tid}: {verdict}  {detail if verdict != "reached" else ""}', flush=True)
    counts = {}
    for _, v, _ in results:
        counts[v] = counts.get(v, 0) + 1
    print(counts)
    sys.exit(0 if counts.get('reached', 0) == len(results) else 1)


def has_loop_dynfork(shape, in_loop=False):
    for x in shape:
        if x[0] == 'dynfork' and in_loop:
            return True
        if x[0] in ('switch', 'fork') and (has_loop_dynfork(x[1], in_loop) or has_loop_dynfork(x[2], in_loop)):
            return True
        if x[0] == 'loop' and has_loop_dynfork(x[1], True):
            return True
    return False


if __name__ == '__main__':
    main()
