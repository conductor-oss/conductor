# Decision agents

A decision agent uses `kind: "decision"` and performs one inference per execution. The definition supplies `model` and optionally fixed `questions`;
the execution `prompt` supplies the observed state. If questions are not fixed,
provide them in `context.questions` when starting the agent. Decision agents can also
be child agents.

Set `DECISION_API_KEY` on the server and enable AI integrations. The default
provider is OpenRouter; `DECISION_PROVIDER` selects the default provider.
An agent definition or AI_DECISION task may set `provider` explicitly. Credentials
remain on the server. The default API shape is `system-one`, with a `20s` timeout.

Configuration lives under `conductor.ai.decision`. `api-shape` selects a registered
`DecisionApiAdapter`. Resolution is model override, then provider override, then
the global default. Model keys are exact model identifiers. Provider credentials
never fall back to another provider's credentials.

```yaml
conductor:
  ai:
    decision:
      provider: openrouter
      api-key: ${DECISION_API_KEY}
      api-shape: system-one
      providers:
        typesafe:
          api-key: ${TYPESAFE_API_KEY}
          models:
            "[jev-1.13]":
              api-shape: system-one
```

Provider and model entries can override `endpoint`; provider entries can also set
`api-key` and `api-shape`. The System One adapter supplies default endpoints for
OpenRouter and TypeSafe. Other API shapes require an endpoint and a registered
adapter bean implementing request encoding and response decoding. Unknown shapes
fail before sending a request. System One is the only built-in adapter.

Canonical agent kind, task type, and SSE event type are `decision`,
`DECISION_AGENT`, and `decision`. The UI renders inference as `decision`.
`AI_DECISION` uses the same inference client and provider/model selection for
traditional workflows. Its output includes `selectedCase`, so a downstream SWITCH task can
route on `${<ref>.output.selectedCase}`.

Each question requires `type` and `instructions`:

| Type | Options | Answer |
| --- | --- | --- |
| `choice` | `choices`: 2–255 named descriptions | `choice`: selected name |
| `score` | `scale`: 2–10 ordered descriptions | `score`: 0 to scale length minus one |
| `boolean` | None | `probability`: 0 to 1 |

The agent result contains the resolved `provider`, `model`, `answers`, `usage`,
`latencyMs`, and optional `requestId`. Answers may include `confidence`. Usage contains `inputTokens`,
`outputTokens`, `cost`, and `currency` when reported by the provider.

The runtime retries transport errors, HTTP 429, and HTTP 5xx up to three times
with exponential backoff capped at five seconds. Invalid requests and responses
fail terminally. The HTTP client does not retry or follow redirects. Agent
`timeoutSeconds` follows the same policy as other agents. Chat retry settings
are unchanged.
