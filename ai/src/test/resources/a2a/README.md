# A2A end-to-end testing

This directory holds helpers for testing Conductor's A2A (Agent2Agent) client against **real
agents**. The implementation lives in `ai/src/main/java/org/conductoross/conductor/ai/a2a/`.

## Test layers

| Layer | What it covers | Runs in CI? |
|---|---|---|
| `A2AServiceTest` | JSON-RPC client wire behavior via OkHttp `MockWebServer` | yes |
| `A2AEndToEndTest` | Discovery, send, **poll-to-completion**, streaming (SSE), cancel — against a real embedded A2A HTTP server (`EmbeddedA2AAgent`) over loopback | yes |
| `A2ACallbackResourceTest` | Push-notification receiver completing a task, against the embedded agent (engine mocked) | yes |
| `A2ASdkInteropTest` | Discovery + send + poll + streaming + message-mode against the **official `a2a-sdk` reference agent**, launched as a subprocess | when a Python with `a2a-sdk` is found (set `A2A_PYTHON`) |
| `A2ADurableEngineEndToEndTest` (`test-harness`) | `CALL_AGENT` through the **real engine** (decider + `AsyncSystemTaskExecutor` + Redis), proving crash/restart resume | yes (needs Docker for Redis) |
| `A2ARealAgentIntegrationTest` | Discovery + `CALL_AGENT` against a **real external agent** | only when `A2A_AGENT_URL` is set |

`EmbeddedA2AAgent` is a genuine HTTP server speaking A2A JSON-RPC + SSE — not a mock — so the
CI suite already exercises the real wire protocol. The opt-in layer below points the same client
at a third-party agent (e.g. one built on the official `a2a-sdk`).

## Run against a real agent (opt-in)

### 1. Start an agent

A minimal real agent built on the official Python `a2a-sdk` is provided here:

```bash
uv venv --python 3.12 && uv pip install "a2a-sdk>=0.2,<0.3" uvicorn
AGENT_MODE=task python ai/src/test/resources/a2a/echo_agent.py   # serves http://localhost:9999
# AGENT_MODE=message  -> returns a direct message instead of a Task
```

Or run any agent from [`a2aproject/a2a-samples`](https://github.com/a2aproject/a2a-samples)
(e.g. `samples/python/agents/helloworld` or the LangGraph currency agent).

### 2. Run the integration test against it

```bash
A2A_AGENT_URL=http://localhost:9999 \
A2A_AGENT_PROMPT="convert 100 USD to EUR" \
  ./gradlew :conductor-ai:test --tests '*A2ARealAgentIntegrationTest'
```

Optional env: `A2A_AGENT_TOKEN` (sent as `Authorization: Bearer <token>`).

> Verified locally against `echo_agent.py` (a2a-sdk 0.2.6): discovery resolved the Agent Card and
> `CALL_AGENT` returned `state=completed`, `text=echo-task: convert 100 USD`.

## Full-server verification (manual)

To exercise the whole engine path (workflow → engine → `CALL_AGENT` system task → real agent):

1. Start an agent (above).
2. Start Conductor with the AI integration enabled:
   `conductor.integrations.ai.enabled=true` (and, for push mode,
   `conductor.a2a.callback.url=<externally-reachable-base-url>`).
3. Register and run `ai/examples/10-a2a-call-agent.json`, setting `inputParameters.agentUrl`
   to the agent's URL (e.g. `http://localhost:9999`).
4. Confirm the workflow completes and the `CALL_AGENT` task output carries the agent's
   `state`, `text`, `artifacts`, `taskId`, and `contextId`. Use the `conductor` CLI/skill to
   start the workflow and inspect the execution.

## Task types

- `CALL_AGENT` — send a message to a remote agent; poll (default), stream (`streaming:true`),
  or push (`pushNotification:true` + `conductor.a2a.callback.url`) for long-running work.
- `GET_AGENT_CARD` — discover an agent's skills/capabilities.
- `CANCEL_AGENT_TASK` — cancel a running remote agent task.
