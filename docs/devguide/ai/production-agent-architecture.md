---
description: "A compact, framework-neutral blueprint for adopting production AI agents on Conductor: choose an execution boundary, define contracts, govern side effects, and operate durable workflows."
---

# Production agent architecture

For the focused, runnable implementation of this pattern, start with **[Durable Adaptive Graphs](dynamic-workflows.md)**. Its source-backed `governed_github_pr_reviewer` v1 makes the controls concrete: four durable PR-review passes, a bounded allowlisted deep dive, a compact evidence ledger, and approval before one GitHub comment.

This is a compact, framework-neutral parent-workflow blueprint for an agent that plans, acts, waits, recovers, and runs in production. Keep detailed framework setup, A2A protocol behavior, and incident procedures in their specialist guides.

## The parent workflow reference path

Every path starts and ends in the parent workflow: validate the request, choose an execution boundary, validate the returned result, then apply approval, writes, or compensation. The parent owns the business process; each agent path owns only the work behind its boundary.

<div style="margin: 2rem 0;">
<svg viewBox="0 0 960 430" xmlns="http://www.w3.org/2000/svg" style="max-width: 960px; width: 100%; height: auto;" role="img" aria-labelledby="production-agent-parent-title production-agent-parent-desc">
  <title id="production-agent-parent-title">Production agent parent workflow</title>
  <desc id="production-agent-parent-desc">A parent workflow validates a request, chooses native tasks, a deployed Conductor Agent, or a remote A2A agent, validates the returned result, and then obtains approval before writing or compensates on failure. Native and deployed-agent paths are observable in Conductor; an A2A handoff is observable at the parent boundary while its internals remain remote.</desc>
  <defs>
    <marker id="parent-arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto"><path d="M 0 0 L 10 5 L 0 10 z" fill="#4a5568"/></marker>
  </defs>

  <rect x="20" y="18" width="920" height="390" rx="12" fill="rgba(59,130,246,0.04)" stroke="#94a3b8" stroke-width="1.5"/>
  <text x="42" y="45" font-size="13" font-weight="600" fill="#2e3545" font-family="sans-serif">Parent workflow — durable business-process boundary</text>

  <rect x="52" y="88" width="150" height="58" rx="6" fill="#e2e8f0" stroke="#4a5568" stroke-width="1.5"/>
  <text x="127" y="113" text-anchor="middle" font-size="12" font-weight="600" fill="#2e3545" font-family="sans-serif">Validate request</text>
  <text x="127" y="130" text-anchor="middle" font-size="10" fill="#4a5568" font-family="sans-serif">shape, policy, IDs</text>

  <line x1="202" y1="117" x2="256" y2="117" stroke="#4a5568" stroke-width="1.5" marker-end="url(#parent-arrow)"/>
  <polygon points="285,82 326,117 285,152 244,117" fill="#e2e8f0" stroke="#4a5568" stroke-width="1.5"/>
  <text x="285" y="113" text-anchor="middle" font-size="10" font-weight="600" fill="#2e3545" font-family="sans-serif">Choose</text>
  <text x="285" y="127" text-anchor="middle" font-size="10" fill="#4a5568" font-family="sans-serif">boundary</text>

  <line x1="326" y1="117" x2="372" y2="117" stroke="#4a5568" stroke-width="1.5" marker-end="url(#parent-arrow)"/>
  <line x1="285" y1="152" x2="285" y2="290" stroke="#4a5568" stroke-width="1.5"/>
  <line x1="285" y1="290" x2="372" y2="290" stroke="#4a5568" stroke-width="1.5" marker-end="url(#parent-arrow)"/>
  <line x1="326" y1="117" x2="372" y2="214" stroke="#4a5568" stroke-width="1.5" marker-end="url(#parent-arrow)"/>

  <rect x="372" y="72" width="220" height="90" rx="7" fill="rgba(6,214,160,0.10)" stroke="#06a88a" stroke-width="1.5"/>
  <text x="482" y="99" text-anchor="middle" font-size="12" font-weight="600" fill="#155e75" font-family="sans-serif">Native tasks</text>
  <text x="482" y="118" text-anchor="middle" font-size="10" fill="#2e3545" font-family="sans-serif">LLM, MCP, control flow</text>
  <text x="482" y="140" text-anchor="middle" font-size="10" fill="#155e75" font-family="sans-serif">execution + observability: Conductor</text>

  <rect x="372" y="170" width="220" height="90" rx="7" fill="rgba(59,130,246,0.10)" stroke="#2563eb" stroke-width="1.5"/>
  <text x="482" y="197" text-anchor="middle" font-size="12" font-weight="600" fill="#1d4ed8" font-family="sans-serif">Conductor Agent</text>
  <text x="482" y="216" text-anchor="middle" font-size="10" fill="#2e3545" font-family="sans-serif">AGENT: agentType conductor</text>
  <text x="482" y="238" text-anchor="middle" font-size="10" fill="#1d4ed8" font-family="sans-serif">execution + observability: Conductor</text>

  <rect x="372" y="268" width="220" height="90" rx="7" fill="rgba(245,158,11,0.12)" stroke="#d97706" stroke-width="1.5"/>
  <text x="482" y="295" text-anchor="middle" font-size="12" font-weight="600" fill="#92400e" font-family="sans-serif">Remote A2A agent</text>
  <text x="482" y="314" text-anchor="middle" font-size="10" fill="#2e3545" font-family="sans-serif">AGENT: agentType a2a</text>
  <text x="482" y="336" text-anchor="middle" font-size="10" fill="#92400e" font-family="sans-serif">handoff observable; internals remote</text>

  <line x1="592" y1="117" x2="656" y2="117" stroke="#4a5568" stroke-width="1.5"/>
  <line x1="592" y1="215" x2="656" y2="215" stroke="#4a5568" stroke-width="1.5"/>
  <line x1="592" y1="313" x2="656" y2="313" stroke="#4a5568" stroke-width="1.5"/>
  <line x1="656" y1="117" x2="656" y2="313" stroke="#4a5568" stroke-width="1.5"/>
  <line x1="656" y1="215" x2="708" y2="215" stroke="#4a5568" stroke-width="1.5" marker-end="url(#parent-arrow)"/>

  <rect x="708" y="186" width="178" height="58" rx="6" fill="#e2e8f0" stroke="#4a5568" stroke-width="1.5"/>
  <text x="797" y="211" text-anchor="middle" font-size="12" font-weight="600" fill="#2e3545" font-family="sans-serif">Validate result</text>
  <text x="797" y="228" text-anchor="middle" font-size="10" fill="#4a5568" font-family="sans-serif">schema, policy, artifacts</text>

  <line x1="797" y1="244" x2="797" y2="278" stroke="#4a5568" stroke-width="1.5" marker-end="url(#parent-arrow)"/>
  <rect x="708" y="278" width="178" height="58" rx="6" fill="#f59e0b" stroke="#d97706" stroke-width="1.5"/>
  <text x="797" y="302" text-anchor="middle" font-size="12" font-weight="600" fill="#fff" font-family="sans-serif">Approve then write</text>
  <text x="797" y="319" text-anchor="middle" font-size="10" fill="rgba(255,255,255,0.85)" font-family="sans-serif">or compensate on failure</text>
