#!/usr/bin/env python3
"""Checks that the workflow a native execution built matches the path its code took.

A native execution starts with an empty definition; each continuation appends what the code did:
the tasks it called, the forks it started, a SWITCH for every if, switch or switchOn whose arms
call the server (with every arm, the untaken ones holding previews of what they would call), and,
when the code threw, a mark where it threw followed by previews of what it skipped. This checker
reads one finished execution and checks that this drawing and the tasks that ran agree:

  P1 Ran as drawn     every task that ran has an entry; every entry that is not a preview ran,
                      in a COMPLETED execution (a FAILED one may stop short); types match.
  P2 Previews         no preview ran; each sits in an arm the switch did not take, or after the
                      mark of the exception that ended the run (metadata.previewAfter).
  P3 One arm taken    a switch that ran took exactly the arm whose case is selectedCase (the default
                      when no case has that key); that arm holds no preview at its top level; every
                      other arm holds only previews.
  P4 Lanes            each lane's entries are in one region: the top level or one fork branch, with
                      the switch cases inside it (a lane whose block calls nothing itself has no
                      branch of its own: what it starts sits where it would have been).
  P5 Order            the tasks of a list were scheduled in the order of the list.
  P6 Loops            an entry inside a switch case or a fork branch is in at least the Java loops
                      the switch or fork is in (metadata.loops starts with theirs).
  P7 Outcome          a COMPLETED execution holds no mark of an exception and names no failed task; a
                      FAILED one names the task it failed at (failedTaskId): the mark where its code
                      threw, or the task whose failure it did not catch, shown failed, with the
                      workflow's reason built from it; nothing but previews follows a mark. A server
                      that does not return failedTaskId is checked on the reason alone.

    python3 check_paths.py [--server http://localhost:8099/api] <workflow id>...
"""
import json, re, sys, urllib.request

SERVER = 'http://localhost:8099/api'
SUCCESS = {'COMPLETED', 'COMPLETED_WITH_ERRORS', 'SKIPPED'}
FAILURES = {'FAILED', 'FAILED_WITH_TERMINAL_ERROR', 'TIMED_OUT', 'CANCELED'}
# definition type -> the taskType its task row has; SIMPLE rows carry the task's name instead
SYSTEM_TYPES = {'SWITCH': 'SWITCH', 'FORK_JOIN': 'FORK', 'FORK_JOIN_DYNAMIC': 'FORK', 'JOIN': 'JOIN',
                'WAIT': 'WAIT', 'HTTP': 'HTTP', 'EVENT': 'EVENT', 'HUMAN': 'HUMAN', 'SUB_WORKFLOW': 'SUB_WORKFLOW',
                'START_WORKFLOW': 'START_WORKFLOW', 'TERMINATE': 'TERMINATE', 'DO_WHILE': 'DO_WHILE',
                'LLM_CHAT_COMPLETE': 'LLM_CHAT_COMPLETE', 'LIST_MCP_TOOLS': 'LIST_MCP_TOOLS',
                'CALL_MCP_TOOL': 'CALL_MCP_TOOL', 'AGENT': 'AGENT', 'DYNAMIC': None}


class Violation(Exception):
    pass


def get(path):
    with urllib.request.urlopen(SERVER + path, timeout=30) as r:
        return json.load(r)


def md(e):
    return e.get('metadata') or {}


def is_preview(e):
    return bool(md(e).get('preview'))


def walk(seq):
    for e in seq:
        yield e
        for c in (e.get('decisionCases') or {}).values():
            yield from walk(c)
        yield from walk(e.get('defaultCase') or [])
        for b in e.get('forkTasks') or []:
            yield from walk(b)
        yield from walk(e.get('loopOver') or [])


