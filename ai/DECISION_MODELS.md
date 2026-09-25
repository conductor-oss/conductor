# Decision models

`DECISION_MODEL` runs typed decisions on the server. Providers implement
`DecisionModel`; Jev supports OpenRouter and TypeSafe.
See the [Jev API reference](https://docs.typesafe.ai/api) for the provider protocol.

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
`questions` come from tool arguments. The [Python example](examples/jev/jev_agent.py)
uses `DecisionModelTool` in an `@agent`; no Python worker is needed.

Standalone decision agents compile directly to one `DECISION_MODEL` task, with
`classifier=agent` metadata so they appear in Agent Executions. Set `kind` to
`decision`, `decisionProvider` to `jev`, and `model` to the Jev model. Fixed
`questions` belong in the agent definition; otherwise supply `context.questions`
when starting an execution. The execution prompt supplies the observed state.
Results retain answers and provider usage, and decision events appear in the UI.
Decision agents can also run as child agents.

Compiled decision agents and chat agents use three retries with exponential
delays of 1, 2, and 4 seconds, capped at 5 seconds. The existing
`DecisionModelTool` keeps its zero-retry default. Explicit task policies may retry
transport failures, HTTP 429, or HTTP 5xx. Invalid decision requests and responses
fail terminally. The HTTP client does not retry or follow redirects.

To run the example, use Python 3.10+ and the SDK checkout from
[python-sdk #511](https://github.com/conductor-oss/python-sdk/pull/511).
Configure an orchestration chat model on the server, then run from the repository root:

```bash
python3 -m venv .venv
source .venv/bin/activate
python3 -m pip install -e '/path/to/python-sdk[agents]'
export CONDUCTOR_SERVER_URL=http://localhost:8080/api
export CONDUCTOR_AGENT_LLM_MODEL=openai/gpt-4o-mini
python3 ai/examples/jev/jev_agent.py run
```

Use `plan` to compile without inference, or `--request` to supply another state file.
Both the chat model and Jev incur inference costs. Read the `DECISION_MODEL` task
output for exact answers and usage.
