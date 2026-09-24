# Decision models

`DECISION_MODEL` runs typed decisions on the server. Providers implement
`DecisionModel`; Jev supports OpenRouter and TypeSafe.

Set `JEV_API_KEY` on the server. The default route is OpenRouter; use
`JEV_ROUTE=typesafe` for TypeSafe credentials. AI integrations must be enabled.
Optional properties: `conductor.ai.jev.endpoint` and `conductor.ai.jev.timeout`
(default `20s`). Credentials stay in server configuration.

```json
{
  "name": "decision_model",
  "taskReferenceName": "check_urgency",
  "type": "DECISION_MODEL",
  "retryCount": 0,
  "inputParameters": {
    "provider": "jev",
    "model": "jev-1.13",
    "state": "The customer cannot access their account.",
    "questions": {
      "urgent": {"type": "boolean", "instructions": "Does this need urgent attention?"}
    }
  }
}
```

Each question requires `type` and `instructions`:

| Type | Options | Answer |
| --- | --- | --- |
| `choice` | `choices`: 2–255 named descriptions | `choice`: selected name |
| `score` | `scale`: 2–10 ordered descriptions | `score`: 0 to scale length minus one |
| `boolean` | None | `probability`: 0 to 1 |

Results contain `model`, `answers`, `usage`, `latencyMs`, and optional `requestId`.
Answers may include `confidence`. Usage reports `inputTokens`, `outputTokens`,
`cost`, and `currency` when available; missing values are omitted.

Agent tools use `toolType: "decision_model"` with fixed `config.provider`,
`config.model`, and optional `config.questions`. Only `state` and unfixed
`questions` come from tool arguments. See the [Python example](examples/jev/README.md).

Agent decision tasks default to zero retries. Explicit task policies may retry
transport failures, HTTP 429, or HTTP 5xx. Invalid requests and responses fail
terminally. The HTTP client does not retry or follow redirects.
