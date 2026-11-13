#!/usr/bin/env python3
"""Checks real native executions against the promises of NativeProtocol.tla.

It reads finished executions from a server and checks what their stored state shows: tasks, the
definition that grew, and the continuation markers with their lease and apply times. It compares
final states and timestamps, so it cannot see a behaviour that left no trace.

    python3 check_runs.py --server http://localhost:8099/api --since <epoch-ms> [--limit 500]
"""
import argparse, json, re, sys, collections, urllib.parse, urllib.request

TERMINAL = {'COMPLETED', 'COMPLETED_WITH_ERRORS', 'SKIPPED', 'FAILED', 'FAILED_WITH_TERMINAL_ERROR', 'TIMED_OUT', 'CANCELED'}
ENDED = {'COMPLETED', 'FAILED', 'TIMED_OUT', 'TERMINATED'}


def get(base, path):
    with urllib.request.urlopen(base + path, timeout=30) as r:
        return json.load(r)


def base_ref(ref):
    return re.sub(r'__\d+$', '', ref)


def walk(seq, owner_seq=None):
    """Every definition entry at any depth, with the list holding it."""
    for j, t in enumerate(seq):
        yield t, seq, j
        for c in (t.get('decisionCases') or {}).values():
            yield from walk(c)
        yield from walk(t.get('defaultCase') or [])
        for b in t.get('forkTasks') or []:
            yield from walk(b)
        yield from walk(t.get('loopOver') or [])


def is_preview(t):
    return bool((t.get('metadata') or {}).get('preview'))


def latest_attempts(tasks):
    by = {}
    for t in sorted(tasks, key=lambda x: x['seq']):
        by[t['referenceTaskName']] = t
    return list(by.values())


def markers(w):
    """(id, marker) for every marker of the latest attempts and the execution's own."""
    out = [(t['referenceTaskName'], t['continuation']) for t in latest_attempts(w['tasks']) if t.get('continuation')]
    ns = w.get('nativeState') or {}
    for name in ('bootstrap',):
        if ns.get(name):
            out.append((name, ns[name]))
    return out


