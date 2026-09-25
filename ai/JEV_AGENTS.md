# Jev agents

Jev is used through an agent definition (`kind: "jev"`). It performs one inference
per execution. The definition supplies `model` and optionally fixed `questions`;
the execution `prompt` supplies the observed state. If questions are not fixed,
provide them in `context.questions` when starting the agent. Jev agents can also
be child agents.

Set `JEV_API_KEY` on the server and enable AI integrations. The default route is
OpenRouter; set `JEV_ROUTE=typesafe` for TypeSafe credentials. Optional server
properties are `conductor.ai.jev.endpoint` and `conductor.ai.jev.timeout` (default
`20s`). Credentials remain on the server.

The [example agent definition](examples/jev/agent.json) routes support requests.
The Python example uses the standard library to submit that definition through
`POST /api/agent/compile` or `POST /api/agent/start`. No SDK checkout, chat model,
or Python worker is required.

```bash
export CONDUCTOR_SERVER_URL=http://localhost:8080/api
python3 ai/examples/jev/jev_agent.py plan
python3 ai/examples/jev/jev_agent.py run
```

`run` starts the agent and prints the start response; inspect the returned
execution in Agent Executions for its result. Use `--request` to supply another
JSON file containing a nonempty `state` string.

<!-- TODO: verify against live server -->
The example's HTTP requests are covered by a local test server; live Jev inference
requires a configured Conductor server and provider credentials.

Each question requires `type` and `instructions`:

| Type | Options | Answer |
| --- | --- | --- |
| `choice` | `choices`: 2–255 named descriptions | `choice`: selected name |
| `score` | `scale`: 2–10 ordered descriptions | `score`: 0 to scale length minus one |
| `boolean` | None | `probability`: 0 to 1 |

The agent result contains `model`, `answers`, `usage`, `latencyMs`, and optional
`requestId`. Answers may include `confidence`. Usage contains `inputTokens`,
`outputTokens`, `cost`, and `currency` when reported by the provider.

The runtime retries transport errors, HTTP 429, and HTTP 5xx up to three times
with exponential backoff capped at five seconds. Invalid requests and responses
fail terminally. The HTTP client does not retry or follow redirects. Agent
`timeoutSeconds` follows the same policy as other agents. Chat retry settings
are unchanged.
