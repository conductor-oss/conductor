#!/usr/bin/env python3
"""The spec checker for how a native workflow's plain Java control flow is drawn.

Generates workflows from a grammar of plain Java (loops nested in loops, if / else if / else,
switches old and arrow style over ints, strings and enums, continue and break, guard clauses,
helpers, return and throw, a while loop whose condition calls a task, do-while), runs them on a
server, and checks each execution against the program's own control-flow graph, interpreted here:
it never asks the SDK or the server what happened.

The rules are the definitions in the java-sdk's design/native-workflows/README.md ("Plain ifs and
switches"), computed here on the program's graph: an arm holds what runs only because its outcome
was taken, within the method and the loop pass the decision is in; a decision is drawn when an arm
holds a call and no case falls into the next. As checked:

  S  sound      every SWITCH drawn is a decision the code made, with the arm it took; a task inside
                a case is in that arm's region
  C  complete   a task in an arm's region is drawn inside that arm's case; a decision the code went
                past without a call is drawn when every call-free path between the two calls
                around it goes through it exactly once, in one arm only
  P  previews   a case not taken shows the calls of its arm's region, never run
  L  loops      each entry is tagged with the loops around it, outermost first
  R  result     the workflow returns what the program computes
  D  replay     the code, run again against the recorded execution as a worker would, matches it

When the calls cannot tell whether the code went round a loop to a decision again, a call is not
drawn in it until a later call shows it for sure; that certainty is tracked here too.

    python3 check_drawing.py --server http://localhost:8099/api --programs 40 --seeds 4
"""
import argparse
import concurrent.futures as cf
import json
import os
import random
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
from dataclasses import dataclass, field

HERE = os.path.dirname(os.path.abspath(__file__))

# ------------------------------------------------------------------ tasks (drawing.GenTasks)

TASKS = {
    'a': lambda x: (x * 7 + 3) % 5,
    'b': lambda x: (x * 5 + 1) % 4,
    'c': lambda x: (x * 3 + 2) % 6,
    's': lambda x: ['red', 'green', 'blue', 'other'][(x * 3 + 1) % 4],
    'e': lambda x: ['RED', 'GREEN', 'BLUE'][(x * 5 + 2) % 3],
}

# ------------------------------------------------------------------ program

_ids = iter(range(1, 10 ** 9))


@dataclass
class Call:
    task: str
    arg: str
    id: int = field(default_factory=lambda: next(_ids))


@dataclass
class If:
    cond: str
    then: list
    els: list = None  # None: no else; [If(elif_=True)]: an else if
    elif_: bool = False
    id: int = field(default_factory=lambda: next(_ids))


@dataclass
class Switch:
    kind: str  # int, str, enum
    arg: str  # what the subject is computed from
    style: str  # arrow, old
    cases: list  # [(labels, body, falls through)]
    default: list = None
    id: int = field(default_factory=lambda: next(_ids))


@dataclass
class Loop:
    kind: str  # for, each, while, whilecall, do
    var: str
    n: int
    body: list
    id: int = field(default_factory=lambda: next(_ids))


@dataclass
class Jump:
    kind: str  # continue, break
    id: int = field(default_factory=lambda: next(_ids))


@dataclass
class Return:
    id: int = field(default_factory=lambda: next(_ids))


@dataclass
class Throw:
    id: int = field(default_factory=lambda: next(_ids))


@dataclass
class Helper:
    name: str
    body: list
    id: int = field(default_factory=lambda: next(_ids))


@dataclass
class HelperCall:
    helper: Helper
    id: int = field(default_factory=lambda: next(_ids))


@dataclass
class Program:
    main: list
    helpers: list


def ends_abruptly(block):
    return bool(block) and isinstance(block[-1], Jump)


def completes(stmt):
    """Whether control can go on to the statement after this one."""
    if isinstance(stmt, (Jump, Return, Throw)):
        return False
    if isinstance(stmt, If):
        return stmt.els is None or not ends_abruptly_block(stmt.then) or not ends_abruptly_block(stmt.els)
    if isinstance(stmt, Switch):
        if stmt.default is None:
            return True
        arms = [b for _, b, _ in stmt.cases] + [stmt.default]
        return any(not ends_abruptly_block(b) for b in arms)
    return True


def ends_abruptly_block(block):
    return bool(block) and not completes(block[-1])


def has_call(block):
    for s in block or []:
        if isinstance(s, (Call, HelperCall)):
            return True
        if isinstance(s, If) and (has_call(s.then) or has_call(s.els)):
            return True
        if isinstance(s, Switch) and (s.kind != 'int' or any(has_call(b) for _, b, _ in s.cases) or has_call(s.default)):
            return True
        if isinstance(s, Loop) and (s.kind == 'whilecall' or has_call(s.body)):
            return True
    return False


# ------------------------------------------------------------------ generator

CONDS = ['v % 2 == 0', 'v % 3 == 1', 'v > 2', 'v % 4 != 3']
LOOP_CONDS = ['{i} == 1', 'v % 2 == 0 && {i} > 0', 'v > 3 || {i} == 0', '{i} % 2 == 0']
LABELS = {'int': ['0', '1', '2', '3'], 'str': ['"red"', '"green"', '"blue"'], 'enum': ['RED', 'GREEN', 'BLUE']}


