---
description: AI agent orchestration and LLM orchestration with Conductor — LLM tasks with function calling, tool use via MCP, human-in-the-loop approval, dynamic workflows, vector database workflows, and saga pattern compensation. The open source workflow engine for AI agents.
---

# AI Cookbook

Conductor is not an AI framework. It is a durable execution engine that provides AI agent orchestration and LLM orchestration by solving the hard infrastructure problems that AI agents create: long-running processes, unreliable external calls, function calling and tool use, human-in-the-loop approval, structured output, and the need to survive failures across any of these steps. Conductor makes every agent a durable agent — one that survives crashes, retries, and infrastructure failures without losing progress.


## The problem agents create

An AI agent is a long-running process that:

1. **Calls an LLM** to decide what to do next.
2. **Calls tools** (APIs, databases, other services) to take action.
3. **Waits** for external events, human approval, or time-based delays.
4. **Loops** through plan/act/observe cycles until a goal is reached.
5. **Returns structured output** to the caller or another system.

Each of these steps can fail, take minutes to hours, or require intervention. Running this in a single process means any crash loses all progress. Running it in a queue means building your own state machine, retry logic, and observability. Conductor provides all of this out of the box.


## How it works

```mermaid
graph LR
    A[Your Agent Code] -->|start workflow| B[Conductor Server]
    B -->|schedule tasks| C[Task Queue]
    C -->|poll| D[LLM Worker]
    C -->|poll| E[Tool Worker]
    C -->|poll| F[MCP Worker]
    B -->|persist every step| G[(Durable Storage)]
    B -->|pause & resume| H[HUMAN / WAIT]
    H -->|API call or signal| B
    D -->|result| B
    E -->|result| B
    F -->|result| B
```

Your agent code starts a workflow. Conductor schedules each step as a task, persists every input and output to durable storage, and manages retries, timeouts, and pauses. Workers (LLM calls, tool calls, MCP calls) poll for tasks, execute them, and return results. If any worker or the server itself crashes, execution resumes from the last completed step.


## How Conductor's primitives map to agent patterns

| Agent pattern | Conductor primitive | What happens mechanically |
|---|---|---|
| **LLM call** | `LLM_CHAT_COMPLETE` / `LLM_TEXT_COMPLETE` system task | Native LLM task. Configure provider and model as parameters. Retried on failure. Prompt, response, and token usage persisted. Supports built-in tools: web search, code execution, file search, extended thinking. |
| **Embeddings** | `LLM_GENERATE_EMBEDDINGS` system task | Generate vector embeddings using any configured provider. Output stored and passed to downstream tasks. |
| **Tool call / function calling** | `CALL_MCP_TOOL` system task, or `SIMPLE` / `HTTP` task | Call tools on any MCP server, or implement custom tool workers. Each call is tracked, retried on failure, and fully auditable. |
| **Tool discovery** | `LIST_MCP_TOOLS` system task | Discover available tools from an MCP server at runtime. Feed the tool list to an LLM for dynamic tool selection. |
| **RAG / semantic search** | `LLM_INDEX_TEXT` + `LLM_SEARCH_INDEX` system tasks | Index documents and run semantic search against Pinecone, pgvector, or MongoDB Atlas. No external RAG framework needed. |
| **Wait for human approval** | `HUMAN` task | Workflow pauses. Remains `IN_PROGRESS` in persistent storage. Resumes when the Task Update API is called with approval/rejection. Survives deploys. |
| **Wait for external event** | `WAIT` task (time-based) or `HUMAN` task with event handler | Durable pause. Timer or signal resolution survives server restarts. |
| **Wait for webhook** | `HUMAN` task + webhook endpoint | External system calls the Task Update API with payload. Workflow resumes with that payload as task output. |
| **Plan/act/observe loop** | `DO_WHILE` operator | Loop until a condition is met. Each iteration is a persisted step. The loop counter and state survive failures. |
| **Dynamic tool selection** | `DYNAMIC` task or `DYNAMIC_FORK` | The LLM output determines which task(s) to run next. Conductor resolves the task type at runtime. |
| **Multi-agent / sub-agent** | `SUB_WORKFLOW` task | Spawn a child agent as a sub-workflow. Parent waits for completion. Failure in a child can trigger compensation in the parent. Full observability across the entire agent tree. |
| **Rollback on failure** | `failureWorkflow` + compensation pattern | When an agent fails after taking real-world actions, a failure workflow runs compensating tasks (undo API calls, send notifications, release resources). |
| **Structured output** | Workflow `outputParameters` | Map task outputs to a structured JSON response using Conductor's expression syntax. |
| **Expose as API** | Conductor REST API: `POST /api/workflow/{name}` | Any workflow is callable via HTTP. Start synchronously or asynchronously. Get structured output back. |
| **Expose as MCP tool** | MCP Gateway integration | Register any workflow as an MCP tool. LLMs and agents invoke it directly via `LIST_MCP_TOOLS` / `CALL_MCP_TOOL` and receive structured output. |


## What you'd have to build without Conductor

If you run agents on a framework like LangChain, CrewAI, or LangGraph without a durable execution backend, you are responsible for:

- **State persistence** &mdash; Checkpointing agent progress so crashes don't restart from zero.
- **Retry logic** &mdash; Retrying failed LLM and tool calls with backoff, deduplication, and timeout handling.
- **Human-in-the-loop** &mdash; Building a pause/resume mechanism that survives process restarts and deploys.
- **Compensation** &mdash; Rolling back side effects (sent emails, created records, charged payments) when a downstream step fails.
- **Observability** &mdash; Logging every LLM prompt, response, tool call, and decision in a queryable, auditable format.
- **Multi-agent coordination** &mdash; Managing parent-child lifecycle, failure propagation, and shared state across sub-agents.
- **Scalability** &mdash; Distributing work across multiple worker processes and scaling them independently.

Conductor provides all of this as infrastructure. Your agent code focuses on the logic — what to ask the LLM, which tools to call, what to do with the results.


## Next steps

- **[Build Your First AI Agent](first-ai-agent.md)** &mdash; Step-by-step: discover MCP tools, call an LLM, execute, add human approval, make it autonomous. 5 minutes.
- **[AI & LLM Recipes](../cookbook/ai-llm.md)** &mdash; Ready-to-use recipes: chat completion, RAG, MCP agents, web search, code execution, coding agents, extended thinking, and more.
- **[LLM Orchestration](llm-orchestration.md)** &mdash; Native LLM providers, built-in tools, vector databases, and content generation.
- **[MCP Integration](mcp-guide.md)** &mdash; Connect to any MCP server, expose workflows as MCP tools, multi-server agents.
- **[Production Agent Architecture](production-agent-architecture.md)** &mdash; The canonical reference architecture for a durable production agent. End-to-end pattern with every primitive mapped.
- **[Failure Semantics for AI Agents](failure-semantics.md)** &mdash; The exact failure contract: what happens under crashes, retries, duplicates, long waits, and partial side effects.
- **[Why Conductor for Agents](why-conductor.md)** &mdash; What Conductor gives you out of the box for agentic workflows.
- **[Durable Agents](durable-agents.md)** &mdash; What persists, what gets retried, and why JSON is AI-native.
- **[Human-in-the-Loop](human-in-the-loop.md)** &mdash; Pre-execution review, conditional approval, and LLM-as-judge patterns.
- **[Dynamic Workflows](dynamic-workflows.md)** &mdash; Agent loops, dynamic workflow generation, and tool use examples.
- **[Token Efficiency](token-efficiency.md)** &mdash; How durable execution saves tokens and reduces LLM costs.