</svg>
</div>

## Choose the execution boundary

The parent workflow can use one or more of these execution paths. Choose the path based on where the agent behavior belongs; all three participate in the same durable business process.

- **Native AI tasks** run directly in the workflow graph. Use `LLM_CHAT_COMPLETE`, MCP tasks, `HUMAN`, and control-flow tasks when the workflow definition is the agent implementation.
- **Deployed Conductor Agents** run through an `AGENT` task with `agentType: "conductor"`. They include agents authored with a Conductor SDK or framework bridges for OpenAI Agents, Google ADK, LangChain, LangGraph, and Vercel AI SDK. Conductor compiles these agents into deployed workflow graphs.
- **Remote A2A agents** run through an `AGENT` task with `agentType: "a2a"`. This is a durable handoff to an independently deployed Agent2Agent service: Conductor manages the parent-workflow lifecycle, while the remote service keeps its own implementation and internals.

`agentType` selects the execution mode; it does not name an authoring framework. Use `SUB_WORKFLOW` or `START_WORKFLOW` to compose child workflows, and use `AGENT` when the parent invokes an agent runtime.

| Boundary | Use it when | Execution and observability |
|---|---|---|
| Native tasks | The workflow graph owns the orchestration and agent behavior. | Native system tasks execute and are observable in Conductor. |
| `AGENT` / `agentType: "conductor"` | The agent is authored in a Conductor SDK or a supported framework bridge: OpenAI Agents, Google ADK, LangChain, LangGraph, or Vercel AI SDK. | Conductor compiles and runs the deployed agent graph, so its execution is observable in Conductor. |
| `AGENT` / `agentType: "a2a"` | A specialist is independently deployed as a remote A2A service. | Conductor observes the durable handoff, lifecycle, and returned artifacts; the remote agent owns its private internals. |
| `SUB_WORKFLOW` / `START_WORKFLOW` | You are composing another Conductor workflow, synchronously or fire-and-forget. | These compose workflow definitions; they do not invoke either `AGENT` runtime mode. |

## Production contract at every agent boundary

| Decision | Default production contract |
|---|---|
| Input and output | Define and validate input before the boundary and output after it; do not let an unvalidated model or remote response decide a consequential action. |
| Identity and side effects | Carry a correlation ID and idempotency key into external effects and remote handoffs. Treat every tool and remote-agent side effect as at-least-once; use idempotency or an explicit reconciliation marker. |
| State owner | Keep orchestration state in workflow variables, resumable deployed-agent state behind its execution ID, and remote continuation state in A2A context and task IDs. |
| Durable payload | Return small durable artifacts and references, not raw histories or large payloads. |