class Gen:
    def __init__(self, rnd, depth=3, width=3):
        self.r, self.depth, self.width = rnd, depth, width
        self.vars = 0
        self.helpers = []
        self.in_helper = False

    def var(self, p):
        self.vars += 1
        return f'{p}{self.vars}'

    def block(self, d, loops, in_switch=False, min_calls=0):
        out = []
        n = self.r.randint(1, self.width)
        for k in range(n):
            s = self.stmt(d, loops, in_switch)
            out.append(s)
            if not completes(s):
                break
        if min_calls and not has_call(out):
            out.insert(0, self.call(loops))
        return out

    def call(self, loops):
        add = self.r.choice([str(self.r.randint(0, 3))] + list(loops))
        return Call(self.r.choice('abc'), f'v + {add}')

    def stmt(self, d, loops, in_switch):
        r = self.r.random()
        if d <= 0 or r < 0.35:
            if loops and self.r.random() < 0.12:
                return Jump('continue' if in_switch or self.r.random() < 0.6 else 'break')
            if not self.in_helper and self.r.random() < 0.15:
                return HelperCall(self.helper())
            return self.call(loops)
        if r < 0.6:
            return self.if_(d, loops, in_switch)
        if r < 0.78:
            return self.switch(d, loops, in_switch)
        return self.loop(d, loops)

    def helper(self):
        """A helper method: it always starts with a call, so every invocation shows on the stack."""
        if self.helpers and (len(self.helpers) >= 2 or self.r.random() < 0.5):
            return self.r.choice(self.helpers)
        self.in_helper = True
        body = [self.call(())] + self.block(2, (), False)
        self.in_helper = False
        h = Helper(f'h{len(self.helpers) + 1}', body)
        self.helpers.append(h)
        return h

    def cond(self, loops):
        if loops and self.r.random() < 0.5:
            return self.r.choice(LOOP_CONDS).format(i=self.r.choice(loops))
        return self.r.choice(CONDS)

    def if_(self, d, loops, in_switch):
        then = self.block(d - 1, loops, in_switch, min_calls=1)
        x = self.r.random()
        if x < 0.35:
            els = None
        elif x < 0.6:
            els = [self.if_(d - 1, loops, in_switch)]
            els[0].elif_ = True
        else:
            els = self.block(d - 1, loops, in_switch)
        x = self.r.random()
        target = then if self.r.random() < 0.5 or els is None or (els and isinstance(els[0], If) and els[0].elif_) else els
        if not ends_abruptly_block(target):
            # end an arm with a jump, as a guard clause would: a continue or break, a return, a throw
            if loops and x < 0.25:
                target.append(Jump('continue' if in_switch or self.r.random() < 0.6 else 'break'))
            elif x < 0.33:
                target.append(Return())
            elif x < 0.36:
                target.append(Throw())
        return If(self.cond(loops), then, els)

    def switch(self, d, loops, in_switch):
        kind = self.r.choice(['int', 'str', 'enum'])
        style = self.r.choice(['arrow', 'old'])
        labels = LABELS[kind][:]
        self.r.shuffle(labels)
        k = self.r.randint(1, 3)
        groups = []
        for g in range(k):
            take = labels[:self.r.randint(1, 2 if len(labels) > k - g else 1)]
            labels = labels[len(take):]
            if not take:
                break
            groups.append(sorted(take, key=LABELS[kind].index))
        cases = []
        for labs in groups:
            x = self.r.random()
            body = [] if x < 0.12 else self.block(d - 1, loops, True)
            falls = style == 'old' and body and self.r.random() < 0.08 and not ends_abruptly_block(body)
            cases.append((labs, body, falls))
        default = None if self.r.random() < 0.4 else self.block(d - 1, loops, True)
        sw = Switch(kind, self.call(loops).arg, style, cases, default)
        if kind == 'int' and not has_call([sw]):
            cases[0] = (cases[0][0], [self.call(loops)] + cases[0][1], cases[0][2])
        return sw

    def loop(self, d, loops):
        kind = self.r.choice(['for', 'each', 'while', 'whilecall', 'do'])
        var = self.var({'for': 'i', 'each': 'f', 'while': 'w', 'whilecall': 'w', 'do': 'd'}[kind])
        n = self.r.randint(0, 3) if kind != 'do' else self.r.randint(1, 3)
        body = self.block(d - 1, loops + (var,), False, min_calls=1)
        return Loop(kind, var, n, body)

    def program(self):
        main = self.block(self.depth, (), min_calls=1)
        return Program(main, self.helpers)


# ------------------------------------------------------------------ Java source

class Emitter:
    def __init__(self, cls):
        self.cls, self.lines, self.at = cls, [], {}

    def put(self, ind, text, node=None, role=None):
        self.lines.append('    ' * ind + text)
        if node is not None:
            self.at[(node.id, role)] = len(self.lines)

    def emit(self, prog, name):
        head = [
            'package drawing.gen;', '', 'import java.util.List;', '',
            'import org.conductoross.conductor.sdk.Workflow;', '',
            'import com.netflix.conductor.sdk.workflow.task.InputParam;', '',
            'import drawing.GenTasks;', '',
            f'public class {self.cls} {{', '',
            '    private final GenTasks tasks;', '',
            f'    public {self.cls}(GenTasks tasks) {{', '        this.tasks = tasks;', '    }', '',
            f'    @Workflow(name = "{name}", version = 1)',
            '    public String run(@InputParam("seed") int seed) {',
            '        int v = seed;',
        ]
        self.lines.extend(head)
        self.helper = False
        self.block(prog.main, 2, None)
        if not ends_abruptly_block(prog.main):
            self.put(2, 'return "" + v;')
        self.put(1, '}')
        for h in prog.helpers:
            self.put(0, '')
            self.put(1, f'private int {h.name}(int v) {{', h, 'head')
            self.helper = True
            self.block(h.body, 2, None)
            self.helper = False
            if not ends_abruptly_block(h.body):
                self.put(2, 'return v;')
            self.put(1, '}')
        self.put(0, '}')
        return '\n'.join(self.lines) + '\n'

    def block(self, stmts, ind, switch_style):
        for s in stmts:
            self.stmt(s, ind, switch_style)

    def stmt(self, s, ind, switch_style):
        if isinstance(s, Call):
            self.put(ind, f'v = tasks.{s.task}({s.arg});', s, 'call')
        elif isinstance(s, Jump):
            self.put(ind, f'{s.kind};', s, 'jump')
        elif isinstance(s, Return):
            self.put(ind, 'return v;' if self.helper else 'return "" + v;', s, 'return')
        elif isinstance(s, Throw):
            self.put(ind, 'throw new IllegalStateException("boom");', s, 'throw')
        elif isinstance(s, HelperCall):
            self.put(ind, f'v = {s.helper.name}(v);', s, 'call')
        elif isinstance(s, If):
            self.put(ind, f'if ({s.cond}) {{', s, 'dec')
            self.if_rest(s, ind)
        elif isinstance(s, Switch):
            subject = {'int': f'v % 4', 'str': f'tasks.s({s.arg})', 'enum': f'tasks.e({s.arg})'}[s.kind]
            self.put(ind, f'switch ({subject}) {{', s, 'dec')
            for labs, body, falls in s.cases:
                self.case(s, ', '.join(labs), body, falls, ind + 1)
            if s.default is not None:
                self.case(s, None, s.default, False, ind + 1)
            self.put(ind, '}')
        elif isinstance(s, Loop):
            self.loop(s, ind)

    def if_rest(self, s, ind):
        self.block(s.then, ind + 1, None)
        if s.els is None:
            self.put(ind, '}')
        elif len(s.els) == 1 and isinstance(s.els[0], If) and s.els[0].elif_:
            e = s.els[0]
            self.put(ind, f'}} else if ({e.cond}) {{', e, 'dec')
            self.if_rest(e, ind)
        else:
            self.put(ind, '} else {')
            self.block(s.els, ind + 1, None)
            self.put(ind, '}')

    def case(self, s, labs, body, falls, ind):
        if s.style == 'arrow':
            self.put(ind, (f'case {labs} -> {{' if labs else 'default -> {'))
            self.block(body, ind + 1, 'arrow')
            self.put(ind, '}')
        else:
            self.put(ind, f'case {labs}:' if labs else 'default:')
            self.block(body, ind + 1, 'old')
            if not falls and not ends_abruptly_block(body) and labs is not None:
                self.put(ind + 1, 'break;')

    def loop(self, s, ind):
        if s.kind == 'for':
            self.put(ind, f'for (int {s.var} = 0; {s.var} < {s.n}; {s.var}++) {{', s, 'head')
            self.block(s.body, ind + 1, None)
            self.put(ind, '}')
        elif s.kind == 'each':
            items = ', '.join(str(k + 1) for k in range(s.n))
            self.put(ind, f'for (int {s.var} : List.<Integer>of({items})) {{', s, 'head')
            self.block(s.body, ind + 1, None)
            self.put(ind, '}')
        elif s.kind == 'while':
            self.put(ind, f'int {s.var} = 0;', s, 'init')
            self.put(ind, f'while ({s.var} < {s.n}) {{', s, 'head')
            self.put(ind + 1, f'{s.var}++;', s, 'incr')
            self.block(s.body, ind + 1, None)
            self.put(ind, '}')
        elif s.kind == 'whilecall':
            self.put(ind, f'int {s.var} = 0;', s, 'init')
            self.put(ind, f'while (tasks.b(v + {s.var}++) != 0 && {s.var} <= {s.n}) {{', s, 'head')
            self.block(s.body, ind + 1, None)
            self.put(ind, '}')
        elif s.kind == 'do':
            self.put(ind, f'int {s.var} = 0;', s, 'init')
            self.put(ind, 'do {')
            self.put(ind + 1, f'{s.var}++;', s, 'incr')
            self.block(s.body, ind + 1, None)
            self.put(ind, f'}} while ({s.var} < {s.n});', s, 'tail')


