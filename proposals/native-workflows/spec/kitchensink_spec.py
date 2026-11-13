#!/usr/bin/env python3
"""
Reference semantics of KitchenSinkWorkflow (native-examples/src/main/java/kitchensink), written
from the Java source and independent of the SDK, used as a trace checker.

    Spec(input, Omega) = the unique execution the program denotes, given its input and an oracle
    Omega of the values the program does not determine: results of external systems (HTTP, LLM,
    MCP, agent, the human answer, the callback payload, generated ids) and the timing of the two
    handler messages.

The checker interprets the program against a recorded execution. Every step the program takes in
lane L consumes the next recorded event of L and must match it exactly (task type, the inputs the
code passes, number of attempts, final status); values the program does not determine are read from
the recording (that is Omega), everything else is computed here and compared. Theorems checked per
execution (each failure is a counterexample with its location):

  T1 Faithfulness   the recording, lane by lane, is exactly the sequence of steps the program takes:
                    no step missing, none extra, every input as computed from earlier results.
  T2 Task semantics every task's output is what KitchenSinkTasks/SubFlow compute for its input
                    (external tasks excepted, listed in Omega).
  T3 Decisions      every SWITCH took the arm its Java condition evaluates to; its definition entry
                    has every arm of the code; untaken arms hold only previews, naming exactly the
                    tasks that arm calls, and no preview ever ran.
  T4 Order          in each lane a step is scheduled only after the previous blocking step ended
                    (the code was paused on it); a join ends only after every lane it waits for,
                    including lanes those lanes started.
  T5 Retries        each task ran the number of attempts its definition allows: 1 when it
                    succeeded, retryCount+1 for a failing HTTP/LLM call, 1 for the no-retry charge.
  T6 Outcome        status COMPLETED, output and workflow variables equal the program's.
  T7 Children       every child workflow (sub_flow_1) is checked the same way against SubFlow.run.

With --chaos, for executions run while workers were killed, paused and cut off (durability.
KitchenSinkChaos), Omega also holds what infrastructure failures decide, and nothing else changes:
  T5   a task may take more attempts than its definition's retries would need when a worker died
       holding it: every attempt before the last ended unsuccessfully, with the same input
  T2   check_status counts its calls per worker process, and a restarted process counts from zero,
       so when it reports done is Omega: its output must still say done after 3 or more checks,
       or still running
  charge, which has no retries, may fail because its worker died: the code must then compensate
       exactly as for an over-limit charge
  any task may fail for good (the chaos run injects terminal failures, and errors that can exhaust
       its retries): the program then goes on as the Java does. The failure is raised at the call
       as a TaskFailure; the code catches it around get_es_1 and around charge and the task_30 after
       it (compensating with refund when the charge had succeeded); in an async lane it ends the
       lane and is raised where the code waits for the lane (join, get), and a lane nothing waits
       for keeps it to itself. One that reaches the workflow method fails the run: the run must be
       FAILED with "task <ref> <status>: <reason>" as its reason, that task shown failed, and nothing
       recorded after it but tasks cancelled in flight. Children are checked the same way.

    python3 kitchensink_spec.py [--server http://localhost:8099/api] <workflow id>...

The ids are the executions kitchensink.Main starts (odd, even, failure); it prints PROVED with the
number of checks per theorem, or the first COUNTEREXAMPLE. kitchensink_mutants.py shows it can fail.
"""
import json, re, sys, urllib.request

SERVER = 'http://localhost:8099/api'
CHAOS = False
FAILURES = {'FAILED', 'FAILED_WITH_TERMINAL_ERROR', 'TIMED_OUT', 'CANCELED', 'COMPLETED_WITH_ERRORS'}
BLOCKING = {'SIMPLE', 'WAIT', 'HTTP', 'LLM_CHAT_COMPLETE', 'LIST_MCP_TOOLS', 'CALL_MCP_TOOL', 'AGENT',
            'HUMAN', 'SUB_WORKFLOW', 'JOIN'}


class Violation(Exception):
    pass


def get(path):
    with urllib.request.urlopen(SERVER + path, timeout=30) as r:
        return json.load(r)


