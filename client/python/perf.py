from conductor.ConductorWorker import ConductorWorker

def execute(task):
    print 'Execute.'
    return {'status': 'COMPLETED', 'output': {'mod': 5, 'taskToExecute': 'perf_task_1', 'oddEven': 0}}

def execute4(task):
    forkTasks = [{"name": "perf_task_1", "taskReferenceName": "task_1_1", "type": "SIMPLE"},{"name": "sub_workflow_4", "taskReferenceName": "wf_dyn", "type": "SUB_WORKFLOW", "subWorkflowParam": {"name": "sub_flow_1"}}];
    input = {'task_1_1': {}, 'wf_dyn': {}}
    return {'status': 'COMPLETED', 'output': {'mod': 5, 'taskToExecute': 'perf_task_1', 'oddEven': 0, 'dynamicTasks': forkTasks, 'inputs': input}}

def main():
    print 'Hello World'
    cc1 = ConductorWorker('http://100.66.45.146:7001', 10, 0.001)
    cc1.start('perf_task_10', execute, False)
    cc1.start('perf_task_10', execute, True)

if __name__ == '__main__':
    main()