# ------------------------------------------------------------------ control-flow graph

@dataclass
class Node:
    id: int
    kind: str  # entry, exit, call, dec, cond, op
    line: int = 0
    loops: tuple = ()  # the program loops around it, outermost first
    arms: tuple = ()  # the (decision, arm) it is lexically inside, outermost first
    succ: dict = field(default_factory=dict)  # label -> node
    task: str = None
    arg: str = None
    post: str = None
    assign: str = None
    expr: str = None  # dec / cond / op
    dec: object = None  # the If or Switch
    keys: dict = None  # arm key -> the labels it is chosen by (switch)
    drawable: bool = True  # an arm's region holds a call, and no case falls into the next
    ret: object = None  # where the method it is in returns to: its arms end there
    sdk_key: dict = None  # arm key -> the key the compiled code names it by, or None


class Graph:
    def __init__(self, prog, em):
        self.nodes, self.em = [], em
        self.heads = {}  # loop id -> the node each pass starts from
        self.ret = None  # where a return goes: the end, or after the helper's call
        self.backs = set()  # nodes a loop goes back to by jumping backwards
        self.exits = set()  # where loops go when they end (a break jumps there)
        self.exit = self.new('exit', line=len(em.lines) - 2)
        self.entry = self.new('entry')
        self.entry.succ['next'] = self.block(prog.main, self.exit, None, None, (), (), self.exit)
        self.preds = {n.id: [] for n in self.nodes}
        for n in self.nodes:
            for t in n.succ.values():
                self.preds[t.id].append(n)
        # a loop whose body always breaks never comes round: javac emits no jump back, so it is no loop
        self.real_loops = set()
        for loop_id, head in self.heads.items():
            seen, todo = set(), [t for t in head.succ.values()]
            while todo:
                x = todo.pop()
                if x is head:
                    self.real_loops.add(loop_id)
                    break
                if x.id in seen or loop_id not in x.loops:
                    continue
                seen.add(x.id)
                todo.extend(x.succ.values())
        self.once_heads = {h.line for lid, h in self.heads.items() if lid not in self.real_loops}
        # a decision is drawn when what runs only because one of its arms was taken holds a call
        ipdom = self.postdominators()
        for d in self.nodes:
            if d.kind == 'dec':
                d.drawable = d.drawable and any(self.nodes[i].kind == 'call'
                                                for k in d.succ for i in self.region(d, k, ipdom))

    def new(self, kind, **kw):
        n = Node(len(self.nodes), kind, **kw)
        self.nodes.append(n)
        return n

    def line(self, s, role):
        return self.em.at.get((s.id, role), 0)

    def block(self, stmts, nxt, brk, cont, loops, arms, ret=None):
        ret = ret if ret is not None else self.ret
        for s in reversed(stmts or []):
            saved, self.ret = self.ret, ret
            nxt = self.stmt(s, nxt, brk, cont, loops, arms)
            self.ret = saved
        return nxt

    def stmt(self, s, nxt, brk, cont, loops, arms):
        if isinstance(s, Return):
            return self.ret
        if isinstance(s, Throw):
            return self.new('throw', line=self.line(s, 'throw'), loops=loops, arms=arms)
        if isinstance(s, HelperCall):
            # the helper's code, in place; its returns come back to after the call
            return self.block(s.helper.body, nxt, None, None, loops, arms, nxt)
        if isinstance(s, Call):
            n = self.new('call', line=self.line(s, 'call'), loops=loops, arms=arms, task=s.task, arg=s.arg, assign='v')
            n.succ['next'] = nxt
            return n
        if isinstance(s, Jump):
            return cont if s.kind == 'continue' else brk
        if isinstance(s, If):
            d = self.new('dec', line=self.line(s, 'dec'), loops=loops, arms=arms, expr=s.cond, dec=s, ret=self.ret)
            d.succ['true'] = self.block(s.then, nxt, brk, cont, loops, arms + ((d.id, 'true'),))
            d.succ['false'] = self.block(s.els, nxt, brk, cont, loops, arms + ((d.id, 'false'),))
            d.sdk_key = {'true': 'true', 'false': 'false'}
            return d
        if isinstance(s, Switch):
            line = self.line(s, 'dec')
            d = self.new('dec', line=line, loops=loops, arms=arms, dec=s, keys={}, ret=self.ret)
            # the default goes to the code after the switch when there is none
            dflt = self.block(s.default, nxt, nxt, cont, loops, arms + ((d.id, 'default'),)) if s.default is not None else nxt
            if s.default and isinstance(s.default[0], Jump):
                jump = self.new('op', line=line, expr='pass', loops=loops, arms=arms + ((d.id, 'default'),))
                jump.succ['next'] = dflt
                dflt = jump
            follow = dflt if s.default is not None else nxt
            targets = []
            for labs, body, falls in reversed(s.cases):
                key = ','.join(l.strip('"') for l in labs)
                to = follow if falls else nxt
                entry = self.block(body, to, nxt, cont, loops, arms + ((d.id, key),))
                targets.insert(0, (key, labs, entry))
                follow = entry
            d.succ['default'] = dflt
            d.sdk_key = {'default': 'default'}
            for k, (key, labs, entry) in enumerate(targets):
                last = k == len(targets) - 1 and s.default is None
                body = next(b for l, b, f in s.cases if ','.join(x.strip('"') for x in l) == key)
                if body and isinstance(body[0], Jump):
                    # a case that only jumps (continue) is a jump of its own: its own arm
                    entry = self.new('op', line=line, expr='pass', loops=loops, arms=arms + ((d.id, key),))
                    entry.succ['next'] = cont if body[0].kind == 'continue' else nxt
                    targets[k] = (key, labs, entry)
                elif entry is nxt and not last:
                    # `case x: break;`, or an empty arrow case before another, is a jump of its own: an arm
                    # without a call; only the last arrow case, with no default, jumps straight past the switch
                    entry = self.new('op', line=line, expr='pass', loops=loops, arms=arms + ((d.id, key),))
                    entry.succ['next'] = nxt
                    targets[k] = (key, labs, entry)
                d.succ[key] = entry
                d.keys[key] = [l.strip('"') for l in labs]
                # javac jumps an empty case to where the default goes (one arm with it), or past the switch
                d.sdk_key[key] = 'default' if entry is dflt else (None if entry is nxt else key)
            # a case falling into the next runs code of two arms: the switch is plain code
            # (a last case without a break just ends the switch: it falls into nothing)
            # (falling into a case that only breaks is the same as breaking: javac jumps straight past)
            # (falling into the default is the same as a switch without one followed by its code:
            # the compiled code cannot tell them apart, so only falling into a written case counts)
            nexts = [b for _, b, _ in s.cases[1:]]
            falls = any(f and k < len(s.cases) - 1 and has_call(nexts[k])
                        for k, (_, _, f) in enumerate(s.cases))
            d.drawable = not falls  # and an arm's region holds a call: decided once the graph is built
            if s.kind == 'int':
                return d
            subj = self.new('call', line=line, loops=loops, arms=arms, task=s.kind[0], arg=s.arg, assign='subj')
            subj.succ['next'] = d
            return subj
        if isinstance(s, Loop):
            self.exits.add(nxt.id)
            inner = loops + (s.id,)
            head = self.line(s, 'head')
            if s.kind == 'for':
                init = self.new('op', line=head, expr=f'{s.var} = 0', loops=loops, arms=arms)
                cond = self.new('cond', line=head, expr=f'{s.var} < {s.n}', loops=inner, arms=arms)
                upd = self.new('op', line=head, expr=f'{s.var} += 1', loops=inner, arms=arms)
                upd.succ['next'] = cond
                self.heads[s.id] = cond
                cond.succ['true'] = self.block(s.body, upd, nxt, upd, inner, arms)
                cond.succ['false'] = nxt
                init.succ['next'] = cond
                return init
            if s.kind == 'each':
                init = self.new('op', line=head, expr=f'{s.var}_k = 0', loops=loops, arms=arms)
                cond = self.new('cond', line=head, expr=f'{s.var}_k < {s.n}', loops=inner, arms=arms)
                take = self.new('op', line=head, expr=f'{s.var}_k += 1; {s.var} = {s.var}_k', loops=inner, arms=arms)
                self.backs.add(cond.id)
                take.succ['next'] = self.block(s.body, cond, nxt, cond, inner, arms)
                self.heads[s.id] = cond
                cond.succ['true'] = take
                cond.succ['false'] = nxt
                init.succ['next'] = cond
                return init
            if s.kind == 'while':
                init = self.new('op', line=self.line(s, 'init'), expr=f'{s.var} = 0', loops=loops, arms=arms)
                cond = self.new('cond', line=head, expr=f'{s.var} < {s.n}', loops=inner, arms=arms)
                incr = self.new('op', line=self.line(s, 'incr'), expr=f'{s.var} += 1', loops=inner, arms=arms)
                self.backs.add(cond.id)
                incr.succ['next'] = self.block(s.body, cond, nxt, cond, inner, arms)
                self.heads[s.id] = cond
                cond.succ['true'] = incr
                cond.succ['false'] = nxt
                init.succ['next'] = cond
                return init
            if s.kind == 'whilecall':
                init = self.new('op', line=self.line(s, 'init'), expr=f'{s.var} = 0', loops=loops, arms=arms)
                call = self.new('call', line=head, loops=inner, arms=arms, task='b', arg=f'v + {s.var}',
                                post=f'{s.var} += 1', assign='tmp')
                cond = self.new('cond', line=head, expr=f'tmp != 0 and {s.var} <= {s.n}', loops=inner, arms=arms)
                call.succ['next'] = cond
                self.heads[s.id] = call
                self.backs.add(call.id)
                cond.succ['true'] = self.block(s.body, call, nxt, call, inner, arms)
                cond.succ['false'] = nxt
                init.succ['next'] = call
                return init
            if s.kind == 'do':
                init = self.new('op', line=self.line(s, 'init'), expr=f'{s.var} = 0', loops=loops, arms=arms)
                incr = self.new('op', line=self.line(s, 'incr'), expr=f'{s.var} += 1', loops=inner, arms=arms)
                cond = self.new('cond', line=self.line(s, 'tail'), expr=f'{s.var} < {s.n}', loops=inner, arms=arms)
                incr.succ['next'] = self.block(s.body, cond, nxt, cond, inner, arms)
                self.heads[s.id] = incr
                cond.succ['true'] = incr
                cond.succ['false'] = nxt
                init.succ['next'] = incr
                return init
        raise ValueError(s)

    # -------------------------------------------------------------- running it

    def run(self, seed):
        env = {'v': seed}
        trace, calls = [], []
        n = self.entry.succ['next']
        for _ in range(100000):
            if n is self.exit:
                return env['v'], trace, calls
            trace.append(n)
            if n.kind == 'throw':
                calls.append((len(trace) - 1, n))
                return None, trace, calls
            if n.kind == 'call':
                arg = eval(n.arg, {}, env)
                if n.post:
                    exec(n.post, {}, env)
                r = TASKS[n.task](arg)
                env[n.assign] = r
                calls.append((len(trace) - 1, n))
                n = n.succ['next']
            elif n.kind == 'op':
                if n.expr != 'pass':
                    for part in n.expr.split(';'):
                        exec(part.strip(), {}, env)
                n = n.succ['next']
            elif n.kind == 'cond':
                n = n.succ['true' if eval(n.expr, {}, env) else 'false']
            elif n.kind == 'dec':
                taken = self.decide(n, env)
                trace[-1] = (n, taken)
                n = n.succ[taken]
        raise RuntimeError('program did not end')

    @staticmethod
    def decide(d, env):
        if isinstance(d.dec, If):
            return 'true' if eval(d.dec.cond.replace('&&', ' and ').replace('||', ' or '), {}, env) else 'false'
        value = str(env['v'] % 4) if d.dec.kind == 'int' else env['subj']
        for key, labs in d.keys.items():
            if value in labs:
                return key
        return 'default'

    # -------------------------------------------------------------- analysis

    def postdominators(self):
        allids = {n.id for n in self.nodes}
        pdom = {n.id: set(allids) for n in self.nodes}
        pdom[self.exit.id] = {self.exit.id}
        changed = True
        while changed:
            changed = False
            for n in self.nodes:
                if n is self.exit:
                    continue
                succ = list(n.succ.values())
                new = {n.id} | (set.intersection(*(pdom[s.id] for s in succ)) if succ else set())
                if new != pdom[n.id]:
                    pdom[n.id], changed = new, True
        ipdom = {}
        for n in self.nodes:
            strict = pdom[n.id] - {n.id}
            for c in strict:
                if pdom[c] == strict:
                    ipdom[n.id] = c
        return ipdom

    def reach(self, d, key, ipdom):
        """What can run after the decision took this arm, before its paths meet again, it decides
        again, or a loop it is in goes round to its next pass."""
        stop = {ipdom.get(d.id), d.id} | {self.heads[l].id for l in d.loops if l in self.heads}
        seen, todo = set(), [d.succ[key]]
        while todo:
            n = todo.pop()
            if n.id in stop or n.id in seen:
                continue
            seen.add(n.id)
            todo.extend(n.succ.values())
        return seen

    def region(self, d, key, ipdom):
        """What runs only when the decision took this arm: reached from it and from no other arm
        before the arms meet (code after a switch that one case continues past is in no case)."""
        memo = self.__dict__.setdefault('_regions', {})
        if (d.id, key) not in memo:
            memo[(d.id, key)] = self._region(d, key, ipdom)
        return memo[(d.id, key)]

    def _region(self, d, key, ipdom):
        # what runs only because the arm was taken: every way to it from the start goes through
        # the decision to the arm, and it is in the decision's method (not where that returns to)
        target = d.succ[key]
        without, todo = set(), [self.entry]
        while todo:
            n = todo.pop()
            if n.id in without:
                continue
            without.add(n.id)
            todo.extend(t for t in n.succ.values() if not (n is d and t is target))
        stop = {d.id} | ({d.ret.id} if d.ret is not None and d.ret is not self.exit else set())
        seen, todo = set(), [target]
        while todo:
            n = todo.pop()
            if n.id in seen or n.id in without or n.id in stop:
                continue
            seen.add(n.id)
            todo.extend(n.succ.values())
        return seen

    def through(self, c1, c2, d):
        """(may, must): whether some / every call-free path from c1 to c2 goes through decision d."""
        start = list(c1.succ.values())
        fwd, todo, reached = set(), list(start), False
        while todo:
            n = todo.pop()
            if n is c2:
                reached = True
                continue
            if n.kind == 'call' or n is self.exit or n.id in fwd:
                continue
            fwd.add(n.id)
            todo.extend(n.succ.values())
        if not reached or d.id not in fwd:
            return False, False
        back, todo = set(), [p for p in self.preds[c2.id] if p.id in fwd]
        while todo:
            n = todo.pop()
            if n.id in back:
                continue
            back.add(n.id)
            todo.extend(p for p in self.preds[n.id] if p.id in fwd)
        if d.id not in back:
            return False, False
        seen, todo = set(), list(start)
        while todo:
            n = todo.pop()
            if n is c2:
                return True, False
            if n.id not in back or n.id in seen or n is d:
                continue
            seen.add(n.id)
            todo.extend(n.succ.values())
        return True, True

    def must_pass(self, c1, c2):
        """Decisions every call-free path from c1 (a call, or the entry) to c2 (a call, or the exit)
        goes through exactly once, in one arm only: {decision node id: the node that arm goes to}."""
        start = list(c1.succ.values())
        fwd, reached, todo = set(), False, list(start)
        while todo:
            n = todo.pop()
            if n is c2:
                reached = True
                continue
            if n.kind == 'call' or n is self.exit or n.id in fwd:
                continue
            fwd.add(n.id)
            todo.extend(n.succ.values())
        if not reached:
            return {}
        back, todo = set(), [p for p in self.preds[c2.id] if p.id in fwd]
        while todo:
            n = todo.pop()
            if n.id in back:
                continue
            back.add(n.id)
            todo.extend(p for p in self.preds[n.id] if p.id in fwd)
        rel = fwd & back

        def reaches(srcs, avoid):
            seen, todo = set(), [s for s in srcs]
            while todo:
                n = todo.pop()
                if n is c2:
                    return True
                if n.id not in rel or n.id in seen or n.id == avoid:
                    continue
                seen.add(n.id)
                todo.extend(n.succ.values())
            return False

        out = {}
        for nid in rel:
            d = self.nodes[nid]
            if d.kind != 'dec':
                continue
            if reaches(start, d.id):
                continue  # a path avoids it
            # once: no way round from it back to it
            seen, todo, cyc = set(), list(d.succ.values()), False
            while todo:
                n = todo.pop()
                if n is d:
                    cyc = True
                    break
                if n.id not in rel or n.id in seen:
                    continue
                seen.add(n.id)
                todo.extend(n.succ.values())
            if cyc:
                continue
            # cases that go to the same place are one arm to the compiled code
            targets = {t.id for t in d.succ.values() if t is c2 or t.id in rel}
            if len(targets) == 1:
                out[nid] = next(iter(targets))
        return out