## Production readiness

- Resolve credentials server-side. Never put secrets in prompts or workflow input.
- Use least-privileged tools, validate outputs, and require human approval before consequential writes.
- Bound turns, parallelism, time, tokens or cost, retries, cancellation, and compensation behavior.
- Name an owner and define one correlation-ID convention. Monitor terminal state, duration, retries, timeout or cancellation, tool failures, budget exhaustion, and approval age.
- Run one recovery drill: interrupt a safe execution, locate it by correlation ID, retry, resume, or terminate as appropriate, and verify the audit trail.
- Keep releases KISS: test the changed path against sandbox tools, deploy it, and retain a known-good definition for rollback.

For implementation details, see [Conductor Agents](conductor-agents.md), [Framework Agent Recipes](agent-framework-recipes.md), [A2A Integration](a2a-integration.md), [Guardrails](agent-guardrails.md), [Evals](agent-evals.md), [Failure Semantics](failure-semantics.md), and [Durable Adaptive Graphs](dynamic-workflows.md).

## Native-task implementation: architecture diagram

<div style="margin: 2rem 0;">
<svg viewBox="0 0 720 820" xmlns="http://www.w3.org/2000/svg" style="max-width: 720px; width: 100%; height: auto;">
  <defs>
    <marker id="pa-arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse"><path d="M 0 0 L 10 5 L 0 10 z" fill="#4a5568"/></marker>
    <marker id="pa-arrow-teal" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse"><path d="M 0 0 L 10 5 L 0 10 z" fill="#06d6a0"/></marker>
    <marker id="pa-arrow-red" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse"><path d="M 0 0 L 10 5 L 0 10 z" fill="#dc2626"/></marker>
    <marker id="pa-arrow-blue" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse"><path d="M 0 0 L 10 5 L 0 10 z" fill="#3b82f6"/></marker>
  </defs>

  <!-- Background for loop region -->
  <rect x="30" y="215" width="660" height="480" rx="12" fill="rgba(6,214,160,0.06)" stroke="#06d6a0" stroke-width="1.5" stroke-dasharray="6,4"/>
  <text x="50" y="240" font-size="11" font-weight="600" fill="#06d6a0" font-family="sans-serif">DO_WHILE — Agent Loop (checkpointed per iteration)</text>

  <!-- Start -->
  <circle cx="360" cy="30" r="22" fill="#e2e8f0" stroke="#4a5568" stroke-width="1.5"/>
  <text x="360" y="35" text-anchor="middle" font-size="11" fill="#2e3545" font-family="sans-serif">Start</text>

  <!-- Discover Tools -->
  <line x1="360" y1="52" x2="360" y2="80" stroke="#4a5568" stroke-width="1.5" marker-end="url(#pa-arrow)"/>
  <rect x="245" y="80" width="230" height="40" rx="6" fill="#e2e8f0" stroke="#4a5568" stroke-width="1.5"/>
  <text x="360" y="98" text-anchor="middle" font-size="11" fill="#2e3545" font-family="sans-serif" font-weight="600">Discover Tools</text>
  <text x="360" y="112" text-anchor="middle" font-size="9" fill="#4a5568" font-family="sans-serif">LIST_MCP_TOOLS</text>

  <!-- Init Memory -->
  <line x1="360" y1="120" x2="360" y2="148" stroke="#4a5568" stroke-width="1.5" marker-end="url(#pa-arrow)"/>
  <rect x="245" y="148" width="230" height="40" rx="6" fill="#e2e8f0" stroke="#4a5568" stroke-width="1.5"/>
  <text x="360" y="166" text-anchor="middle" font-size="11" fill="#2e3545" font-family="sans-serif" font-weight="600">Initialize Memory</text>
  <text x="360" y="180" text-anchor="middle" font-size="9" fill="#4a5568" font-family="sans-serif">SET_VARIABLE</text>

  <!-- Arrow into loop -->
  <line x1="360" y1="188" x2="360" y2="260" stroke="#4a5568" stroke-width="1.5" marker-end="url(#pa-arrow)"/>

  <!-- Plan (LLM) -->
  <rect x="245" y="260" width="230" height="45" rx="6" fill="#3b82f6" stroke="#2563eb" stroke-width="1.5"/>
  <text x="360" y="280" text-anchor="middle" font-size="11" fill="#fff" font-weight="600" font-family="sans-serif">Plan Next Action</text>
  <text x="360" y="296" text-anchor="middle" font-size="9" fill="rgba(255,255,255,0.8)" font-family="sans-serif">LLM_CHAT_COMPLETE</text>

  <!-- Switch: done / needs_approval / execute -->
  <line x1="360" y1="305" x2="360" y2="335" stroke="#4a5568" stroke-width="1.5" marker-end="url(#pa-arrow)"/>
  <polygon points="360,335 400,365 360,395 320,365" fill="#e2e8f0" stroke="#4a5568" stroke-width="1.5"/>
  <text x="360" y="362" text-anchor="middle" font-size="9" fill="#2e3545" font-family="sans-serif" font-weight="600">SWITCH</text>
  <text x="360" y="374" text-anchor="middle" font-size="8" fill="#4a5568" font-family="sans-serif">done?</text>

  <!-- Done branch (exits loop) — goes right and down to end -->
  <line x1="400" y1="365" x2="620" y2="365" stroke="#06d6a0" stroke-width="1.5"/>
  <text x="500" y="358" text-anchor="middle" font-size="9" fill="#06d6a0" font-family="sans-serif" font-weight="600">done = true</text>
  <line x1="620" y1="365" x2="620" y2="770" stroke="#06d6a0" stroke-width="1.5" marker-end="url(#pa-arrow-teal)"/>

  <!-- Needs approval branch — goes left -->
  <line x1="320" y1="365" x2="140" y2="365" stroke="#4a5568" stroke-width="1.5"/>
  <text x="230" y="358" text-anchor="middle" font-size="9" fill="#4a5568" font-family="sans-serif">needs_approval</text>
  <line x1="140" y1="365" x2="140" y2="420" stroke="#4a5568" stroke-width="1.5" marker-end="url(#pa-arrow)"/>

  <!-- Human Approval -->
  <rect x="55" y="420" width="170" height="45" rx="6" fill="#f59e0b" stroke="#d97706" stroke-width="1.5"/>
  <text x="140" y="440" text-anchor="middle" font-size="11" fill="#fff" font-weight="600" font-family="sans-serif">Human Approval</text>
  <text x="140" y="456" text-anchor="middle" font-size="9" fill="rgba(255,255,255,0.8)" font-family="sans-serif">HUMAN (durable pause)</text>

  <!-- Arrow from approval to tool -->
  <line x1="140" y1="465" x2="140" y2="500" stroke="#4a5568" stroke-width="1.5"/>
  <line x1="140" y1="500" x2="360" y2="500" stroke="#4a5568" stroke-width="1.5"/>

  <!-- Execute branch — goes straight down -->
  <line x1="360" y1="395" x2="360" y2="490" stroke="#4a5568" stroke-width="1.5" marker-end="url(#pa-arrow)"/>
  <text x="375" y="440" font-size="9" fill="#4a5568" font-family="sans-serif">execute</text>

  <!-- Execute Tool -->
  <rect x="265" y="490" width="190" height="45" rx="6" fill="#e2e8f0" stroke="#4a5568" stroke-width="1.5"/>
  <text x="360" y="510" text-anchor="middle" font-size="11" fill="#2e3545" font-family="sans-serif" font-weight="600">Execute Tool</text>
  <text x="360" y="526" text-anchor="middle" font-size="9" fill="#4a5568" font-family="sans-serif">CALL_MCP_TOOL</text>

  <!-- Retry badge on tool -->
  <circle cx="465" cy="500" r="12" fill="#f59e0b" stroke="#d97706" stroke-width="1"/>
  <text x="465" y="504" text-anchor="middle" font-size="9" fill="#fff" font-weight="bold" font-family="sans-serif">!</text>
  <text x="485" y="504" font-size="8" fill="#4a5568" font-family="sans-serif">auto-retry</text>

  <!-- Update Memory -->
  <line x1="360" y1="535" x2="360" y2="570" stroke="#4a5568" stroke-width="1.5" marker-end="url(#pa-arrow)"/>
  <rect x="255" y="570" width="210" height="40" rx="6" fill="#e2e8f0" stroke="#4a5568" stroke-width="1.5"/>
  <text x="360" y="588" text-anchor="middle" font-size="11" fill="#2e3545" font-family="sans-serif" font-weight="600">Update Memory</text>
  <text x="360" y="602" text-anchor="middle" font-size="9" fill="#4a5568" font-family="sans-serif">SET_VARIABLE</text>

  <!-- Budget check -->
  <line x1="360" y1="610" x2="360" y2="640" stroke="#4a5568" stroke-width="1.5" marker-end="url(#pa-arrow)"/>
  <polygon points="360,640 400,665 360,690 320,665" fill="#e2e8f0" stroke="#4a5568" stroke-width="1.5"/>
  <text x="360" y="662" text-anchor="middle" font-size="8" fill="#2e3545" font-family="sans-serif" font-weight="600">Budget</text>
  <text x="360" y="674" text-anchor="middle" font-size="8" fill="#4a5568" font-family="sans-serif">check</text>

  <!-- Loop back arrow -->
  <line x1="320" y1="665" x2="80" y2="665" stroke="#06d6a0" stroke-width="1.5"/>
  <line x1="80" y1="665" x2="80" y2="282" stroke="#06d6a0" stroke-width="1.5"/>
  <line x1="80" y1="282" x2="245" y2="282" stroke="#06d6a0" stroke-width="1.5" marker-end="url(#pa-arrow-teal)"/>
  <text x="68" y="480" text-anchor="middle" font-size="9" fill="#06d6a0" font-family="sans-serif" font-weight="600" transform="rotate(-90 68 480)">next iteration</text>

  <!-- Budget exceeded — exit loop -->
  <line x1="400" y1="665" x2="620" y2="665" stroke="#dc2626" stroke-width="1.5"/>
  <text x="510" y="658" text-anchor="middle" font-size="9" fill="#dc2626" font-family="sans-serif" font-weight="600">budget exceeded</text>
  <line x1="620" y1="665" x2="620" y2="770" stroke="#dc2626" stroke-width="1.5"/>

  <!-- End -->
  <line x1="360" y1="695" x2="360" y2="770" stroke="#4a5568" stroke-width="1.5" marker-end="url(#pa-arrow)"/>
  <circle cx="360" cy="792" r="22" fill="#e2e8f0" stroke="#4a5568" stroke-width="1.5"/>
  <text x="360" y="797" text-anchor="middle" font-size="11" fill="#2e3545" font-family="sans-serif">End</text>

  <!-- Compensation annotation -->
  <rect x="490" y="748" width="180" height="42" rx="6" fill="#fff" stroke="#dc2626" stroke-width="1" stroke-dasharray="4,3"/>
  <text x="580" y="765" text-anchor="middle" font-size="9" fill="#dc2626" font-family="sans-serif" font-weight="600">On failure:</text>
  <text x="580" y="780" text-anchor="middle" font-size="9" fill="#dc2626" font-family="sans-serif">failureWorkflow runs</text>
  <text x="580" y="790" text-anchor="middle" font-size="9" fill="#dc2626" font-family="sans-serif">compensation</text>
  <line x1="490" y1="770" x2="385" y2="785" stroke="#dc2626" stroke-width="1" stroke-dasharray="3,3"/>

  <!-- Persistence annotation -->
  <rect x="500" y="260" width="150" height="50" rx="6" fill="#fff" stroke="#3b82f6" stroke-width="1" stroke-dasharray="4,3"/>
  <text x="575" y="278" text-anchor="middle" font-size="9" fill="#3b82f6" font-family="sans-serif" font-weight="600">Every step persisted</text>
  <text x="575" y="292" text-anchor="middle" font-size="9" fill="#3b82f6" font-family="sans-serif">Prompt, response,</text>
  <text x="575" y="304" text-anchor="middle" font-size="9" fill="#3b82f6" font-family="sans-serif">tokens, timing</text>
  <line x1="500" y1="285" x2="475" y2="282" stroke="#3b82f6" stroke-width="1" stroke-dasharray="3,3"/>