def check(w):
    """Returns the number of checks made; raises Violation at the first that fails."""
    count = [0]

    def ok(prop, cond, where, msg):
        count[0] += 1
        if not cond:
            raise Violation(f'{prop} at {where}: {msg}')

    tasks = [t for t in w['tasks'] if not t['taskType'].endswith('.code')]
    attempts = {}
    for t in sorted(tasks, key=lambda t: t['seq']):
        # an iteration of a server-side loop is <ref>__<n>
        attempts.setdefault(re.sub(r'__\d+$', '', t['referenceTaskName']), []).append(t)
    last = {r: a[-1] for r, a in attempts.items()}
    first = {r: a[0] for r, a in attempts.items()}
    entries = list(walk(w['workflowDefinition']['tasks']))
    by_ref = {e['taskReferenceName']: e for e in entries}
    status = w['status']

    # P1: ran as drawn
    for ref, t in last.items():
        ok('P1', ref in by_ref, ref, 'a task ran that the definition does not hold')
        e = by_ref[ref]
        want = SYSTEM_TYPES.get(e['type'], e['type'])
        if e['type'] == 'SIMPLE':
            want = e['name']
        if want is not None:
            ok('P1', t['taskType'] == want, ref, f'ran as {t["taskType"]}, drawn as {e["type"]} {e["name"]}')
    for e in entries:
        if not is_preview(e) and status == 'COMPLETED':
            ok('P1', e['taskReferenceName'] in last, e['taskReferenceName'], 'drawn, but never ran in a completed execution')

    # P2: previews never ran, and sit where a preview may
    marks = {e['taskReferenceName'] for e in entries if e['type'] == 'TERMINATE' and md(e).get('thrown')}

    def previews_ok(seq, untaken):
        for e in seq:
            ref = e['taskReferenceName']
            if is_preview(e):
                ok('P2', ref not in last, ref, 'a preview ran')
                ok('P2', untaken or md(e).get('previewAfter') in marks, ref,
                   'a preview outside an untaken arm and not after a mark')
            if e['type'] == 'SWITCH':
                taken = (last.get(ref) or {}).get('outputData', {}).get('selectedCase') if ref in last else None
                cases = e.get('decisionCases') or {}
                arms = list(cases.items()) + [('\0default', e.get('defaultCase') or [])]
                for key, arm in arms:
                    is_taken = ref in last and not is_preview(e) and (
                        key == taken or (key == '\0default' and taken not in cases))
                    previews_ok(arm, untaken or not is_taken)
            for b in e.get('forkTasks') or []:
                previews_ok(b, untaken or (is_preview(e)))
            previews_ok(e.get('loopOver') or [], untaken)

    previews_ok(w['workflowDefinition']['tasks'], False)

    # P3: one arm taken
    for e in entries:
        ref = e['taskReferenceName']
        if e['type'] != 'SWITCH' or is_preview(e):
            continue
        if ref not in last:
            ok('P3', status != 'COMPLETED', ref, 'a switch drawn as real never ran')
            continue
        out = last[ref].get('outputData') or {}
        recorded = (e.get('inputParameters') or {}).get('switchCaseValue')
        taken = out.get('selectedCase')
        recorded = None if recorded is None else str(recorded).replace('$' + '${', '${')  # stored escaped
        ok('P3', str(taken) == str(recorded), ref, f'took {taken}, drawn as taking {recorded}')
        cases = e.get('decisionCases') or {}
        taken_arm = cases.get(taken, e.get('defaultCase') or [])
        ok('P3', not any(is_preview(x) for x in taken_arm if not md(x).get('previewAfter')), ref,
           f'the taken arm {taken} holds a preview')
        for key, arm in list(cases.items()) + [('default', e.get('defaultCase') or [])]:
            if arm is taken_arm:
                continue
            ok('P3', all(is_preview(x) for x in walk(arm)), ref, f'the untaken arm {key} holds a real entry')

    # P4: lanes
    region_of = {}

    def lanes_ok(seq, region):
        for e in seq:
            ref = e['taskReferenceName']
            if not is_preview(e):
                lane = md(e).get('lane')
                ok('P4', region_of.setdefault(lane, region) == region, ref,
                   f'lane {lane} has entries in two regions: {region_of[lane]} and {region}')
            for c in (e.get('decisionCases') or {}).values():
                lanes_ok(c, region)
            lanes_ok(e.get('defaultCase') or [], region)
            if not is_preview(e):
                for i, b in enumerate(e.get('forkTasks') or []):
                    lanes_ok(b, f'{ref}[{i}]')

    lanes_ok(w['workflowDefinition']['tasks'], 'top')

    # P5: order within a list
    def order_ok(seq):
        ran = [e['taskReferenceName'] for e in seq if e['taskReferenceName'] in first and not is_preview(e)]
        seqs = [first[r]['seq'] for r in ran]
        ok('P5', seqs == sorted(seqs), ran[0] if ran else '-', f'scheduled out of order: {list(zip(ran, seqs))}')
        for e in seq:
            for c in (e.get('decisionCases') or {}).values():
                order_ok(c)
            order_ok(e.get('defaultCase') or [])
            for b in e.get('forkTasks') or []:
                order_ok(b)

    order_ok(w['workflowDefinition']['tasks'])

    # P6: loops
    def loops_ok(seq, outer):
        for e in seq:
            ref = e['taskReferenceName']
            loops = md(e).get('loops') or []
            if not is_preview(e):
                ok('P6', loops[:len(outer)] == outer, ref, f'in loops {loops}, inside a switch or fork in {outer}')
            inner = loops if not is_preview(e) else outer
            for c in (e.get('decisionCases') or {}).values():
                loops_ok(c, inner)
            loops_ok(e.get('defaultCase') or [], inner)
            for b in e.get('forkTasks') or []:
                loops_ok(b, inner)

    loops_ok(w['workflowDefinition']['tasks'], [])

    # P7: outcome
    if status == 'COMPLETED':
        ok('P7', not marks, w['workflowId'], f'a completed execution holds a mark {marks}')
        ok('P7', w.get('failedTaskId') is None, w['workflowId'], 'a completed execution names a failed task')
    if status == 'FAILED':
        failed = [t for t in tasks if t['taskId'] == w.get('failedTaskId')]
        if 'failedTaskId' in w:
            ok('P7', w['failedTaskId'] is not None, w['workflowId'], 'a failed execution names no failed task')
            ok('P7', len(failed) == 1, w['workflowId'], 'the failed task is not in the execution')
            cause = failed[0]
            ref = cause['referenceTaskName']
            # a task failure's reason is "task <ref> <status>: ..."; a mark's is the exception
            ok('P7', ref in marks or (w.get('reasonForIncompletion') or '').startswith(f'task {ref} '), ref,
               f'the workflow reason names another task: {w.get("reasonForIncompletion")!r}')
        else:
            cause = None
        if cause is None:
            # the API may leave failedTaskId out: the cause is then the one failed mark, or task
            candidates = [t for t in last.values() if t['status'] in FAILURES]
            ok('P7', len(candidates) >= 1, w['workflowId'], 'a failed execution with no failed task')
            cause = candidates[-1]
        ok('P7', cause['status'] in FAILURES, cause['referenceTaskName'], f'the cause is {cause["status"]}')
        ok('P7', (cause.get('reasonForIncompletion') or '') in (w.get('reasonForIncompletion') or ''), cause['referenceTaskName'],
           'the workflow reason is not the cause\'s')

        def after_mark_ok(seq):
            for i, e in enumerate(seq):
                if e['taskReferenceName'] in marks:
                    ok('P7', all(is_preview(x) for x in seq[i + 1:]), e['taskReferenceName'], 'a real entry follows the mark')
                for c in (e.get('decisionCases') or {}).values():
                    after_mark_ok(c)
                after_mark_ok(e.get('defaultCase') or [])
                for b in e.get('forkTasks') or []:
                    after_mark_ok(b)

        after_mark_ok(w['workflowDefinition']['tasks'])
    return count[0]


if __name__ == '__main__':
    args = sys.argv[1:]
    if args[:1] == ['--server']:
        SERVER, args = args[1], args[2:]
    bad = 0
    for wid in args:
        w = get(f'/workflow/{wid}?includeTasks=true')
        try:
            n = check(w)
            print(f'OK {wid} {w["workflowName"]} {w["status"]}: {n} checks')
        except Violation as v:
            bad += 1
            print(f'VIOLATION {wid} {w["workflowName"]}: {v}')
    sys.exit(1 if bad else 0)