# ------------------------------------------------------------------ the recorded execution

def real_entries(tasks, ancestors=()):
    """Entries the code made, in the order it made them, with the SWITCHes they are inside."""
    for t in tasks or []:
        md = t.get('metadata') or {}
        if md.get('preview') or md.get('previewAfter'):
            continue
        yield t, ancestors
        if t.get('type') == 'SWITCH':
            key = (t.get('inputParameters') or {}).get('switchCaseValue')
            cases = t.get('decisionCases') or {}
            inside = cases.get(key) if key in cases else t.get('defaultCase')
            yield from real_entries(inside, ancestors + (t,))


def preview_names(tasks):
    out = []
    for t in tasks or []:
        if (t.get('metadata') or {}).get('previewAfter'):
            continue  # what a throw skipped, not the arm's preview
        if t.get('type') == 'SWITCH':
            for v in (t.get('decisionCases') or {}).values():
                out += preview_names(v)
            out += preview_names(t.get('defaultCase'))
        elif t.get('type') not in ('SWITCH',):
            out.append(t.get('name'))
    return out


def same_key(a, b):
    return a is not None and b is not None and sorted(str(a).split(',')) == sorted(str(b).split(','))


def line_of(entry):
    site = (entry.get('metadata') or {}).get('site') or ''
    return int(site.rsplit(':', 1)[1]) if ':' in site else 0