</svg>
</div>


## The canonical agent pattern

A production agent has these concerns. Each one maps to a specific Conductor primitive:

| Agent concern | Conductor primitive | How it works |
|---|---|---|
| **Plan next action** | `LLM_CHAT_COMPLETE` | LLM receives goal + context + tool list, returns structured plan |
| **Select an approved tool at runtime** | `SWITCH` + guarded `CALL_MCP_TOOL` | The LLM proposes a route; the graph revalidates capability selection before execution. |
| **Execute tool** | `CALL_MCP_TOOL`, `HTTP`, or `SIMPLE` worker | Tool runs with retry policy, timeout, and full I/O recording |
| **Retry with backoff** | Task definition `retryLogic` | `FIXED`, `EXPONENTIAL_BACKOFF`, or `LINEAR_BACKOFF` — no code needed |
| **Parallel tool calls** | `FORK/JOIN` or `FORK_JOIN_DYNAMIC` | Fan out to a bounded set of tools in parallel, then join their results |
| **Memory / context handoff** | `SET_VARIABLE` + workflow variables | Accumulate results across loop iterations; pass to next LLM call |
| **Human approval gate** | `HUMAN` task | Durable pause. Survives restarts and deploys. Resumes on API signal. |
| **Long wait (hours/days)** | `WAIT` task | Timer-based durable pause. Survives server restarts. |
| **Resume from external event** | `HUMAN` task + webhook/API | External system calls Task Update API. Workflow resumes with payload. |
| **Reflection / evaluation loop** | `DO_WHILE` with LLM-as-judge | Second LLM evaluates output quality; loop continues if below threshold |
| **Budget / iteration cap** | `DO_WHILE` `loopCondition` | `iteration < maxIterations` or token/cost check in loop condition |
| **Termination criteria** | `DO_WHILE` exit + `SWITCH` | LLM sets `done: true`, or evaluator decides goal is met |
| **Invoke a deployed specialist agent** | `AGENT` with `agentType: "conductor"` | Run a deployed Conductor Agent by name; its compiled graph is visible in Conductor. |
| **Hand off to a remote specialist agent** | `AGENT` with `agentType: "a2a"` | Call a remote A2A service; Conductor persists the handoff, lifecycle, and returned artifacts at the parent boundary. |
| **Compose a child workflow** | `SUB_WORKFLOW` or `START_WORKFLOW` | Use `SUB_WORKFLOW` when the parent waits, or `START_WORKFLOW` for fire-and-forget workflow composition. |
| **Compensation on failure** | `failureWorkflow` | Undo side effects: revoke API calls, send notifications, release resources |
| **Audit trail** | Automatic | Every task's input, output, timing, retry count, and worker ID is persisted |


