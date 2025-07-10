# Event Handlers
Eventing in Conductor provides for loose coupling between workflows and support for producing and consuming events from external systems.

This includes:

1. Being able to produce an event (message) in an external system like SQS, Kafka or internal to Conductor. 
2. Start a workflow when a specific event occurs that matches the provided criteria.

Conductor provides SUB_WORKFLOW task that can be used to embed a workflow inside parent workflow.  Eventing supports provides similar capability without explicitly adding dependencies and provides **fire-and-forget** style integrations.

## Event Task
Event task provides ability to publish an event (message) to either Conductor or an external eventing system like SQS or Kafka. Event tasks are useful for creating event based dependencies for workflows and tasks.

See [Event Task](workflowdef/systemtasks/event-task.md) for documentation.

## Event Handler
Event handlers are listeners registered that executes an action when a matching event occurs.  The supported actions are:

1.  Start a Workflow
2.  Fail a Task
3.  Complete a Task

Event Handlers can be configured to listen to Conductor Events or an external event like SQS or Kafka.

## Configuration
Event Handlers are configured via ```/event/``` APIs.

### Structure
```json
{
  "name" : "descriptive unique name",
  "event": "event_type:event_location",
  "condition": "boolean condition",
  "actions": ["see examples below"]
}
```
`condition` is an expression that MUST evaluate to a boolean value.  A Javascript like syntax is supported that can be used to evaluate condition based on the payload.
Actions are executed only when the condition evaluates to `true`.

## Examples
### Condition
Given the following payload in the message:

```json
{
    "fileType": "AUDIO",
    "version": 3,
    "metadata": {
       "length": 300,
       "codec": "aac"
    }
}
```

The following expressions can be used in `condition` with the indicated results:

| Expression                 | Result |
| -------------------------- | ------ |
| `$.version > 1`            | true   |
| `$.version > 10`           | false  |
| `$.metadata.length == 300` | true   |


### Actions
Examples of actions that can be configured in the `actions` array:

**To start a workflow**

```json
{
    "action": "start_workflow",
    "start_workflow": {
        "name": "WORKFLOW_NAME",
        "version": "<optional_param>",
        "input": {
            "param1": "${param1}" 
        }
    }
}
```

**To complete a task**

```json
{
    "action": "complete_task",
    "complete_task": {
      "workflowId": "${workflowId}",
      "taskRefName": "task_1",
      "output": {
        "response": "${result}"
      }
    },
    "expandInlineJSON": true
}
```

**To fail a task***

```json
{
    "action": "fail_task",
    "fail_task": {
      "workflowId": "${workflowId}",
      "taskRefName": "task_1",
      "output": {
        "response": "${result}"
      }
    },
    "expandInlineJSON": true
}
```
Input for starting a workflow and output when completing / failing task follows the same [expressions](workflowdef/index.md#using-expressions) used for wiring task inputs.

!!!info "Expanding stringified JSON elements in payload"
	`expandInlineJSON` property, when set to true will expand the inlined stringified JSON elements in the payload to JSON documents and replace the string value with JSON document.  
	This feature allows such elements to be used with JSON path expressions. 
