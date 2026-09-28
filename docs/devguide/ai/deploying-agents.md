---
description: The four ways to put an agent on a Conductor server — plan, run, deploy, and serve — and which one belongs in CI, in production, and at your desk.
---

# Deploying Agents

An agent you write in an SDK is just a definition until something puts it on a server. `AgentRuntime` gives you four verbs for that, and the difference between them is the difference between a script and a deployed capability.

| Verb | What it does | Where it belongs |
|---|---|---|
| `plan()` | Compiles the agent to a workflow definition and returns it. Nothing is registered, nothing runs. | Development and CI |
| `run()` | Registers if needed, executes once, blocks for the result. | Your desk |
| `deploy()` | Registers a named, versioned agent on the server. Does not execute. | Release pipeline |
| `serve()` | Starts the long-lived worker process that executes the agent's tools. | Production, as a service |

## plan — see the graph before anything runs

`plan()` compiles the agent and hands back the workflow definition. No server writes, no execution.

```python
with AgentRuntime() as runtime:
    definition = runtime.plan(agent)
```

This is the cheapest possible check and the one most people skip. Diff the output in CI and a reviewer sees exactly what changed in the graph when someone edits an instruction or adds a tool.

## run — one execution, blocking

```python
with AgentRuntime() as runtime:
    result = runtime.run(agent, "What's the weather in San Francisco?")
    result.print_result()
    print(result.execution_id)
```

`run()` is the development loop: it registers the agent if it isn't there, executes once, and blocks until there's a result. It also starts the workers it needs in-process, which is why a script with tools works without you running anything else.

That in-process convenience is exactly why it isn't a production pattern — when the script exits, the workers go with it.

There are two non-blocking siblings:

- **`start()`** returns an `AgentHandle` immediately instead of waiting.
- **`stream()`** returns an `AgentStream` so you can consume events as they happen.

## deploy — register a named, versioned capability

```python
with AgentRuntime() as runtime:
    runtime.deploy(agent)
```

After `deploy()`, the agent exists on the server under its name and can be invoked by anything — an `AGENT` task in a workflow, the API, a schedule — without your code being involved. This is what makes an agent a shared capability rather than a script someone runs.

`deploy()` takes several agents at once, and accepts `packages=`:

```python
runtime.deploy(billing_agent, support_agent)
```

Once deployed, put the agent on a cadence with the CLI or the API rather than in code — see [Scheduling Agents](scheduling-agents.md).

## serve — the worker process that does the work

Deploying registers the definition. It does not start anything that can execute your Python tools. `serve()` is that process:

```python
with AgentRuntime() as runtime:
    runtime.serve(agent)          # blocks
    # runtime.serve(agent, blocking=False)   # returns, for tests
```

If a deployed agent's executions sit in a scheduled state and never progress, this is almost always the reason: nobody is serving its workers.

## The production shape

Split the two, and run them at different times:

```python
# release.py — runs once in CI/CD
with AgentRuntime() as runtime:
    runtime.deploy(agent)

# worker.py — runs continuously as a service
with AgentRuntime() as runtime:
    runtime.serve(agent)
```

Callers then invoke the agent by name and never import your code:

```json
{
  "name": "run_agent",
  "taskReferenceName": "run_agent_ref",
  "type": "AGENT",
  "inputParameters": { "agentType": "conductor", "name": "your-agent-name" }
}
```

## Controlling a running execution

Once something is running, `AgentRuntime` is also the control plane. Each takes an `execution_id`:

| Method | Use |
|---|---|
| `get_status(id)` | Where is it |
| `pause(id)` / `resume(id, agent)` | Hold and continue |
| `cancel(id, reason)` / `stop(id)` | End it |
| `approve(id)` / `reject(id, reason)` | Answer a human gate |
| `respond(id, output)` / `send_message(id, msg)` / `signal(id, msg)` | Feed something in |

## Production notes

- **`deploy()` and `serve()` are different jobs.** Deploying from your laptop and never serving is the most common way to get a stuck execution.
- **Version deliberately.** Callers resolve by name; pin the version in the caller when a change isn't backward compatible.
- **`plan()` belongs in CI.** It is the only way to review a graph change without touching the server.
- **`run()` in a script is not a deployment.** The workers die with the process.
- **Serve where the tools can run.** CLI tools, file access, and credentials all resolve in the worker process, not on the server.

## Next steps

- [Agent Configuration](agent-configuration.md) — what's fixed at deploy time and what you can override per run
- [Scheduling Agents](scheduling-agents.md) — attach a cron schedule at deploy time
- [Conductor agent recipe](cookbook/reusable-conductor-agent.md) — a deployed agent invoked from a workflow
