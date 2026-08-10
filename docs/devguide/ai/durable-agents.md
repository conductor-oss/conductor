---
description: What makes a durable AI agent — persisted state, crash recovery, and why JSON workflow definitions are AI-native for agent orchestration.
---

# Durable agents

An agent that runs in a single process is fragile. A crashed pod replays every LLM call from the beginning — burning tokens and money. A human approval that took three days is lost because a deploy bounced the server. A multi-hour research pipeline fails at step 47 and starts over from step 1.

Conductor eliminates all of this. Every step of a durable agent workflow is persisted to storage as it completes. If the process dies, the agent resumes from the last completed step — not from the beginning.


## What gets persisted

- The **workflow definition snapshot** (immutable for this execution).
- Each **LLM call**: input prompt, model response, token usage, latency.
- Each **tool call**: input, output, status, retry count.
- Each **wait state**: when it started, what it's waiting for, the resume payload when it arrives.
- Each **human decision**: who approved, when, with what data.
- The **loop state**: iteration count, intermediate results, exit condition evaluation.

No LLM calls are repeated unless a task explicitly failed and needs retry. A human approval that completed on Tuesday is still there on Wednesday, even if the cluster was replaced overnight. This is what makes Conductor-based agents production-ready.


## JSON is AI-native

LLMs natively produce JSON. Conductor natively executes JSON. This means an agent can generate its own execution plan as a workflow definition and Conductor will execute it immediately — no compilation, no deployment, no code generation step.

```
LLM generates plan → JSON workflow definition → Conductor executes it
```

This is not a workaround. It is the intended design, and it makes Conductor uniquely suited to agent orchestration:

**Runtime generation.** An LLM or planner emits a workflow definition as JSON, your code passes it to the [StartWorkflowRequest API](../../documentation/api/startworkflow.md), and Conductor validates, persists, and executes it immediately — without pre-registration. The workflow itself becomes a first-class output of the agent's planning step.

**Inspectability.** Every agent run is a JSON document you can query, diff, and audit. You can see exactly what the LLM decided, what tools were called, what the human approved, and in what order. No opaque framework state — just data.

**Versioning.** Workflow definitions are versioned. Run multiple agent versions concurrently, A/B test different tool configurations, and roll back without affecting running executions.

**SDK/UI/API parity.** The same workflow can be defined via JSON file, SDK code, API call, or the Conductor UI. All paths produce the same stored JSON definition. An agent that generates workflows programmatically and a human who designs them in the UI are using the same runtime.


## Error handling and compensation

Agents don't just read data — they take actions. They send emails, create tickets, charge cards, update databases. When a step fails after earlier steps have already produced side effects, you need compensation: the ability to undo or mitigate what was already done.

Conductor provides this through the `failureWorkflow` field and the saga compensation pattern:

```json
{
  "name": "booking_agent",
  "failureWorkflow": "booking_agent_compensation",
  "tasks": [
    { "name": "reserve_flight", "type": "HTTP", "taskReferenceName": "flight" },
    { "name": "reserve_hotel", "type": "HTTP", "taskReferenceName": "hotel" },
    { "name": "charge_payment", "type": "HTTP", "taskReferenceName": "payment" }
  ]
}
```

If `charge_payment` fails, the `booking_agent_compensation` workflow runs automatically. It receives the full execution state — including the outputs of `reserve_flight` and `reserve_hotel` — so it can cancel the flight, release the hotel reservation, and notify the user.

This is not error handling you bolt on later. It is built into the execution model:

- **`failureWorkflow`** runs a separate workflow on failure, with full access to the failed execution's state.
- **Retry policies** on individual tasks (fixed, exponential backoff, linear) with configurable limits.
- **Timeout policies** that fail or alert when an LLM call or tool takes too long.
- **`TERMINATE` task** to end execution early with a specific status and output when the agent detects an unrecoverable condition.

Most AI frameworks have no concept of compensation. If your LangChain agent sends an email in step 3 and crashes in step 5, the email is already sent and there is no built-in mechanism to undo it. Conductor's failure workflows solve this.


## Multi-agent composition

Real-world AI systems rarely run as a single agent. A research agent delegates to specialist sub-agents. A customer service agent escalates to a billing agent. A planning agent spawns parallel analysis agents and synthesizes their results.

Conductor models this with `SUB_WORKFLOW` tasks inside a `FORK`/`JOIN` for parallel execution:

