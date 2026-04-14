---
description: "Build your first AI agent with Conductor in 5 minutes. Step-by-step tutorial: discover MCP tools, call an LLM, execute tools, add human approval, and make it autonomous — all with durable execution guarantees."
---

# Build your first AI agent

**Build a durable AI agent in 5 minutes.** Your agent will discover tools, plan actions, execute them, and summarize results — with full crash recovery, observability, and human approval built in.

**Prerequisites:**

- Conductor running locally (`conductor server start`)
- An LLM provider API key (OpenAI or Anthropic)
- An MCP server running (we'll use a simple example below)

## Step 1: Start an MCP server

Your agent needs tools to call. MCP (Model Context Protocol) is the open standard for connecting AI agents to tools. Start a test MCP server — or use any MCP server you already have running.

```bash
pip install mcp-testkit
mcp-testkit --transport http
```

This starts an MCP server at `http://localhost:3001/mcp` with 65 deterministic tools for testing. You'll use this URL in the workflow definition.

!!! tip "Any MCP server works"
    Conductor connects to any MCP-compatible server. Use community MCP servers for GitHub, Slack, databases, or any API — or build your own. See the [MCP integration guide](mcp-guide.md) for details.


## Step 2: Configure your LLM provider

Add your API key to Conductor's configuration. If you started with the CLI, edit `~/.conductor/config.properties`:

```properties
conductor.integrations.ai.enabled=true

# Choose one (or both):
conductor.ai.openai.apiKey=sk-your-openai-key
conductor.ai.anthropic.apiKey=sk-ant-your-anthropic-key
```

Restart the server after updating the configuration.


## Step 3: Create the agent workflow

Save this as `my_first_agent.json`. This is a complete AI agent in four tasks — no custom code, no workers, no framework:

```json
{
  "name": "my_first_agent",
  "description": "AI agent that discovers tools, plans, executes, and summarizes",
  "version": 1,
  "schemaVersion": 2,
  "inputParameters": ["task"],
  "tasks": [
    {
      "name": "discover_tools",
      "taskReferenceName": "discover",
      "type": "LIST_MCP_TOOLS",
      "inputParameters": {
        "mcpServer": "http://localhost:3001/mcp"
      }
    },
    {
      "name": "plan_action",
      "taskReferenceName": "plan",
      "type": "LLM_CHAT_COMPLETE",
      "inputParameters": {
        "llmProvider": "openai",
        "model": "gpt-4o-mini",
        "messages": [
          {
            "role": "system",
            "message": "You are an AI agent. Available tools: ${discover.output.tools}. The user wants to: ${workflow.input.task}. Decide which tool to use. Respond with JSON: {\"method\": \"tool_name\", \"arguments\": {}}"
          },
          {
            "role": "user",
            "message": "${workflow.input.task}"
          }
        ],
        "temperature": 0.1,
        "maxTokens": 500
      }
    },
    {
      "name": "execute_tool",
      "taskReferenceName": "execute",
      "type": "CALL_MCP_TOOL",
      "inputParameters": {
        "mcpServer": "http://localhost:3001/mcp",
        "method": "${plan.output.result.method}",
        "arguments": "${plan.output.result.arguments}"
      }
    },
    {
      "name": "summarize_result",
      "taskReferenceName": "summarize",
      "type": "LLM_CHAT_COMPLETE",
      "inputParameters": {
        "llmProvider": "openai",
        "model": "gpt-4o-mini",
        "messages": [
          {
            "role": "user",
            "message": "The user asked: ${workflow.input.task}\n\nTool result: ${execute.output.content}\n\nSummarize this clearly for the user."
          }
        ],
        "maxTokens": 500
      }
    }
  ],
  "outputParameters": {
    "plan": "${plan.output.result}",
    "toolResult": "${execute.output.content}",
    "summary": "${summarize.output.result}"
  }
}
```

**What each task does:**

| Task | Type | Purpose |
|------|------|---------|
| `discover` | `LIST_MCP_TOOLS` | Queries the MCP server to discover available tools |
| `plan` | `LLM_CHAT_COMPLETE` | Sends the tool list + user task to the LLM, which picks a tool and arguments |
| `execute` | `CALL_MCP_TOOL` | Calls the selected tool on the MCP server |
| `summarize` | `LLM_CHAT_COMPLETE` | Summarizes the raw tool output for the user |

Every task is a native Conductor system task. No workers to write, no code to deploy.


## Step 4: Register and run

```bash
# Register the workflow
conductor workflow create my_first_agent.json

# Run the agent synchronously — output prints directly to your terminal
curl -s -X POST 'http://localhost:8080/api/workflow/execute/my_first_agent/1' \
  -H 'Content-Type: application/json' \
  -d '{
    "task": "What is the weather in San Francisco?"
  }' | jq .
```

Or using the CLI:

```bash
conductor workflow start -w my_first_agent --sync --input '{"task": "What is the weather in San Francisco?"}'
```

Open [http://localhost:8080](http://localhost:8080) to see the execution. Click into the workflow to see each task's input, output, and timing.

!!! success "What just happened"
    Your agent discovered tools from an MCP server, asked an LLM to pick the right one, executed it, and summarized the result. Every step was persisted — if the server had crashed at any point, execution would have resumed from the last completed task. No tokens wasted, no progress lost.


## Step 5: Add human approval

Real agents need guardrails. Add a `HUMAN` task between planning and execution so a person reviews the agent's plan before it acts.

Update `my_first_agent.json` — insert this task between `plan_action` and `execute_tool`:

```json
{
  "name": "human_review",
  "taskReferenceName": "approval",
  "type": "HUMAN",
  "inputParameters": {
    "plannedAction": "${plan.output.result}",
    "userTask": "${workflow.input.task}"
  }
}
```

Now when you run the agent, it pauses after planning and waits for human approval. Approve it via the UI or API:

```bash
# Approve the plan (replace TASK_ID with the actual task ID from the execution)
curl -X POST 'http://localhost:8080/api/tasks' \
  -H 'Content-Type: application/json' \
  -d '{
    "workflowInstanceId": "WORKFLOW_ID",
    "taskId": "TASK_ID",
    "status": "COMPLETED",
    "outputData": {"approved": true, "reviewer": "you"}
  }'
```

The approval is durable — the workflow stays paused indefinitely, even across server restarts and deploys, until someone approves it.


## Step 6: Make it autonomous

Turn your agent into an autonomous loop that keeps working until the task is done. Replace the linear workflow with a `DO_WHILE` loop:

```json
{
  "name": "autonomous_agent",
  "description": "Agent that loops until the task is complete",
  "version": 1,
  "schemaVersion": 2,
  "inputParameters": ["task"],
  "tasks": [
    {
      "name": "discover_tools",
      "taskReferenceName": "discover",
      "type": "LIST_MCP_TOOLS",
      "inputParameters": {
        "mcpServer": "http://localhost:3001/mcp"
      }
    },
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
            "llmProvider": "openai",
            "model": "gpt-4o-mini",
            "messages": [
              {
                "role": "system",
                "message": "You are an autonomous agent. Available tools: ${discover.output.tools}. Previous results: ${loop.output.results}. Respond with JSON: {\"action\": \"tool_name\", \"arguments\": {}, \"done\": false} when you need to use a tool, or {\"answer\": \"final answer\", \"done\": true} when the task is complete."
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
                  "mcpServer": "http://localhost:3001/mcp",
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

Each iteration of the loop is a durable checkpoint. If the agent crashes at iteration 12, it resumes from iteration 12 — not from the beginning. Every LLM call and tool call is persisted and observable.


## What you built

In 5 minutes, you built an AI agent that:

- **Discovers tools** from any MCP server at runtime
- **Plans actions** using an LLM
- **Executes tools** with full retry and error handling
- **Supports human approval** as a durable pause
- **Loops autonomously** until the task is complete
- **Survives crashes** without losing progress or re-running LLM calls
- **Is fully observable** — every prompt, response, tool call, and decision is recorded

All of this with zero custom code. The entire agent is a JSON workflow definition that Conductor executes with durable execution guarantees.


## Next steps

- **[MCP Integration](mcp-guide.md)** — Connect to any MCP server, expose workflows as MCP tools.
- **[Human-in-the-Loop](human-in-the-loop.md)** — Advanced approval patterns: conditional review, LLM-as-judge.
- **[Dynamic Workflows](dynamic-workflows.md)** — Agents that generate their own execution plans as JSON.
- **[Token Efficiency](token-efficiency.md)** — How durable execution saves tokens and reduces LLM costs.
- **[LLM Orchestration](llm-orchestration.md)** — 14+ native LLM providers, vector databases, content generation.