def static_calls(g, d, key, ipdom):
    """The calls an arm makes as written, and those that can run in its region."""
    region = g.region(d, key, ipdom)
    lexical = [n for n in g.nodes if n.kind == 'call' and (d.id, key) in n.arms]
    inregion = [g.nodes[i] for i in region if g.nodes[i].kind == 'call']
    return lexical, inregion


def check(g, seed, wf):
    """Returns (problems, stats) for one execution against the program's own graph."""
    problems, stats = [], {'calls': 0, 'switches': 0, 'passed': 0, 'hidden': 0}
    # 'passed': decisions gone through without a call in their arm that the rules say can be known;
    # 'hidden': decisions gone through that the compiled code cannot tell, so are not drawn
    v, trace, calls = g.run(seed)
    ipdom = g.postdominators()
    out = (wf.get('output') or {}).get('result')
    thrown = calls and calls[-1][1].kind == 'throw'
    if thrown:
        reason = wf.get('reasonForIncompletion') or ''
        if wf.get('status') != 'FAILED' or 'boom' not in reason:
            problems.append(f'R: {wf.get("status")} {reason[:120]!r}, the program throws at line {calls[-1][1].line}')
            return problems, stats
    elif wf.get('status') != 'COMPLETED' or out != str(v):
        problems.append(f'R: {wf.get("status")} {out!r}, the program computes {v}')
        return problems, stats

    entries = list(real_entries(wf['workflowDefinition']['tasks']))
    tasks = [(e, a) for e, a in entries if e.get('type') != 'SWITCH']
    if len(tasks) != len(calls):
        problems.append(f'C: {len(tasks)} tasks drawn, the code called {len(calls)}')
    # occurrences: (trace index, node, arm)
    occ = [(i, x[0], x[1]) for i, x in enumerate(trace) if isinstance(x, tuple)]

    def open_at(i):
        """Decisions whose region the trace is in at index i: [(index, node, arm)], outermost first."""
        live = []
        for j, d, key in occ:
            if j >= i:
                break
            stop = {ipdom.get(d.id), d.id} | {g.heads[l].id for l in d.loops if l in g.heads}
            ended = any((trace[k][0] if isinstance(trace[k], tuple) else trace[k]).id in stop for k in range(j + 1, i + 1))
            if not ended and d.drawable:
                live.append((j, d, key))
        return live

    # Whether the compiled code can know each call is in each occurrence around it: it knows it
    # entered one when every way from the call before goes through the decision (or that call was
    # in none of its arms), and stays sure while no call-free way between two calls could go round
    # through the decision again. {(occurrence index, call index): sure}
    sure = {}
    state = {}
    for ci, (ti, node) in enumerate(calls):
        prev = calls[ci - 1][1] if ci > 0 else g.entry
        for j, d, key in open_at(ti):
            if node.id not in g.region(d, key, ipdom):
                continue
            if j not in state:
                in_arm = ci > 0 and any(prev.id in g.region(d, k, ipdom) for k in d.succ)
                may, must = g.through(prev, node, d)
                state[j] = (not in_arm) or must
            else:
                may, must = g.through(prev, node, d)
                if may:
                    state[j] = False
            sure[(j, ci)] = state[j]

    loopmap = {}

    def check_loops(entry, node, what):
        got = tuple((entry.get('metadata') or {}).get('loops') or ())
        want = tuple(l for l in node.loops if l in g.real_loops)
        if len(got) != len(want):
            problems.append(f'L: {what} at line {node.line} is tagged {list(got)}, it is inside {len(want)} loops')
            return
        for w, gl in zip(want, got):
            if loopmap.setdefault(w, gl) != gl or list(loopmap.values()).count(gl) > 1:
                problems.append(f'L: {what} at line {node.line} is tagged {gl}, another loop has it')

    # match each SWITCH to the occurrence it draws, and each task to its call
    matched = {}  # id(entry) -> (trace index, node, arm)
    ci = 0
    prev_call = -1
    pending = []
    for e, anc in entries:
        if e.get('type') == 'SWITCH':
            pending.append((e, anc))
            continue
        if ci >= len(calls):
            break
        ti, node = calls[ci]
        stats['calls'] += 1
        want = 'TERMINATE' if node.kind == 'throw' else 'g_' + node.task
        got = e.get('type') if node.kind == 'throw' else e.get('name')
        if got != want or line_of(e) != node.line:
            problems.append(f'C: entry {ci + 1} is {e.get("name")} ({e.get("type")}) at line {line_of(e)}, the code '
                            f'{"threw" if node.kind == "throw" else "called " + want} at line {node.line}')
            return problems, stats
        for sw, _ in pending:
            line = line_of(sw)
            cands = [o for o in occ if o[1].line == line and prev_call < o[0] < ti]
            if not cands and line in g.once_heads:
                continue  # a loop that never comes round is an if to javac: it may be drawn as one
            if not cands:
                problems.append(f'S: SWITCH {sw["taskReferenceName"]} at line {line} before call {ci + 1}: the code made no such decision there')
                continue
            matched[id(sw)] = cands[-1]
            j, d, key = cands[-1]
            stats['switches'] += 1
            drawn = (sw.get('inputParameters') or {}).get('switchCaseValue')
            if not same_key(drawn, d.sdk_key.get(key)):
                problems.append(f'S: SWITCH {sw["taskReferenceName"]} at line {line} shows {drawn}, the code took {key}')
            check_loops(sw, d, 'SWITCH ' + sw['taskReferenceName'])
        pending = []
        if node.kind != 'throw':
            check_loops(e, node, 'task ' + e['taskReferenceName'])
        # S: each SWITCH around the task is open and holds it in its region
        live = open_at(ti)
        for sw in anc:
            o = matched.get(id(sw))
            if o is None:
                continue
            j, d, key = o
            if all(o[0] != l[0] for l in live) or node.id not in g.region(d, key, ipdom):
                problems.append(f'S: task {e["taskReferenceName"]} (line {node.line}) is drawn inside {sw["taskReferenceName"]}, outside the arm it ran in')
        # C: each decision whose arm holds the task as written is around it
        anc_occ = {matched[id(sw)][0] for sw in anc if id(sw) in matched}
        for j, d, key in live:
            inside = node.id in g.region(d, key, ipdom)
            if inside and d.sdk_key.get(key) is not None and j not in anc_occ \
                    and sure.get((j, ci), True):
                problems.append(f'C: task {e["taskReferenceName"]} (line {node.line}) is not drawn inside the decision at line {d.line} ({key}), whose arm it is in')
        prev_call = ti
        ci += 1
    for sw, _ in pending:  # after the last call
        line = line_of(sw)
        cands = [o for o in occ if o[1].line == line and o[0] > prev_call]
        if not cands and line in g.once_heads:
            continue
        if not cands:
            problems.append(f'S: SWITCH {sw["taskReferenceName"]} at line {line} after the last call: no such decision')
        else:
            matched[id(sw)] = cands[-1]
            stats['switches'] += 1
            drawn = (sw.get('inputParameters') or {}).get('switchCaseValue')
            if not same_key(drawn, cands[-1][1].sdk_key.get(cands[-1][2])):
                problems.append(f'S: SWITCH {sw["taskReferenceName"]} at line {line} shows {drawn}, the code took {cands[-1][2]}')

    # C: decisions passed between two calls that the rules say are known
    drawn_occ = {o[0] for o in matched.values()}
    entered = {id(sw) for e, anc in entries if e.get('type') != 'SWITCH' for sw in anc}
    bounds = [(-1, g.entry)] + [(ti, n) for ti, n in calls] + ([] if thrown else [(len(trace), g.exit)])
    for (i1, n1), (i2, n2) in zip(bounds, bounds[1:]):
        known = g.must_pass(n1, n2)
        between = [o for o in occ if i1 < o[0] < i2]
        for j, d, key in between:
            if not d.drawable:
                continue
            if d.id in known and d.sdk_key.get(key) is not None:
                if known[d.id] != d.succ[key].id:
                    problems.append(f'checker: must-pass says another arm at line {d.line} than the run took ({key})')
                if (d.id, key) not in n2.arms:
                    stats['passed'] += 1
                # inside a decision that is itself not drawn: the outermost-only rule
                outer = [o for o in open_at(j) if (o[1].id, o[2]) in d.arms]
                if j not in drawn_occ and all(o[0] in drawn_occ for o in outer):
                    problems.append(f'C: the decision at line {d.line} ({key}) was passed between two calls and can be known, but is not drawn')
            elif j not in drawn_occ:
                stats['hidden'] += 1
        for j, d, key in between:
            sw = next((e for e, _ in entries if id(e) in matched and matched[id(e)][0] == j), None)
            if sw is not None and id(sw) not in entered and known.get(d.id) != d.succ[key].id:
                problems.append(f'S: the decision at line {d.line} is drawn as passed ({key}) without a call, '
                                f'but the code could have gone another way or round it again')

    # P: a case not taken previews its arm's calls; the taken case holds no previews
    for e, anc in entries:
        if e.get('type') != 'SWITCH' or id(e) not in matched:
            continue
        j, d, taken = matched[id(e)]
        if not d.drawable:
            problems.append(f'S: the decision at line {d.line} is drawn, but no arm of it calls the server or a case falls through')
        taken_sdk = d.sdk_key.get(taken)
        groups = {}
        for k, sk in d.sdk_key.items():
            if sk is not None:
                groups.setdefault(sk, []).append(k)
        cases = e.get('decisionCases') or {}
        for sk, keys in groups.items():
            server = next((k for k in cases if same_key(k, sk)), None)
            lst = (e.get('defaultCase') if sk in ('default', 'false') else cases.get(server)) or []
            if same_key(sk, taken_sdk):
                if any((t.get('metadata') or {}).get('preview') and not (t.get('metadata') or {}).get('previewAfter') for t in lst):
                    problems.append(f'P: the taken case {sk} of the decision at line {d.line} holds previews')
                continue
            names = preview_names(lst)
            must, may = [], []
            for k in keys:
                _, inregion = static_calls(g, d, k, ipdom)
                must += ['g_' + n.task for n in inregion]
                may += ['g_' + n.task for n in inregion]
            if any(names.count(x) > may.count(x) for x in set(names)) or any(must.count(x) > names.count(x) for x in set(must)):
                problems.append(f'P: case {sk} of the decision at line {d.line} previews {sorted(names)}, its arm calls {sorted(must)}')
    return problems, stats


