#!/usr/bin/env python3
"""Each mutant breaks one thing in a recorded execution; check_paths.py must reject every one.

    python3 check_paths_mutants.py [--server http://localhost:8099/api] <kitchensink odd-path id> <failed between_tasks id>

The first execution is a completed kitchensink run (its switches, forks and loops), the second a
run whose code threw between two tasks (its mark and preview).
"""
import copy, json, sys

import check_paths as cp


def task(w, ref):
    return sorted([t for t in w['tasks'] if t['referenceTaskName'] == ref], key=lambda t: t['seq'])[-1]


def entry(w, ref):
    return next(e for e in cp.walk(w['workflowDefinition']['tasks']) if e['taskReferenceName'] == ref)


def first_preview(w, switch_ref):
    sw = entry(w, switch_ref)
    for arm in list((sw.get('decisionCases') or {}).values()) + [sw.get('defaultCase') or []]:
        for e in arm:
            if cp.is_preview(e):
                return e
    raise AssertionError('no preview under ' + switch_ref)


# --- on the completed kitchensink run
def m_duplicate_ref(w):
    # the same reference drawn twice: a second entry that names a task already drawn
    tasks = w['workflowDefinition']['tasks']
    tasks.append(json.loads(json.dumps(next(e for e in tasks if e['type'] == 'SIMPLE'))))


def m_task_without_entry(w):
    t = copy.deepcopy(task(w, 'step_2')); t['referenceTaskName'] = 'stray'; t['taskId'] = 'stray'; t['seq'] = 9_000
    w['tasks'].append(t)
def m_entry_never_ran(w):
    w['tasks'] = [t for t in w['tasks'] if t['referenceTaskName'] != 'step_3']
def m_wrong_type(w):              task(w, 'label_item')['taskType'] = 'step'
def m_preview_ran(w):
    p = first_preview(w, 'if_big'); t = copy.deepcopy(task(w, 'step')); t['referenceTaskName'] = p['taskReferenceName']
    t['taskId'] = 'p'; t['seq'] = 9_001; w['tasks'].append(t)
def m_preview_outside_an_arm(w):
    p = copy.deepcopy(first_preview(w, 'if_big')); p['taskReferenceName'] = 'loose'; w['workflowDefinition']['tasks'].append(p)
def m_other_case_taken(w):        task(w, 'if_pass')['outputData']['selectedCase'] = 'false'
def m_real_entry_in_untaken_arm(w):
    p = first_preview(w, 'if_inner'); p['metadata'].pop('preview')
def m_preview_in_taken_arm(w):
    sw = entry(w, 'if_odd'); sw['decisionCases']['true'][0].setdefault('metadata', {})['preview'] = True
def m_lane_split(w):
    e = entry(w, 'step_5'); e['metadata']['lane'] = entry(w, 'step_4')['metadata']['lane']
def m_out_of_order(w):
    a, b = task(w, 'label_item'), task(w, 'label_item_2'); a['seq'], b['seq'] = b['seq'], a['seq']
def m_loop_tag_lost(w):
    sw = entry(w, 'if_inner_2'); sw['decisionCases'] and None
    for arm in [sw.get('defaultCase') or []]:
        for e in arm:
            if not cp.is_preview(e):
                e['metadata']['loops'] = []
def m_completed_names_a_failed_task(w):  w['failedTaskId'] = task(w, 'task_1')['taskId']
def m_mark_in_completed(w):
    w['workflowDefinition']['tasks'].append({'name': 'X', 'taskReferenceName': 'throw', 'type': 'TERMINATE',
                                             'metadata': {'lane': 'main'}})


# --- on the failed run
def f_no_failed_task(w):
    w['tasks'] = [t for t in w['tasks'] if t['taskType'] != 'TERMINATE']; w.pop('failedTaskId', None)
def f_mark_not_failed(w):         task(w, 'throw')['status'] = 'COMPLETED'
def f_real_entry_after_mark(w):
    p = entry(w, 'task2_if_no_error'); p['metadata'].pop('preview')
def f_preview_ran(w):
    t = copy.deepcopy(task(w, 'task1')); t['referenceTaskName'] = 'task2_if_no_error'; t['taskType'] = 'task2'
    t['taskId'] = 'q'; t['seq'] = 9_002; w['tasks'].append(t)
def f_reason_not_the_cause(w):    w['reasonForIncompletion'] = 'something else'
def f_names_another_task(w):
    w['failedTaskId'] = task(w, 'task1')['taskId']
def f_names_no_task(w):           w['failedTaskId'] = None


if __name__ == '__main__':
    args = sys.argv[1:]
    if args[:1] == ['--server']:
        cp.SERVER, args = args[1], args[2:]
    completed, failed = (cp.get(f'/workflow/{x}?includeTasks=true') for x in args[:2])
    cp.check(completed); cp.check(failed)  # both pass as recorded
    caught = total = 0
    for prefix, base in (('m_', completed), ('f_', failed)):
        for name, mutant in [(k, v) for k, v in sorted(globals().items()) if k.startswith(prefix)]:
            total += 1
            w = copy.deepcopy(base)
            mutant(w)
            try:
                cp.check(w)
                print(f'  MISSED  {name}')
            except cp.Violation as v:
                caught += 1
                print(f'  caught  {name:30} {str(v)[:100]}')
    print(f'{caught}/{total} mutants rejected')
    sys.exit(0 if caught == total else 1)
