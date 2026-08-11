---
description: "Conductor Agents — compile SDK-authored agents into durable, inspectable Conductor graphs and use them as reusable AGENT tasks."
---

# Conductor Agents

<section class="integration-hero integration-hero--agents" aria-labelledby="conductor-agents-hero-title">
  <h2 id="conductor-agents-hero-title">Get started with Conductor Agents</h2>
  <div class="integration-action-grid integration-action-grid--three">
    <a class="integration-action-card" href="../../quickstart/first-agent.html">
      <span class="integration-action-card__title">Author a Conductor Agent</span>
      <span>Author with Python, Java, TypeScript/JavaScript, or C#.</span>
    </a>
    <a class="integration-action-card" href="agent-framework-recipes.html">
      <span class="integration-action-card__title">Bring a framework agent</span>
      <span>Choose a bridge for OpenAI Agents, Google ADK, LangChain, LangGraph, or more.</span>
    </a>
    <a class="integration-action-card" href="#use-a-deployed-agent-in-a-workflow">
      <span class="integration-action-card__title">Use it in a workflow</span>
      <span>Invoke the deployed graph as a reusable <code>AGENT</code> task.</span>
    </a>
  </div>
</section>

A **Conductor Agent** is an agent you author in code and register on the server. You write it with a Conductor SDK, or bring it from a supported framework through a bridge, and Conductor compiles it into an ordinary workflow definition. Because the compiled agent is a workflow, every LLM call, tool invocation, wait, retry, and branch is visible in the UI and API, and the agent composes with everything else a workflow can contain: other tasks, branching, schedules, human approval, and cancellation. Conductor Agents are available in Python, Java, TypeScript/JavaScript, and C#.

Conductor Agents are one of two ways to build AI behavior. The other is a [declarative AI workflow](llm-orchestration.md), where you place LLM, MCP, and control-flow tasks directly in the workflow definition. Choose the declarative path when the orchestration itself is what you are building. Choose a Conductor Agent when the agent logic lives in code and you want to run it inside a durable process.

## Lifecycle

Every Conductor Agent moves through the same five operations, and the names below are the SDK verbs you will see in code:

1. **Create**: define the agent in code, from the SDK's own `Agent` class or from a supported framework object.
2. **Plan**: inspect the workflow graph the agent will compile to. Useful during development and in CI, before anything is deployed.
3. **Deploy**: register the compiled agent on the server as a reusable, versioned Conductor Agent.
4. **Serve**: start the worker process that executes the agent's tools, where the bridge requires one.
5. **Run**: execute the agent. During development, `run` compiles and runs it in one step. In production, workflows invoke the deployed agent by name through an `AGENT` task.

In short: use `run` while you iterate, then `deploy` and `serve` so workflows and other callers can start the stable deployed version.

For framework-specific code, package versions, and runnable examples, see [Framework Agent Bridges](agent-framework-recipes.md). For server setup and credentials, complete [Connect to Conductor](../../quickstart/connect.md).

## Use a deployed agent in a workflow

`agentType` chooses the **execution mode**, not the authoring framework:

- `agentType: "a2a"` (default) calls a remote A2A endpoint.
- `agentType: "conductor"` runs a deployed Conductor Agent selected by `name`.

OpenAI Agents, Google ADK, LangGraph, and other supported bridges are SDK authoring paths. They are not `agentType` values.

```json
{
  "name": "run_agent",
  "taskReferenceName": "run_agent_ref",
  "type": "AGENT",
  "inputParameters": {
    "agentType": "conductor",
    "name": "planner",
    "prompt": "${workflow.input.prompt}",
    "pollIntervalSeconds": 5
  }
}
```

On a fresh call, `name` and `prompt` are required. `version` optionally pins the deployed agent version; omit it to use the latest version. `sessionId`, `runId`, `context`, `media`, `model`, `timeoutSeconds`, and `idempotencyKey` are available when the deployed agent contract needs them. The runtime creates a restart-stable idempotency key if one is not supplied.

## Output and durable execution contract