# ------------------------------------------------------------------ catalogue: shapes found by hand

def catalogue():
    """Shapes worth always running: each once found a problem, or is easy to get wrong."""
    def c(t, a='v + 1'):
        return Call(t, a)
    fleet_like = [Loop('each', 'f1', 3, [
        Switch('str', 'v + f1', 'arrow', [(['"red"'], [c('a')], False), (['"green"'], [c('b')], False)], []),
        Loop('each', 'f2', 2, [
            c('c', 'v + f2'),
            If('v % 2 == 0', [c('a')], [If('v % 3 == 1', [c('b'), c('c')], [c('a', 'v + 2'), Jump('continue')], elif_=True)]),
            Loop('whilecall', 'w3', 2, [c('a', 'v + w3')]),
            Switch('enum', 'v + f2', 'arrow', [(['RED'], [c('b')], False), (['GREEN'], [c('c')], False), (['BLUE'], [], False)]),
        ]),
        If('v % 2 == 0', [c('a')], [c('b')]),
    ])]
    guard = [Loop('while', 'w1', 3, [If('v % 2 == 0', [c('a'), Jump('continue')]), c('b'), c('c')])]
    for_guard = [Loop('for', 'i1', 3, [If('v % 2 == 0', [c('a'), Jump('continue')], [c('b')]), c('c')])]
    top_switch = [Loop('for', 'i1', 3, [
        Switch('int', '', 'old', [(['0'], [c('a')], False), (['1'], [c('b')], False)], []),
        c('c')])]
    empty_case = [Loop('for', 'i1', 3, [
        Switch('enum', 'v + i1', 'arrow', [(['RED'], [], False), (['GREEN'], [c('b')], False)], [c('c')]), c('a')])]
    do_if = [Loop('do', 'd1', 3, [c('a'), If('d1 == 1', [c('b')]), If('v > 2', [Jump('break')])]), c('c')]
    # a decision gone past without a call on every pass of a loop: it cannot be drawn as passed once
    # (a do-while, so every way between the calls goes through it, and round it again)
    round_and_round = [c('a'), Loop('do', 'd1', 3, [If('v % 2 == 5', [c('b')])]), c('c')]
    # a loop inside an arm inside a loop: from the inner loop's last call to the next outer pass's
    # first, the code may or may not have decided the arm again
    pass_or_not = [Loop('for', 'i1', 3, [If('v > 3 || i1 == 0', [Loop('each', 'f2', 2, [c('b'), c('c')])], [c('a')])])]
    # `if (...) { ...; break; }` last in a loop: its arm is in the loop, though it never goes round
    break_last = [Loop('whilecall', 'w1', 1, [Loop('while', 'w2', 2, [c('a', 'v + w2'),
                                                                       If('w1 == 1', [c('b'), c('c'), Jump('break')])])])]
    # a helper called in a loop, with ifs before and after its calls, one passed after its last call
    h1 = Helper('h1', [c('a'), If('v % 2 == 0', [c('b')], [c('c')]), If('v % 3 == 1', [c('a')])])
    helper_loop = Program([Loop('for', 'i1', 3, [HelperCall(h1), If('i1 == 1', [c('c')])]), c('b')], [h1])
    # a helper returning early, and the code after its call
    h2 = Helper('h2', [c('a'), If('v % 2 == 0', [c('b'), Return()]), c('c')])
    helper_return = Program([HelperCall(h2), If('v > 2', [c('a')], [c('b')]), HelperCall(h2)], [h2])
    # the workflow method returning early from an arm
    early_return = [c('a'), If('v % 2 == 0', [c('b'), Return()]), Switch('enum', 'v + 1', 'arrow',
                    [(['RED'], [c('c')], False)], [c('a')])]
    # a throw in an arm, inside a loop: the mark goes in the arm it was thrown from
    throw_loop = [Loop('for', 'i1', 3, [c('a'), If('i1 == 2 && v % 2 == 0', [c('b'), Throw()])]), c('c')]
    # a throw in a helper
    h3 = Helper('h3', [c('b'), If('v % 3 == 1', [Throw()], [c('c')])])
    helper_throw = Program([c('a'), HelperCall(h3), c('a')], [h3])
    return [fleet_like, guard, for_guard, top_switch, empty_case, do_if, round_and_round, pass_or_not, break_last,
            helper_loop, helper_return, early_return, throw_loop, helper_throw]


