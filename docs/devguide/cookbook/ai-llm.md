---
description: "LLM orchestration cookbook — AI agent orchestration recipes for chat completion, RAG pipelines with vector databases, MCP agents with function calling and tool use, image generation, LLM-to-PDF, and provider configuration."
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

### AI provider configuration

Configure LLM providers and vector databases in `application.properties` to use the AI recipes above.

```properties
# Enable AI integrations
conductor.integrations.ai.enabled=true

# OpenAI
conductor.ai.openai.apiKey=sk-your-openai-api-key

# Anthropic
conductor.ai.anthropic.apiKey=sk-ant-your-anthropic-key

# Google Vertex AI (Gemini)
conductor.ai.gemini.project-id=your-gcp-project
conductor.ai.gemini.location=us-central1

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
