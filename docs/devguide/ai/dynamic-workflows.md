---
description: Dynamic workflow execution for AI agents — agents that build their own plans as JSON workflow definitions, agent loops with DO_WHILE, and tool use with MCP. Full durability, observability, and retry support.
---

# Dynamic workflows for agents

Conductor supports three levels of agent dynamism, from simple tool use to fully self-generating agents.


## Agent loop: plan/act/observe with DO_WHILE

The defining pattern of an autonomous agent is the loop: call an LLM, execute a tool, observe the result, decide whether to continue. Conductor models this with `DO_WHILE`:

```json
{
  "name": "autonomous_agent",
  "description": "Agent that loops until the task is complete",
  "version": 1,
  "schemaVersion": 2,
  "tasks": [
    {
      "name": "agent_loop",
      "taskReferenceName": "loop",
      "type": "DO_WHILE",
      "loopCondition": "if ($.loop['think'].output.result.done == true) { false; } else { true; }",
      "loopOver": [
        {
          "name": "think",
          "taskReferenceName": "think",
          "type": "LLM_CHAT_COMPLETE",
          "inputParameters": {
            "llmProvider": "anthropic",
            "model": "claude-sonnet-4-20250514",
            "messages": [
              {
                "role": "system",
                "message": "You are an agent. Available tools: ${workflow.input.tools}. Previous results: ${loop.output.results}. Respond with JSON: {\"action\": \"tool_name\", \"arguments\": {}, \"done\": false} or {\"answer\": \"...\", \"done\": true}"
              },
              {
                "role": "user",
                "message": "${workflow.input.task}"
              }
            ],
            "temperature": 0.1
          }
        },
        {
          "name": "act",
          "taskReferenceName": "act",
          "type": "SWITCH",
          "evaluatorType": "javascript",
          "expression": "$.think.output.result.done ? 'done' : 'call_tool'",
          "decisionCases": {
            "call_tool": [
              {
                "name": "execute_tool",
                "taskReferenceName": "tool_call",
                "type": "CALL_MCP_TOOL",
                "inputParameters": {
                  "mcpServer": "${workflow.input.mcpServerUrl}",
                  "method": "${think.output.result.action}",
                  "arguments": "${think.output.result.arguments}"
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
    "answer": "${loop.output.think.output.result.answer}",
    "iterations": "${loop.output.iteration}"
  }
}
```

**What makes this durable:**

- Each iteration of the loop is a persisted checkpoint. If the agent crashes at iteration 12, it resumes from iteration 12 — not from iteration 1.
- Every LLM call (prompt, response, token usage) is recorded. You can inspect exactly what the agent decided at each step.
- Every tool call (input, output, status) is tracked. If a tool call fails, it retries according to the task's retry policy without re-running the LLM.
- The loop counter and all intermediate state survive server restarts.


## Dynamic workflow generation: agents that build their own plans

Conductor supports dynamic workflow execution where the complete workflow definition is provided at start time, without pre-registration. This is the most powerful form of agent dynamism — the LLM generates the entire execution plan as JSON, and Conductor runs it immediately.

1. An LLM generates a plan as a JSON workflow definition.
2. Your code passes that definition directly to the `StartWorkflowRequest`.
3. Conductor validates, persists, and executes it immediately.
4. Every step is durable, observable, and retryable — even though the workflow was generated at runtime.

```json
{
  "name": "dynamic_agent_planner",
  "version": 1,
  "schemaVersion": 2,
  "tasks": [
    {
      "name": "generate_plan",
      "taskReferenceName": "planner",
      "type": "LLM_CHAT_COMPLETE",
      "inputParameters": {
        "llmProvider": "anthropic",
        "model": "claude-sonnet-4-20250514",
        "messages": [
          {
            "role": "system",
            "message": "You are a workflow planner. Given a user task, generate a Conductor workflow definition as JSON. Available task types: LLM_CHAT_COMPLETE, CALL_MCP_TOOL, LIST_MCP_TOOLS, HTTP, HUMAN, LLM_SEARCH_INDEX. The workflow must include a 'name', 'tasks' array, and 'outputParameters'."
          },
          {
            "role": "user",
            "message": "${workflow.input.task}"
          }
        ],
        "temperature": 0.2
      }
    },
    {
      "name": "review_plan",
      "taskReferenceName": "approval",
      "type": "HUMAN",
      "inputParameters": {
        "generatedWorkflow": "${planner.output.result}"
      }
    },
    {
      "name": "execute_plan",
      "taskReferenceName": "execution",
      "type": "START_WORKFLOW",
      "inputParameters": {
        "startWorkflow": {
          "workflowDefinition": "${planner.output.result}",
          "input": "${workflow.input.taskInput}"
        }
      }
    }
  ],
  "outputParameters": {
    "generatedPlan": "${planner.output.result}",
    "executionId": "${execution.output.workflowId}"
  }
}
```