# ------------------------------------------------------------------ running

def http(server, method, path, body=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(server + path, data=data, method=method, headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(req, timeout=30) as r:
        raw = r.read().decode()
        return json.loads(raw) if raw.startswith(('{', '[')) else raw


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--server', required=True)
    ap.add_argument('--programs', type=int, default=30)
    ap.add_argument('--seeds', type=int, default=4)
    ap.add_argument('--seed', type=int, default=1)
    ap.add_argument('--sdk', default=os.environ.get('JAVA_SDK', os.path.join(HERE, '../../../../sdk/java-sdk')))
    ap.add_argument('--java', default=os.environ.get('JAVA_HOME', ''))
    ap.add_argument('--keep', help='directory to keep the programs and executions in')
    ap.add_argument('--no-catalogue', action='store_true')
    args = ap.parse_args()
    if ':8080' in args.server:
        sys.exit('refusing port 8080')

    rnd = random.Random(args.seed)
    progs = [] if args.no_catalogue else [p if isinstance(p, Program) else Program(p, []) for p in catalogue()]
    progs += [Gen(rnd).program() for _ in range(args.programs)]
    run_id = f'{int(time.time()) % 100000:05d}'
    work = args.keep or tempfile.mkdtemp(prefix='drawing-')
    src = os.path.join(work, 'src', 'drawing', 'gen')
    classes = os.path.join(work, 'classes')
    shutil.rmtree(os.path.join(work, 'src'), ignore_errors=True)
    os.makedirs(src)
    os.makedirs(classes, exist_ok=True)
    built = []
    for k, prog in enumerate(progs):
        cls, name = f'G{run_id}_{k:03d}', f'gen_{run_id}_{k:03d}'
        em = Emitter(cls)
        text = em.emit(prog, name)
        with open(os.path.join(src, cls + '.java'), 'w') as f:
            f.write(text)
        built.append((cls, name, Graph(prog, em), text))

    java = os.path.join(args.java, 'bin', 'java') if args.java else 'java'
    javac = os.path.join(args.java, 'bin', 'javac') if args.java else 'javac'
    lib = os.path.join(os.path.abspath(args.sdk), 'native-examples', 'build', 'install', 'native-examples', 'lib', '*')
    r = subprocess.run([javac, '-parameters', '-g', '-cp', lib, '-d', classes] + [os.path.join(src, c + '.java') for c, *_ in built],
                       capture_output=True, text=True)
    if r.returncode:
        print(r.stderr[:4000])
        sys.exit('the generated programs do not compile')

    env = dict(os.environ, CONDUCTOR_SERVER_URL=args.server, CONDUCTOR_DEV='true')
    log = open(os.path.join(work, 'host.log'), 'w')
    host = subprocess.Popen([java, '-cp', classes + os.pathsep + lib, 'drawing.DrawingHost'] + ['drawing.gen.' + c for c, *_ in built],
                            env=env, stdout=log, stderr=subprocess.STDOUT)
    try:
        for _, name, *_ in built:
            for _ in range(600):
                try:
                    http(args.server, 'GET', f'/metadata/workflow/{name}?version=1')
                    break
                except urllib.error.HTTPError:
                    if host.poll() is not None:
                        sys.exit('the host stopped: see ' + log.name)
                    time.sleep(0.5)
            else:
                sys.exit(f'{name} was not registered: see {log.name}')

        def one(item, seed):
            cls, name, g, _ = item
            wid = http(args.server, 'POST', f'/workflow/{name}?version=1', {'seed': seed})
            for _ in range(240):
                wf = http(args.server, 'GET', f'/workflow/{wid}?includeTasks=true')
                if wf['status'] in ('COMPLETED', 'FAILED', 'TERMINATED', 'TIMED_OUT'):
                    break
                time.sleep(0.5)
            problems, stats = check(g, seed, wf)
            return cls, seed, wid, problems, stats, wf

        jobs = [(item, s) for item in built for s in range(args.seeds)]
        totals = {'calls': 0, 'switches': 0, 'passed': 0, 'hidden': 0}
        bad = []
        replays = os.path.join(work, 'replay')
        shutil.rmtree(replays, ignore_errors=True)
        os.makedirs(replays)
        ids = {}
        with cf.ThreadPoolExecutor(8) as pool:
            for cls, seed, wid, problems, stats, wf in pool.map(lambda j: one(*j), jobs):
                for k in totals:
                    totals[k] += stats[k]
                ids[(cls, seed)] = wid
                if wf.get('status') in ('COMPLETED', 'FAILED'):
                    with open(os.path.join(replays, f'{cls}-{seed}.json'), 'w') as f:
                        json.dump(wf, f)
                if problems:
                    bad.append((cls, seed, wid, problems))
                    if args.keep:
                        with open(os.path.join(work, f'{cls}-{seed}.json'), 'w') as f:
                            json.dump(wf, f, indent=1)
    finally:
        host.kill()

    # D: every recorded execution replays against its code with no divergence
    r = subprocess.run([java, '-cp', classes + os.pathsep + lib, 'drawing.ReplayCheck', replays],
                       env=env, capture_output=True, text=True)
    replayed, diverged = 0, 0
    for line in r.stdout.split('\n'):
        if line.startswith('OK '):
            replayed += 1
        elif line.startswith('BAD '):
            name, why = line[4:].split(': ', 1) if ': ' in line[4:] else (line[4:], '')
            cls, seed = name[:-5].rsplit('-', 1)
            entry = next((b for b in bad if b[0] == cls and b[1] == int(seed)), None)
            if entry is None:
                entry = (cls, int(seed), ids.get((cls, int(seed))), [])
                bad.append(entry)
            entry[3].append('D: replay diverges: ' + why[:300])
            diverged += 1
    # every recorded execution must get a verdict from a replay tool that ran to the end
    handed = len([f for f in os.listdir(replays) if f.endswith('.json')])
    if r.returncode or replayed + diverged != handed:
        print(r.stderr[:2000])
        bad.append(('ReplayCheck', 0, None, [f'D: {replayed + diverged} of {handed} executions got a replay verdict'
                                             f' (exit {r.returncode})']))
    if not jobs:
        bad.append(('(nothing)', 0, None, ['no program was run: that is not a pass']))

    for cls, seed, wid, problems in bad:
        print(f'{cls} seed {seed} ({wid}):')
        for p in problems[:6]:
            print('   ', p)
    print(f'DRAWING programs={len(built)} executions={len(jobs)} calls={totals["calls"]} switches={totals["switches"]} '
          f'passed-and-known={totals["passed"]} passed-and-unknowable={totals["hidden"]} replayed={replayed} failing={len(bad)}')
    print('kept in', work)
    sys.exit(1 if bad else 0)


if __name__ == '__main__':
    main()
