---
description: "LLM orchestration cookbook — AI agent orchestration recipes for chat completion, RAG pipelines, MCP agents with function calling, web search, code execution, coding agents, extended thinking, image generation, LLM-to-PDF, and provider configuration."
---

# AI & LLM orchestration recipes

Build durable agents and LLM workflows with Conductor's native AI capabilities. Every recipe below runs with full durable execution guarantees — retries, state persistence, and crash recovery.

### Chat completion

A single-step workflow that sends a question to an LLM and returns the answer.

```json
{
  "name": "chat_workflow",
  "version": 1,
  "schemaVersion": 2,
  "tasks": [
    {
      "name": "chat_task",
      "taskReferenceName": "chat",
      "type": "LLM_CHAT_COMPLETE",
      "inputParameters": {
        "llmProvider": "openai",
        "model": "gpt-4o-mini",
        "messages": [
          {"role": "system", "message": "You are a helpful assistant."},
          {"role": "user", "message": "${workflow.input.question}"}
        ],
        "temperature": 0.7,
        "maxTokens": 500
      }
    }
  ],
  "inputParameters": ["question"],
  "outputParameters": {
    "answer": "${chat.output.result}"
  }
}
```

**Register and run:**

```shell
curl -X POST 'http://localhost:8080/api/metadata/workflow' \
  -H 'Content-Type: application/json' \
  -d @chat_workflow.json

curl -X POST 'http://localhost:8080/api/workflow/chat_workflow' \
  -H 'Content-Type: application/json' \
  -d '{"question": "What is workflow orchestration?"}'
```

---

### RAG pipeline with vector database (search + answer)

A vector database workflow for retrieval-augmented generation: vector search retrieves relevant documents, then an LLM generates an answer grounded in those results.

```json
{
  "name": "rag_workflow",
  "version": 1,
  "schemaVersion": 2,
  "inputParameters": ["question"],
  "tasks": [
    {
      "name": "search_knowledge_base",
      "taskReferenceName": "search",
      "type": "LLM_SEARCH_INDEX",
      "inputParameters": {
        "vectorDB": "postgres-prod",
        "namespace": "kb",
        "index": "articles",
        "embeddingModelProvider": "openai",
        "embeddingModel": "text-embedding-3-small",
        "query": "${workflow.input.question}",
        "llmMaxResults": 3
      }
    },
    {
      "name": "generate_answer",
      "taskReferenceName": "answer",
      "type": "LLM_CHAT_COMPLETE",
      "inputParameters": {
        "llmProvider": "anthropic",
        "model": "claude-sonnet-4-20250514",
        "messages": [
          {"role": "system", "message": "Answer based on the following context: ${search.output.result}"},
          {"role": "user", "message": "${workflow.input.question}"}
        ],
        "temperature": 0.3
      }
    }
  ],
  "outputParameters": {
    "answer": "${answer.output.result}",
    "sources": "${search.output.result}"
  }
}
```

**Register and run:**

```shell
curl -X POST 'http://localhost:8080/api/metadata/workflow' \
  -H 'Content-Type: application/json' \
  -d @rag_workflow.json

curl -X POST 'http://localhost:8080/api/workflow/rag_workflow' \
  -H 'Content-Type: application/json' \
  -d '{"question": "How do I configure retry policies?"}'
```

