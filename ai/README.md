# Conductor AI Module

The Conductor AI module provides built-in integration with 11 popular LLM providers and vector databases, enabling AI-powered workflows through simple task definitions.

## Table of Contents
- [Supported Providers](#supported-providers)
- [AI Task Types](#ai-task-types)
- [Configuration](#configuration)
- [Sample Workflows](#sample-workflows)
- [Enable/Disable AI Workers](#enabledisable-ai-workers)
- [Testing](#testing)

## Supported Providers

### LLM Providers

| Provider | Chat | Embeddings | Image Gen | Audio Gen | Models |
|----------|:----:|:----------:|:---------:|:---------:|--------|
| **OpenAI** | ✅ | ✅ | ✅ | ✅ | GPT-4o, GPT-4o-mini, GPT-4-turbo, GPT-3.5-turbo, DALL-E-3, text-embedding-3-small/large |
| **Anthropic** | ✅ | ❌ | ❌ | ❌ | Claude 3.5 Sonnet, Claude 3 Opus/Sonnet/Haiku, Claude 4 Sonnet |
| **Google Vertex AI** | ✅ | ✅ | ❌ | ❌ | Gemini 1.5 Pro/Flash, Gemini 2.0 Flash, text-embedding-004 |
| **Azure OpenAI** | ✅ | ✅ | ✅ | ❌ | GPT-4o, GPT-4, GPT-3.5-turbo, text-embedding-ada-002, DALL-E-3 |
| **AWS Bedrock** | ✅ | ✅ | ❌ | ❌ | Claude 3.x, Titan, Llama 3.x, amazon.titan-embed-text-v2:0 |
| **Mistral AI** | ✅ | ✅ | ❌ | ❌ | Mistral Small/Medium/Large, Mixtral 8x7B, mistral-embed |
| **Cohere** | ✅ | ✅ | ❌ | ❌ | Command, Command-R, Command-R+, embed-english-v3.0 |
| **Grok** | ✅ | ❌ | ❌ | ❌ | Grok-2, Grok-2-mini |
| **Perplexity AI** | ✅ | ❌ | ❌ | ❌ | Sonar, Sonar Pro |
| **HuggingFace** | ✅ | ❌ | ❌ | ❌ | Llama 3.x, Mistral 7B, Zephyr |
| **Ollama** | ✅ | ✅ | ❌ | ❌ | Llama 3.x, Mistral, Phi, nomic-embed-text (local deployment) |

### Vector Database Providers

| Provider | Storage | Search | Description |
|----------|:-------:|:------:|-------------|
| **PostgreSQL (pgvector)** | ✅ | ✅ | Postgres with vector extension |
| **Pinecone** | ✅ | ✅ | Managed vector database |
| **MongoDB Atlas** | ✅ | ✅ | MongoDB vector search |

## AI Task Types

| Task Type | Task Name | Description | Input | Output |
|-----------|-----------|-------------|-------|--------|
| **Chat Complete** | `LLM_CHAT_COMPLETE` | Multi-turn conversational AI | Messages, model, parameters | Response text, token usage |
| **Text Complete** | `LLM_TEXT_COMPLETE` | Single prompt completion | Prompt, model, parameters | Completion text |
| **Generate Embeddings** | `LLM_GENERATE_EMBEDDINGS` | Convert text to vector embeddings | Text, model | Float array (embeddings) |
| **Image Generation** | `GENERATE_IMAGE` | Generate images from text | Prompt, style, size, model | Image URL/data |
| **Audio Generation** | `GENERATE_AUDIO` | Text-to-speech synthesis | Text, voice, model | Audio URL/data |
| **Index Text** | `LLM_INDEX_TEXT` | Store text with embeddings in vector DB | Text, namespace, index, metadata | Document ID |
| **Store Embeddings** | `LLM_STORE_EMBEDDINGS` | Store pre-computed embeddings | Embeddings, namespace, index | Document ID |
| **Search Index** | `LLM_SEARCH_INDEX` | Semantic search using text | Query text, namespace, index, limit | Matching documents |
| **Search Embeddings** | `LLM_SEARCH_EMBEDDINGS` | Search using embedding vectors | Embeddings, namespace, index, limit | Matching documents |
| **Get Embeddings** | `LLM_GET_EMBEDDINGS` | Retrieve stored embeddings | Document ID, namespace, index | Embeddings array |
| **List MCP Tools** | `LIST_MCP_TOOLS` | List tools from MCP server | MCP server URL (stdio/HTTP/HTTPS) | Tool definitions |
| **Call MCP Tool** | `CALL_MCP_TOOL` | Call a tool on MCP server | MCP server URL, tool name, arguments | Tool result |

## Configuration

### Global Configuration

Add to your `application.properties` or `application.yml`:

```properties
# Enable AI integrations and workers (default: false, must be explicitly enabled)
conductor.integrations.ai.enabled=true

# Payload storage location for large AI inputs/outputs (optional)
conductor.ai.payload-store-location=/tmp/conductor-ai
```

> **Note**: AI workers are disabled by default. You must set `conductor.integrations.ai.enabled=true` to enable them.

### Provider-Specific Configuration

#### OpenAI

```properties
conductor.ai.openai.api-key=${OPENAI_API_KEY}
conductor.ai.openai.base-url=https://api.openai.com/v1
conductor.ai.openai.organization-id=org-xxxxx
```

| Property | Required | Default | Description |
|----------|:--------:|---------|-------------|
| `api-key` | ✅ | - | OpenAI API key |
| `base-url` | ❌ | `https://api.openai.com/v1` | API base URL |
| `organization-id` | ❌ | - | Organization ID |

#### Anthropic

```properties
conductor.ai.anthropic.api-key=${ANTHROPIC_API_KEY}
conductor.ai.anthropic.base-url=https://api.anthropic.com
conductor.ai.anthropic.version=2023-06-01
conductor.ai.anthropic.beta-version=prompt-caching-2024-07-31
```

| Property | Required | Default | Description |
|----------|:--------:|---------|-------------|
| `api-key` | ✅ | - | Anthropic API key |
| `base-url` | ❌ | `https://api.anthropic.com` | API base URL |
| `version` | ❌ | - | API version |
| `beta-version` | ❌ | - | Beta features (e.g., prompt caching) |
| `completions-path` | ❌ | - | Custom completions endpoint path |

#### Google Vertex AI (Gemini)

```properties
conductor.ai.gemini.project-id=${GOOGLE_CLOUD_PROJECT}
conductor.ai.gemini.location=us-central1
conductor.ai.gemini.publisher=google
```

| Property | Required | Default | Description |
|----------|:--------:|---------|-------------|
| `project-id` | ✅ | - | GCP project ID |
| `location` | ✅ | - | GCP region (e.g., us-central1) |
| `base-url` | ❌ | `{location}-aiplatform.googleapis.com:443` | API endpoint |
| `publisher` | ❌ | - | Model publisher |

> **Note**: Vertex AI uses Application Default Credentials (ADC) or service account credentials from the environment.

#### Azure OpenAI

```properties
conductor.ai.azureopenai.api-key=${AZURE_OPENAI_API_KEY}
conductor.ai.azureopenai.base-url=${AZURE_OPENAI_ENDPOINT}
conductor.ai.azureopenai.deployment-name=gpt-4o-mini
conductor.ai.azureopenai.user=your-user-id
```

| Property | Required | Default | Description |
|----------|:--------:|---------|-------------|
| `api-key` | ✅ | - | Azure OpenAI API key |
| `base-url` | ✅ | - | Azure resource endpoint |
| `deployment-name` | ✅ | - | Deployment name |
| `user` | ❌ | - | User identifier for tracking |

#### AWS Bedrock

```properties
conductor.ai.bedrock.access-key=${AWS_ACCESS_KEY_ID}
conductor.ai.bedrock.secret-key=${AWS_SECRET_ACCESS_KEY}
conductor.ai.bedrock.region=us-east-1
# OR use bearer token for AWS SSO/temporary credentials
conductor.ai.bedrock.bearer-token=${AWS_SESSION_TOKEN}
```

| Property | Required | Default | Description |
|----------|:--------:|---------|-------------|
| `access-key` | ✅* | - | AWS access key ID |
| `secret-key` | ✅* | - | AWS secret access key |
| `region` | ✅ | `us-east-1` | AWS region |
| `bearer-token` | ❌ | - | AWS session token (for temporary credentials) |

\* Required unless using bearer token or IAM roles

#### Mistral AI

```properties
conductor.ai.mistral.api-key=${MISTRAL_API_KEY}
conductor.ai.mistral.base-url=https://api.mistral.ai
```

| Property | Required | Default | Description |
|----------|:--------:|---------|-------------|
| `api-key` | ✅ | - | Mistral AI API key |
| `base-url` | ❌ | `https://api.mistral.ai` | API base URL |

#### Cohere

```properties
conductor.ai.cohere.api-key=${COHERE_API_KEY}
conductor.ai.cohere.base-url=https://api.cohere.ai
```

| Property | Required | Default | Description |
|----------|:--------:|---------|-------------|
| `api-key` | ✅ | - | Cohere API key |
| `base-url` | ❌ | `https://api.cohere.ai` | API base URL |

#### Grok (xAI)

```properties
conductor.ai.grok.api-key=${GROK_API_KEY}
conductor.ai.grok.base-url=https://api.x.ai/v1
```

| Property | Required | Default | Description |
|----------|:--------:|---------|-------------|
| `api-key` | ✅ | - | Grok API key |
| `base-url` | ❌ | `https://api.x.ai/v1` | API base URL |

#### Perplexity AI

```properties
conductor.ai.perplexity.api-key=${PERPLEXITY_API_KEY}
conductor.ai.perplexity.base-url=https://api.perplexity.ai
```

| Property | Required | Default | Description |
|----------|:--------:|---------|-------------|
| `api-key` | ✅ | - | Perplexity API key |
| `base-url` | ❌ | `https://api.perplexity.ai` | API base URL |

#### HuggingFace

```properties
conductor.ai.huggingface.api-key=${HUGGINGFACE_API_KEY}
conductor.ai.huggingface.base-url=https://api-inference.huggingface.co/models
```

| Property | Required | Default | Description |
|----------|:--------:|---------|-------------|
| `api-key` | ✅ | - | HuggingFace API token |
| `base-url` | ❌ | `https://api-inference.huggingface.co/models` | API base URL |

#### Ollama (Local)

```properties
conductor.ai.ollama.base-url=http://localhost:11434
conductor.ai.ollama.auth-header-name=Authorization
conductor.ai.ollama.auth-header=Bearer token-here
```

| Property | Required | Default | Description |
|----------|:--------:|---------|-------------|
| `base-url` | ❌ | `http://localhost:11434` | Ollama server URL |
| `auth-header-name` | ❌ | - | Custom auth header name |
| `auth-header` | ❌ | - | Custom auth header value |

## Sample Workflows

### 1. Chat Completion (Conversational AI)

```json
{
  "name": "chat_workflow",
  "version": 1,
  "tasks": [
    {
      "name": "chat_task",
      "taskReferenceName": "chat",
      "type": "LLM_CHAT_COMPLETE",
      "inputParameters": {
        "llmProvider": "openai",
        "model": "gpt-4o-mini",
        "messages": [
          {
            "role": "system",
            "message": "You are a helpful assistant."
          },
          {
            "role": "user",
            "message": "What is the capital of France?"
          }
        ],
        "temperature": 0.7,
        "maxTokens": 500
      }
    }
  ]
}
```

**Output:**
```json
{
  "result": "The capital of France is Paris.",
  "metadata": {
    "usage": {
      "promptTokens": 25,
      "completionTokens": 8,
      "totalTokens": 33
    }
  }
}
```

### 2. Generate Embeddings

```json
{
  "name": "embedding_workflow",
  "version": 1,
  "tasks": [
    {
      "name": "generate_embeddings",
      "taskReferenceName": "embeddings",
      "type": "LLM_GENERATE_EMBEDDINGS",
      "inputParameters": {
        "llmProvider": "openai",
        "model": "text-embedding-3-small",
        "text": "Conductor is an orchestration platform"
      }
    }
  ]
}
```

**Output:**
```json
{
  "result": [0.123, -0.456, 0.789, ...]  // 1536-dimensional vector
}
```

### 3. Image Generation

```json
{
  "name": "image_gen_workflow",
  "version": 1,
  "tasks": [
    {
      "name": "generate_image",
      "taskReferenceName": "image",
      "type": "GENERATE_IMAGE",
      "inputParameters": {
        "llmProvider": "openai",
        "model": "dall-e-3",
        "prompt": "A futuristic cityscape at sunset",
        "width": 1024,
        "height": 1024,
        "n": 1,
        "style": "vivid"
      }
    }
  ]
}
```

**Output:**
```json
{
  "url": "https://...",
  "b64_json": "base64-encoded-image-data"
}
```

### 4. Audio Generation (Text-to-Speech)

```json
{
  "name": "tts_workflow",
  "version": 1,
  "tasks": [
    {
      "name": "generate_audio",
      "taskReferenceName": "audio",
      "type": "GENERATE_AUDIO",
      "inputParameters": {
        "llmProvider": "openai",
        "model": "tts-1",
        "text": "Hello, this is a test of text to speech.",
        "voice": "alloy"
      }
    }
  ]
}
```

**Output:**
```json
{
  "url": "https://...",
  "format": "mp3"
}
```

### 5. Semantic Search with Vector DB

```json
{
  "name": "semantic_search_workflow",
  "version": 1,
  "tasks": [
    {
      "name": "index_documents",
      "taskReferenceName": "index",
      "type": "LLM_INDEX_TEXT",
      "inputParameters": {
        "vectorDB": "pgvectordb",
        "namespace": "documentation",
        "index": "tech_docs",
        "embeddingModelProvider": "openai",
        "embeddingModel": "text-embedding-3-small",
        "text": "Conductor is a workflow orchestration platform",
        "docId": "doc_001"
      }
    },
    {
      "name": "search_documents",
      "taskReferenceName": "search",
      "type": "LLM_SEARCH_INDEX",
      "inputParameters": {
        "vectorDB": "pgvectordb",
        "namespace": "documentation",
        "index": "tech_docs",
        "embeddingModelProvider": "openai",
        "embeddingModel": "text-embedding-3-small",
        "query": "workflow orchestration",
        "llmMaxResults": 5
      }
    }
  ]
}
```

**Output:**
```json
{
  "result": [
    {
      "docId": "doc_001",
      "score": 0.95,
      "text": "Conductor is a workflow orchestration platform"
    }
  ]
}
```

### 6. RAG (Retrieval Augmented Generation)

```json
{
  "name": "rag_workflow",
  "version": 1,
  "tasks": [
    {
      "name": "search_knowledge_base",
      "taskReferenceName": "search",
      "type": "LLM_SEARCH_INDEX",
      "inputParameters": {
        "vectorDB": "pgvectordb",
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
        "model": "claude-3-5-sonnet-20241022",
        "messages": [
          {
            "role": "system",
            "message": "Answer based on the following context: ${search.output.result}"
          },
          {
            "role": "user",
            "message": "${workflow.input.question}"
          }
        ],
        "temperature": 0.3
      }
    }
  ]
}
```

### 7. MCP (Model Context Protocol) Tool Integration

MCP allows workflows to interact with external tools and data sources via HTTP/HTTPS or stdio (local) servers.

#### List Tools from MCP Server

```json
{
  "name": "mcp_list_tools_workflow",
  "version": 1,
  "tasks": [
    {
      "name": "list_mcp_tools",
      "taskReferenceName": "list_tools",
      "type": "LIST_MCP_TOOLS",
      "inputParameters": {
        "mcpServer": "http://localhost:3000"
      }
    }
  ]
}
```

**Output:**
```json
{
  "tools": [
    {
      "name": "get_weather",
      "description": "Get current weather for a location",
      "inputSchema": {
        "type": "object",
        "properties": {
          "location": {"type": "string"}
        },
        "required": ["location"]
      }
    }
  ]
}
```

The Model Context Protocol supports multiple [transport types](https://modelcontextprotocol.io/specification/2025-11-25/basic/transports):
- **Streamable HTTP** (default): Standard HTTP/HTTPS endpoints (recommended per MCP spec 2025-11-25)
- **SSE** (deprecated): Only used when URL explicitly contains `/sse` endpoint
- **stdio**: Local MCP servers running as processes (e.g., `stdio://npx -y @modelcontextprotocol/server-everything`)

#### Call MCP Tool (HTTP Server)

```json
{
  "name": "mcp_weather_workflow",
  "version": 1,
  "tasks": [
    {
      "name": "get_weather",
      "taskReferenceName": "weather",
      "type": "CALL_MCP_TOOL",
      "inputParameters": {
        "mcpServer": "http://localhost:3000",
        "methodName": "get_weather",
        "location": "New York",
        "units": "fahrenheit"
      }
    }
  ]
}
```

**Output:**
```json
{
  "content": [
    {
      "type": "text",
      "text": "Current weather in New York: 72°F, Partly cloudy"
    }
  ],
  "isError": false
}
```

#### Call MCP Tool (stdio/Local Server)

```json
{
  "name": "mcp_local_tools_workflow",
  "version": 1,
  "tasks": [
    {
      "name": "filesystem_operation",
      "taskReferenceName": "fs_op",
      "type": "CALL_MCP_TOOL",
      "inputParameters": {
        "mcpServer": "stdio://npx -y @modelcontextprotocol/server-filesystem /tmp",
        "methodName": "read_file",
        "path": "/tmp/config.json"
      }
    }
  ]
}
```

**MCP Server URL Formats:**
- **HTTP**: `http://localhost:3000` (uses Streamable HTTP transport)
- **HTTP/SSE (deprecated)**: `http://localhost:3000/sse`
- **HTTP/Streamable**: `http://localhost:3000/mcp`
- **HTTPS**: `https://api.example.com/mcp`
- **stdio (local)**: `stdio://npx -y @modelcontextprotocol/server-everything`
- **stdio (Python)**: `stdio://python -m mcp_server.weather`

> **Note**: All input parameters except `mcpServer`, `methodName`, and `headers` are automatically passed as arguments to the MCP tool.

#### MCP + AI Agent Workflow

Complete example combining MCP tools with LLM for autonomous agent behavior:

```json
{
  "name": "mcp_ai_agent_workflow",
  "version": 1,
  "tasks": [
    {
      "name": "list_available_tools",
      "taskReferenceName": "discover_tools",
      "type": "LIST_MCP_TOOLS",
      "inputParameters": {
        "mcpServer": "http://localhost:3000"
      }
    },
    {
      "name": "decide_which_tools_to_use",
      "taskReferenceName": "plan",
      "type": "LLM_CHAT_COMPLETE",
      "inputParameters": {
        "llmProvider": "anthropic",
        "model": "claude-3-5-sonnet-20241022",
        "messages": [
          {
            "role": "system",
            "message": "You are an AI agent. Available tools: ${discover_tools.output.tools}. User wants to: ${workflow.input.task}"
          },
          {
            "role": "user",
            "message": "Which tool should I use and what parameters? Respond with JSON: {methodName: string, arguments: object}"
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
        "mcpServer": "http://localhost:3000",
        "methodName": "${plan.output.result.methodName}",
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
            "message": "Summarize this result for the user: ${execute.output.content}"
          }
        ],
        "maxTokens": 200
      }
    }
  ]
}
```

**Workflow Input:**
```json
{
  "task": "Get the current weather in San Francisco"
}
```

**Workflow Output:**
```json
{
  "discover_tools": {
    "tools": [
      {"name": "get_weather", "description": "..."},
      {"name": "calculate", "description": "..."}
    ]
  },
  "plan": {
    "result": {
      "methodName": "get_weather",
      "arguments": {"location": "San Francisco", "units": "fahrenheit"}
    }
  },
  "execute": {
    "content": [{"type": "text", "text": "72°F, Sunny"}]
  },
  "summarize": {
    "result": "The current weather in San Francisco is 72°F and sunny."
  }
}
```

## Enable/Disable AI Workers

### Global Enable/Disable

AI workers are **disabled by default** for security. Enable them explicitly:

```properties
# Enable all AI workers and integrations
conductor.integrations.ai.enabled=true
```

To disable:

```properties
# Disable all AI workers (or simply omit the property)
conductor.integrations.ai.enabled=false
```

### Conditional Provider Registration

Providers are automatically registered only when their API keys are configured. To disable a specific provider, simply remove or comment out its configuration:

```properties
# OpenAI will be registered
conductor.ai.openai.api-key=sk-xxx

# Anthropic will NOT be registered (commented out)
# conductor.ai.anthropic.api-key=sk-ant-xxx
```

### Environment-Based Configuration

Use environment variables to control which providers are enabled in different environments:

```bash
# Development - use local Ollama
export OLLAMA_BASE_URL=http://localhost:11434
./gradlew bootRun

# Production - use OpenAI and Anthropic
export OPENAI_API_KEY=sk-xxx
export ANTHROPIC_API_KEY=sk-ant-xxx
./gradlew bootRun
```

## Testing

### Integration Tests

The module includes integration tests that run against real APIs when credentials are provided via environment variables:

```bash
# Run all tests (integration tests skipped if no API keys)
./gradlew :conductor-ai:test

# Run with real OpenAI API
export OPENAI_API_KEY=sk-xxx
./gradlew :conductor-ai:test

# Run without integration tests
env -u OPENAI_API_KEY -u ANTHROPIC_API_KEY ./gradlew :conductor-ai:test
```

### Test Environment Variables

| Provider | Environment Variable |
|----------|---------------------|
| OpenAI | `OPENAI_API_KEY` |
| Anthropic | `ANTHROPIC_API_KEY` |
| Mistral | `MISTRAL_API_KEY` |
| Grok | `GROK_API_KEY` |
| Cohere | `COHERE_API_KEY` |
| HuggingFace | `HUGGINGFACE_API_KEY` |
| Perplexity | `PERPLEXITY_API_KEY` |
| Ollama | `OLLAMA_BASE_URL` |
| AWS Bedrock | `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` |
| Azure OpenAI | `AZURE_OPENAI_API_KEY`, `AZURE_OPENAI_ENDPOINT` |
| Gemini Vertex | `GOOGLE_CLOUD_PROJECT` |

## License

Copyright 2025 Conductor Authors. Licensed under the Apache License 2.0.
