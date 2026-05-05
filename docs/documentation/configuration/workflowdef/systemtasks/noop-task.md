---
description: "No-Op Task — a pass-through task in Conductor workflows useful for routing, placeholder steps, and workflow testing."
---
# No Op Task
```json
"type" : "NOOP"
```

The No Op task (NOOP) is a no-op task. It can be used in Switch tasks in cases where there are switch cases that require no action.

## JSON configuration

Here is the task configuration for a No Op task.

```json
{
	"name": "noop",
    "taskReferenceName": "noop_ref",
	"inputParameters": {},
	"type": "NOOP"
}
```