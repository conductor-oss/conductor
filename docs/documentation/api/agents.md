---
description: "Conductor Agents REST API — compile, deploy, start, observe, control, and respond to SDK-authored durable agent executions."
---

# Conductor Agents API

The Conductor Agents control plane compiles SDK-authored or framework-native agents into durable Conductor graphs, then deploys and operates those graphs. Use the SDK for framework setup and interactive development; use these REST endpoints when you need CI/CD, an operations console, or a custom integration.

These endpoints are available when the embedded Conductor Agents runtime is enabled on the server.

## Base path

```
http://localhost:8080/api/agent
```

## Agent lifecycle

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/compile` | Compile an inline agent request into a plan without deploying or running it. |
| `POST` | `/inspect-plan` | Validate and inspect a deterministic plan against an agent configuration. |
| `POST` | `/deploy` | Compile and register an agent definition for later runs. |
| `POST` | `/start` | Start a deployed agent or an inline agent configuration. |
| `GET` | `/list` | List registered agents. |
| `GET` | `/{name}?version=` | Get a registered agent definition. |
| `DELETE` | `/{name}?version=` | Delete a registered agent definition. |

`/compile`, `/deploy`, and `/start` accept an `AgentStartRequest`. To use a previously deployed agent, provide `name` and optionally `version`. To create an agent inline, provide either `agentConfig` for a native Conductor Agent or `framework` plus framework-specific `rawConfig` for a supported bridge.

```json
{
  "name": "customer-support-agent",
  "version": 1,
  "prompt": "Summarize the customer's latest support case.",
  "sessionId": "case-1234"
}
```

Start a deployed agent:

```shell
curl -X POST 'http://localhost:8080/api/agent/start' \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "customer-support-agent",
    "prompt": "Summarize the customer case.",
    "sessionId": "case-1234"
  }'
```

The response includes `executionId`, `agentName`, and any `requiredWorkers` that an SDK must register.

## Observe and interact with executions

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/executions` | Search agent executions. Supports `start`, `size`, `sort`, `freeText`, `status`, `agentName`, and `classifier`. |
| `GET` | `/executions/{executionId}` | Get detailed execution state. |
| `GET` | `/{executionId}/status` | Get lightweight execution status for polling. |
| `GET` | `/stream/{executionId}` | Open an SSE stream of agent events. Supports `Last-Event-ID` reconnection. |
| `POST` | `/{executionId}/respond` | Supply output to a pending human-in-the-loop request. |
| `POST` | `/{executionId}/signal` | Add a persistent message to an active agent's context. |
| `POST` | `/events/{executionId}` | Accept a framework-worker event, such as a LangChain or LangGraph event. |

Use `respond` when the agent is waiting for human input:

```shell
curl -X POST 'http://localhost:8080/api/agent/EXECUTION_ID/respond' \
  -H 'Content-Type: application/json' \
  -d '{"approved": true}'
```

## Control execution

| Method | Path | Purpose |
|---|---|---|
| `PUT` | `/{executionId}/pause` | Pause a running agent. |
| `PUT` | `/{executionId}/resume` | Resume a paused agent. |
| `DELETE` | `/{executionId}/cancel?reason=` | Cancel an agent and propagate cancellation through its graph. |
| `POST` | `/{executionId}/stop` | Request a graceful stop after the current iteration. |

## Provider and skill endpoints

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/providers/status` | Report which server-side AI providers are configured; Ollama also reports its resolved URL and reachability. |
| `POST` | `/api/skills/register` | Upload a skill package and manifest. |
| `GET` | `/api/skills` | List skill packages. |
| `GET` | `/api/skills/{name}` | Get the latest version of a skill package. |
| `POST` | `/api/skills/{name}/versions/{version}/deploy` | Deploy a specific skill package as an agent. |
| `DELETE` | `/api/skills/{name}/versions/{version}` | Delete a skill package version. |

The skill endpoints are present only when skill packages are enabled on the server.

## Related guides

- [Conductor Agents](../../devguide/ai/conductor-agents.md) — SDK creation, deploy/serve lifecycle, and use as an `AGENT` task.
- [Framework Agent Recipes](../../devguide/ai/agent-framework-recipes.md) — OpenAI Agents, Google ADK, LangChain, LangGraph, Vercel AI SDK, and native paths.
- [A2A Integration](../../devguide/ai/a2a-integration.md) — Remote A2A agents; this is a separate `AGENT` mode.