## Native-task implementation: end-to-end workflow

The runnable source of truth for the native-task path is `ai/examples/35-governed-adaptive-agent.json` in this repository's AI examples directory. Every step is a native system task or operator — no custom code or external framework. The compact JSON below is a conceptual baseline for that path; use the governed PR reviewer when deploying it because it adds the production guardrails described above.

```json
{
  "name": "production_agent",
  "description": "Reference architecture: durable production agent",
  "version": 1,
  "schemaVersion": 2,
  "inputParameters": ["goal", "mcpServerUrl", "maxIterations"],
  "tasks": [
    {
      "name": "discover_tools",
      "taskReferenceName": "discover",
      "type": "LIST_MCP_TOOLS",
      "inputParameters": {
        "mcpServer": "${workflow.input.mcpServerUrl}"
      }
    },
    {
      "name": "initialize_memory",
      "taskReferenceName": "init_memory",
      "type": "SET_VARIABLE",
      "inputParameters": {
        "last_action": "",
        "last_result": "",
        "final_answer": ""
      }
    },
    {
      "name": "agent_loop",
      "taskReferenceName": "loop",
      "type": "DO_WHILE",
      "loopCondition": "$.plan['route'] != 'done' && $.loop['iteration'] < $.maxIterations",
      "inputParameters": {
        "maxIterations": "${workflow.input.maxIterations}"
      },
      "loopOver": [
        {
          "name": "plan_next_action",
          "taskReferenceName": "plan",
          "type": "LLM_CHAT_COMPLETE",
          "inputParameters": {
            "llmProvider": "anthropic",
            "model": "claude-sonnet-4-20250514",
            "messages": [
              {
                "role": "system",
                "message": "You are a production AI agent. Goal: ${workflow.input.goal}\n\nAvailable tools: ${discover.output.tools}\n\nMost recent action: ${workflow.variables.last_action}\nMost recent result: ${workflow.variables.last_result}\n\nRespond with JSON only. Use {\"route\": \"execute\", \"action\": \"tool_name\", \"arguments\": {}, \"reasoning\": \"why\"} for a safe tool call, {\"route\": \"needs_approval\", \"action\": \"tool_name\", \"arguments\": {}, \"reasoning\": \"why\"} for a reviewable tool call, or {\"route\": \"done\", \"answer\": \"final answer\"} when complete."
              }
            ],
            "temperature": 0.1,
            "maxTokens": 1000,
            "jsonOutput": true
          }
        },
        {
          "name": "check_if_done",
          "taskReferenceName": "done_check",
          "type": "SWITCH",
          "evaluatorType": "value-param",
          "expression": "route",
          "inputParameters": {
            "route": "${plan.output.result.route}"
          },
          "decisionCases": {
            "needs_approval": [
              {
                "name": "human_approval",
                "taskReferenceName": "approval",
                "type": "HUMAN",
                "inputParameters": {
                  "plannedAction": "${plan.output.result.action}",
                  "arguments": "${plan.output.result.arguments}",
                  "reasoning": "${plan.output.result.reasoning}",
                  "goal": "${workflow.input.goal}"
                }
              },
              {
                "name": "execute_approved_tool",
                "taskReferenceName": "approved_tool_call",
                "type": "CALL_MCP_TOOL",
                "inputParameters": {
                  "mcpServer": "${workflow.input.mcpServerUrl}",
                  "method": "${plan.output.result.action}",
                  "arguments": "${plan.output.result.arguments}"
                }
              },
              {
                "name": "update_memory_approved",
                "taskReferenceName": "mem_update_approved",
                "type": "SET_VARIABLE",
                "inputParameters": {
                  "last_action": "${plan.output.result.action}",
                  "last_result": "${approved_tool_call.output.content}"
                }
              }
            ],
            "execute": [
              {
                "name": "execute_tool",
                "taskReferenceName": "tool_call",
                "type": "CALL_MCP_TOOL",
                "inputParameters": {
                  "mcpServer": "${workflow.input.mcpServerUrl}",
                  "method": "${plan.output.result.action}",
                  "arguments": "${plan.output.result.arguments}"
                }
              },
              {
                "name": "update_memory",
                "taskReferenceName": "mem_update",
                "type": "SET_VARIABLE",
                "inputParameters": {
                  "last_action": "${plan.output.result.action}",
                  "last_result": "${tool_call.output.content}"
                }
              }
            ],
            "done": [
              {
                "name": "save_answer",
                "taskReferenceName": "save_answer",
                "type": "SET_VARIABLE",
                "inputParameters": {
                  "final_answer": "${plan.output.result.answer}"
                }
              }
            ]
          },
          "defaultCase": []
        }
      ]
    }
  ],
  "outputParameters": {
    "answer": "${workflow.variables.final_answer}",
    "iterations": "${loop.output.iteration}",
    "last_action": "${workflow.variables.last_action}",
    "last_result": "${workflow.variables.last_result}"
  },
  "failureWorkflow": "agent_compensation_workflow"
}
```


