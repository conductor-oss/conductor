# Decision models

`DECISION_MODEL` is an annotated server task for bounded, typed decisions. It is
independent of chat completion. `DecisionModel` is the provider interface;
`JevDecisionModel` is the first implementation. `DecisionModelRegistry` resolves
the requested provider and `DecisionModelWorker` validates inputs and outputs.

## Configure Jev

Enable AI integrations and supply the credential to the server process:

```properties
conductor.integrations.ai.enabled=true
conductor.ai.jev.api-key=${JEV_API_KEY:}
conductor.ai.jev.route=${JEV_ROUTE:openrouter}
```

These mappings are included in the server's `application.properties`. Set
`JEV_API_KEY` to an OpenRouter key for the default route, or set `JEV_ROUTE=typesafe`
and use a TypeSafe credential. A Jev token obtained through Vercel Connect can be
supplied as the TypeSafe credential; token renewal is managed by the deployment,
not by this provider.

The default endpoints are `https://openrouter.ai/api/v1/systemone` and
`https://api.typesafe.ai/v1/systemone`. Administrators may override
`conductor.ai.jev.endpoint` and `conductor.ai.jev.timeout` (default `20s`). Neither
setting is accepted from task input. No API key is included in task input/output,
agent configuration, or provider error messages. Redirects and HTTP client retries
are disabled. Conductor task policy owns retries.

## Task contract

A workflow can use the task directly without an external worker:

```json
{
  "name": "decision_model",
  "taskReferenceName": "choose_team",
  "type": "DECISION_MODEL",
  "retryCount": 0,
  "inputParameters": {
    "provider": "jev",
    "model": "jev-1.13",
    "state": "The customer was charged twice.",
    "questions": {
      "team": {
        "type": "choice",
        "instructions": "Which team should handle this issue?",
        "choices": {"billing": "Payment issues", "technical": "Software issues"}
      }
    }
  }
}
```

The contract is defined by `DecisionRequest`, `DecisionQuestion`, and
`DecisionResult` in `ai/.../decision/`:

| Question type | Input | Answer field |
| --- | --- | --- |
| `choice` | `choices`: 2–255 named descriptions | `choice`: an allowed name |
| `score` | `scale`: 2–10 ordered descriptions | `score`: 0 through scale length minus one |
| `boolean` | No choices or scale | `probability`: 0 through 1 |

All questions require nonempty `instructions`. Results contain `model` (actual
provider revision), `answers`, `usage`, `latencyMs`, and optional `requestId`.
Answers may include `confidence`. Usage contains reported `inputTokens`,
`outputTokens`, and optional `cost`/`currency`; unknown values are omitted.
OpenRouter's reported cost is denominated in USD. The Jev adapter translates
boolean questions to Jev's `noul` wire type and converts the answer back to
`probability`.

Invalid inputs, malformed responses, redirects, and non-transient HTTP errors
fail terminally. Transport failures, HTTP 429, and HTTP 5xx are retryable by an
explicit task policy. The agent-tool compiler defaults to zero retries; rerunning
an execution can still cause another billed request.

## Agent tool contract

Agents declare `toolType: "decision_model"` with `config.provider`, `config.model`,
and optional fixed `config.questions`. The model supplies `state` and, only when
not fixed, `questions`. Agent-loop and plan compilation produce `DECISION_MODEL`
tasks, preserving configured routing and fixed questions and excluding other
model-generated arguments. Credential fields in tool configuration are rejected.

The Python SDK's `DecisionModelTool` serializes this contract and registers no
Python worker. The [Python example](examples/jev/README.md) uses it with `@agent`.
Other SDKs can implement the same thin tool definition without duplicating Jev
transport or credentials.

References: [TypeSafe API](https://docs.typesafe.ai/introduction/quickstart),
[OpenRouter System One API](https://openrouter.ai/docs/guides/community/typesafe-sdk).
