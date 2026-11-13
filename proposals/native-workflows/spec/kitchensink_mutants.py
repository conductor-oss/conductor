"""Each mutant breaks one thing in a recorded odd-path execution; kitchensink_spec.py must reject every one.

    python3 kitchensink_mutants.py [--server http://localhost:8099/api] <odd-path workflow id>
"""
import copy, json, sys
import kitchensink_spec as spec

real_get = spec.get

def task(w, ref, seq_last=True):
    ts = sorted([t for t in w['tasks'] if t['referenceTaskName'] == ref], key=lambda t: t['seq'])
    return ts[-1] if seq_last else ts[0]

def entry(w, ref):
    return next(t for t in spec.walk(w['workflowDefinition']['tasks']) if t['taskReferenceName'] == ref)

def m_wrong_case(w):      task(w, 'if_big')['outputData']['selectedCase'] = 'false'
def m_wrong_input(w):     task(w, 'step_4')['inputData']['label'] = 'r1-later'
def m_wrong_output(w):    task(w, 'label_item_2')['outputData']['result'] = 'LABEL[box-9]'
def m_extra_task(w):
    t = copy.deepcopy(task(w, 'step_2')); t['referenceTaskName'] = 'step_99'; t['seq'] = 10_000; w['tasks'].append(t)
def m_missing_task(w):    w['tasks'] = [t for t in w['tasks'] if t['referenceTaskName'] != 'step_16']
def m_preview_ran(w):
    p = entry(w, 'if_big')['defaultCase'][0]
    t = copy.deepcopy(task(w, 'step')); t['referenceTaskName'] = p['taskReferenceName']; t['seq'] = 10_001
    t['workflowTask'] = p; w['tasks'].append(t)
def m_missing_arm(w):     entry(w, 'if_pass')['decisionCases'].pop('true')
def m_wrong_preview(w):   entry(w, 'if_inner')['defaultCase'][0]['name'] = 'label_item'
def m_early_start(w):
    a, b = task(w, 'label_item'), task(w, 'label_item_2'); b['scheduledTime'] = a['endTime'] - 50
def m_join_too_soon(w):
    task(w, 'fork_8_join')['endTime'] = task(w, 'slow_step')['endTime'] - 100
def m_extra_retry(w):
    t = copy.deepcopy(task(w, 'task_1')); t['seq'] = 0; t['status'] = 'FAILED'; w['tasks'].append(t)
def m_duplicate_success(w):
    t = copy.deepcopy(task(w, 'task_1')); t['seq'] = 0; t['status'] = 'COMPLETED'; w['tasks'].append(t)
def m_wrong_output_field(w): w['output']['composed'][3] = 'r1-later'
def m_lost_note(w):       w['variables']['notes'].remove('cancelled: demo')
def m_reordered_notes(w):
    n = w['variables']['notes']; n[-1], n[-2] = n[-2], n[-1]
def m_status(w):          w['status'] = 'FAILED'
def m_loop_tag(w):        entry(w, 'if_inner_3')['metadata']['loops'] = ['KitchenSinkWorkflow_loop_440']
def m_swapped_lanes(w):
    a, b = task(w, 'step_4'), task(w, 'step_5')
    a['workflowTask']['metadata']['lane'], b['workflowTask']['metadata']['lane'] = b['workflowTask']['metadata']['lane'], a['workflowTask']['metadata']['lane']
def m_child_input(w):     task(w, 'sub_flow_1_3')['inputData']['workflowInput']['oddEven'] = '0'

MUTANTS = [v for k, v in sorted(globals().items()) if k.startswith('m_')]

if __name__ == '__main__':
    args = sys.argv[1:]
    if args[:1] == ['--server']:
        spec.SERVER, args = args[1], args[2:]
    if args[:1] == ['--chaos']:
        # a failed attempt before the last is what a worker dying looks like: allowed under chaos
        spec.CHAOS, args = True, args[1:]
        MUTANTS = [m for m in MUTANTS if m.__name__ != 'm_extra_retry']
    wid = args[0]
    base = real_get(f'/workflow/{wid}?includeTasks=true')
    caught = 0
    for m in MUTANTS:
        w = copy.deepcopy(base); m(w)
        spec.get = lambda path, w=w: w if path.startswith(f'/workflow/{wid}') else real_get(path)
        try:
            spec.kitchensink(w, {}, [])
            print(f'  MISSED  {m.__name__}')
        except spec.Violation as v:
            caught += 1
            print(f'  caught  {m.__name__:22} {str(v)[:110]}')
        except Exception as x:
            caught += 1
            print(f'  caught  {m.__name__:22} (shape) {type(x).__name__}: {str(x)[:90]}')
    print(f'{caught}/{len(MUTANTS)} mutants rejected')
    sys.exit(0 if caught == len(MUTANTS) else 1)
