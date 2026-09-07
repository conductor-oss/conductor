# A2A interop showcase — Conductor calling a real, non-Conductor agent

This demo points Conductor at a **genuine third-party A2A agent** — the official
[`a2a-sdk`](https://github.com/a2aproject/a2a-python) reference "echo" agent — and runs a workflow
that **discovers** it (`GET_AGENT_CARD`) and **calls** it (`AGENT`) over the real A2A JSON-RPC
wire protocol. It proves the integration works against the protocol's reference implementation, not
just our own fixtures.

```
┌─────────────────────────┐   A2A / JSON-RPC over HTTP   ┌───────────────────────────┐
│ Conductor (this branch) │ ───────────────────────────▶ │ echo agent (official      │
│  a2a_interop_echo wf     │   GET /.well-known/card      │ a2a-sdk — NOT Conductor)  │
│  GET_AGENT_CARD          │   POST message/send          │  :9998                    │
│  AGENT                  │   POST tasks/get             │                           │
└─────────────────────────┘ ◀─────────────────────────── └───────────────────────────┘
```

## Run it

```bash
./run-interop-demo.sh
```

Requires Java 21+, curl, and either [`uv`](https://docs.astral.sh/uv/) (auto-creates a Python venv
with the `a2a-sdk`) or `A2A_VENV` pointing at a Python that already has `a2a-sdk` + `uvicorn`. No
Docker, no Redis, no API keys.

Expected tail:

```
  workflow status : COMPLETED
  discovered agent: Echo Agent
  agent state     : completed
  agent reply     : echo-task: convert 100 USD to EUR
✓ Conductor discovered and called a real third-party A2A agent. That is A2A interop.
```

## Point it at other real agents

Any A2A agent works — the workflow only needs its base URL. To showcase against the broader
ecosystem, run an agent from [`a2aproject/a2a-samples`](https://github.com/a2aproject/a2a-samples)
(e.g. `samples/python/agents/helloworld` or the LangGraph currency agent) and start the workflow
with that agent's URL:

```bash
curl -X POST localhost:7002/api/workflow/a2a_interop_echo \
  -H 'Content-Type: application/json' \
  -d '{"agentUrl":"http://localhost:9999","prompt":"convert 100 USD to EUR"}'
```

## Related

- **Automated interop test** (no manual steps): `A2ASdkInteropTest` launches this same `a2a-sdk`
  agent as a subprocess and drives discovery + send + poll + streaming + message-mode against it.
  Run with `A2A_PYTHON=<venv>/bin/python ./gradlew :conductor-ai:test --tests '*A2ASdkInteropTest'`.
- **Durability** (kill the server mid-call, it resumes): `../durable-demo/`.
- **Real-engine durability test** (CI, through the decider/sweeper/Redis):
  `A2ADurableEngineEndToEndTest` in the `test-harness` module.
