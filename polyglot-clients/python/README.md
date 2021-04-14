# Python client for Conductor
Python client for Conductor provides two sets of functions:

1. Workflow management APIs (start, terminate, get workflow status etc.)
2. Worker execution framework

## Install

```Using virtualenv
   virtualenv conductorclient
   source conductorclient/bin/activate
   cd ../conductor/client/python
   python setup.py install
```

## Using Workflow Management API
Python class ```WorkflowClient``` provides client API calls to the conductor server to start manage the workflows.  

### Example

```python
import sys
from conductor import conductor
import json

def getStatus(workflowId):
	
	workflowClient = conductor.WorkflowClient('http://localhost:8080/api')
	
	workflow_json = workflowClient.getWorkflow(workflowId)
	print json.dumps(workflow_json, indent=True, separators=(',', ': '))
	
	return workflow_json
	
```

## Task Worker Execution
Task worker execution APIs facilitates execution of a task worker using python client.
The API provides necessary mechanism to poll for task work at regular interval and executing the python worker in a separate threads.

### Example
The following python script demonstrates workers for the kitchensink workflow.

```python
from __future__ import print_function
from conductor.ConductorWorker import ConductorWorker

def execute(task):
	return {'status': 'COMPLETED', 'output': {'mod': 5, 'taskToExecute': 'task_1', 'oddEven': 0}, 'logs': ['one', 'two']}

def execute4(task):
	forkTasks = [{"name": "task_1", "taskReferenceName": "task_1_1", "type": "SIMPLE"},{"name": "sub_workflow_4", "taskReferenceName": "wf_dyn", "type": "SUB_WORKFLOW", "subWorkflowParam": {"name": "sub_flow_1"}}];
	input = {'task_1_1': {}, 'wf_dyn': {}}
	return {'status': 'COMPLETED', 'output': {'mod': 5, 'taskToExecute': 'task_1', 'oddEven': 0, 'dynamicTasks': forkTasks, 'inputs': input}, 'logs': ['one','two']}

def main():
	print('Starting Kitchensink workflows')
	cc = ConductorWorker('http://localhost:8080/api', 1, 0.1)
	for x in range(1, 30):
		if(x == 4):
			cc.start('task_{0}'.format(x), execute4, False)
		else:
			cc.start('task_{0}'.format(x), execute, False)
	cc.start('task_30', execute, True)

if __name__ == '__main__':
    main()
```