def walk(seq):
    for t in seq:
        yield t
        for c in (t.get('decisionCases') or {}).values():
            yield from walk(c)
        yield from walk(t.get('defaultCase') or [])
        for b in t.get('forkTasks') or []:
            yield from walk(b)
        yield from walk(t.get('loopOver') or [])


class TaskFailed(Exception):
    """A task the code called failed for good: the code sees a TaskFailure at the call."""

    def __init__(self, event):
        super().__init__(event.ref)
        self.event = event


def recorded_value(w, name):
    """A value once() recorded: on the marker of the continuation that computed it."""
    markers = [t.get('continuation') or {} for t in w['tasks']] + [(w.get('nativeState') or {}).get('bootstrap') or {}]
    for m in markers:
        v = (m.get('recorded') or {}).get(name)
        if isinstance(v, dict) and 'value' in v:
            return v['value']
    return None


def failure_message(e):
    """The SDK's TaskFailure message, which a run it fails takes as its reason."""
    status = e.output.get('conductor.failedStatus', e.last['status'])
    return f'task {e.ref} {status}: {e.last.get("reasonForIncompletion")}'


class Event:
    """One step as recorded: every attempt of one reference name."""

    def __init__(self, attempts):
        self.attempts = sorted(attempts, key=lambda t: t['seq'])
        self.first, self.last = self.attempts[0], self.attempts[-1]
        self.ref = self.first['referenceTaskName']
        self.type = self.first['taskType']
        wt = self.first.get('workflowTask') or {}
        self.wtype = wt.get('type')
        self.entry = wt
        self.lane = (wt.get('metadata') or {}).get('lane')
        self.sched = self.first['scheduledTime']
        self.end = self.last['endTime']

    @property
    def input(self):
        return self.first.get('inputData') or {}

    @property
    def output(self):
        return self.last.get('outputData') or {}


class Run:
    def __init__(self, w, checks):
        self.w = w
        self.checks = checks
        by_ref = {}
        for t in w['tasks']:
            if t['taskType'].endswith('.code'):
                continue
            by_ref.setdefault(t['referenceTaskName'], []).append(t)
        self.events = {r: Event(a) for r, a in by_ref.items()}
        self.lanes = {}
        for e in sorted(self.events.values(), key=lambda e: e.first['seq']):
            self.lanes.setdefault(e.lane, []).append(e)
        self.defs = {t['taskReferenceName']: t for t in walk(w['workflowDefinition']['tasks'])}
        self.consumed = set()

    def ok(self, theorem, cond, where, msg):
        self.checks[theorem] = self.checks.get(theorem, 0) + 1
        if not cond:
            raise Violation(f'{theorem} at {where}: {msg}')


