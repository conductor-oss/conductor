# Conductor AI Workflow Examples

This folder contains ready-to-use workflow examples demonstrating the AI capabilities of Conductor.

## Prerequisites

### 1. Start Conductor Server

Ensure Conductor is running with AI integrations enabled:

```bash
# From the conductor root directory
./gradlew bootRun
```

### 2. Configure AI Providers

Add the following to your conductor server configuration file:

```properties
# Enable AI integrations
conductor.integrations.ai.enabled=true

# OpenAI (required for most examples)
conductor.ai.openai.apiKey=sk-your-openai-api-key

# Anthropic (optional, for RAG examples)
conductor.ai.anthropic.apiKey=sk-ant-your-anthropic-key

# Google Vertex AI (optional, for Gemini/Veo video examples)
conductor.ai.gemini.project-id=your-gcp-project
conductor.ai.gemini.location=us-central1

# PostgreSQL Vector DB (for RAG/embedding examples)
conductor.vectordb.instances[0].name=postgres-prod
conductor.vectordb.instances[0].type=postgres
conductor.vectordb.instances[0].postgres.datasourceURL=jdbc:postgresql://localhost:5432/vectors
conductor.vectordb.instances[0].postgres.user=conductor
conductor.vectordb.instances[0].postgres.password=secret
conductor.vectordb.instances[0].postgres.dimensions=1536
```

### 3. MCP Weather Server (for MCP examples)

Install and start the MCP weather server:

```bash
# Install the MCP weather server
pip install mcp-server-fetch

# Start the server in streamable HTTP mode
python3 -m mcp_server_fetch --mode streamable-http --host localhost --port 3001 --stateless
```

The server will be available at `http://localhost:3001/mcp`.

---

## Available Examples

| File | Description | Requirements |
|------|-------------|--------------|
| `01-chat-completion.json` | Basic chat with GPT-4o-mini | OpenAI |
| `02-generate-embeddings.json` | Generate text embeddings | OpenAI |
| `03-image-generation.json` | Generate images with DALL-E 3 | OpenAI |
| `04-audio-generation.json` | Text-to-speech with OpenAI TTS | OpenAI |
| `05-semantic-search.json` | Index and search documents | OpenAI, PostgreSQL |
| `06-rag-basic.json` | Basic RAG with search + answer | OpenAI/Anthropic, PostgreSQL |
| `07-rag-complete.json` | Full RAG demo (index + search + answer) | OpenAI, PostgreSQL |
| `08-mcp-list-tools.json` | List tools from MCP server | MCP Server |
| `09-mcp-call-tool.json` | Call MCP tool (weather) | MCP Server |
| `10-mcp-ai-agent.json` | AI agent with MCP tools | OpenAI/Anthropic, MCP Server |
| `11-video-openai-sora.json` | Generate video with OpenAI Sora-2 (async) | OpenAI |
| `12-video-gemini-veo.json` | Generate video with Google Veo-3 (async) | Google Vertex AI |
| `13-image-to-video-pipeline.json` | Image + video generation pipeline | OpenAI |
| `14-stabilityai-image.json` | Image generation with Stability AI (SD3.5) | Stability AI |

---

## Quick Start

### Step 1: Register a Workflow

```bash
# Register the chat completion workflow
curl -X POST 'http://localhost:8080/api/metadata/workflow' \
  -H 'Content-Type: application/json' \
  -d @01-chat-completion.json
```

### Step 2: Execute the Workflow

```bash
# Run the workflow (no input needed for hardcoded examples)
curl -X POST 'http://localhost:8080/api/workflow/chat_workflow' \
  -H 'Content-Type: application/json' \
  -d '{}'
```

### Step 3: Check the Result

```bash
# Get workflow execution status (replace {workflowId} with the returned ID)
curl -X GET 'http://localhost:8080/api/workflow/{workflowId}'
```

---

## Example Commands

### 1. Chat Completion

```bash
# Register
curl -X POST 'http://localhost:8080/api/metadata/workflow' \
  -H 'Content-Type: application/json' \
  -d @01-chat-completion.json

# Execute
curl -X POST 'http://localhost:8080/api/workflow/chat_workflow' \
  -H 'Content-Type: application/json' \
  -d '{}'
```

### 2. Generate Embeddings

```bash
# Register
curl -X POST 'http://localhost:8080/api/metadata/workflow' \
  -H 'Content-Type: application/json' \
  -d @02-generate-embeddings.json

# Execute
curl -X POST 'http://localhost:8080/api/workflow/embedding_workflow' \
  -H 'Content-Type: application/json' \
  -d '{}'
```

### 3. Image Generation

```bash
# Register
curl -X POST 'http://localhost:8080/api/metadata/workflow' \
  -H 'Content-Type: application/json' \
  -d @03-image-generation.json

# Execute
curl -X POST 'http://localhost:8080/api/workflow/image_gen_workflow' \
  -H 'Content-Type: application/json' \
  -d '{}'
```

### 4. Audio Generation (TTS)

```bash
# Register
curl -X POST 'http://localhost:8080/api/metadata/workflow' \
  -H 'Content-Type: application/json' \
  -d @04-audio-generation.json

# Execute
curl -X POST 'http://localhost:8080/api/workflow/tts_workflow' \
  -H 'Content-Type: application/json' \
  -d '{}'
```

### 5. Semantic Search

```bash
# Register
curl -X POST 'http://localhost:8080/api/metadata/workflow' \
  -H 'Content-Type: application/json' \
  -d @05-semantic-search.json

# Execute
curl -X POST 'http://localhost:8080/api/workflow/semantic_search_workflow' \
  -H 'Content-Type: application/json' \
  -d '{}'
```

