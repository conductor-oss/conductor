---
description: "The canonical reference architecture for building production AI agents on Conductor — end-to-end pattern with planner, tool selection, execution, retry, memory, human approval, long waits, reflection loops, budget caps, and full observability."
---

# Production agent architecture

This is the reference architecture for a durable AI agent on Conductor. Not a toy. Not a feature list. This is the exact pattern for an agent that plans, acts, waits, recovers, and runs in production.


## Architecture diagram

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
| **Select tool at runtime** | `DYNAMIC` task | LLM output determines which task type executes next |
| **Execute tool** | `CALL_MCP_TOOL`, `HTTP`, or `SIMPLE` worker | Tool runs with retry policy, timeout, and full I/O recording |
| **Retry with backoff** | Task definition `retryLogic` | `FIXED`, `EXPONENTIAL_BACKOFF`, or `LINEAR_BACKOFF` — no code needed |
| **Parallel tool calls** | `FORK/JOIN` or `DYNAMIC_FORK` | Fan out to N tools in parallel, join when all complete |
| **Memory / context handoff** | `SET_VARIABLE` + workflow variables | Accumulate results across loop iterations; pass to next LLM call |
| **Human approval gate** | `HUMAN` task | Durable pause. Survives restarts and deploys. Resumes on API signal. |
| **Long wait (hours/days)** | `WAIT` task | Timer-based durable pause. Survives server restarts. |
| **Resume from external event** | `HUMAN` task + webhook/API | External system calls Task Update API. Workflow resumes with payload. |
| **Reflection / evaluation loop** | `DO_WHILE` with LLM-as-judge | Second LLM evaluates output quality; loop continues if below threshold |
| **Budget / iteration cap** | `DO_WHILE` `loopCondition` | `iteration < maxIterations` or token/cost check in loop condition |
| **Termination criteria** | `DO_WHILE` exit + `SWITCH` | LLM sets `done: true`, or evaluator decides goal is met |
| **Delegate to specialist** | `SUB_WORKFLOW` or `START_WORKFLOW` | Spawn child agent. Parent waits. Failure propagates. Full observability across the tree. |
| **Compensation on failure** | `failureWorkflow` | Undo side effects: revoke API calls, send notifications, release resources |
| **Audit trail** | Automatic | Every task's input, output, timing, retry count, and worker ID is persisted |


## End-to-end workflow

Here is the complete agent as a single Conductor workflow. Every step is a native system task or operator — no custom code, no external framework.

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
        "context": [],
        "actions_taken": []
      }
    },
    {
      "name": "agent_loop",
      "taskReferenceName": "loop",
      "type": "DO_WHILE",
      "loopCondition": "if ($.loop['plan'].output.result.done == true) { false; } else if ($.loop['plan'].output.iteration >= $.maxIterations) { false; } else { true; }",
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
                "message": "You are a production AI agent. Goal: ${workflow.input.goal}\n\nAvailable tools: ${discover.output.tools}\n\nPrevious actions and results: ${workflow.variables.context}\n\nDecide the next action. Respond with JSON:\n- To use a tool: {\"action\": \"tool_name\", \"arguments\": {}, \"reasoning\": \"why\", \"needs_approval\": true/false, \"done\": false}\n- To finish: {\"answer\": \"final answer\", \"done\": true}"
              }
            ],
            "temperature": 0.1,
            "maxTokens": 1000
          }
        },
        {
          "name": "check_if_done",
          "taskReferenceName": "done_check",
          "type": "SWITCH",
          "evaluatorType": "javascript",
          "expression": "$.plan.output.result.done ? 'done' : ($.plan.output.result.needs_approval ? 'needs_approval' : 'execute')",
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
                  "context": "${workflow.variables.context.concat([{action: plan.output.result.action, result: approved_tool_call.output.content, approved: true}])}"
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
                  "context": "${workflow.variables.context.concat([{action: plan.output.result.action, result: tool_call.output.content}])}"
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
    "answer": "${loop.output.plan.output.result.answer}",
    "iterations": "${loop.output.iteration}",
    "actions_taken": "${workflow.variables.context}"
  },
  "failureWorkflow": "agent_compensation_workflow"
}
```


## What makes this production-ready

### Every step is a durable checkpoint

Each iteration of `DO_WHILE` is persisted before the next begins. If the agent crashes at iteration 15 of 20, it resumes from iteration 15 — not from scratch. Every LLM prompt, response, tool call, and human decision is recorded.

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

Open the Conductor UI to see:

- The exact task graph for this execution
- Every LLM prompt and response (click any `LLM_CHAT_COMPLETE` task)
- Every tool call with input, output, and timing
- Every human approval with who approved and when
- The iteration count and loop state
- Retry history for any failed task
- The full workflow input, output, and variables


## Extending the pattern

### Add parallel research

Replace a single tool call with `DYNAMIC_FORK` to fan out to multiple tools in parallel:

```json
{
  "name": "parallel_research",
  "taskReferenceName": "research",
  "type": "DYNAMIC_FORK",
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

Use `SUB_WORKFLOW` to spawn a child agent for a specialized task:

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

The parent agent waits for the child to complete. If the child fails, the parent's failure handling kicks in. The entire agent tree is observable in the UI — drill from parent to child to sub-child.


## The primitives, mapped

| "I need my agent to..." | Use this | Why |
|---|---|---|
| Wait for a tool callback | `HUMAN` task or async completion | Durable pause. Resumes on API signal with payload. |
| Sleep until a retry window | `WAIT` task | Timer-based durable pause. Zero resource consumption. |
| Pick the next tool at runtime | `DYNAMIC` task | LLM output determines task type. Resolved at execution time. |
| Call multiple tools in parallel | `FORK/JOIN` or `DYNAMIC_FORK` | Static or runtime-determined parallelism. Join waits for all. |
| Loop until goal is met | `DO_WHILE` | Checkpointed loop. Each iteration persisted. |
| Delegate to a specialist agent | `SUB_WORKFLOW` or `START_WORKFLOW` | Child workflow with full lifecycle management. |
| Accumulate context across steps | `SET_VARIABLE` | Workflow variables persisted to durable storage. |
| Evaluate output quality | `LLM_CHAT_COMPLETE` as evaluator | LLM-as-judge pattern inside the loop. |
| Cap iterations or cost | `DO_WHILE` `loopCondition` | Check iteration count, token usage, or cost. |
| Undo side effects on failure | `failureWorkflow` | Compensation tasks run automatically on workflow failure. |
| Pause for human review | `HUMAN` task | Indefinite durable pause. Survives restarts and deploys. |
| Resume on external event | `HUMAN` task + API/webhook | External system calls Task Update API with payload. |
| Post-process structured output | `INLINE` (JavaScript) or `JSON_JQ_TRANSFORM` | Server-side transforms without a worker. |


## Next steps

- **[Failure Semantics for AI Agents](failure-semantics.md)** — The exact failure contract: what happens under crashes, retries, duplicates, and long waits.
- **[Why Conductor for Agents](why-conductor.md)** — What Conductor gives you out of the box for agentic workflows.
- **[Build Your First AI Agent](first-ai-agent.md)** — Start simple and build up to this architecture in 5 minutes.
- **[MCP Integration](mcp-guide.md)** — Connect to any MCP server, expose workflows as MCP tools.
- **[Token Efficiency](token-efficiency.md)** — How durable execution saves tokens and reduces LLM costs.