## What makes this production-ready

### Every step is a durable checkpoint

In the native-task path, each iteration of `DO_WHILE` is persisted before the next begins. If the agent crashes at iteration 15 of 20, it resumes from iteration 15 — not from scratch. Every LLM prompt, response, tool call, and human decision is recorded. Deployed Conductor Agents provide the same internal Conductor visibility because their graphs are compiled into Conductor workflows.

For an A2A path, the durable checkpoint is the `AGENT` handoff: Conductor records its status, retry and cancellation lifecycle, and returned artifacts. The remote agent's private internal steps remain owned and observed by that remote service.

### Human approval is a durable gate

The `HUMAN` task pauses the workflow indefinitely. The pause survives server restarts, deploys, and infrastructure changes. When a reviewer approves via the API or UI, the workflow resumes with the approval payload as task output. No polling, no timeouts (unless you configure one), no lost approvals.

### Retry is automatic and configurable

Every tool call (`CALL_MCP_TOOL`, `HTTP`, `SIMPLE`) inherits retry behavior from its [task definition](../../documentation/configuration/taskdef.md):

```json
{
  "name": "execute_tool",
  "retryCount": 3,
  "retryLogic": "EXPONENTIAL_BACKOFF",
  "retryDelaySeconds": 2,
  "responseTimeoutSeconds": 30
}
```