### 6. RAG (Basic)

```bash
# Register
curl -X POST 'http://localhost:8080/api/metadata/workflow' \
  -H 'Content-Type: application/json' \
  -d @06-rag-basic.json

# Execute with a question
curl -X POST 'http://localhost:8080/api/workflow/rag_workflow' \
  -H 'Content-Type: application/json' \
  -d '{"question": "What is Conductor?"}'
```

### 7. RAG (Complete Demo)

```bash
# Register
curl -X POST 'http://localhost:8080/api/metadata/workflow' \
  -H 'Content-Type: application/json' \
  -d @07-rag-complete.json

# Execute (no input needed - fully self-contained)
curl -X POST 'http://localhost:8080/api/workflow/complete_rag_demo' \
  -H 'Content-Type: application/json' \
  -d '{}'
```

### 8. MCP List Tools

```bash
# Start MCP server first (see Prerequisites)

# Register
curl -X POST 'http://localhost:8080/api/metadata/workflow' \
  -H 'Content-Type: application/json' \
  -d @08-mcp-list-tools.json

# Execute
curl -X POST 'http://localhost:8080/api/workflow/mcp_list_tools_workflow' \
  -H 'Content-Type: application/json' \
  -d '{}'
```

### 9. MCP Call Tool (Weather)

```bash
# Start MCP server first (see Prerequisites)

# Register
curl -X POST 'http://localhost:8080/api/metadata/workflow' \
  -H 'Content-Type: application/json' \
  -d @09-mcp-call-tool.json

# Execute
curl -X POST 'http://localhost:8080/api/workflow/mcp_weather_workflow' \
  -H 'Content-Type: application/json' \
  -d '{}'
```

### 10. MCP AI Agent

```bash
# Start MCP server first (see Prerequisites)

# Register
curl -X POST 'http://localhost:8080/api/metadata/workflow' \
  -H 'Content-Type: application/json' \
  -d @10-mcp-ai-agent.json

# Execute with a task
curl -X POST 'http://localhost:8080/api/workflow/mcp_ai_agent_workflow' \
  -H 'Content-Type: application/json' \
  -d '{"task": "Get the current weather in San Francisco"}'
```

### 11. Video Generation (OpenAI Sora)

```bash
# Register
curl -X POST 'http://localhost:8080/api/metadata/workflow' \
  -H 'Content-Type: application/json' \
  -d @11-video-openai-sora.json

# Execute (async -- returns workflowId immediately, polls internally until video is ready)
curl -X POST 'http://localhost:8080/api/workflow/video_gen_openai_sora' \
  -H 'Content-Type: application/json' \
  -d '{}'
```

### 12. Video Generation (Google Gemini Veo)

```bash
# Requires Google Vertex AI credentials (see Prerequisites)

# Register
curl -X POST 'http://localhost:8080/api/metadata/workflow' \
  -H 'Content-Type: application/json' \
  -d @12-video-gemini-veo.json

# Execute
curl -X POST 'http://localhost:8080/api/workflow/video_gen_gemini_veo' \
  -H 'Content-Type: application/json' \
  -d '{}'
```

### 13. Image-to-Video Pipeline

```bash
# Register
curl -X POST 'http://localhost:8080/api/metadata/workflow' \
  -H 'Content-Type: application/json' \
  -d @13-image-to-video-pipeline.json

# Execute (generates a DALL-E image first, then a Sora video)
curl -X POST 'http://localhost:8080/api/workflow/image_to_video_pipeline' \
  -H 'Content-Type: application/json' \
  -d '{}'
```

### 14. Image Generation (Stability AI)

```bash
# Requires STABILITY_API_KEY environment variable

# Register
curl -X POST 'http://localhost:8080/api/metadata/workflow' \
  -H 'Content-Type: application/json' \
  -d @14-stabilityai-image.json

# Execute
curl -X POST 'http://localhost:8080/api/workflow/image_gen_stabilityai' \
  -H 'Content-Type: application/json' \
  -d '{}'
```

---

## Register All Workflows at Once

```bash
# Register all example workflows
for f in *.json; do
  echo "Registering $f..."
  curl -s -X POST 'http://localhost:8080/api/metadata/workflow' \
    -H 'Content-Type: application/json' \
    -d @"$f"
  echo ""
done
```

---

## Troubleshooting

### "VectorDB not found: postgres-prod"

Ensure you have configured the PostgreSQL vector database in your `application.properties`:

```properties
conductor.vectordb.instances[0].name=postgres-prod
conductor.vectordb.instances[0].type=postgres
conductor.vectordb.instances[0].postgres.datasourceURL=jdbc:postgresql://localhost:5432/vectors
conductor.vectordb.instances[0].postgres.user=conductor
conductor.vectordb.instances[0].postgres.password=secret
conductor.vectordb.instances[0].postgres.dimensions=1536
```

### "No configuration found for: openai"

Ensure you have set the OpenAI API key:

```properties
conductor.ai.openai.apiKey=sk-your-openai-api-key
```

### MCP Server Connection Refused

1. Verify the MCP server is running:
   ```bash
   curl http://localhost:3001/mcp
   ```

2. Check the server logs for errors

3. Ensure you're using the correct port in the workflow (default: 3001)

### PostgreSQL Vector Extension Not Found

Ensure the `pgvector` extension is installed in your PostgreSQL database:

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

---

## License

Copyright 2025 Conductor Authors. Licensed under the Apache License 2.0.
