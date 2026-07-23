---
description: Internal, ready-to-paste messaging kit for the Durable Adaptive Graphs launch.
---

# Durable Adaptive Graphs Launch Kit v1

**Status:** Ready-to-paste copy. External publishing is intentionally outside this repository.

## Category and positioning

**Category:** Durable execution for adaptive agentic graphs.

**Positioning:** Conductor is the durable execution platform for adaptive agentic graphs. It turns runtime choices—loops, branching, fan-out, tool calls, approvals, retries, and cancellation—into durable, inspectable, governed execution.

**Core line:** Build agents that adapt. Run graphs that endure.

### Messaging house

| Element | Message |
|---|---|
| North star | Adaptive agent behavior should be as controllable as every other production system. |
| Developer promise | Keep your preferred SDK or framework; compose runtime-adaptive graphs with built-in AI, MCP, and workflow primitives. |
| Platform-architect promise | Retain persisted state, explicit policy boundaries, approval, versioned definitions, retries, cancellation, and inspectable execution history. |
| Proof pillar: durable decisions | Workflow state, task inputs/outputs, waits, approvals, and loop state are persisted. |
| Proof pillar: bounded adaptation | `DO_WHILE`, `SWITCH`, `FORK_JOIN_DYNAMIC`, MCP tasks, and runtime workflow definitions support adaptive paths that teams can validate and cap. |
| Proof pillar: operational control | Operators can inspect, retry, pause, resume, cancel, and recover workflow executions. |
| Proof pillar: choice without lock-in | Workers are plain code and the graph can compose SDK-authored agents, system tasks, MCP tools, and sub-workflows. |

### Anti-messaging

- Do not say Conductor is the “only,” “best,” or “most complete” platform.
- Do not imply exactly-once side effects. Conductor uses at-least-once task delivery; tools must be idempotent.
- Do not say an agent freely mutates a running workflow snapshot. It selects approved runtime paths or starts validated child plans.
- Do not promise that all loop history is retained when `keepLastN` is enabled; older iteration data is intentionally removed.

## Source-backed claims matrix

| Claim | Evidence in this repository | Safe wording |
|---|---|---|
| Adaptive paths can be expressed as loops, branches, and runtime fan-out | `core/.../DoWhile.java`, `core/.../ForkJoinDynamicTaskMapper.java`, `common/.../TaskType.java` | “Build runtime-adaptive paths with durable loop, branch, and fan-out primitives.” |
| Loop conditions can use current loop-body results | `core/.../DoWhile.java#evaluateCondition`, `docs/documentation/configuration/workflowdef/operators/do-while-task.md` | “Evaluate a loop from the current iteration’s task outputs.” |
| Dynamic fan-out uses `FORK_JOIN_DYNAMIC` and a following `JOIN` | `core/.../ForkJoinDynamicTaskMapper.java`, `docs/.../dynamic-fork-task.md` | “Fan out a bounded runtime plan and join its results.” |
| Start requests embed a runtime definition in `workflowDef` | `common/.../StartWorkflowRequest.java`, `rest/.../WorkflowResource.java` | “Start a validated runtime-generated definition with `workflowDef`.” |
| Older loop history can be removed | `core/.../DoWhile.java`, `docs/.../do-while-task.md` | “Use `keepLastN` to bound long-loop storage when historical iterations are not needed.” |
| Failed `DO_WHILE` retry restarts iterations | `docs/.../do-while-task.md` | “Treat a retried adaptive loop as a new loop history; make its tools idempotent.” |

## Homepage and README copy

**Homepage proof card**

> Durable adaptive graphs
>
> Build agents that adapt. Run graphs that endure.
>
> Turn runtime choices—loops, branching, bounded fan-out, tool calls, approvals, retries, and cancellation—into durable, inspectable execution.
>
> CTA 1: Build the governed graph
>
> CTA 2: Review production architecture

**README lead**

> Conductor is an open-source durable execution platform for microservices, AI agents, and adaptive workflow graphs. It turns runtime choices—loops, branching, fan-out, tool calls, approvals, retries, and cancellation—into durable, inspectable execution.

## Sales narratives

### 30-second version

“Agents have to make runtime choices: which tool to call, whether to branch, when to ask a human, and whether to keep going. Conductor turns those choices into a durable adaptive graph. Developers keep their preferred SDK or framework, while operators get persisted state, approval gates, retries, cancellation, and a complete execution trail.”

### Two-minute version

“Most agent demos show a loop. Production systems need more: a plan that is constrained to approved capabilities, bounded fan-out, human approval before consequential actions, recovery when infrastructure fails, and a way to see exactly what happened. Conductor provides the execution layer for that graph. Use LLM and MCP tasks directly, or compose an SDK-authored agent with ordinary workflow steps. Each runtime decision becomes a stored checkpoint. You can inspect a branch, retry a failed task, pause for an operator, cancel work, or start a validated generated child plan. That is the difference between a process that happens to be adaptive and an adaptive graph you can actually govern.”

### Discovery questions

1. Which runtime choices can your agent make today, and which must be explicitly approved?
2. What happens when a tool call fails after it changes an external system?
3. How do you cap agent loops, fan-out, cost, and execution time?
4. Can an operator inspect, pause, retry, or cancel a single in-flight execution?
5. What evidence do you retain for the plan, approvals, tool calls, and result?

### Objection handling

| Objection | Response |
|---|---|
| “We already have an agent framework.” | Keep it. Conductor can compose an SDK-authored agent with durable workflow controls around it. |
| “We need flexibility.” | Runtime selection is the point: constrain the selectable capabilities and bounds rather than hard-coding every path. |
| “We cannot let an LLM execute arbitrary actions.” | Do not. Validate plans, filter to allowlists, require approval before writes, and make tools idempotent. |
| “We only need a simple loop.” | Start with a loop, but add explicit limits and recovery semantics before it touches real systems. |

## Ready-to-paste launch copy

### Announcement

> Introducing Durable Adaptive Graphs for Conductor.
>
> Build agents that adapt. Run graphs that endure.
>
> Adaptive agents make runtime choices: loop, branch, fan out, call tools, ask for approval, retry, or stop. Conductor turns those choices into durable, inspectable, governed execution. Keep your preferred SDK or framework, then add persisted state, bounded fan-out, approval gates, retries, cancellation, and recovery around the graph.
>
> Start with the governed adaptive-agent example: plan → validate approved capabilities → bounded fan-out or human approval → evaluate → continue or finish.

### Social post 1

> Agent loops are easy. Governing them in production is harder.
>
> Durable Adaptive Graphs in Conductor make runtime decisions inspectable and controllable: bounded fan-out, capability allowlists, human approval before writes, retries, cancellation, and recovery.
>
> Build agents that adapt. Run graphs that endure.

### Social post 2

> Your agent can choose the next approved path. Your operators can still inspect, pause, retry, approve, cancel, and recover it.
>
> That is a durable adaptive graph.

### Outbound email

**Subject:** Govern adaptive agents without replacing your framework

Hi {{first_name}},

Agent frameworks make it easy to reason and call tools. The production gap is governing what happens after the model makes a runtime choice.

Conductor adds a durable execution graph around those choices: approved capability selection, bounded fan-out, human approval before writes, retries, cancellation, and an inspectable history of every plan and tool result. Your team can keep its existing SDK or framework.

The governed adaptive-agent pattern is: plan → validate → execute bounded work or wait for approval → evaluate → continue or finish.

Would it be useful to compare that pattern with how your agents currently handle retries, approvals, and recovery?