!!! note "Prerequisites"
    Requires a vector database (pgvector, Pinecone, or MongoDB Atlas) configured as a Conductor integration, plus at least one LLM provider. See [AI provider configuration](#ai-provider-configuration) below.

---

### MCP AI agent with function calling

A four-step agentic workflow demonstrating AI agent orchestration with function calling: discover available tools via MCP, ask an LLM to pick the right tool, execute it via tool use, and summarize the result.

```json
{
  "name": "mcp_ai_agent_workflow",
  "version": 1,
  "schemaVersion": 2,
  "inputParameters": ["task"],
  "tasks": [
    {
      "name": "list_available_tools",
      "taskReferenceName": "discover_tools",
      "type": "LIST_MCP_TOOLS",
      "inputParameters": {
        "mcpServer": "http://localhost:3001/mcp"
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
          {"role": "system", "message": "You are an AI agent. Available tools: ${discover_tools.output.tools}. User wants to: ${workflow.input.task}"},
          {"role": "user", "message": "Which tool should I use and what parameters? Respond with JSON: {method: string, arguments: object}"}
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
          {"role": "user", "message": "Summarize this result for the user: ${execute.output.content}"}
        ],
        "maxTokens": 200
      }
    }
  ],
  "outputParameters": {
    "summary": "${summarize.output.result}",
    "rawToolOutput": "${execute.output.content}"
  }
}
```

**Register and run:**

```shell
curl -X POST 'http://localhost:8080/api/metadata/workflow' \
  -H 'Content-Type: application/json' \
  -d @mcp_ai_agent_workflow.json

curl -X POST 'http://localhost:8080/api/workflow/mcp_ai_agent_workflow' \
  -H 'Content-Type: application/json' \
  -d '{"task": "Look up the latest order status for customer 42"}'
```

---

### Image generation

Generate images from a text prompt using DALL-E or another supported provider.

```json
{
  "name": "image_gen_workflow",
  "version": 1,
  "schemaVersion": 2,
  "inputParameters": ["prompt"],
  "tasks": [
    {
      "name": "generate_image",
      "taskReferenceName": "image",
      "type": "GENERATE_IMAGE",
      "inputParameters": {
        "llmProvider": "openai",
        "model": "dall-e-3",
        "prompt": "${workflow.input.prompt}",
        "width": 1024,
        "height": 1024,
        "n": 1,
        "style": "vivid"
      }
    }
  ],
  "outputParameters": {
    "imageUrl": "${image.output.result}"
  }
}
```

**Register and run:**

```shell
curl -X POST 'http://localhost:8080/api/metadata/workflow' \
  -H 'Content-Type: application/json' \
  -d @image_gen_workflow.json

curl -X POST 'http://localhost:8080/api/workflow/image_gen_workflow' \
  -H 'Content-Type: application/json' \
  -d '{"prompt": "A futuristic city skyline at sunset, digital art"}'
```

---

### LLM report to PDF pipeline

An LLM generates a structured markdown report, then Conductor converts it to a downloadable PDF.

```json
{
  "name": "llm_to_pdf_pipeline",
  "description": "LLM generates a markdown report, then converts it to PDF",
  "version": 1,
  "schemaVersion": 2,
  "inputParameters": ["topic", "audience"],
  "tasks": [
    {
      "name": "generate_report_markdown",
      "taskReferenceName": "llm_report",
      "type": "LLM_CHAT_COMPLETE",
      "inputParameters": {
        "llmProvider": "openai",
        "model": "gpt-4o-mini",
        "messages": [
          {"role": "system", "message": "You are a professional report writer. Generate well-structured markdown reports."},
          {"role": "user", "message": "Write a detailed report about: ${workflow.input.topic}\nTarget audience: ${workflow.input.audience}"}
        ],
        "temperature": 0.7,
        "maxTokens": 2000
      }
    },
    {
      "name": "convert_to_pdf",
      "taskReferenceName": "pdf_output",
      "type": "GENERATE_PDF",
      "inputParameters": {
        "markdown": "${llm_report.output.result}",
        "pageSize": "A4",
        "theme": "default",
        "baseFontSize": 11,
        "pdfMetadata": {
          "title": "${workflow.input.topic}",
          "author": "Conductor AI Pipeline"
        }
      }
    }
  ],
  "outputParameters": {
    "reportMarkdown": "${llm_report.output.result}",
    "pdfLocation": "${pdf_output.output.result.location}"
  }
}
```

**Register and run:**

```shell
curl -X POST 'http://localhost:8080/api/metadata/workflow' \
  -H 'Content-Type: application/json' \
  -d @llm_to_pdf_pipeline.json

curl -X POST 'http://localhost:8080/api/workflow/llm_to_pdf_pipeline' \
  -H 'Content-Type: application/json' \
  -d '{"topic": "Microservices observability best practices", "audience": "Platform engineering team"}'
```

---

### Web search — real-time information retrieval

Enable the LLM's built-in web search to answer questions about current events or find up-to-date information. No MCP server or external tool needed — the provider handles the search natively.

```json
{
  "name": "web_search_workflow",
  "version": 1,
  "schemaVersion": 2,
  "inputParameters": ["question"],
  "tasks": [
    {
      "name": "web_search_chat",
      "taskReferenceName": "chat",
      "type": "LLM_CHAT_COMPLETE",
      "inputParameters": {
        "llmProvider": "openai",
        "model": "gpt-4o-mini",
        "messages": [
          {"role": "system", "message": "Use web search to find current information."},
          {"role": "user", "message": "${workflow.input.question}"}
        ],
        "webSearch": true,
        "maxTokens": 1000
      }
    }
  ],
  "outputParameters": {
    "answer": "${chat.output.result}"
  }
}
```

**Register and run:**

```shell
curl -X POST 'http://localhost:8080/api/metadata/workflow' \
  -H 'Content-Type: application/json' \
  -d @web_search_workflow.json

curl -X POST 'http://localhost:8080/api/workflow/web_search_workflow' \
  -H 'Content-Type: application/json' \
  -d '{"question": "What are the latest developments in AI regulation?"}'
```

!!! note "Provider support"
    Web search is supported by OpenAI, Anthropic, and Google Gemini. Set `"webSearch": true` — the same parameter works across all providers.

---

### Code execution — sandboxed code interpreter

Let the LLM write and run code in a sandboxed environment. Useful for data analysis, calculations, chart generation, and tasks that benefit from executable code.

```json
{
  "name": "code_execution_workflow",
  "version": 1,
  "schemaVersion": 2,
  "inputParameters": ["task"],
  "tasks": [
    {
      "name": "code_chat",
      "taskReferenceName": "chat",
      "type": "LLM_CHAT_COMPLETE",
      "inputParameters": {
        "llmProvider": "google_gemini",
        "model": "gemini-2.5-flash",
        "messages": [
          {"role": "system", "message": "Use code execution to compute results and analyze data."},
          {"role": "user", "message": "${workflow.input.task}"}
        ],
        "codeInterpreter": true,
        "maxTokens": 2000
      }
    }
  ],
  "outputParameters": {
    "result": "${chat.output.result}"
  }
}
```

**Register and run:**

```shell
curl -X POST 'http://localhost:8080/api/metadata/workflow' \
  -H 'Content-Type: application/json' \
  -d @code_execution_workflow.json

curl -X POST 'http://localhost:8080/api/workflow/code_execution_workflow' \
  -H 'Content-Type: application/json' \
  -d '{"task": "Calculate the first 100 prime numbers and find the average gap between consecutive primes"}'
```

!!! note "Provider support"
    Code execution is supported by OpenAI (`code_interpreter`), Anthropic (`code_execution`), and Google Gemini (`codeExecution`). Set `"codeInterpreter": true` — the same parameter works across all providers.

---

### Coding agent — plan, code, and review

A three-step agent that plans an implementation, writes and executes the code using the code interpreter, and reviews the result. This pattern is useful for automated code generation tasks.

```json
{
  "name": "coding_agent",
  "version": 1,
  "schemaVersion": 2,
  "inputParameters": ["task"],
  "tasks": [
    {
      "name": "plan",
      "taskReferenceName": "plan",
      "type": "LLM_CHAT_COMPLETE",
      "inputParameters": {
        "llmProvider": "openai",
        "model": "gpt-4o",
        "messages": [
          {"role": "system", "message": "Break down the coding task into clear numbered steps."},
          {"role": "user", "message": "${workflow.input.task}"}
        ],
        "temperature": 0.2,
        "maxTokens": 1000
      }
    },
    {
      "name": "write_and_run",
      "taskReferenceName": "code",
      "type": "LLM_CHAT_COMPLETE",
      "inputParameters": {
        "llmProvider": "openai",
        "model": "gpt-4o",
        "messages": [
          {"role": "system", "message": "Write the code, run it, verify the output, and fix any errors."},
          {"role": "user", "message": "Plan:\n${plan.output.result}\n\nTask: ${workflow.input.task}"}
        ],
        "codeInterpreter": true,
        "temperature": 0.1,
        "maxTokens": 4000
      }
    },
    {
      "name": "review",
      "taskReferenceName": "review",
      "type": "LLM_CHAT_COMPLETE",
      "inputParameters": {
        "llmProvider": "openai",
        "model": "gpt-4o-mini",
        "messages": [
          {"role": "system", "message": "Review the implementation for correctness and code quality."},
          {"role": "user", "message": "Task: ${workflow.input.task}\n\nCode:\n${code.output.result}"}
        ],
        "maxTokens": 1000
      }
    }
  ],
  "outputParameters": {
    "code": "${code.output.result}",
    "review": "${review.output.result}"
  }
}
```

**Register and run:**

```shell
curl -X POST 'http://localhost:8080/api/metadata/workflow' \
  -H 'Content-Type: application/json' \
  -d @coding_agent.json

curl -X POST 'http://localhost:8080/api/workflow/coding_agent' \
  -H 'Content-Type: application/json' \
  -d '{"task": "Write a Python function that converts Roman numerals to integers, with unit tests"}'
```

---

### Extended thinking — complex reasoning

Give the LLM a token budget for step-by-step reasoning before generating its final response. Useful for math, logic, code review, and complex analysis.

```json
{
  "name": "extended_thinking_workflow",
  "version": 1,
  "schemaVersion": 2,
  "inputParameters": ["problem"],
  "tasks": [
    {
      "name": "think_deeply",
      "taskReferenceName": "think",
      "type": "LLM_CHAT_COMPLETE",
      "inputParameters": {
        "llmProvider": "anthropic",
        "model": "claude-sonnet-4-20250514",
        "messages": [
          {"role": "user", "message": "${workflow.input.problem}"}
        ],
        "thinkingTokenLimit": 10000,
        "maxTokens": 16000
      }
    }
  ],
  "outputParameters": {
    "answer": "${think.output.result}"
  }
}
```

**Register and run:**

```shell
curl -X POST 'http://localhost:8080/api/metadata/workflow' \
  -H 'Content-Type: application/json' \
  -d @extended_thinking_workflow.json

curl -X POST 'http://localhost:8080/api/workflow/extended_thinking_workflow' \
  -H 'Content-Type: application/json' \
  -d '{"problem": "Prove that the square root of 2 is irrational."}'
```

!!! note "Provider support"
    Extended thinking is supported by Anthropic (`thinkingTokenLimit`) and Google Gemini (`thinkingBudgetTokens`). OpenAI uses `"reasoningEffort": "high"` for a similar effect.

---

### Multi-turn conversation chaining with previousResponseId

Chain multiple LLM calls as a conversation without resending the full message history. The first call returns a `responseId`; pass it as `previousResponseId` to the next call. OpenAI's Responses API stores the conversation server-side, saving tokens and latency.

```json
{
  "name": "multi_turn_chain",
  "description": "Two-step conversation using previousResponseId to avoid resending history",
  "version": 1,
  "schemaVersion": 2,
  "inputParameters": ["topic"],
  "tasks": [
    {
      "name": "first_turn",
      "taskReferenceName": "turn1",
      "type": "LLM_CHAT_COMPLETE",
      "inputParameters": {
        "llmProvider": "openai",
        "model": "gpt-4o",
        "messages": [
          {"role": "system", "message": "You are a technical architect. Be concise."},
          {"role": "user", "message": "Design a high-level architecture for: ${workflow.input.topic}"}
        ],
        "temperature": 0.3,
        "maxTokens": 2000
      }
    },
    {
      "name": "follow_up",
      "taskReferenceName": "turn2",
      "type": "LLM_CHAT_COMPLETE",
      "inputParameters": {
        "llmProvider": "openai",
        "model": "gpt-4o",
        "messages": [
          {"role": "user", "message": "Now list the key risks and mitigations for this architecture."}
        ],
        "previousResponseId": "${turn1.output.responseId}",
        "temperature": 0.3,
        "maxTokens": 2000
      }
    }
  ],
  "outputParameters": {
    "architecture": "${turn1.output.result}",
    "risks": "${turn2.output.result}"
  }
}
```

**Register and run:**

```shell
curl -X POST 'http://localhost:8080/api/metadata/workflow' \
  -H 'Content-Type: application/json' \
  -d @multi_turn_chain.json

curl -X POST 'http://localhost:8080/api/workflow/multi_turn_chain' \
  -H 'Content-Type: application/json' \
  -d '{"topic": "Real-time collaborative document editor"}'
```

The second call sends only the new user message — OpenAI already has the full conversation context from `previousResponseId`. This is especially useful for long agent loops where resending the full history each iteration would be expensive.

!!! note "Provider support"
    `previousResponseId` is supported by OpenAI and Azure OpenAI (Responses API). Other providers require sending the full message history in each call.

---

### Web research agent — search, synthesize, PDF

A multi-step agent that uses web search to gather information, an LLM with extended thinking to synthesize a report, and converts it to PDF. Combines three built-in capabilities in a single workflow.

```json
{
  "name": "web_research_agent",
  "version": 1,
  "schemaVersion": 2,
  "inputParameters": ["topic"],
  "tasks": [
    {
      "name": "gather_information",
      "taskReferenceName": "research",
      "type": "LLM_CHAT_COMPLETE",
      "inputParameters": {
        "llmProvider": "openai",
        "model": "gpt-4o",
        "messages": [
          {"role": "system", "message": "Use web search to find comprehensive, current information. Search for multiple perspectives and recent developments."},
          {"role": "user", "message": "Research this topic thoroughly: ${workflow.input.topic}"}
        ],
        "webSearch": true,
        "temperature": 0.3,
        "maxTokens": 3000
      }
    },
    {
      "name": "synthesize_report",
      "taskReferenceName": "report",
      "type": "LLM_CHAT_COMPLETE",
      "inputParameters": {
        "llmProvider": "anthropic",
        "model": "claude-sonnet-4-20250514",
        "messages": [
          {"role": "system", "message": "Synthesize the research into a well-structured markdown report with sections, key findings, and citations."},
          {"role": "user", "message": "Topic: ${workflow.input.topic}\n\nResearch:\n${research.output.result}\n\nWrite a comprehensive report."}
        ],
        "thinkingTokenLimit": 5000,
        "maxTokens": 8000
      }
    },
    {
      "name": "convert_to_pdf",
      "taskReferenceName": "pdf",
      "type": "GENERATE_PDF",
      "inputParameters": {
        "markdown": "${report.output.result}",
        "pageSize": "A4",
        "pdfMetadata": {
          "title": "${workflow.input.topic}",
          "author": "Conductor Research Agent"
        }
      }
    }
  ],
  "outputParameters": {
    "report": "${report.output.result}",
    "pdf": "${pdf.output.result.location}"
  }
}
```

**Register and run:**

```shell
curl -X POST 'http://localhost:8080/api/metadata/workflow' \
  -H 'Content-Type: application/json' \
  -d @web_research_agent.json

curl -X POST 'http://localhost:8080/api/workflow/web_research_agent' \
  -H 'Content-Type: application/json' \
  -d '{"topic": "The state of WebAssembly adoption in 2026"}'
```

---

### AI provider configuration

Set environment variables before starting the server. Conductor auto-enables providers when their API key is present.

```bash
# OpenAI (required for most examples)
export OPENAI_API_KEY=sk-your-openai-api-key

# Anthropic (for RAG, extended thinking examples)
export ANTHROPIC_API_KEY=sk-ant-your-anthropic-key

# Google Gemini — API key (simplest)
export GEMINI_API_KEY=your-gemini-api-key
# Or Vertex AI (for enterprise/GCP) — set project and location in application.properties
```

For vector database and other advanced configuration, add to `application.properties`:

```properties
# PostgreSQL Vector DB (for RAG examples)
conductor.vectordb.instances[0].name=postgres-prod
conductor.vectordb.instances[0].type=postgres
conductor.vectordb.instances[0].postgres.datasourceURL=jdbc:postgresql://localhost:5432/vectors
conductor.vectordb.instances[0].postgres.user=conductor
conductor.vectordb.instances[0].postgres.password=secret
conductor.vectordb.instances[0].postgres.dimensions=1536
```

---

## More examples

For additional AI workflow definitions, see the [AI workflow examples on GitHub](https://github.com/conductor-oss/conductor/tree/main/ai/examples).
