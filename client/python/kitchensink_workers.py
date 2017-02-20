from conductor.ConductorWorker import ConductorWorker

def execute(task):
    return {'status': 'COMPLETED', 'output': {'mod': 5, 'taskToExecute': 'task_1', 'oddEven': 0}}

def execute4(task):
    forkTasks = [{"name": "task_1", "taskReferenceName": "task_1_1", "type": "SIMPLE"},{"name": "sub_workflow_4", "taskReferenceName": "wf_dyn", "type": "SUB_WORKFLOW", "subWorkflowParam": {"name": "sub_flow_1"}}];
    input = {'task_1_1': {}, 'wf_dyn': {}}
    return {'status': 'COMPLETED', 'output': {'mod': 5, 'taskToExecute': 'task_1', 'oddEven': 0, 'dynamicTasks': forkTasks, 'inputs': input}}

def main():
    print 'Hello World'
    cc = ConductorWorker('http://localhost:8080/api', 1, 0.1)
    for x in range(1, 30):
        if(x == 4):
            cc.start('task_{0}'.format(x), execute4, False)
        else:
            cc.start('task_{0}'.format(x), execute, False)
    cc.start('task_30', execute, True)

if __name__ == '__main__':
    main()
