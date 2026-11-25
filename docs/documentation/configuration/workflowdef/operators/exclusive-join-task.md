---
description: "EXCLUSIVE_JOIN operator — continue when the first selected branch completes."
---

# Exclusive Join

```json
"type": "EXCLUSIVE_JOIN"
```

`EXCLUSIVE_JOIN` waits for the first task among `joinOn` to complete, rather than waiting for every branch as `JOIN` does. It is useful for race or fallback patterns.

## Configuration

| Field | Required | Description |
|---|---:|---|
| `joinOn` | Yes | List of task reference names that may satisfy the join. |
| `defaultExclusiveJoinTask` | No | Fallback task-reference list used when no listed task is selected. |

```json
{
  "name": "first_response",
  "taskReferenceName": "first_response",
  "type": "EXCLUSIVE_JOIN",
  "joinOn": ["primary_response", "fallback_response"],
  "defaultExclusiveJoinTask": ["fallback_response"]
}
```

The mapper passes `joinOn` and, when present, `defaultExclusiveJoinTask` to the runtime join task. Use ordinary [Join](join-task.md) when every branch must complete.