If the MCP server is down, Conductor retries with exponential backoff. The LLM is **not** re-called — only the failed tool call retries.

### Memory persists across iterations

`SET_VARIABLE` stores accumulated context in workflow variables. These variables are persisted to durable storage and available to every subsequent task. The LLM receives the full history of actions and results on each iteration.

### Budget cap prevents runaway agents

The `loopCondition` checks both the agent's `done` flag and an iteration cap. You can also check token usage or cost in the condition. The agent terminates cleanly when the budget is exhausted.

### Compensation handles side effects

If the agent fails after taking real-world actions (sent an email, created a record, charged a payment), the `failureWorkflow` runs compensating tasks automatically. The compensation workflow receives the full execution context: which actions succeeded, which failed, and why.

### Observability is automatic

For native tasks and compiled Conductor Agent graphs, open the Conductor UI to see:

- The exact task graph for this execution
- Every LLM prompt and response (click any `LLM_CHAT_COMPLETE` task)
- Every tool call with input, output, and timing
- Every human approval with who approved and when
- The iteration count and loop state
- Retry history for any failed task
- The full workflow input, output, and variables

For a remote A2A agent, the parent workflow exposes the durable `AGENT` task — handoff state, retry and cancellation lifecycle, and returned text or artifacts. The remote agent's internal graph stays private to its operator, which is what keeps the boundary clean.


## Extending the pattern

### Add parallel research

Replace a single tool call with `FORK_JOIN_DYNAMIC` to fan out to multiple tools in parallel. Validate and cap the LLM-produced inputs before this task; an unbounded plan is not a safe production fan-out.

```json
{
  "name": "parallel_research",
  "taskReferenceName": "research",
  "type": "FORK_JOIN_DYNAMIC",
  "inputParameters": {
    "dynamicTasks": "${plan.output.result.parallel_tasks}",
    "dynamicTasksInput": "${plan.output.result.task_inputs}"
  },
  "dynamicForkTasksParam": "dynamicTasks",
  "dynamicForkTasksInputParamName": "dynamicTasksInput"
}
```

The LLM decides how many tools to call in parallel and with what inputs. Conductor creates the branches at runtime.

### Add a reflection / evaluation step

Insert an LLM-as-judge after tool execution to evaluate output quality:

```json
{
  "name": "evaluate_result",
  "taskReferenceName": "evaluator",
  "type": "LLM_CHAT_COMPLETE",
  "inputParameters": {
    "llmProvider": "anthropic",
    "model": "claude-sonnet-4-20250514",
    "messages": [
      {
        "role": "system",
        "message": "Evaluate this result against the goal. Is it sufficient? Respond with JSON: {\"quality\": \"good\" or \"insufficient\", \"feedback\": \"...\"}"
      },
      {
        "role": "user",
        "message": "Goal: ${workflow.input.goal}\nResult: ${tool_call.output.content}"
      }
    ]
  }
}
```

If the evaluator returns `insufficient`, the loop continues with the feedback as context for the next planning step.

### Add long waits