def check(w):
    bad = []
    tasks = w['tasks']
    entries = list(walk(w['workflowDefinition']['tasks']))
    known = {t['taskReferenceName'] for t, _, _ in entries}
    marks = markers(w)
    ended = w['status'] in ENDED

    # Nothing outside the code: every task is in the definition, and no preview ever ran.
    previews = {t['taskReferenceName'] for t, _, _ in entries if is_preview(t)}
    for t in tasks:
        if t['taskType'].endswith('.code'):
            continue
        ref = base_ref(t['referenceTaskName'])
        if ref not in known:   # (a fan-out's copies are drawn too: every task is in the definition)
            bad.append(('AsIfRunOnce', f'{ref} is not in the definition'))
        if ref in previews:
            bad.append(('AsIfRunOnce', f'preview {ref} ran'))

    # Applied at most once: an applied marker has one lease that applied it, after that lease began.
    for name, m in marks:
        if m.get('state') == 'APPLIED':
            if m.get('epoch', 0) < 1:
                bad.append(('AppliedOnce', f'{name} applied with no lease'))
            if m.get('appliedAt', 0) < m.get('leasedAt', 0):
                bad.append(('AppliedAtLatestLease', f'{name} applied before its last lease began'))

    # One continuation at a time: the last leases do not overlap. An expired lease can.
    spans = sorted((m['leasedAt'], m['appliedAt'], n) for n, m in marks
                   if m.get('state') == 'APPLIED' and m.get('leasedAt', 0) > 0 and m.get('attempts', 1) == 1)
    for (s1, e1, n1), (s2, e2, n2) in zip(spans, spans[1:]):
        if s2 < e1:
            bad.append(('OneContinuationAtATime', f'{n1} and {n2} overlap by {e1 - s2} ms'))

    if ended:
        for t in tasks:
            if t['status'] not in TERMINAL:
                bad.append(('EndedIsQuiet', f'{t["referenceTaskName"]} is {t["status"]} after the end'))
    if w['status'] == 'COMPLETED':
        for name, m in marks:
            if m.get('state') != 'APPLIED':
                bad.append(('CompletedSound', f'marker of {name} is {m.get("state")}'))
        main = [m for n, m in marks if m.get('lane') == 'main' and m.get('state') == 'APPLIED']
        last = max(main, key=lambda m: m.get('n', 0), default=None)
        if last is None or not last.get('done') or last.get('error'):
            bad.append(('CompletedSound', 'the main lane did not return'))
        # Every entry landed where it would run.
        ran = {base_ref(t['referenceTaskName']) for t in tasks}
        for t, _, _ in entries:
            if is_preview(t) or t['type'] in ('FORK_JOIN_DYNAMIC', 'JOIN', 'FORK_JOIN') and t['taskReferenceName'] in ran:
                continue
            if t['taskReferenceName'] not in ran and t['type'] != 'FORK_JOIN_DYNAMIC':
                if not inside_unrun_loop(t, entries, ran):
                    bad.append(('NeverStuck', f'{t["taskReferenceName"]} never ran'))
    if w['status'] == 'RUNNING':
        busy = [t for t in tasks if t['status'] not in TERMINAL]
        runnable = [n for n, m in marks if m.get('state') in ('RUNNABLE', 'LEASED')]
        if not busy and not runnable:
            bad.append(('NeverStuck', 'running with nothing in flight'))

    # Order: a task starts after the task before it in its list, and after the code applied.
    end_of = collections.defaultdict(int)
    applied = {}
    for t in tasks:
        r = t['referenceTaskName']
        end_of[r] = max(end_of[r], t['endTime'])
        if t.get('continuation'):
            applied[r] = t['continuation'].get('appliedAt', 0)
    first = {}
    for t in sorted(tasks, key=lambda x: x['seq']):
        first.setdefault(t['referenceTaskName'], t)
    for t, seq, j in entries:
        r = t['taskReferenceName']
        if j == 0 or r not in first or (t.get('metadata') or {}).get('after'):
            continue
        p = seq[j - 1]['taskReferenceName']
        if is_preview(seq[j - 1]) or p not in first or seq[j - 1]['type'] in ('SWITCH', 'FORK_JOIN', 'DO_WHILE', 'FORK_JOIN_DYNAMIC'):
            continue
        if first[r]['scheduledTime'] < end_of[p]:
            bad.append(('SequenceOrder', f'{r} started {end_of[p] - first[r]["scheduledTime"]} ms before {p} ended'))
        if p in applied and applied[p] and first[r]['scheduledTime'] < applied[p]:
            bad.append(('SequenceOrder', f'{r} started before the code after {p} was applied'))

    # A join completes after its lanes and what they started.
    for t, seq, j in entries:
        if t['type'] != 'JOIN' or (t.get('metadata') or {}).get('detached') or is_preview(t):
            continue
        jt = first.get(t['taskReferenceName'])
        if not jt or jt['status'] not in ('COMPLETED', 'COMPLETED_WITH_ERRORS'):
            continue
        lanes = (t.get('metadata') or {}).get('waitFor')
        branches = []
        if lanes:
            for f, _, _ in entries:
                branches += [b for b in f.get('forkTasks') or [] if b and (b[0].get('metadata') or {}).get('lane') in lanes]
        elif j > 0 and seq[j - 1]['type'] == 'FORK_JOIN':
            branches = seq[j - 1].get('forkTasks') or []
        refs = {x['taskReferenceName'] for b in branches for x, _, _ in walk(b)}
        for x in latest_attempts(tasks):   # an older, retried attempt's marker stays WAITING
            if base_ref(x['referenceTaskName']) in refs:
                if x['endTime'] > jt['endTime'] or x['status'] not in TERMINAL:
                    bad.append(('JoinWaitsForLanes', f'{t["taskReferenceName"]} ended before {x["referenceTaskName"]}'))
                c = x.get('continuation')
                if c and (c.get('state') != 'APPLIED' or c.get('appliedAt', 0) > jt['endTime']):
                    bad.append(('JoinWaitsForLanes', f'{t["taskReferenceName"]} ended before the code after {x["referenceTaskName"]} ran'))
    return bad


def inside_unrun_loop(t, entries, ran):
    return False


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--server', default='http://localhost:8099/api')
    ap.add_argument('--since', type=int, default=0, help='only executions started after this time, in epoch ms')
    ap.add_argument('--limit', type=int, default=500)
    a = ap.parse_args()
    ids, start = [], 0
    while len(ids) < a.limit:
        q = urllib.parse.urlencode({'size': 100, 'start': start, 'sort': 'startTime:DESC', 'freeText': '*'})
        page = get(a.server, f'/workflow/search?{q}')['results']
        if not page:
            break
        ids += [(r['workflowId']) for r in page]
        start += 100
    counts, found, runs = collections.Counter(), [], 0
    for wid in ids[:a.limit]:
        w = get(a.server, f'/workflow/{wid}?includeTasks=true')
        if not w.get('nativeState') or w['startTime'] < a.since:
            continue
        runs += 1
        counts[w['status']] += 1
        for prop, msg in check(w):
            found.append((prop, wid, w['workflowName'], msg))
    print(f'{runs} native executions: {dict(counts)}')
    for prop, n in collections.Counter(f[0] for f in found).most_common():
        print(f'  {prop}: {n}')
    seen = collections.Counter()
    for prop, wid, name, msg in found:
        seen[prop] += 1
        if seen[prop] <= 5:
            print(f'  {prop} {wid} {name}: {msg}')
    if runs == 0:
        print('no native executions to check: that is not a pass')
    sys.exit(1 if found or runs == 0 else 0)


if __name__ == '__main__':
    main()
