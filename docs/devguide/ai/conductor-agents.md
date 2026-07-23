---
description: "Conductor Agents — compile SDK-authored agents into durable, inspectable Conductor graphs and use them as reusable AGENT tasks."
---

# Conductor Agents

<section class="integration-hero integration-hero--agents" aria-labelledby="conductor-agents-hero-title">
  <div class="integration-hero__identity integration-hero__identity--conductor" aria-hidden="true">
    <img class="integration-hero__logo integration-hero__logo--conductor" src="../../img/logo.svg" alt="" />
  </div>
  <p class="integration-hero__eyebrow">SDK-authored durable agents</p>
  <h2 id="conductor-agents-hero-title">Keep your framework. Get a durable graph.</h2>
  <p>Author agents with the SDK or framework your team already uses. Conductor compiles the result into an inspectable, retryable graph that larger workflows can invoke.</p>
  <div class="agent-framework-strip" aria-label="Supported Conductor Agent authoring paths">
    <span class="agent-framework-strip__item"><img src="../../assets/images/frameworks/openai.svg" alt="" />OpenAI Agents</span>
    <span class="agent-framework-strip__item"><img src="../../assets/images/frameworks/google-adk.svg" alt="" />Google ADK</span>
    <span class="agent-framework-strip__item"><img src="../../assets/images/frameworks/langchain.svg" alt="" />LangChain</span>
    <span class="agent-framework-strip__item"><img src="../../assets/images/frameworks/langgraph.svg" alt="" />LangGraph</span>
    <span class="agent-framework-strip__item"><img src="../../assets/images/frameworks/vercel.svg" alt="" />Vercel AI SDK</span>
  </div>
  <div class="integration-action-grid integration-action-grid--three">
    <a class="integration-action-card" href="../../quickstart/first-agent.html">
      <span class="integration-action-card__title">Author in an SDK</span>
      <span>Run a native Python Conductor Agent interactively.</span>
    </a>
    <a class="integration-action-card" href="agent-framework-recipes.html">
      <span class="integration-action-card__title">Bring a framework agent</span>
      <span>Choose a supported bridge and its maintained example.</span>
    </a>
    <a class="integration-action-card" href="#use-a-deployed-agent-in-a-workflow">
      <span class="integration-action-card__title">Use it in a workflow</span>
      <span>Invoke the deployed graph as a reusable <code>AGENT</code> task.</span>
    </a>
  </div>
</section>

Conductor Agents are distinct from building a declarative AI workflow directly with `LLM_CHAT_COMPLETE`, MCP, `HUMAN`, and control-flow tasks. Both paths are first-class: use direct tasks when the graph is the application, and Conductor Agents when you bring framework-native logic into a broader durable process.

The SDK-created agent is compiled into ordinary Conductor workflow definitions. That makes every LLM call, tool invocation, wait, retry, and branch visible in the UI and API, and lets the same graph compose with ordinary tasks, `SWITCH`, `FORK_JOIN`, `HUMAN`, schedules, and workflow cancellation.

## Lifecycle

Use the SDK that owns the framework bridge for framework code, package versions, and runnable examples. The stable lifecycle is:

1. **Create** an agent from a supported framework object in the SDK.
2. **Plan** or inspect the generated graph during development and CI.
3. **Deploy** the agent to register a reusable, versioned Conductor Agent.
4. **Serve** its workers where the SDK bridge requires a long-running worker process.
5. **Run** directly for interactive use, or invoke the deployed agent by name from an `AGENT` task in a larger workflow.

For interactive development, use `run`. For production, deploy the agent and serve its workers so that workflow callers can start the stable deployed version. See [Framework Agent Recipes](agent-framework-recipes.md) for maintained SDK documentation and executable examples.

For server setup, start a local Conductor server and configure the SDK with `CONDUCTOR_SERVER_URL`. The maintained SDK setup guides are the source of truth for deployment-specific agent runtime configuration.

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

Next: choose a framework route in [Framework Agent Recipes](agent-framework-recipes.md), or compose the deployed agent in [Build Your First Agentic Workflow Graph](first-ai-agent.md).