Insert a `WAIT` task for time-based pauses (rate limiting, cooldown periods, scheduled actions):

```json
{
  "name": "wait_before_retry",
  "taskReferenceName": "cooldown",
  "type": "WAIT",
  "inputParameters": {
    "duration": "1 hour"
  }
}
```

The wait is durable. The workflow does not consume resources while waiting. After 1 hour — even if the server restarted during that time — the workflow resumes.

### Delegate to specialist agents

Use `AGENT` when the specialist is an agent runtime. A deployed Conductor Agent is invoked by name:

```json
{
  "name": "delegate_to_planner",
  "taskReferenceName": "planner_agent",
  "type": "AGENT",
  "inputParameters": {
    "agentType": "conductor",
    "name": "specialist_planner",
    "prompt": "${workflow.input.goal}"
  }
}
```

Use `agentType: "a2a"` when the specialist is an independently deployed A2A service:

```json
{
  "name": "delegate_to_researcher",
  "taskReferenceName": "research_agent",
  "type": "AGENT",
  "inputParameters": {
    "agentType": "a2a",
    "agentUrl": "${workflow.input.researchAgentUrl}",
    "text": "${plan.output.result.research_topic}"
  }
}
```

Use `SUB_WORKFLOW` when the specialist is a child workflow rather than an agent runtime:

```json
{
  "name": "delegate_to_researcher",
  "taskReferenceName": "research_agent",
  "type": "SUB_WORKFLOW",
  "inputParameters": {
    "name": "research_agent_workflow",
    "version": 1,
    "input": {
      "topic": "${plan.output.result.research_topic}",
      "mcpServerUrl": "${workflow.input.mcpServerUrl}"
    }
  }
}
```

The parent waits for the child workflow to complete. If it fails, the parent's failure handling kicks in. Its workflow tree is observable in the UI. `START_WORKFLOW` is the corresponding fire-and-forget option; neither task is a substitute for invoking a deployed or remote agent runtime.


## The primitives, mapped

| "I need my agent to..." | Use this | Why |
|---|---|---|
| Wait for a tool callback | `HUMAN` task or async completion | Durable pause. Resumes on API signal with payload. |
| Sleep until a retry window | `WAIT` task | Timer-based durable pause. Zero resource consumption. |
| Pick the next tool at runtime | `DYNAMIC` task | LLM output determines task type. Resolved at execution time. |
| Call multiple tools in parallel | `FORK/JOIN` or `FORK_JOIN_DYNAMIC` | Static or runtime-determined parallelism. Join waits for all. |
| Loop until goal is met | `DO_WHILE` | Checkpointed loop. Each iteration persisted. |
| Invoke a deployed specialist agent | `AGENT` with `agentType: "conductor"` | Runs a named Conductor Agent; its compiled workflow graph is inspectable in Conductor. |
| Hand off to a remote specialist agent | `AGENT` with `agentType: "a2a"` | Durable remote handoff with parent-boundary status, lifecycle, and artifacts. |
| Compose a child workflow | `SUB_WORKFLOW` or `START_WORKFLOW` | Waiting or fire-and-forget child-workflow composition; distinct from invoking an agent runtime. |
| Accumulate context across steps | `SET_VARIABLE` | Workflow variables persisted to durable storage. |
| Evaluate output quality | `LLM_CHAT_COMPLETE` as evaluator | LLM-as-judge pattern inside the loop. |
| Cap iterations or cost | `DO_WHILE` `loopCondition` | Check iteration count, token usage, or cost. |
| Undo side effects on failure | `failureWorkflow` | Compensation tasks run automatically on workflow failure. |
| Pause for human review | `HUMAN` task | Indefinite durable pause. Survives restarts and deploys. |
| Resume on external event | `HUMAN` task + API/webhook | External system calls Task Update API with payload. |
| Post-process structured output | `INLINE` (JavaScript) or `JSON_JQ_TRANSFORM` | Server-side transforms without a worker. |


## Next steps

- **[Conductor Agents](conductor-agents.md)** — Use this architecture around a deployed SDK-authored agent graph.
- **[Framework Agent Recipes](agent-framework-recipes.md)** — Supported framework routes and maintained SDK examples.
- **[A2A Integration](a2a-integration.md)** — Hand off to independently deployed A2A agents while retaining a durable parent-workflow boundary.
- **[Failure Semantics for AI Agents](failure-semantics.md)** — The exact failure contract: what happens under crashes, retries, duplicates, and long waits.
- **[Why Conductor for Agents](why-conductor.md)** — What Conductor gives you out of the box for agentic workflows.
- **[Build Your First Agentic Workflow Graph](first-ai-agent.md)** — Compose an SDK-authored agent with ordinary workflow tasks.
- **[MCP Integration](mcp-guide.md)** — Connect to any MCP server, expose workflows as MCP tools.
- **[Token Efficiency](token-efficiency.md)** — How durable execution saves tokens and reduces LLM costs.
