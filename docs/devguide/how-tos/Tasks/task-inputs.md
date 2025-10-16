# Wiring Task Inputs

In Conductor, task inputs can be provided in the workflow definition in multiple ways:

- As a hard-coded value – 
```
"taskInputA": true
```
- As a dynamic reference to the workflow inputs, workflow variables, or the inputs/outputs of prior tasks – 
```
"taskInputA": "${workflow.input.someValue}
```

## Syntax for dynamic references

All dynamic references are formatted as the following expression: 

```
"${type.jsonpath}"
```

These dynamic references are formatted as dot-notation expressions, taking after [JSONPath syntax](https://goessner.net/articles/JsonPath/).

| Component            | Description                                                                                                                    |
| -------------------- | ----------------------------------------------------------------------------------------------------- |
| `${...}`             | The root notation indicating that the variable will be dynamically replaced at runtime.             |
| type                 | The type of reference. Supported values:<ul><li>**workflow**—Refers to the current workflow instance.</li> <li>**workflow.input**—Refers to the workflow’s input parameters.</li> <li>**workflow.output**—Refers to the workflow’s output parameters.</li> <li>**workflow.variables**—Refers to the workflow variables set in the workflow using the [Set Variable](../../../documentation/configuration/workflowdef/operators/set-variable-task.md) task.</li> <li>**_taskReferenceName_**—Refers to a task in the current workflow instance by its reference name. (For example, “http_ref”).</li> <li>**_taskReferenceName_.input**—Refers to the task’s input parameters.</li> <li>**_taskReferenceName_.output**—Refers to the task’s output parameters.</li></ul> |
| jsonpath             | The [JSONPath](https://goessner.net/articles/JsonPath/) expression in dot-notation.                 |


### Sample expressions

Here is a non-exhaustive list of dynamic references you can use:

- To reference a task’s input payload –        
```
${<taskReferenceName>.input}
```
- To reference a task’s output payload –        
```
${<taskReferenceName>.output}
```
- To reference a task’s input parameter –        
```
${<taskReferenceName>.input.<someKey>}
```
- To reference a task’s output parameter –        
```
${<taskReferenceName>.output.<someKey>}
```
- To reference the workflow's input payload –        
```
${workflow.input}
```
- To reference the workflow's output payload –        
```
${workflow.output}
```
- To reference the workflow's input parameter –        
```
${workflow.input.<someKey>}
```
- To reference the workflow's output parameter –        
```
${workflow.output.<someKey>}
```
- To reference the workflow's current status (RUNNING, PAUSED, TIMED_OUT, TERMINATED, FAILED, or COMPLETED) –        
```
${workflow.status}
```
- To reference the workflow's (execution) ID –        
```
${workflow.workflowId}
```
- (Used in sub-workflows) To reference the parent workflow (execution) ID –        
```
${workflow.parentWorkflowId}
```
- (Used in sub-workflows) To reference the task execution ID for the Sub Workflow task in the parent workflow –        
```
${workflow.parentWorkflowTaskId}
```
- To reference the workflow's name –        
```
${workflow.workflowType} 
```  
- To reference the workflow's version –        
```
${workflow.version} 
```    
- To reference the start time of the workflow execution –        
```
${workflow.createTime} 
```  
- To reference the workflow's correlation ID –        
```
${workflow.correlationId} 
```  
- To reference the workflow’s domain name that was invoked during its execution –        
```
${workflow.taskToDomain.<domainName>} 
```  
- To reference the workflow's variable created using the Set Variable task –        
```
${workflow.variables.<someKey>} 
```   


## Examples

Here are some examples for using dynamic references in workflows.

<details>
<summary>Referencing workflow inputs​​</summary>

For the given workflow input:

```json
{
  "userID": 1,
  "userName": "SAMPLE",
  "userDetails": {
    "country": "nestedValue",
    "age": 50
  }
}
```

You can reference these workflow inputs elsewhere using the following expressions:

```json
{
  "user": "${workflow.input.userName}",
  "userAge": "${workflow.input.userDetails.age}"
}
```

At runtime, the parameters will be:

```json
{
  "user": "SAMPLE",
  "userAge": 50
}
```

</details>

<details>
<summary>Referencing other task outputs​​</summary>

If a task <code>previousTaskReference</code> produced the following output:

```json
{
  "taxZone": "A",
  "productDetails": {
    "nestedKey1": "outputValue-1",
    "nestedKey2": "outputValue-2"
  }
}
```

You can reference these task outputs elsewhere using the following expressions:

```json
{
  "nextTaskInput1": "${previousTaskReference.output.taxZone}",
  "nextTaskInput2": "${previousTaskReference.output.productDetails.nestedKey1}"
}
```

At runtime, the parameters will be:

```json
{
  "nextTaskInput1": "A",
  "nextTaskInput2": "outputValue-1"
}
```

</details>

<details>
<summary>Referencing workflow variables</summary>

If a workflow variable is set using the Set Variable task:

```json
{
  "name": "Ipsum"
}
```

The variable can be referenced in the same workflow using the following expression:

```json
{
  "user": "${workflow.variables.name}"
}
```

<b>Note:</b> Workflow variables cannot be re-referenced across workflows, even between a parent workflow and a sub-workflow.

</details>


<details>
<summary>Referencing data between parent workflow and sub-workflow​</summary>

To pass parameters from a parent workflow into its sub-workflow, you must declare them as input parameters for the Sub Workflow task. If needed, these inputs can then be set as workflow variables within the sub-workflow definition itself using a Set Variable task.

```
// parent workflow definition with task configuration

{
 "createTime": 1733980872607,
 "updateTime": 0,
 "name": "testParent",
 "description": "workflow with subworkflow",
 "version": 1,
 "tasks": [
   {
     "name": "get_item",
     "taskReferenceName": "get_item_ref",
     "inputParameters": {
       "uri": "https://example.com/api",
       "method": "GET",
       "accept": "application/json",
       "contentType": "application/json",
       "encode": true
     },
     "type": "HTTP",
   },
   {
     "name": "sub_workflow",
     "taskReferenceName": "sub_workflow_ref",
     "inputParameters": {
       "user": "${workflow.variables.name}",
       "item": "${previous_task_ref.output.item[0]}"
     },
     "type": "SUB_WORKFLOW",
     "subWorkflowParam": {
       "name": "testSub",
       "version": 1
     }
   }
 ],
 "inputParameters": [],
 "outputParameters": {}
}
```


To pass parameters from a sub-workflow back to its parent workflow, you must pass them as the sub-workflow’s output parameters in the sub-workflow definition.

```
// sub-workflow definition

{
 "createTime": 1726651838873,
 "updateTime": 1733983507294,
 "name": "testSub",
 "description": "subworkflow for parent workflow",
 "version": 1,
 "tasks": [
   {
     "name": "get-user",
     "taskReferenceName": "get-user_ref",
     "inputParameters": {
       "uri": "https://example.com/api",
       "method": "GET",
       "accept": "application/json",
       "contentType": "application/json",
       "encode": true
     },
     "type": "HTTP",
   },
   {
     "name": "send-notification",
     "taskReferenceName": "send-notification_ref",
     "inputParameters": {
       "uri": "https://example.com/api",
       "method": "GET",
       "accept": "application/json",
       "contentType": "application/json",
       "encode": true
     },
     "type": "HTTP",
   }
 ],
 "inputParameters": [],
 "outputParameters": {
   "location": "${get-user_ref.output.response.body.results[0].location.country}",
   "isNotif": "${send-notification_ref.output}"
 }
}
```

In the parent workflow, these sub-workflow outputs can be referenced using the expression format `${&lt;sub_workflow_ref>.output.&lt;someKey>}`.

</details>


## Troubleshooting

You can verify if the data was passed correctly by checking the input/output values of the task execution in the UI. Common errors:

- If the reference expression is incorrectly formatted, the referencing parameter value may end up with the wrong data or a null value.
- If the referenced value (such as a task output) has not resolved at the point when it is referenced, the referencing parameter value will be null.