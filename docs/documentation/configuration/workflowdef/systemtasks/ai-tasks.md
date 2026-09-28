---
description: "AI system-task reference for Conductor LLM, vector, media, MCP, and A2A tasks."
---

# AI Tasks

AI task types are registered by the `ai` module. Enable them with `conductor.integrations.ai.enabled=true`, then configure the relevant provider, vector database, MCP server, or A2A endpoint. These types are mapped to server-managed tasks; they are not ordinary user-defined `SIMPLE` task definitions.

## LLM

| Type | Purpose |
|---|---|
| `LLM_CHAT_COMPLETE` | Chat completion, including model tool-calling support. |
| `LLM_TEXT_COMPLETE` | Single-prompt text completion. |

Both require a configured LLM provider and model in task input.

## Embeddings and vector databases

| Type | Purpose |
|---|---|
| `LLM_GENERATE_EMBEDDINGS` | Generate embeddings for supplied text. |
| `LLM_INDEX_TEXT` | Generate embeddings and index text. |
| `LLM_STORE_EMBEDDINGS` | Store precomputed embeddings. |
| `LLM_SEARCH_INDEX` | Embed a query and search an index. |
| `LLM_SEARCH_EMBEDDINGS` | Search an index with supplied embeddings. |
| `LLM_GET_EMBEDDINGS` | Retrieve stored embeddings. |

Vector operations require a configured vector database and, where the operation generates vectors, an embedding provider/model.

## Media and documents

| Type | Purpose |
|---|---|
| `GENERATE_IMAGE` | Generate images from a prompt. |
| `GENERATE_AUDIO` | Generate audio from text. |
| `GENERATE_VIDEO` | Generate video from supported prompt or image inputs. |
| `GENERATE_PDF` | Generate a PDF document. |

Provider-backed media tasks require the corresponding provider configuration. PDF generation uses the AI module's registered task implementation.

## MCP

| Type | Purpose |
|---|---|
| `LIST_MCP_TOOLS` | Discover tools exposed by an MCP server. |
| `CALL_MCP_TOOL` | Invoke a named MCP tool. |

Supply the MCP server connection details in task input. The server must be reachable from Conductor.

## A2A agents

| Type | Purpose |
|---|---|
| `GET_AGENT_CARD` | Fetch an A2A agent card from `agentUrl`. |
| `AGENT` | Send work to a Conductor or remote A2A agent and await its result. |
| `CANCEL_AGENT` | Cancel a running Conductor or remote A2A agent task. |

These task workers are registered by the AI integration. Remote A2A calls require `agentUrl`; `CANCEL_AGENT` uses an execution ID for a Conductor target or an agent URL and task ID for a remote target. See the [A2A integration guide](../../../../devguide/ai/a2a-integration.md) for protocol details.