class Lane:
    def __init__(self, run, lane_id, start=0):
        self.run, self.id, self.i, self.last_end = run, lane_id, 0, start
        self.events = run.lanes.get(lane_id, [])
        self.children = []   # lanes started from this one, for joins that wait on what it left running
        self.failure = None  # the TaskFailure that ended the lane, if one did

    def next(self, what):
        self.run.ok('T1', self.i < len(self.events), self.id, f'the code {what} next, but the lane has no more steps')
        e = self.events[self.i]
        self.i += 1
        self.run.consumed.add(e.ref)
        return e

    def order(self, e):
        self.run.ok('T4', e.sched >= self.last_end, e.ref,
                    f'scheduled {self.last_end - e.sched} ms before the step the code waited on ended')
        if e.wtype in BLOCKING:
            self.last_end = e.end

    def finished(self):
        self.run.ok('T1', self.i == len(self.events), self.id,
                    f'steps the code never takes: {[e.ref for e in self.events[self.i:]]}')

    # ----------------------------------------------------------------- steps of the program

    def task(self, name, inp, wtype='SIMPLE', attempts=1, status='COMPLETED'):
        e = self.next(f'calls {name}')
        r = self.run
        r.ok('T1', e.entry.get('name') == name and e.wtype == wtype, e.ref,
             f'expected {wtype} {name}, recorded {e.wtype} {e.entry.get("name")}')
        for k, v in inp.items():
            r.ok('T1', e.input.get(k) == v, e.ref, f'input {k}: code passes {v!r}, recorded {e.input.get(k)!r}')
        if CHAOS:
            r.ok('T5', len(e.attempts) >= attempts, e.ref, f'{len(e.attempts)} attempts, at least {attempts} expected')
            for earlier in e.attempts[:-1]:
                r.ok('T5', earlier['status'] in FAILURES, e.ref, f'an attempt before the last ended {earlier["status"]}')
                for k, v in inp.items():
                    r.ok('T5', (earlier.get('inputData') or {}).get(k) == v, e.ref, f'a retry ran with another {k}')
        else:
            r.ok('T5', len(e.attempts) == attempts, e.ref, f'{len(e.attempts)} attempts, expected {attempts}')
        if status == 'COMPLETED' and e.last['status'] != 'COMPLETED':
            r.ok('T6', e.last['status'] in FAILURES, e.ref, f'ended {e.last["status"]}')
            self.order(e)
            raise TaskFailed(e)
        r.ok('T6', e.last['status'] == status, e.ref, f'status {e.last["status"]}, expected {status}')
        self.order(e)
        return e

    def simple(self, name, inp, result=None, **kw):
        """A SIMPLE task whose output the task class determines; result is that output."""
        e = self.task(name, inp, **kw)
        if result is not None:
            self.run.ok('T2', e.output == result, e.ref, f'output {e.output}, {name} computes {result}')
        return e.output

    def switch(self, ref, taken, arms, loops=0):
        """arms: case key -> the task names that arm calls (in order); 'default' is the else/default."""
        e = self.next(f'decides {ref}')
        r = self.run
        r.ok('T3', e.wtype == 'SWITCH' and e.ref == ref, e.ref, f'expected SWITCH {ref}, recorded {e.wtype} {e.ref}')
        r.ok('T3', e.output.get('selectedCase') == taken, ref, f'took {e.output.get("selectedCase")}, the condition is {taken}')
        d = r.defs[ref]
        cases = dict(d.get('decisionCases') or {})
        r.ok('T3', set(cases) == {k for k in arms if k != 'default'}, ref, f'cases {sorted(cases)}, code has {sorted(arms)}')
        cases['default'] = d.get('defaultCase') or []
        for key, names in arms.items():
            entries = cases[key]
            arm_taken = key == taken or (key == 'default' and taken not in cases)
            if arm_taken:
                r.ok('T3', not any((x.get('metadata') or {}).get('preview') for x in entries), ref, f'taken arm {key} holds a preview')
            else:
                r.ok('T3', all((x.get('metadata') or {}).get('preview') for x in walk(entries)), ref, f'untaken arm {key} holds a real step')
                shown = ['SWITCH' if x['type'] == 'SWITCH' else x['name'] for x in entries]
                r.ok('T3', shown == names, ref, f'arm {key} previews {shown}, the code calls {names}')
                for x in walk(entries):
                    r.ok('T3', x['taskReferenceName'] not in r.events, ref, f'preview {x["taskReferenceName"]} ran')
        r.ok('T3', len((d.get('metadata') or {}).get('loops') or []) == loops, ref,
             f'tagged with {(d.get("metadata") or {}).get("loops")}, it is inside {loops} Java loop(s)')
        self.order(e)
        return taken

    def fork(self, n, name='fork'):
        e = self.next(f'starts {n} lanes')
        r = self.run
        r.ok('T1', e.type == 'FORK' and e.ref.startswith(name), e.ref, f'expected a fork, recorded {e.type} {e.ref}')
        branches = e.entry.get('forkTasks') or []
        r.ok('T1', len(branches) == n, e.ref, f'{len(branches)} lanes, the code starts {n}')
        self.order(e)
        lanes = [Lane(r, b[0]['metadata']['lane'], e.sched) for b in branches]
        self.children += lanes
        return lanes

    def join(self, lanes):
        e = self.next('joins')
        r = self.run
        r.ok('T1', e.type == 'JOIN', e.ref, f'expected a join, recorded {e.type}')
        for lane in lanes:
            lane.finished()
            for x in all_events(lane):
                r.ok('T4', x.end <= e.end, e.ref, f'ended before {x.ref} of a lane it waits for')
        self.order(e)
        for lane in lanes:
            if lane.failure is not None:
                raise lane.failure
        return e


