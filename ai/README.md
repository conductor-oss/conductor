# Conductor AI Module

The Conductor AI module provides built-in integration with 12 popular LLM providers and vector databases, enabling AI-powered workflows through simple task definitions -- including chat, embeddings, image generation, audio synthesis, video generation, and tool calling.

## Table of Contents
- [Supported Providers](#supported-providers)
- [AI Task Types](#ai-task-types)
- [Configuration](#configuration)
- [Environment Variables](#environment-variables)
- [Docker](#docker)
- [Sample Workflows](#sample-workflows)
- [Enable/Disable AI Workers](#enabledisable-ai-workers)
- [Testing](#testing)

## Supported Providers

### LLM Providers

| Provider | Chat | Embeddings | Image Gen | Audio Gen | Video Gen | Models |
|----------|:----:|:----------:|:---------:|:---------:|:---------:|--------|
| **OpenAI** | ✅ | ✅ | ✅ | ✅ | ✅ | GPT-4o, GPT-4o-mini, DALL-E-3, Sora-2, text-embedding-3-small/large |
| **Anthropic** | ✅ | ❌ | ❌ | ❌ | ❌ | Claude 3.5 Sonnet, Claude 3 Opus/Sonnet/Haiku, Claude 4 Sonnet |
| **Google Vertex AI** | ✅ | ✅ | ✅ | ❌ | ✅ | Gemini 1.5/2.0, Veo 2/3, Imagen, text-embedding-004 |
| **Azure OpenAI** | ✅ | ✅ | ✅ | ❌ | ❌ | GPT-4o, GPT-4, GPT-3.5-turbo, text-embedding-ada-002, DALL-E-3 |
| **AWS Bedrock** | ✅ | ✅ | ❌ | ❌ | ❌ | Claude 3.x, Titan, Llama 3.x, amazon.titan-embed-text-v2:0 |
| **Mistral AI** | ✅ | ✅ | ❌ | ❌ | ❌ | Mistral Small/Medium/Large, Mixtral 8x7B, mistral-embed |
| **Cohere** | ✅ | ✅ | ❌ | ❌ | ❌ | Command, Command-R, Command-R+, embed-english-v3.0 |
| **Grok** | ✅ | ❌ | ❌ | ❌ | ❌ | Grok-3, Grok-3-mini |
| **Perplexity AI** | ✅ | ❌ | ❌ | ❌ | ❌ | Sonar, Sonar Pro |
| **HuggingFace** | ✅ | ❌ | ❌ | ❌ | ❌ | Llama 3.x, Mistral 7B, Zephyr |
| **Ollama** | ✅ | ✅ | ❌ | ❌ | ❌ | Llama 3.x, Mistral, Phi, nomic-embed-text (local deployment) |
| **Stability AI** | ❌ | ❌ | ✅ | ❌ | ❌ | SD3.5 Large/Medium, Stable Image Core, Stable Image Ultra |

### Vector Database Providers

| Provider | Storage | Search | Description |
|----------|:-------:|:------:|-------------|
| **PostgreSQL (pgvector)** | ✅ | ✅ | Postgres with vector extension |
| **Pinecone** | ✅ | ✅ | Managed vector database |
| **MongoDB Atlas** | ✅ | ✅ | MongoDB vector search |

> **Note**: Multiple named instances of these providers can be configured. See [Vector Database Configuration](VECTORDB_CONFIGURATION.md) for details.

## AI Task Types

### Overview

| Task Type | Task Name | Description |
|-----------|-----------|-------------|
| **Chat Complete** | `LLM_CHAT_COMPLETE` | Multi-turn conversational AI with optional tool calling |
| **Text Complete** | `LLM_TEXT_COMPLETE` | Single prompt completion |
| **Generate Embeddings** | `LLM_GENERATE_EMBEDDINGS` | Convert text to vector embeddings |
| **Image Generation** | `GENERATE_IMAGE` | Generate images from text prompts |
| **Audio Generation** | `GENERATE_AUDIO` | Text-to-speech synthesis |
| **Video Generation** | `GENERATE_VIDEO` | Generate videos from text/image prompts (async) |
| **Index Text** | `LLM_INDEX_TEXT` | Store text with embeddings in vector DB |
| **Store Embeddings** | `LLM_STORE_EMBEDDINGS` | Store pre-computed embeddings |
| **Search Index** | `LLM_SEARCH_INDEX` | Semantic search using text query |
| **Search Embeddings** | `LLM_SEARCH_EMBEDDINGS` | Search using embedding vectors |
| **Get Embeddings** | `LLM_GET_EMBEDDINGS` | Retrieve stored embeddings |
| **List MCP Tools** | `LIST_MCP_TOOLS` | List tools from MCP server |
| **Call MCP Tool** | `CALL_MCP_TOOL` | Call a tool on MCP server |

---

### LLM_CHAT_COMPLETE

Multi-turn conversational AI with support for tool calling.

**Inputs:**

| Parameter | Type | Required | Description |
|-----------|------|:--------:|-------------|
| `llmProvider` | String | ✅ | Provider name (e.g., `openai`, `anthropic`, `gemini`) |
| `model` | String | ✅ | Model identifier (e.g., `gpt-4o`, `claude-3-5-sonnet-20241022`) |
| `messages` | Array | ✅ | Conversation messages with `role` and `message` fields |
| `temperature` | Number | ❌ | Sampling temperature (0.0-2.0, default: 1.0) |
| `maxTokens` | Integer | ❌ | Maximum tokens in response |
| `topP` | Number | ❌ | Nucleus sampling parameter |
| `stopSequences` | Array | ❌ | Sequences that stop generation |
| `tools` | Array | ❌ | Tool definitions for function calling |

**Outputs:**

| Field | Type | Description |
|-------|------|-------------|
| `result` | String | Generated response text |
| `finishReason` | String | Why generation stopped (`STOP`, `TOOL_CALLS`, `LENGTH`) |
| `tokenUsed` | Integer | Total tokens used |
| `promptTokens` | Integer | Tokens in the prompt |
| `completionTokens` | Integer | Tokens in the response |
| `toolCalls` | Array | Tool invocations (when `finishReason` is `TOOL_CALLS`) |

---

### LLM_TEXT_COMPLETE

Single prompt text completion.

**Inputs:**

| Parameter | Type | Required | Description |
|-----------|------|:--------:|-------------|
| `llmProvider` | String | ✅ | Provider name |
| `model` | String | ✅ | Model identifier |
| `prompt` | String | ✅ | Text prompt to complete |
| `temperature` | Number | ❌ | Sampling temperature |
| `maxTokens` | Integer | ❌ | Maximum tokens in response |

**Outputs:**

| Field | Type | Description |
|-------|------|-------------|
| `result` | String | Generated completion text |
| `tokenUsed` | Integer | Total tokens used |

---

### LLM_GENERATE_EMBEDDINGS

Convert text to vector embeddings for semantic search.

**Inputs:**

| Parameter | Type | Required | Description |
|-----------|------|:--------:|-------------|
| `llmProvider` | String | ✅ | Provider name |
| `model` | String | ✅ | Embedding model (e.g., `text-embedding-3-small`) |
| `text` | String | ✅ | Text to embed |

**Outputs:**

| Field | Type | Description |
|-------|------|-------------|
| `result` | Array\<Number\> | Vector embedding (e.g., 1536 dimensions for OpenAI) |

---

### GENERATE_IMAGE

Generate images from text prompts.

**Inputs:**

| Parameter | Type | Required | Description |
|-----------|------|:--------:|-------------|
| `llmProvider` | String | ✅ | Provider name (e.g., `openai`) |
| `model` | String | ✅ | Image model (e.g., `dall-e-3`) |
| `prompt` | String | ✅ | Image description |
| `width` | Integer | ❌ | Image width in pixels |
| `height` | Integer | ❌ | Image height in pixels |
| `n` | Integer | ❌ | Number of images to generate |
| `style` | String | ❌ | Style preset (e.g., `vivid`, `natural`) |

**Outputs:**

| Field | Type | Description |
|-------|------|-------------|
| `url` | String | URL to generated image |
| `b64_json` | String | Base64-encoded image data (if requested) |

---

### GENERATE_AUDIO

Text-to-speech synthesis.

**Inputs:**

| Parameter | Type | Required | Description |
|-----------|------|:--------:|-------------|
| `llmProvider` | String | ✅ | Provider name |
| `model` | String | ✅ | TTS model (e.g., `tts-1`, `tts-1-hd`) |
| `text` | String | ✅ | Text to convert to speech |
| `voice` | String | ❌ | Voice selection (e.g., `alloy`, `echo`, `nova`) |

**Outputs:**

| Field | Type | Description |
|-------|------|-------------|
| `media` | Array | Media items with `location` (URL/path) and `mimeType` |

---

### GENERATE_VIDEO

Generate videos from text or image prompts. This is an **async task** -- it submits a generation job and polls for completion automatically.

**Supported Providers:** OpenAI (Sora-2), Google Vertex AI (Veo 2/3)

**Inputs:**

| Parameter | Type | Required | Description |
|-----------|------|:--------:|-------------|
| `llmProvider` | String | Yes | Provider name (`openai` or `vertex_ai`) |
| `model` | String | Yes | Video model (e.g., `sora-2`, `veo-3`) |
| `prompt` | String | Yes | Text description of the video to generate |
| `duration` | Integer | No | Duration in seconds (OpenAI: 4, 8, or 12; default: 5) |
| `size` | String | No | Video dimensions, e.g., `1280x720` (OpenAI) |
| `aspectRatio` | String | No | Aspect ratio, e.g., `16:9`, `9:16` (Gemini) |
| `resolution` | String | No | Resolution preset: `720p`, `1080p` (Gemini) |
| `style` | String | No | Style preset (e.g., `cinematic`) |
| `n` | Integer | No | Number of videos to generate (default: 1) |
| `inputImage` | String | No | URL or base64 image for image-to-video generation |
| `negativePrompt` | String | No | What to exclude from the video (Gemini) |
| `personGeneration` | String | No | Person policy: `dont_allow`, `allow_adult` (Gemini) |
| `generateAudio` | Boolean | No | Generate audio with video (Gemini Veo 3+) |
| `seed` | Integer | No | Seed for reproducibility |
| `maxDurationSeconds` | Integer | No | Hard limit on video duration |
| `maxCostDollars` | Float | No | Estimated cost limit |

**Outputs:**

| Field | Type | Description |
|-------|------|-------------|
| `media` | Array | Generated media items (video MP4 + optional thumbnail) |
| `media[].location` | String | HTTP URL to the stored video or thumbnail file |
| `media[].mimeType` | String | MIME type (`video/mp4` for video, `image/webp` for thumbnail) |
| `jobId` | String | Provider's async job ID |
| `status` | String | Final status (`COMPLETED` or `FAILED`) |
| `pollCount` | Integer | Number of polling iterations |

**Provider-Specific Notes:**

- **OpenAI Sora**: Supports `sora-2` and `sora-2-pro` models. Valid durations are 4, 8, or 12 seconds. Valid sizes: `1280x720`, `720x1280`, `1792x1024`, `1024x1792`. Returns video + webp thumbnail.
- **Google Vertex AI Veo**: Supports `veo-2.0-generate-001`, `veo-3.0`, `veo-3.1`. Requires Vertex AI credentials (Application Default Credentials). Veo 3+ supports audio generation.

---

### LLM_INDEX_TEXT

Store text with auto-generated embeddings in a vector database.

**Inputs:**

| Parameter | Type | Required | Description |
|-----------|------|:--------:|-------------|
| `vectorDB` | String | ✅ | Configured vector database instance name |
| `namespace` | String | ✅ | Namespace for organization |
| `index` | String | ✅ | Index name |
| `embeddingModelProvider` | String | ✅ | Provider for embeddings |
| `embeddingModel` | String | ✅ | Embedding model name |
| `text` | String | ✅ | Text to index |
| `docId` | String | ❌ | Document identifier (auto-generated if not provided) |
| `metadata` | Object | ❌ | Additional metadata to store |

---

### LLM_STORE_EMBEDDINGS

Store pre-computed embeddings in a vector database.

**Inputs:**

| Parameter | Type | Required | Description |
|-----------|------|:--------:|-------------|
| `vectorDB` | String | ✅ | Configured vector database instance name |
| `namespace` | String | ✅ | Namespace for organization |
| `index` | String | ✅ | Index name |
| `embeddings` | Array\<Number\> | ✅ | Pre-computed embedding vector |
| `docId` | String | ❌ | Document identifier |
| `metadata` | Object | ❌ | Additional metadata |

---

### LLM_SEARCH_INDEX

Semantic search using a text query (auto-generates embeddings).

**Inputs:**

| Parameter | Type | Required | Description |
|-----------|------|:--------:|-------------|
| `vectorDB` | String | ✅ | Configured vector database instance name |
| `namespace` | String | ✅ | Namespace to search |
| `index` | String | ✅ | Index name |
| `embeddingModelProvider` | String | ✅ | Provider for query embedding |
| `embeddingModel` | String | ✅ | Embedding model name |
| `query` | String | ✅ | Search query text |
| `llmMaxResults` | Integer | ❌ | Maximum results to return (default: 10) |

---

### LLM_SEARCH_EMBEDDINGS

Search using pre-computed embedding vectors.

**Inputs:**

| Parameter | Type | Required | Description |
|-----------|------|:--------:|-------------|
| `vectorDB` | String | ✅ | Configured vector database instance name |
| `namespace` | String | ✅ | Namespace to search |
| `index` | String | ✅ | Index name |
| `embeddings` | Array\<Number\> | ✅ | Query embedding vector |
| `llmMaxResults` | Integer | ❌ | Maximum results to return |

---

### LLM_GET_EMBEDDINGS

Retrieve stored embeddings by document ID.

**Inputs:**

| Parameter | Type | Required | Description |
|-----------|------|:--------:|-------------|
| `vectorDB` | String | ✅ | Configured vector database instance name |
| `namespace` | String | ✅ | Namespace |
| `index` | String | ✅ | Index name |
| `docId` | String | ✅ | Document identifier |

**Outputs:**

| Field | Type | Description |
|-------|------|-------------|
| `result` | Array\<Number\> | Stored embedding vector |

---

### LIST_MCP_TOOLS

List available tools from an MCP (Model Context Protocol) server.

**Inputs:**

| Parameter | Type | Required | Description |
|-----------|------|:--------:|-------------|
| `mcpServer` | String | ✅ | MCP server URL (e.g., `http://localhost:3000/mcp`) |
| `headers` | Object | ❌ | HTTP headers for authentication |

**Outputs:**

| Field | Type | Description |
|-------|------|-------------|
| `tools` | Array | Tool definitions with `name`, `description`, and `inputSchema` |

---

### CALL_MCP_TOOL

Call a specific tool on an MCP server.

**Inputs:**

| Parameter | Type | Required | Description |
|-----------|------|:--------:|-------------|
| `mcpServer` | String | ✅ | MCP server URL |
| `method` | String | ✅ | Tool name to call |
| `headers` | Object | ❌ | HTTP headers for authentication |
| `*` | Any | ❌ | All other parameters passed as tool arguments |

**Outputs:**

| Field | Type | Description |
|-------|------|-------------|
| `content` | Array | Result content items with `type` and `text` |
| `isError` | Boolean | Whether the call resulted in an error |


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

### Vector Database Configuration

Vector databases support multiple named instances. For detailed configuration options and examples, see [Vector Database Configuration](VECTORDB_CONFIGURATION.md).

### Provider-Specific Configuration (LLM)

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

#### Stability AI

```properties
conductor.ai.stabilityai.api-key=${STABILITY_API_KEY}
```

| Property | Required | Default | Description |
|----------|:--------:|---------|-------------|
| `api-key` | Yes | - | Stability AI API key |

Supported models: `sd3.5-large`, `sd3.5-large-turbo`, `sd3.5-medium`, `sd3-large`, `sd3-medium`, `core` (Stable Image Core), `ultra` (Stable Image Ultra). The endpoint is selected automatically based on the model name.

## Environment Variables

The AI module reads from standard environment variables automatically. Set the environment variable for a provider and it will be enabled -- no need to edit properties files.

### Quick Reference

| Provider | Environment Variable | Description |
|----------|---------------------|-------------|
| OpenAI | `OPENAI_API_KEY` | API key from [platform.openai.com](https://platform.openai.com/api-keys) |
| OpenAI | `OPENAI_ORG_ID` | Optional organization ID |
| Anthropic | `ANTHROPIC_API_KEY` | API key from [console.anthropic.com](https://console.anthropic.com/) |
| Mistral AI | `MISTRAL_API_KEY` | API key from [console.mistral.ai](https://console.mistral.ai/) |
| Cohere | `COHERE_API_KEY` | API key from [dashboard.cohere.com](https://dashboard.cohere.com/) |
| Grok / xAI | `XAI_API_KEY` | API key from [x.ai](https://x.ai/) |
| Perplexity | `PERPLEXITY_API_KEY` | API key from [perplexity.ai](https://www.perplexity.ai/) |
| HuggingFace | `HUGGINGFACE_API_KEY` | Token from [huggingface.co/settings/tokens](https://huggingface.co/settings/tokens) |
| Stability AI | `STABILITY_API_KEY` | API key from [platform.stability.ai](https://platform.stability.ai/) |
| Azure OpenAI | `AZURE_OPENAI_API_KEY` | API key from Azure portal |
| Azure OpenAI | `AZURE_OPENAI_ENDPOINT` | Endpoint URL (e.g., `https://your-resource.openai.azure.com`) |
| Azure OpenAI | `AZURE_OPENAI_DEPLOYMENT` | Deployment name |
| AWS Bedrock | `AWS_ACCESS_KEY_ID` | AWS access key |
| AWS Bedrock | `AWS_SECRET_ACCESS_KEY` | AWS secret key |
| AWS Bedrock | `AWS_REGION` | AWS region (default: `us-east-1`) |
| Google Vertex AI | `GOOGLE_CLOUD_PROJECT` | GCP project ID |
| Google Vertex AI | `GOOGLE_CLOUD_LOCATION` | GCP region (default: `us-central1`) |
| Google Vertex AI | `GOOGLE_APPLICATION_CREDENTIALS` | Path to service account JSON file |
| Ollama | `OLLAMA_HOST` | Ollama server URL (default: `http://localhost:11434`) |

### Usage

**Linux/macOS:**

```bash
export OPENAI_API_KEY=sk-your-api-key
export ANTHROPIC_API_KEY=sk-ant-your-api-key
./gradlew bootRun
```

**Windows (PowerShell):**

```powershell
$env:OPENAI_API_KEY = "sk-your-api-key"
$env:ANTHROPIC_API_KEY = "sk-ant-your-api-key"
./gradlew bootRun
```

> **Note**: Explicit property values in `application.properties` or external configuration files (e.g., `conductor.properties`) take precedence over environment variables.

## Docker

### Docker Run

Pass environment variables using `-e` flags:

```bash
docker run -d \
  -p 8080:8080 \
  -e OPENAI_API_KEY=sk-your-api-key \
  -e ANTHROPIC_API_KEY=sk-ant-your-api-key \
  conductor:server
```

### Docker Compose

Create a `docker-compose.yml`:

```yaml
version: '3.8'
services:
  conductor:
    image: conductor:server
    ports:
      - "8080:8080"
    environment:
      - OPENAI_API_KEY=${OPENAI_API_KEY}
      - ANTHROPIC_API_KEY=${ANTHROPIC_API_KEY}
      - MISTRAL_API_KEY=${MISTRAL_API_KEY}
      # Add other providers as needed
```

Create a `.env` file in the same directory:

```bash
OPENAI_API_KEY=sk-your-api-key
ANTHROPIC_API_KEY=sk-ant-your-api-key
MISTRAL_API_KEY=your-mistral-key
```

Run with:

```bash
docker-compose up -d
```

### Google Vertex AI with Docker

Google Vertex AI requires a service account credentials file:

```bash
docker run -d \
  -p 8080:8080 \
  -e GOOGLE_CLOUD_PROJECT=your-project-id \
  -e GOOGLE_APPLICATION_CREDENTIALS=/app/config/credentials.json \
  -v /path/to/credentials.json:/app/config/credentials.json:ro \
  conductor:server
```

When running on GKE with Workload Identity, credentials are provided automatically by the platform.

### AWS Bedrock with Docker

Using environment variables:

```bash
docker run -d \
  -p 8080:8080 \
  -e AWS_ACCESS_KEY_ID=your-access-key \
  -e AWS_SECRET_ACCESS_KEY=your-secret-key \
  -e AWS_REGION=us-east-1 \
  conductor:server
```

Or mount your AWS credentials directory:

```bash
docker run -d \
  -p 8080:8080 \
  -v ~/.aws:/root/.aws:ro \
  conductor:server
```

## Sample Workflows

### 1. Chat Completion (Conversational AI)

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
  "schemaVersion": 2,
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
  "schemaVersion": 2,
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
  "schemaVersion": 2,
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
  "schemaVersion": 2,
  "tasks": [
    {
      "name": "index_documents",
      "taskReferenceName": "index",
      "type": "LLM_INDEX_TEXT",
      "inputParameters": {
        "vectorDB": "postgres-prod",
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
        "vectorDB": "postgres-prod",
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

A basic RAG workflow that searches a knowledge base and generates an answer:

```json
{
  "name": "rag_workflow",
  "version": 1,
  "schemaVersion": 2,
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

#### Complete RAG Demo (Index + Search + Answer)

A self-contained workflow that indexes documents, searches them, and generates an answer:

```json
{
  "name": "complete_rag_demo",
  "description": "Index documents, search, and generate RAG answer",
  "version": 1,
  "schemaVersion": 2,
  "tasks": [
    {
      "name": "index_doc_1",
      "taskReferenceName": "index_doc_1_ref",
      "type": "LLM_INDEX_TEXT",
      "inputParameters": {
        "vectorDB": "postgres-prod",
        "index": "demo_index",
        "namespace": "demo_docs",
        "docId": "intro-001",
        "text": "Conductor is a distributed workflow orchestration engine that runs in the cloud. It allows developers to build complex stateful applications by orchestrating microservices.",
        "embeddingModelProvider": "openai",
        "embeddingModel": "text-embedding-3-small",
        "dimensions": 1536,
        "metadata": { "category": "introduction" }
      }
    },
    {
      "name": "index_doc_2",
      "taskReferenceName": "index_doc_2_ref",
      "type": "LLM_INDEX_TEXT",
      "inputParameters": {
        "vectorDB": "postgres-prod",
        "index": "demo_index",
        "namespace": "demo_docs",
        "docId": "features-002",
        "text": "Conductor supports multiple vector databases including PostgreSQL (pgvector), MongoDB Atlas, and Pinecone. It also integrates with LLM providers like OpenAI, Anthropic, and Azure OpenAI.",
        "embeddingModelProvider": "openai",
        "embeddingModel": "text-embedding-3-small",
        "dimensions": 1536,
        "metadata": { "category": "features" }
      }
    },
    {
      "name": "index_doc_3",
      "taskReferenceName": "index_doc_3_ref",
      "type": "LLM_INDEX_TEXT",
      "inputParameters": {
        "vectorDB": "postgres-prod",
        "index": "demo_index",
        "namespace": "demo_docs",
        "docId": "config-003",
        "text": "You can configure multiple named instances of the same vector database type for different environments like production, development, and staging.",
        "embeddingModelProvider": "openai",
        "embeddingModel": "text-embedding-3-small",
        "dimensions": 1536,
        "metadata": { "category": "configuration" }
      }
    },
    {
      "name": "search_index",
      "taskReferenceName": "search_ref",
      "type": "LLM_SEARCH_INDEX",
      "inputParameters": {
        "vectorDB": "postgres-prod",
        "index": "demo_index",
        "namespace": "demo_docs",
        "query": "What vector databases does Conductor support?",
        "embeddingModelProvider": "openai",
        "embeddingModel": "text-embedding-3-small",
        "dimensions": 1536,
        "maxResults": 3
      }
    },
    {
      "name": "generate_rag_answer",
      "taskReferenceName": "answer_ref",
      "type": "LLM_CHAT_COMPLETE",
      "inputParameters": {
        "llmProvider": "openai",
        "model": "gpt-4o-mini",
        "messages": [
          {
            "role": "system",
            "message": "You are a technical expert. Answer the question using only the provided context."
          },
          {
            "role": "user",
            "message": "Context:\n${search_ref.output.result}\n\nQuestion: What vector databases does Conductor support?"
          }
        ],
        "temperature": 0.2
      }
    }
  ],
  "outputParameters": {
    "indexed_docs": ["${index_doc_1_ref.output}", "${index_doc_2_ref.output}", "${index_doc_3_ref.output}"],
    "search_results": "${search_ref.output.result}",
    "answer": "${answer_ref.output.result}"
  }
}
```

**Run without input:**
```bash
curl -X POST 'http://localhost:8080/api/workflow/complete_rag_demo' \
  -H 'Content-Type: application/json' \
  -d '{}'
```

### 7. MCP (Model Context Protocol) Tool Integration

MCP allows workflows to interact with external tools and data sources via HTTP/HTTPS or stdio (local) servers.

#### List Tools from MCP Server

```json
{
  "name": "mcp_list_tools_workflow",
    "version": 1,
  "schemaVersion": 2,
  "tasks": [
    {
      "name": "list_mcp_tools",
      "taskReferenceName": "list_tools",
      "type": "LIST_MCP_TOOLS",
      "inputParameters": {
        "mcpServer": "http://localhost:3000/mcp"
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

#### Call MCP Tool (HTTP Server)

```json
{
  "name": "mcp_weather_workflow",
    "version": 1,
  "schemaVersion": 2,
  "tasks": [
    {
      "name": "get_weather",
      "taskReferenceName": "weather",
      "type": "CALL_MCP_TOOL",
      "inputParameters": {
        "mcpServer": "http://localhost:3000/mcp",
        "method": "get_weather",
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

**MCP Server URL Formats:**
- **HTTP**: `http://localhost:3000` (uses Streamable HTTP transport)
- **HTTP/SSE (deprecated)**: `http://localhost:3000/sse`
- **HTTP/Streamable**: `http://localhost:3000/mcp`
- **HTTPS**: `https://api.example.com/mcp`

> **Note**: All input parameters except `mcpServer`, `method`, and `headers` are automatically passed as arguments to the MCP tool.

#### MCP + AI Agent Workflow

Complete example combining MCP tools with LLM for autonomous agent behavior:

```json
{
  "name": "mcp_ai_agent_workflow",
    "version": 1,
  "schemaVersion": 2,
  "tasks": [
    {
      "name": "list_available_tools",
      "taskReferenceName": "discover_tools",
      "type": "LIST_MCP_TOOLS",
      "inputParameters": {
        "mcpServer": "http://localhost:3000/mcp"
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
            "message": "Which tool should I use and what parameters? Respond with JSON: {method: string, arguments: object}"
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
        "mcpServer": "http://localhost:3000/mcp",
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
      "method": "get_weather",
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

### 8. Video Generation (OpenAI Sora)

```json
{
  "name": "video_gen_openai_sora",
  "version": 1,
  "schemaVersion": 2,
  "tasks": [
    {
      "name": "generate_video",
      "taskReferenceName": "sora_video",
      "type": "GENERATE_VIDEO",
      "inputParameters": {
        "llmProvider": "openai",
        "model": "sora-2",
        "prompt": "A slow cinematic aerial shot of a coastal city at golden hour, waves crashing against cliffs",
        "duration": 8,
        "size": "1280x720",
        "n": 1,
        "style": "cinematic"
      }
    }
  ]
}
```

**Output:**
```json
{
  "media": [
    {
      "location": "/api/media/.../video.mp4",
      "mimeType": "video/mp4"
    },
    {
      "location": "/api/media/.../thumbnail.webp",
      "mimeType": "image/webp"
    }
  ],
  "jobId": "video_abc123...",
  "status": "COMPLETED",
  "pollCount": 14
}
```

### 9. Video Generation (Google Gemini Veo)

```json
{
  "name": "video_gen_gemini_veo",
  "version": 1,
  "schemaVersion": 2,
  "tasks": [
    {
      "name": "generate_video",
      "taskReferenceName": "veo_video",
      "type": "GENERATE_VIDEO",
      "inputParameters": {
        "llmProvider": "vertex_ai",
        "model": "veo-3",
        "prompt": "A time-lapse of a blooming flower in a sunlit garden, soft bokeh background",
        "duration": 8,
        "aspectRatio": "16:9",
        "resolution": "720p",
        "personGeneration": "dont_allow",
        "generateAudio": true,
        "negativePrompt": "blurry, low quality, text overlay",
        "n": 1
      }
    }
  ]
}
```

### 10. Multi-Step Pipeline (Image + Video)

A workflow that generates an image and a video in sequence:

```json
{
  "name": "image_to_video_pipeline",
  "version": 1,
  "schemaVersion": 2,
  "tasks": [
    {
      "name": "generate_image",
      "taskReferenceName": "source_image",
      "type": "GENERATE_IMAGE",
      "inputParameters": {
        "llmProvider": "openai",
        "model": "dall-e-3",
        "prompt": "A serene mountain lake at dawn with mist rising from the water",
        "width": 1792,
        "height": 1024,
        "n": 1
      }
    },
    {
      "name": "generate_video",
      "taskReferenceName": "animated_video",
      "type": "GENERATE_VIDEO",
      "inputParameters": {
        "llmProvider": "openai",
        "model": "sora-2",
        "prompt": "A serene mountain lake at dawn, gentle ripples spread across the water as mist slowly drifts",
        "duration": 8,
        "size": "1280x720",
        "style": "cinematic"
      }
    }
  ]
}
```

### 11. LLM Tool Calling with MCP Tools

Use `LLM_CHAT_COMPLETE` with the `tools` parameter to let the LLM autonomously decide when to call MCP tools. When the LLM needs to use a tool, it returns `finishReason: "TOOL_CALLS"` with the tool invocations.

#### LLM Output with Tool Calls

When the LLM decides to call tools, the output looks like this:

```json
{
  "result": [],
  "media": [],
  "finishReason": "TOOL_CALLS",
  "tokenUsed": 90,
  "promptTokens": 75,
  "completionTokens": 15,
  "toolCalls": [
    {
      "taskReferenceName": "call_2prFOIfVdwS4BTAi4Z43qPGe",
      "name": "get_weather",
      "type": "MCP_TOOL",
      "inputParameters": {
        "method": "get_weather",
        "location": "Tokyo"
      }
    }
  ]
}
```

> **Key Points:**
> - `finishReason: "TOOL_CALLS"` indicates the LLM wants to invoke tools
> - `toolCalls` array contains all tool invocations with their parameters
> - Each tool call has a unique `taskReferenceName` for workflow orchestration
> - The `configParams.mcpServer` in each tool definition specifies the MCP server URL


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
