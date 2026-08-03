# Agent Worker Example Shapes

Examples should use the task contract shared by embedded and external worker runtimes.

## Remote A2A

```json
{
  "name": "call_remote_agent",
  "taskReferenceName": "call_remote_agent_ref",
  "type": "AGENT",
  "inputParameters": {
    "agentType": "a2a",
    "agentUrl": "${workflow.input.agentUrl}",
    "text": "${workflow.input.prompt}"
  }
}
```

For a multi-turn remote task, pass the prior `taskId` and `contextId` into a later `AGENT` task.

## Conductor agent

```json
{
  "name": "run_agent",
  "taskReferenceName": "run_agent_ref",
  "type": "AGENT",
  "inputParameters": {
    "agentType": "conductor",
    "name": "planner",
    "version": 1,
    "prompt": "${workflow.input.prompt}"
  }
}
```

The latest deployed version is used when `version` is omitted. A later task can resume a waiting
execution by supplying `executionId` and a new `prompt`.

## Explicit cancellation

Remote A2A:

```json
{
  "name": "cancel_agent",
  "taskReferenceName": "cancel_agent_ref",
  "type": "CANCEL_AGENT",
  "inputParameters": {
    "agentType": "a2a",
    "agentUrl": "${workflow.input.agentUrl}",
    "taskId": "${call_remote_agent_ref.output.taskId}"
  }
}
```

Conductor agent:

```json
{
  "name": "cancel_agent",
  "taskReferenceName": "cancel_agent_ref",
  "type": "CANCEL_AGENT",
  "inputParameters": {
    "agentType": "conductor",
    "executionId": "${run_agent_ref.output.executionId}",
    "reason": "Canceled by workflow"
  }
}
```