def go(lane, body):
    """Runs a lane's code; a failure ends the lane and waits for whoever joins it."""
    try:
        return body()
    except TaskFailed as f:
        lane.failure = f
        return None


def all_events(lane):
    yield from lane.events
    for c in lane.children:
        yield from all_events(c)


# --------------------------------------------------------------------- the program

def failed_run(r, w, f):
    """A failure reached the workflow method: the run failed there, and nothing ran after it."""
    e = f.event
    r.ok('T1', CHAOS, e.ref, f'the task failed for good ({e.last["status"]}) where nothing injects failures')
    r.ok('T6', w['status'] == 'FAILED', w['workflowId'], f'the code raised at {e.ref}, the run is {w["status"]}')
    r.ok('T6', w.get('reasonForIncompletion') == failure_message(e), w['workflowId'],
         f'reason {w.get("reasonForIncompletion")!r}, the code raised {failure_message(e)!r}')
    r.ok('T6', e.last['status'] in {'FAILED', 'FAILED_WITH_TERMINAL_ERROR', 'TIMED_OUT'}, e.ref,
         f'the task the run failed at shows {e.last["status"]}')
    for ref, x in r.events.items():
        if ref not in r.consumed:
            r.ok('T1', x.last['status'] == 'CANCELED', ref, f'ran after the failure ({x.last["status"]})')
    return 'FAILED at ' + e.ref


def sub_flow(w, checks):
    """SubFlow.run(input): task5(mod, oddEven); task6(five.oddEven); return SubFlowResult(five, six)."""
    r = Run(w, checks)
    try:
        return sub_flow_body(r, w)
    except TaskFailed as f:
        failed_run(r, w, f)
        return None


def sub_flow_body(r, w):
    main = Lane(r, 'main')
    i = w['input']
    five = main.simple('task_5', {'mod': i['mod'], 'oddEven': i['oddEven']}, {'mod': i['mod'], 'oddEven': i['oddEven']})
    six = main.simple('task_6', {'previous': five['oddEven']}, {'result': 'task_6 after ' + five['oddEven']})['result']
    main.finished()
    r.ok('T6', w['status'] == 'COMPLETED', w['workflowId'], w['status'])
    r.ok('T6', w['output'] == {'task5': five, 'task6': six}, w['workflowId'], f'output {w["output"]}')
    r.ok('T1', r.consumed == set(r.events), w['workflowId'], f'unexplained {set(r.events) - r.consumed}')
    return {'task5': five, 'task6': six}


def kitchensink(w, checks, children):
    r = Run(w, checks)
    try:
        return kitchensink_body(r, w, checks, children)
    except TaskFailed as f:
        return failed_run(r, w, f)


