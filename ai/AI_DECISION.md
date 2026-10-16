# AI_DECISION task

`AI_DECISION` asks a decision provider structured questions and completes with its validated
answers. It extends the `HTTP` system task: the provider call goes through `HttpTask`'s HTTP client
on the system task worker, never in the decider, and a failed call is retried with the task
definition's retry policy.

Route on the result with a regular `SWITCH`. Set `question` to the `choice` question to route on,
and the task writes that answer to `selectedCase`:

```json
[
  {
    "name": "classify_request",
    "taskReferenceName": "classify_request",
    "type": "AI_DECISION",
    "inputParameters": {
      "model": "jev-1.13",
      "state": "${workflow.input.request}",
      "question": "route",
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
  },
  {
    "name": "route_request",
    "taskReferenceName": "route_request",
    "type": "SWITCH",
    "evaluatorType": "value-param",
    "expression": "selected",
    "inputParameters": {
      "selected": "${classify_request.output.selectedCase}"
    },
    "decisionCases": {
      "billing": [],
      "support": []
    },
    "defaultCase": []
  }
]
```

Without `question`, the task still completes with every answer; route on
`answers.<question>.choice` instead.

## Inputs

| Parameter | Required | Description |
|---|:---:|---|
| `model` | ✅ | Decision model, e.g. `jev-1.13` |
| `state` | ✅ | Observed state the questions are asked about |
| `questions` | ✅ | Named questions. `type` is `choice` (with `choices`), `score` (with `scale`), or `boolean`; each needs `instructions` |
| `question` | ❌ | Name of the `choice` question to report as `selectedCase` |
| `provider` | ❌ | Overrides `conductor.ai.decision.provider` |

## Output

`provider`, `model`, `answers`, `latencyMs`, optional `requestId`, provider-reported `usage`
(`inputTokens`, `outputTokens`, `cost`, `currency`), and `selectedCase` when `question` is set.

## Failures and retries

| Outcome | Task status |
|---|---|
| HTTP 429 or 5xx, timeout, connection error | `FAILED`, retried per the task definition |
| Other non-2xx status, redirect, response over 2 MB, invalid input, invalid answer | `FAILED_WITH_TERMINAL_ERROR` |

Without a task definition the task is not retried. To retry, register a task definition with the
task's `name`, or set `taskDefinition` on the workflow task, with `retryCount`, `retryLogic` and
`retryDelaySeconds`. Agentspan sets an inline definition on the decision tasks it generates: 3
retries with exponential backoff from 2 seconds.

Each attempt sends one request: the HTTP client does not retry on its own and does not follow
redirects, so the API key is only sent to the configured endpoint. Failure reasons carry the HTTP
status, never the provider's response body.

## Server configuration

Configuration uses the `conductor.ai.decision` prefix. The default provider is `openrouter`; the
server properties also accept `DECISION_API_KEY` and `DECISION_PROVIDER`.

```properties
conductor.integrations.ai.enabled=true
conductor.ai.decision.api-key=${DECISION_API_KEY}
conductor.ai.decision.provider=openrouter
conductor.ai.decision.timeout=20s
```

Provider-specific credentials, endpoints, and model overrides are configured under
`conductor.ai.decision.providers`. The built-in `system-one` adapter has default endpoints for
`openrouter` and `typesafe`; other adapters require an explicit endpoint and a registered
`DecisionApiAdapter` bean. The API key is added to the request by the server; it never appears in
task input or output.