The `AGENT` task writes `executionId`, `agentName`, `state`, `text`, and, for completed runs, structured `output`. Its `state` is the normalized A2A lifecycle value: `working`, `input-required`, `completed`, `failed`, or `canceled`.

| Runtime state / output `state` | Conductor task status | Meaning |
|---|---|---|
| `RUNNING` / `working` | `IN_PROGRESS` | The task polls again after `pollIntervalSeconds` (default 5). |
| `WAITING` / `input-required` | `COMPLETED` | The run paused for human or tool input; output includes `waiting: true` and may include `pendingTool`. |
| `COMPLETED` / `completed` | `COMPLETED` | Output includes the final `text` and structured `output`. |
| `FAILED` / `failed` | `FAILED` | The task includes the completion reason. |
| `CANCELED` / `canceled` | `CANCELED` | The task includes the cancellation reason when available. |

`maxDurationSeconds` bounds the full run (default 86400 seconds) and `maxPollFailures` bounds consecutive transient poll failures (default 30). Both fail the task terminally and make a best-effort cancellation of the child execution. These guards are separate from normal task-definition timeouts.

## Resume and cancellation

When an agent waits for external input, its first `AGENT` task completes rather than holding a worker. A workflow can collect the answer in a `HUMAN` task and resume the same run with another `AGENT` task:

```json
{
  "name": "resume_agent",
  "taskReferenceName": "resume_agent_ref",
  "type": "AGENT",
  "inputParameters": {
    "agentType": "conductor",
    "executionId": "${run_agent_ref.output.executionId}",
    "prompt": "${collect_answer_ref.output.answer}"
  }
}
```

On a resume, `executionId` identifies the run and `prompt` provides the response; `name` is not required. Workflow cancellation is propagated to an in-flight Conductor Agent on a best-effort basis.

## Guardrails and evaluations

SDK-authored agents can compile runtime guardrails for agent output and tool input or output. Choose a deterministic regex guardrail for format, PII, and known-dangerous patterns; use an LLM guardrail for semantic policy; use a custom or external guardrail when policy needs an application service. A guardrail can retry, fail closed, provide a custom repair, or pause for durable human review. Put the strongest guardrail directly before a consequential tool call.

Before promotion, evaluate the recorded agent behavior—not only its final text. The Python SDK's evaluation harness can assert tool selection and arguments, handoffs, guardrail events, turn counts, and terminal state, then use an optional LLM judge for qualitative criteria. See [Agent Guardrails](agent-guardrails.md) and [Agent Evals](agent-evals.md) for the runtime policy and CI patterns.

## Workflow-integration recipes

These repository examples deliberately contain only the stable workflow contract. They are framework-agnostic; create and deploy `planner` / `researcher` with the SDK bridge appropriate to your framework.

| Recipe | What it demonstrates |
|---|---|
| [`31-conductor-agent-basic.json`](https://github.com/conductor-oss/conductor/blob/main/ai/examples/31-conductor-agent-basic.json) | A reusable deployed agent as one step in a workflow. |
| [`32-conductor-agent-human-in-loop.json`](https://github.com/conductor-oss/conductor/blob/main/ai/examples/32-conductor-agent-human-in-loop.json) | `WAITING` → `HUMAN` → resume with `executionId`. |
| [`33-conductor-agent-multi-agent.json`](https://github.com/conductor-oss/conductor/blob/main/ai/examples/33-conductor-agent-multi-agent.json) | Parallel specialist agents inside a `FORK_JOIN` / `JOIN` graph. |
| [`34-conductor-agent-cancel.json`](https://github.com/conductor-oss/conductor/blob/main/ai/examples/34-conductor-agent-cancel.json) | Cancellation propagation from the parent graph. |

Next: choose a framework route in [Framework Agent Bridges](agent-framework-recipes.md), compose the deployed agent in [Build Your First Agentic Workflow Graph](first-ai-agent.md), then use the [Production Agent Architecture](production-agent-architecture.md) for governance, evaluation, deployment, recovery, and operations.