def kitchensink_body(r, w, checks, children):
    inp = w['input']
    mod, odd_even = inp['mod'], inp['oddEven']
    main = Lane(r, 'main')
    notes_code = []          # notes the workflow method adds, in order

    def child(lane, data):
        try:
            e = lane.task('sub_flow_1', {}, wtype='SUB_WORKFLOW')
        except TaskFailed as f:
            # the child failed: it is checked as a failed run, and the failure goes on up
            kid = f.event.output.get('subWorkflowId')
            if kid:
                r.ok('T7', sub_flow(get(f'/workflow/{kid}?includeTasks=true'), checks) is None, f.event.ref,
                     'the child task failed, the child did not')
                children.append(kid)
            raise
        r.ok('T1', e.input.get('workflowInput') == data, e.ref, f'child input {e.input.get("workflowInput")}, code passes {data}')
        result = sub_flow(get(f'/workflow/{e.output["subWorkflowId"]}?includeTasks=true'), checks)
        children.append(e.output['subWorkflowId'])
        r.ok('T7', {k: v for k, v in e.output.items() if k != 'subWorkflowId'} == result, e.ref, 'child output differs')
        return result

    run_id = recorded_value(w, 'runId')    # once(): Omega, recorded on the marker of the continuation that ran it
    r.ok('T6', run_id is not None and re.fullmatch(r'ks-[0-9a-f-]{36}', run_id) is not None, 'runId', str(run_id))
    r.ok('T6', w['status'] != 'COMPLETED' or w['output'].get('runId') == run_id, 'runId', 'the output is not the recorded value')

    # section 1
    stats = main.simple('task_1', {'mod': mod, 'oddEven': odd_even}, {'mod': mod, 'oddEven': odd_even})
    main.task('event_0', dict(stats, sink='conductor'), wtype='EVENT')
    oe = main.simple('task_5', {'mod': stats['mod'], 'oddEven': stats['oddEven']}, {'mod': stats['mod'], 'oddEven': stats['oddEven']})
    r.ok('T1', r.events['task_2'].type == 'task_5', 'task_2', 'the dynamic task is named by task2Name')
    key = oe['oddEven']
    main.switch('switch_oddEven', key if key in ('0', '1') else 'default',
                {'0': ['task_4', 'fanout1', 'fanout1_join'], '1': ['fork', 'fork_join'], 'default': []})
    if key == '0':
        plan = main.simple('task_4', {'oddEven': oe}, {'tasks': [
            {'name': 'task_5', 'input': {'mod': oe['mod'], 'oddEven': oe['oddEven']}},
            {'name': 'task_6', 'input': {'previous': 'from task_4'}}]})
        lanes = main.fork(len(plan['tasks']), name='fanout1')
        results = []
        for lane, t in zip(lanes, plan['tasks']):
            if t['name'] == 'task_5':
                results.append(go(lane, lambda lane=lane, t=t: lane.simple('task_5', t['input'], dict(t['input']))))
            else:
                results.append(go(lane, lambda lane=lane, t=t: lane.simple(
                    'task_6', t['input'], {'result': 'task_6 after ' + t['input']['previous']})))
        main.join(lanes)
        notes_code.append(f'fanned out {len(results)} tasks')
    elif key == '1':
        a, b = main.fork(2)
        sub_in = {'mod': stats['mod'], 'oddEven': stats['oddEven']}
        wf3 = go(a, lambda: (a.simple('task_10', {}, {}), child(a, sub_in))[1])
        wf4 = go(b, lambda: (b.simple('task_11', {}, {}), child(b, sub_in))[1])
        main.join([a, b])
        notes_code.append(f'sub flows: {wf3["task6"]} / {wf4["task6"]}')
    url = 'http://localhost:9200/conductor/_search?size=10'
    es = main.task('get_es_1', {}, wtype='HTTP', attempts=4, status='COMPLETED_WITH_ERRORS')   # Omega: Elasticsearch absent; caught
    r.ok('T1', es.input['http_request']['uri'] == url and es.input['http_request']['method'] == 'GET', 'get_es_1', 'request')
    statuses, workflow_ids = [], []
    notes_code.append('search unavailable: ' + failure_message(es))
    summary = main.simple('task_30', {'statuses': statuses, 'workflowIds': workflow_ids}, {'count': len(statuses) + len(workflow_ids)})

    # section 2: ticks while not cancelled (Omega: when the cancel signal arrived)
    ticks = 0
    while ticks < 3 and main.i < len(main.events) and main.events[main.i].type == 'WAIT' \
            and main.events[main.i].input.get('duration') == f'{ticks + 1}s':
        main.task('sleep', {'duration': f'{ticks + 1}s'}, wtype='WAIT')
        ticks += 1
    r.ok('T6', ticks == 3 or w['variables']['cancelled'], 'ticks', 'the tick loop stopped early without a cancel')
    r.ok('T6', sum(1 for n in w['variables']['notes'] if n.startswith('tick ')) == ticks, 'ticks', 'one note per tick')
    check_count = {}

    def check_status(lane, rid):
        check_count[rid] = check_count.get(rid, 0) + 1
        if CHAOS:
            out = lane.simple('check_status', {'runId': rid})
            m = re.fullmatch(r'finished after (\d+) checks', out.get('detail', ''))
            r.ok('T2', (out['done'] and m is not None and int(m.group(1)) >= 3)
                 or (not out['done'] and out.get('detail') == 'still running'), f'check_status {rid}', str(out))
            return out
        done = check_count[rid] >= 3
        return lane.simple('check_status', {'runId': rid},
                           {'done': done, 'detail': f'finished after {check_count[rid]} checks' if done else 'still running'})

    status = check_status(main, run_id)
    wait, attempt = 5, 1
    while not status['done'] and attempt < 20:
        main.task('sleep', {'duration': f'{wait}s'}, wtype='WAIT')
        wait = min(wait * 2, 300)
        status = check_status(main, run_id)
        attempt += 1
    receipt = None
    # try { receipt = charge(...); compensations.push(refund); task30(...) } catch (TaskFailure e) { compensate }
    try:
        receipt = main.simple('charge', {'runId': run_id, 'amount': mod * 100})
        r.ok('T2', mod * 100 <= 1000, 'charge', 'charged over the limit')
        r.ok('T2', receipt['amount'] == mod * 100 and re.fullmatch(r'[0-9a-f-]{36}', receipt['id']), 'charge', str(receipt))
        main.simple('task_30', {'statuses': ['after-charge'], 'workflowIds': workflow_ids}, {'count': 1 + len(workflow_ids)})
    except TaskFailed as f:
        failed = f.event
        if failed.ref == 'charge' and mod * 100 > 1000:
            r.ok('T2', failed.last.get('reasonForIncompletion') == f'amount over limit: {mod * 100}', 'charge', 'the over-limit failure')
        else:
            r.ok('T2', CHAOS, failed.ref, 'failed where nothing injects failures')
        notes_code.append('compensating: ' + failure_message(failed))
        if failed.ref != 'charge':
            main.simple('refund', {'runId': run_id, 'amount': receipt['amount']}, {})  # the pushed compensation
        receipt = receipt if failed.ref != 'charge' else None
    approval = main.task('approval', {'runId': run_id, 'amount': mod * 100, 'assignee': 'finance'}, wtype='HUMAN').output
    callback = main.task('callback', {'duration': '3600s'}, wtype='WAIT').output
    r.ok('T6', w['variables']['cancelled'], 'awaitCondition', 'no WAIT was scheduled, so the condition held at once')
    main.task('start_sub_flow_1', {'startWorkflow': {'input': {'mod': mod, 'oddEven': '1'}, 'name': 'sub_flow_1', 'version': 1}},
              wtype='START_WORKFLOW')
    started = r.events['start_sub_flow_1'].output['workflowId']
    sub_flow(get(f'/workflow/{started}?includeTasks=true'), checks)
    children.append(started)
    notes = w['variables']['notes']
    llm = main.task('delivery_note', {}, wtype='LLM_CHAT_COMPLETE', attempts=4, status='COMPLETED_WITH_ERRORS')  # Omega: no model
    r.ok('T1', llm.input['messages'][0]['message'] == 'Summarize these notes in one sentence: ' + '; '.join(notes),
         'delivery_note', 'the prompt is the notes at that point')
    note = '; '.join(notes)

    # section 3
    labels = [main.simple('label_item', {'item': b}, {'result': f'LABEL[{b}]'})['result'] for b in ('box-1', 'box-2', 'box-3')]
    r.ok('T1', main.events[main.i].input.get('until', '').endswith('UTC'), 'sleep_until', 'a WAIT until an instant')
    main.task('sleep_until', {}, wtype='WAIT')
    tools = main.task('mcp_tools', {'mcpServer': inp['mcpServer']}, wtype='LIST_MCP_TOOLS').output['tools']
    weather = main.task('mcp_weather', {'mcpServer': inp['mcpServer'], 'method': 'get_weather', 'arguments': {'city': 'Seattle'}},
                        wtype='CALL_MCP_TOOL').output
    reply = main.task('support_agent', {'agentUrl': inp['agentUrl'], 'text': 'Summarize order ' + run_id}, wtype='AGENT').output

    # section 4
    lane = oe['oddEven']
    composed = []
    odd = lane == '1'
    main.switch('if_odd', 'true' if odd else 'false', {'true': ['SWITCH'], 'default': ['SWITCH']})
    if odd:
        big = mod > 3
        main.switch('if_big', 'true' if big else 'false', {'true': ['step'], 'default': ['step']})
        label = 'odd-big' if big else 'odd-small'
        composed.append(main.simple('step', {'label': label}, {'result': label})['result'])
    else:
        main.switch('switch_lane', '0' if lane == '0' else 'default', {'0': ['step'], 'default': []})
        if lane == '0':
            composed.append(main.simple('step', {'label': 'even-only'}, {'result': 'even-only'})['result'])
        else:
            composed.append('no task')
    for p, ref in ((1, 'if_pass'), (2, 'if_pass_2')):
        first = p % 2 == 1
        main.switch(ref, 'true' if first else 'false', {'true': ['step'], 'default': ['step']}, loops=1)
        label = 'while-odd' if first else 'while-even'
        composed.append(main.simple('step', {'label': label}, {'result': label})['result'])
    for rnd, ref in ((1, 'if_first'), (2, 'if_first_2')):
        name = f'r{rnd}'
        left, right = main.fork(2)
        lbl = name + ('-first' if rnd == 1 else '-later')
        lv = go(left, lambda: (left.switch(ref, 'true' if rnd == 1 else 'false', {'true': ['step'], 'default': ['step']}, loops=1),
                               left.simple('step', {'label': lbl}, {'result': lbl})['result'])[1])
        rv = go(right, lambda: right.simple('step', {'label': name + '-right'}, {'result': name + '-right'})['result'])
        main.join([left, right])
        composed += [lv, rv]
    a, b = main.fork(2)
    labelled = go(a, lambda: [a.simple('label_item', {'item': x}, {'result': f'LABEL[{x}]'})['result'] for x in ('x', 'y')])
    last = go(b, lambda: [b.simple('step', {'label': f'code-loop-{i}'}, {'result': f'code-loop-{i}'})['result'] for i in (1, 2)][-1])
    main.join([a, b])
    composed += labelled + [last]
    child_in = {'mod': mod, 'oddEven': lane}
    c, d = main.fork(2)
    go(c, lambda: child(c, child_in))

    def beside():
        d.switch('switch_side', '1' if lane == '1' else 'default', {'1': ['step'], 'default': ['step']})
        picked = 'beside-odd' if lane == '1' else 'beside-even'
        picked = d.simple('step', {'label': picked}, {'result': picked})['result']
        return d.simple('step', {'label': picked + '-then'}, {'result': picked + '-then'})['result']
    then = go(d, beside)
    main.join([c, d])
    composed.append(then)
    main.switch('if_lane', 'true' if lane == '1' else 'false', {'true': ['fork', 'fork_join'], 'default': ['fork', 'fork_join']})
    (e,) = main.fork(1)
    cl = 'case-lane-odd' if lane == '1' else 'case-lane-even'
    go(e, lambda: e.simple('step', {'label': cl}, {'result': cl}))
    j = main.join([e])
    composed.append(cl)
    (outer,) = main.fork(1)

    def outer_lane():
        outer.switch('if_startInner', 'true' if mod >= 0 else 'false', {'true': ['fork', 'fork_join'], 'default': []})
        (inner,) = outer.fork(1)
        go(inner, lambda: inner.simple('slow_step', {'label': 'inner-lane'}, {'result': 'inner-lane'}))  # nothing joins it
        detached = outer.next('lets the inner lane run on')
        r.ok('T1', detached.type == 'JOIN' and (detached.entry.get('metadata') or {}).get('detached'), detached.ref,
             'a non-waiting JOIN')
        return outer.simple('step', {'label': 'after-case'}, {'result': 'after-case'})['result']
    after_case = go(outer, outer_lane)
    jo = main.join([outer])
    r.ok('T4', jo.end >= r.events['slow_step'].end, jo.ref, 'the join waited for the lane started inside the if')
    aoj = main.simple('step', {'label': after_case}, {'result': after_case})
    r.ok('T4', r.events['after_outer_join'].sched >= r.events['slow_step'].end, 'after_outer_join', 'after the inner lane')
    composed.append(aoj['result'])
    for depth, ref in ((1, 'if_depth'), (2, 'if_depth_2')):
        main.switch(ref, 'true' if depth == 1 else 'false',
                    {'true': ['fork', 'fork_join'], 'default': ['label_item', 'step']}, loops=1)
        if depth == 1:
            dc, poll = main.fork(2)
            go(dc, lambda: child(dc, child_in))

            def polling():
                deep = check_status(poll, run_id + '-deep')
                while not deep['done']:
                    poll.task('sleep', {'duration': '1s'}, wtype='WAIT')
                    deep = check_status(poll, run_id + '-deep')
                return deep
            deep = go(poll, polling)
            main.join([dc, poll])
            composed.append('deep-done' if deep['done'] else 'deep-not-done')
        else:
            items = [main.simple('label_item', {'item': x}, {'result': f'LABEL[{x}]'})['result'] for x in ('p', 'q')]
            composed.append(main.simple('step', {'label': f'deep-{len(items)}'}, {'result': f'deep-{len(items)}'})['result'])
    k = 0
    for o in (1, 2):
        for i in (1, 2):
            k += 1
            ref = 'if_inner' if k == 1 else f'if_inner_{k}'
            main.switch(ref, 'true' if i == 1 else 'false', {'true': ['step'], 'default': ['step']}, loops=2)
            lbl = f'o{o}-' + ('first' if i == 1 else 'second')
            composed.append(main.simple('step', {'label': lbl}, {'result': lbl})['result'])
    main.finished()

    # T6: outcome, variables and the notes
    expected = {
        'runId': run_id, 'oddEven': oe['oddEven'], 'count': summary['count'], 'statuses': statuses,
        'workflowIds': workflow_ids, 'charged': receipt is not None, 'approvedBy': approval.get('approver'),
        'callbackPayload': callback.get('payload'), 'note': note if status['done'] else note + ' (job did not finish)',
        'cancelled': w['variables']['cancelled'], 'labels': labels, 'toolCount': len(tools),
        'weather': weather['content'][0]['text'], 'agentReply': reply['agentMessage']['parts'][0]['text'] if 'agentMessage' in reply else None,
        'composed': composed}
    for k_, v in expected.items():
        if k_ == 'agentReply' and v is None:
            continue
        r.ok('T6', w['output'].get(k_) == v, f'output.{k_}', f'{w["output"].get(k_)!r} != {v!r}')
    r.ok('T6', w['status'] == 'COMPLETED', w['workflowId'], w['status'])
    r.ok('T6', w['variables']['stage'] == 'done', 'stage', w['variables']['stage'])
    handler_notes = ['first note', 'cancelled: demo']          # the update, then the signal, as Main sends them
    r.ok('T6', is_interleaving(notes, notes_code, handler_notes), 'notes',
         f'{notes} is not the code notes {notes_code} interleaved with the handler notes')
    r.ok('T1', r.consumed == set(r.events), w['workflowId'], f'steps no code explains: {set(r.events) - r.consumed}')
    for lane_id in r.lanes:
        r.ok('T1', all(e.ref in r.consumed for e in r.lanes[lane_id]), lane_id, 'unconsumed steps')


