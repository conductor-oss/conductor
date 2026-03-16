---
description: "How durable execution saves LLM tokens and reduces AI costs — crash recovery without re-execution, replay without re-running LLM calls, and the real cost of non-durable agent frameworks."
---

# Token efficiency with durable execution

LLM calls are expensive. Every token costs money, and every re-execution burns tokens that were already paid for. Durable execution eliminates wasted tokens by ensuring that completed work is never lost.


## The cost of crashes without durability

Consider an autonomous agent that runs a 20-step loop. Each iteration calls an LLM (planning) and a tool (execution). The agent is on iteration 18 when the process crashes.

**Without durable execution:**

The agent restarts from iteration 1. Iterations 1-17 must re-execute — 17 LLM calls that produce the exact same output as before. The tokens are burned again, the tool calls re-execute (potentially causing duplicate side effects), and the user waits for work that was already done.

**With Conductor:**

The agent resumes from iteration 18. Iterations 1-17 are already persisted — their LLM outputs, tool results, and state are all in durable storage. Zero tokens wasted. Zero duplicate tool calls. The agent picks up exactly where it left off.


## Where tokens are saved

### 1. Crash recovery

Every LLM call in a Conductor workflow is persisted at completion. The prompt, response, token usage, and model are all recorded. If the server, worker, or network fails:

- Completed LLM calls are **never re-executed**. Their outputs are read from storage.
- Only the in-progress call is retried — and only that single call.
- The workflow resumes from the last persisted state.

**Token savings:** Proportional to how far the agent progressed before the crash. An agent that crashes at step 18 of 20 saves 17 LLM calls worth of tokens.

### 2. Retry from failed task

When a workflow fails (e.g., a tool call returns an error after the LLM planned successfully), you can [retry from the failed task](../../architecture/durable-execution.md#replay--recovery). Conductor reuses the outputs of all previously completed tasks.

**Example:** A 5-task agent workflow fails at task 4 (tool execution). Tasks 1-3 included two LLM calls that consumed 8,000 tokens total. Retry from task 4:

- Tasks 1-3 are **not re-executed**. Their outputs (including LLM responses) are reused from storage.
- Only task 4 (and anything after it) re-executes.
- **8,000 tokens saved** per retry.

### 3. Rerun from a specific task

When you fix a bug in a task definition and [rerun from that task](../../architecture/durable-execution.md#replay--recovery), all tasks before it keep their persisted outputs. Upstream LLM calls are not re-executed.

### 4. Loop checkpointing

Agent loops (`DO_WHILE`) checkpoint every iteration. If the loop runs 50 iterations and the agent crashes at iteration 48:

- Iterations 1-47 are persisted with all their LLM calls and tool results.
- Only iteration 48 re-executes.
- **47 iterations of LLM tokens saved.**

Without durability, the entire loop restarts from iteration 1.


## Real-world cost impact

Here's a concrete example using typical LLM pricing:

| Scenario | Without durability | With Conductor | Savings |
|----------|-------------------|----------------|---------|
| 20-step agent, crash at step 18 | Re-run all 20 steps: ~40K tokens | Resume from step 18: ~4K tokens | **~36K tokens ($0.04-$0.40)** |
| RAG pipeline fails at PDF generation | Re-run embedding + LLM: ~12K tokens | Retry only PDF step: 0 LLM tokens | **~12K tokens ($0.01-$0.12)** |
| 100-iteration loop, crash at 95 | Re-run all 100: ~200K tokens | Resume from 95: ~10K tokens | **~190K tokens ($0.19-$1.90)** |
| Agent with human approval, reviewer slow | Process may timeout and restart | HUMAN task persists indefinitely | **All upstream tokens preserved** |

These are per-execution savings. Multiply by thousands of daily executions and the cost difference becomes significant.

At scale — thousands of agent executions per day — even a 5% crash/retry rate translates to substantial token waste without durability. With Conductor, that waste drops to near zero.


## Token savings beyond crashes

Durable execution saves tokens in scenarios beyond crashes:

**Long-running agents with human-in-the-loop.** A HUMAN task can pause a workflow for hours or days. Without durability, the process might timeout or be killed, requiring a full restart (and re-running all upstream LLM calls). With Conductor, the pause is durable — the workflow resumes exactly where it stopped, with all LLM outputs preserved.

**Deployment and scaling.** When you deploy a new version of your workers or scale down instances, in-flight workflows survive. No LLM calls are lost. Without durability, scaling events can kill processes mid-execution, wasting all tokens consumed so far.

**Debugging and iteration.** When debugging a failed agent, you can inspect every LLM prompt and response without re-running the agent. Rerun from a specific task to test a fix without re-executing (and re-paying for) upstream LLM calls.


## How it works mechanically

Conductor persists LLM task outputs the same way it persists any task output:

1. The `LLM_CHAT_COMPLETE` task is scheduled and a worker (or the server itself) executes it.
2. The LLM response is received — prompt, completion, token usage, model, and latency are all recorded.
3. The task moves to `COMPLETED` and its output is **written to durable storage** before the next task is scheduled.
4. If anything fails after this point, the LLM output is already persisted. It is never re-executed.

This is the same persistence model that applies to every task in Conductor — the [durable execution semantics](../../architecture/durable-execution.md) guarantee that completed work is never lost.


## Comparison: durable vs non-durable frameworks

| | Non-durable (LangChain, CrewAI, custom) | Durable (Conductor) |
|---|---|---|
| **Crash at step N of M** | Restart from step 1. All N tokens re-consumed. | Resume from step N. Zero tokens wasted. |
| **Retry after tool failure** | Re-run entire chain including LLM calls. | Retry only the failed task. LLM outputs preserved. |
| **Long pause (human review)** | Process may die. Full restart required. | Durable pause. Resume with all state intact. |
| **Debugging** | Re-run the agent to reproduce. More tokens. | Inspect persisted outputs. Rerun from any task. |
| **Deploy/scale** | In-flight work may be lost. | Workflows survive scaling events. |

The bottom line: **durable execution is a cost optimization**, not just a reliability feature. Every crash, retry, pause, or debugging session that would re-execute LLM calls in a non-durable framework is free in Conductor — because the work was already persisted.


## Next steps

- **[Durable Agents](durable-agents.md)** — What persists, what gets retried, and why JSON is AI-native.
- **[Durable Execution Semantics](../../architecture/durable-execution.md)** — The full persistence and recovery model.
- **[Build Your First AI Agent](first-ai-agent.md)** — Step-by-step tutorial with durable execution built in.
- **[LLM Orchestration](llm-orchestration.md)** — 14+ native LLM providers, vector databases, content generation.