```json
{
  "name": "research_coordinator",
  "tasks": [
    {
      "name": "plan",
      "type": "LLM_CHAT_COMPLETE",
      "taskReferenceName": "plan",
      "inputParameters": {
        "llmProvider": "anthropic",
        "model": "claude-sonnet-4-20250514",
        "messages": [
          { "role": "user", "message": "Break this research task into sub-tasks: ${workflow.input.topic}" }
        ]
      }
    },
    {
      "name": "fork_sub_agents",
      "type": "FORK_JOIN",
      "taskReferenceName": "fork",
      "forkTasks": [
        [
          {
            "name": "run_web_researcher",
            "type": "SUB_WORKFLOW",
            "taskReferenceName": "web_research",
            "subWorkflowParam": { "name": "web_research_agent", "version": 1 },
            "inputParameters": { "query": "${plan.output.result.webQuery}" }
          }
        ],
        [
          {
            "name": "run_data_analyst",
            "type": "SUB_WORKFLOW",
            "taskReferenceName": "data_analysis",
            "subWorkflowParam": { "name": "data_analysis_agent", "version": 1 },
            "inputParameters": { "dataset": "${plan.output.result.dataset}" }
          }
        ]
      ]
    },
    {
      "name": "join_sub_agents",
      "type": "JOIN",
      "taskReferenceName": "join",
      "joinOn": ["web_research", "data_analysis"]
    },
    {
      "name": "synthesize",
      "type": "LLM_CHAT_COMPLETE",
      "taskReferenceName": "synthesize",
      "inputParameters": {
        "llmProvider": "anthropic",
        "model": "claude-sonnet-4-20250514",
        "messages": [
          { "role": "user", "message": "Synthesize these findings:\n\nWeb research: ${web_research.output}\n\nData analysis: ${data_analysis.output}" }
        ]
      }
    }
  ],
  "failureWorkflow": "research_coordinator_cleanup"
}
```

Both sub-agents run concurrently. The `JOIN` waits for both to complete before the synthesize step runs. If you don't know the number of sub-agents ahead of time, use `DYNAMIC_FORK` instead — the LLM's plan output determines how many sub-agents to spawn.

**What you get from multi-agent composition in Conductor:**

- **Parallel execution.** Sub-agents run concurrently via `FORK`/`JOIN` or `DYNAMIC_FORK`. The join collects all results before the next step proceeds.
- **Full observability across the agent tree.** The parent workflow shows the status of each sub-agent. You can drill into any sub-workflow to see its individual LLM calls, tool calls, and decisions.
- **Failure isolation.** A failing sub-agent does not crash the parent. The parent can catch the failure, retry with different parameters, or route to a fallback agent.
- **Failure propagation with compensation.** If a sub-agent fails and the parent should also fail, `failureWorkflow` runs compensation across the entire agent tree.
- **Independent scaling.** Each sub-agent type can have its own workers scaled independently. A CPU-heavy data analysis agent doesn't compete for resources with a lightweight web research agent.


## Observability

Every agent execution in Conductor is fully observable — not through external logging you have to set up, but as a built-in property of the execution model. Because every step is persisted, the observability is automatic and complete.

**What you can see for every agent run:**

- **Task-by-task execution timeline.** Each task shows its status (scheduled, in progress, completed, failed), start time, end time, and duration. You see exactly where an agent is in its workflow at any moment.
- **Every LLM prompt and response.** The full input messages, model response, token usage (prompt tokens, completion tokens), and latency for each `LLM_CHAT_COMPLETE` or `LLM_TEXT_COMPLETE` call. You can inspect exactly what the agent decided and why.
- **Every tool call with input/output.** For `CALL_MCP_TOOL`, `HTTP`, and custom worker tasks: the exact arguments sent, the response received, and how many retry attempts were needed.
- **Human approval audit trail.** For `HUMAN` tasks: when the task was created, who completed it, when they completed it, and what data they provided. This is an immutable audit record.
- **Loop iteration history.** For `DO_WHILE` agent loops: the iteration count, the result of each iteration, and the exit condition evaluation. You can trace the agent's reasoning across its entire plan/act/observe cycle.
- **Sub-agent drill-down.** For `SUB_WORKFLOW` tasks: click through to the child workflow's full execution view. The parent shows the sub-agent's overall status; the child shows every step within it.
- **Retry and failure history.** Every retry attempt is recorded with its input, output, and failure reason. If a task failed three times before succeeding, all four attempts are visible.

This observability applies to every workflow — including workflows [generated dynamically by an LLM](dynamic-workflows.md). A workflow that was created 30 seconds ago by an agent's planning step gets the same execution visibility as one that was registered months ago.

For programmatic access, the [Workflow API](../../documentation/api/workflow.md) and [Task API](../../documentation/api/task.md) provide the same data via REST: query execution status, retrieve task inputs/outputs, and search across executions.


## Next steps

- **[Human-in-the-Loop](human-in-the-loop.md)** &mdash; Pre-execution review, conditional approval, and LLM-as-judge patterns.
- **[Dynamic Workflows](dynamic-workflows.md)** &mdash; Agent loops, dynamic workflow generation, and tool use examples.
- **[LLM Orchestration](llm-orchestration.md)** &mdash; Native LLM providers, vector databases, and content generation.
- **[Durable Execution Semantics](../../architecture/durable-execution.md)** &mdash; Failure matrix, state transitions, and exactly what persists.