def is_interleaving(merged, a, b):
    """merged keeps all of a and all of b, each in its own order, and nothing else."""
    if len(merged) != len(a) + len(b):
        return False
    ia = ib = 0
    for x in merged:
        if ib < len(b) and x == b[ib]:
            ib += 1
        elif ia < len(a) and x == a[ia]:
            ia += 1
        else:
            return False
    return True


if __name__ == '__main__':
    args = sys.argv[1:]
    if args[:1] == ['--server']:
        SERVER, args = args[1], args[2:]
    if args[:1] == ['--chaos']:
        CHAOS, args = True, args[1:]
    for wid in args:
        w = get(f'/workflow/{wid}?includeTasks=true')
        checks, children = {}, []
        try:
            kitchensink(w, checks, children)
            print(f'PROVED {wid} (oddEven={w["input"]["oddEven"]}, mod={w["input"]["mod"]}): '
                  + ', '.join(f'{k} {v}' for k, v in sorted(checks.items())) + f'; children {len(children)}')
            print('  children ' + ' '.join(children))
        except Violation as v:
            print(f'COUNTEREXAMPLE {wid}: {v}')
            sys.exit(1)
        except Exception as x:
            print(f'COUNTEREXAMPLE {wid}: the recording does not have the shape the program gives: {type(x).__name__}: {x}')
            raise
