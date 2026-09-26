# Decision inference

Decision inference is an `AI_DECISION` system task. It is not a separate agent
runtime.

There are three ways to use the same task:

1. Add `AI_DECISION` directly to a workflow.
2. Use a decision-backed router, which compiles to `AI_DECISION` followed by
   `SWITCH`.
3. Give an agent a `decision` tool, which the tool mapper executes as
   `AI_DECISION`.

## Workflow task

```json
{
  "name": "AI_DECISION",
  "taskReferenceName": "classify_request",
  "type": "AI_DECISION",
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
  }
}
```

For exactly one `choice` question, the task copies the chosen value to
`selectedCase`. A following `SWITCH` can use:

```text
${classify_request.output.selectedCase}
```

Other question combinations return `answers` without `selectedCase`.

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
      "urgency": {
        "type": "score",
        "instructions": "Rate the urgency.",
        "scale": ["low", "medium", "high"]
      }
    }
  }
}
```

The mapper emits an `AI_DECISION` task. Server-owned values in `config` override
model-supplied arguments, and the completed output returns to the model under the
declared tool name. No worker is required.

## Router

A decision-backed router compiles to:

```text
AI_DECISION -> SWITCH -> selected child agent
```

The selector must contain one `choice` question whose choice keys match the
router's child-agent names. `kind: "decision"` remains in router configuration as
the selector marker; it cannot be deployed or run as a standalone agent.

## Questions and output

Every question has `instructions` and one of these types:

| Type      | Configuration                       | Answer                                 |
| --------- | ----------------------------------- | -------------------------------------- |
| `choice`  | `choices`: 2-255 named descriptions | `choice`: one supplied name            |
| `score`   | `scale`: 2-10 descriptions          | `score`: zero-based index into `scale` |
| `boolean` | none                                | `probability`: number from 0 through 1 |

Output contains `provider`, `model`, `answers`, `latencyMs`, optional `requestId`,
and provider-reported `usage`. Generated router and agent-tool tasks use three
exponential-backoff retries; a hand-written workflow task uses its configured
Conductor retry policy.

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
