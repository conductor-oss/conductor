# Decision inference

Decision inference is a `SWITCH` evaluator. The provider call runs on the system-task
worker, and the completed task selects its branch.

There are three ways to use it:

1. Add a `SWITCH` with `evaluatorType: "decision"` to a workflow.
2. Use a decision-backed router, which compiles to that same `SWITCH`.
3. Give an agent a `decision` tool, which the tool mapper also executes as that
   `SWITCH`.

## Workflow task

```json
{
  "name": "decision_switch",
  "taskReferenceName": "classify_request",
  "type": "SWITCH",
  "evaluatorType": "decision",
  "expression": "route",
  "retryCount": 3,
  "retryLogic": "EXPONENTIAL_BACKOFF",
  "retryDelaySeconds": 1,
  "backoffScaleFactor": 2,
  "maxRetryDelaySeconds": 5,
  "inputParameters": {
    "model": "jev-1.13",
    "state": "${workflow.input.request}",
    "questions": {
      "route": {
        "type": "choice",
        "instructions": "Choose the team that should handle this request.",
        "choices": {
          "billing": "Billing and payment requests",
          "support": "Product support requests"
        }
      }
    }
  },
  "decisionCases": {
    "billing": [],
    "support": []
  },
  "defaultCase": []
}
```

`expression` names the `choice` question used for routing. The task writes the
chosen value to `selectedCase`, preserves the full provider response in its
output, and runs the matching `decisionCases` branch.

## Agent tool

An agent declares a normal tool with `toolType: "decision"`:

```json
{
  "name": "classify_request",
  "description": "Classify the request.",
  "toolType": "decision",
  "inputSchema": {
    "type": "object",
    "properties": {
      "state": { "type": "string" }
    },
    "required": ["state"]
  },
  "config": {
    "provider": "typesafe",
    "model": "jev-1.13",
    "questions": {
      "route": {
        "type": "choice",
        "instructions": "Choose the handling path.",
        "choices": {
          "normal": "Handle through the normal path.",
          "urgent": "Escalate for urgent handling."
        }
      }
    }
  }
}
```

The mapper emits a branchless decision-backed `SWITCH`. Server-owned values in
`config` override model-supplied arguments, and the completed output returns to
the model under the declared tool name. No user worker is required.

## Router

A decision-backed router compiles to one task:

```text
SWITCH (evaluatorType: decision) -> selected child agent
```

The selector must contain one `choice` question whose choice keys match the
router's child-agent names. `kind: "decision"` remains in router configuration as
the selector marker; it cannot be deployed or run as a standalone agent.

## Questions and output

The decision protocol supports the following question types, but routing and
decision tools require a `choice` question because the answer selects a case:

| Type      | Configuration                       | Answer                                 |
| --------- | ----------------------------------- | -------------------------------------- |
| `choice`  | `choices`: 2-255 named descriptions | `choice`: one supplied name            |
| `score`   | `scale`: 2-10 descriptions          | `score`: zero-based index into `scale` |
| `boolean` | none                                | `probability`: number from 0 through 1 |

Output contains `provider`, `model`, `answers`, `selectedCase`, `latencyMs`,
optional `requestId`, and provider-reported `usage`. Generated router and
agent-tool tasks use three exponential-backoff retries; a hand-written workflow
task uses its configured Conductor retry policy.

## Server configuration

Configuration uses the `conductor.ai.decision` prefix. The default provider is
`openrouter`; the server properties also accept `DECISION_API_KEY` and
`DECISION_PROVIDER`.

```properties
conductor.integrations.ai.enabled=true
conductor.ai.decision.api-key=${DECISION_API_KEY}
conductor.ai.decision.provider=openrouter
```

Provider-specific credentials, endpoints, and model overrides are configured
under `conductor.ai.decision.providers`. The built-in `system-one` adapter has
default endpoints for `openrouter` and `typesafe`; other adapters require an
explicit endpoint and a registered `DecisionApiAdapter` bean.