**What happens:**

1. `planner` &mdash; `LLM_CHAT_COMPLETE` generates an entire workflow definition as JSON based on the user's task description.
2. `approval` &mdash; `HUMAN` task pauses the workflow so a reviewer can inspect the generated plan before it runs. This is critical — you don't want an LLM-generated workflow executing unsupervised.
3. `execution` &mdash; `START_WORKFLOW` launches the generated workflow definition directly. Conductor validates it, persists it, and executes it with full durability. No pre-registration needed.

The generated child workflow gets all the same guarantees as any Conductor workflow: persisted state, retry policies, failure handling, full observability. The fact that it was generated by an LLM 30 seconds ago doesn't matter — it runs on the same durable execution engine.

Combined with `DYNAMIC` tasks (where the task type is resolved at runtime based on input) and `DYNAMIC_FORK` (where the number and type of parallel tasks is determined at runtime), this enables agents that create, modify, and execute their own plans.


## Example: MCP agent with tool use and human approval

A more focused example — an agent that discovers tools, plans, gets approval, and executes. Every step uses a built-in system task.

```json
{
  "name": "mcp_agent_with_approval",
  "description": "Discover tools, plan, execute with approval, summarize",
  "version": 1,
  "schemaVersion": 2,
  "tasks": [
    {
      "name": "list_available_tools",
      "taskReferenceName": "discover_tools",
      "type": "LIST_MCP_TOOLS",
      "inputParameters": {
        "mcpServer": "${workflow.input.mcpServerUrl}"
      }
    },
    {
      "name": "decide_which_tools_to_use",
      "taskReferenceName": "plan",
      "type": "LLM_CHAT_COMPLETE",
      "inputParameters": {
        "llmProvider": "anthropic",
        "model": "claude-sonnet-4-20250514",
        "messages": [
          {
            "role": "system",
            "message": "You are an AI agent. Available tools: ${discover_tools.output.tools}. User wants to: ${workflow.input.task}"
          },
          {
            "role": "user",
            "message": "Which tool should I use and what parameters? Respond with JSON: {\"method\": \"string\", \"arguments\": {}}"
          }
        ],
        "temperature": 0.1,
        "maxTokens": 500
      }
    },
    {
      "name": "human_review",
      "taskReferenceName": "approval",
      "type": "HUMAN",
      "inputParameters": {
        "plannedAction": "${plan.output.result}"
      }
    },
    {
      "name": "execute_tool",
      "taskReferenceName": "execute",
      "type": "CALL_MCP_TOOL",
      "inputParameters": {
        "mcpServer": "${workflow.input.mcpServerUrl}",
        "method": "${plan.output.result.method}",
        "arguments": "${plan.output.result.arguments}"
      }
    },
    {
      "name": "summarize_result",
      "taskReferenceName": "summarize",
      "type": "LLM_CHAT_COMPLETE",
      "inputParameters": {
        "llmProvider": "anthropic",
        "model": "claude-sonnet-4-20250514",
        "messages": [
          {
            "role": "user",
            "message": "The user asked: ${workflow.input.task}\n\nTool result: ${execute.output.content}\n\nSummarize this result for the user."
          }
        ],
        "maxTokens": 500
      }
    }
  ],
  "outputParameters": {
    "plan": "${plan.output.result}",
    "toolResult": "${execute.output.content}",
    "summary": "${summarize.output.result}",
    "approvedBy": "${approval.output.reviewer}"
  }
}
```

Every task type here — `LIST_MCP_TOOLS`, `LLM_CHAT_COMPLETE`, `CALL_MCP_TOOL`, `HUMAN` — is a native Conductor system task. No custom workers, no external frameworks.

See the full set of examples in the [`ai/examples/`](https://github.com/conductor-oss/conductor/tree/main/ai/examples) directory.


## Next steps

- **[Durable Agents](durable-agents.md)** &mdash; What persists, what gets retried, and why JSON is AI-native.
- **[LLM Orchestration](llm-orchestration.md)** &mdash; Native LLM providers, vector databases, and content generation.
- **[Dynamic Fork](../configuration/workflowdef/operators/dynamic-fork-task.md)** &mdash; Runtime-determined parallel execution.
- **[DO_WHILE](../configuration/workflowdef/operators/do-while-task.md)** &mdash; Loop operator for agent iterations.
- **[HUMAN task](../configuration/workflowdef/systemtasks/human-task.md)** &mdash; Human-in-the-loop approval.
