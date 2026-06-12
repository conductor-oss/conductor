---
description: Native LLM orchestration with Conductor — supported LLM providers, vector database integration for RAG pipelines, and multimodal content generation tasks.
---

# LLM orchestration

Conductor provides native system tasks for LLM orchestration and integration. No external frameworks or custom workers required — configure a provider and use it in any workflow. Each provider supports function calling via MCP tool integration.

## Supported LLM providers

| Provider | Chat Completion | Text Completion | Embeddings |
|---|---|---|---|
| Anthropic (Claude) | ✓ | ✓ | — |
| OpenAI (GPT) | ✓ | ✓ | ✓ |
| Azure OpenAI | ✓ | ✓ | ✓ |
| Google Gemini | ✓ | ✓ | ✓ |
| AWS Bedrock | ✓ | ✓ | ✓ |
| Mistral | ✓ | ✓ | ✓ |
| Cohere | ✓ | ✓ | ✓ |
| HuggingFace | ✓ | ✓ | ✓ |
| Ollama | ✓ | ✓ | ✓ |
| Perplexity | ✓ | — | — |
| Grok (xAI) | ✓ | ✓ | — |
| StabilityAI | — | — | — |

No other open source workflow engine provides native LLM orchestration at this breadth. Each provider is a configuration — switch models by changing a parameter, not your code.


## Built-in tools & advanced capabilities

Conductor supports provider-native tools that run on the provider's infrastructure — no MCP server or custom worker needed. Enable them with a single parameter in the `LLM_CHAT_COMPLETE` task.

| Capability | Parameter | OpenAI | Anthropic | Google Gemini |
|---|---|---|---|---|
| Web Search | `webSearch: true` | ✓ | ✓ | ✓ |
| Code Execution | `codeInterpreter: true` | ✓ (code_interpreter) | ✓ (code_execution) | ✓ (code_execution) |
| File Search | `fileSearchVectorStoreIds: [...]` | ✓ | — | — |
| Extended Thinking | `thinkingTokenLimit: N` | — | ✓ | ✓ |
| Reasoning Effort | `reasoningEffort: "high"` | ✓ | — | — |
| Google Search | `googleSearchRetrieval: true` | — | — | ✓ |
| Custom Functions | `tools: [...]` | ✓ | ✓ | ✓ |

### Web search

The LLM can search the web for real-time information during chat completion. Enable it with `"webSearch": true`:

```json
{
  "type": "LLM_CHAT_COMPLETE",
  "inputParameters": {
    "llmProvider": "openai",
    "model": "gpt-4o-mini",
    "messages": [{"role": "user", "message": "What happened in tech news today?"}],
    "webSearch": true
  }
}
```

Works with OpenAI, Anthropic, and Google Gemini. Each provider uses its own native web search implementation.

### Code execution

The LLM can write and execute code in a sandboxed environment. Enable it with `"codeInterpreter": true`:

```json
{
  "type": "LLM_CHAT_COMPLETE",
  "inputParameters": {
    "llmProvider": "google_gemini",
    "model": "gemini-2.5-flash",
    "messages": [{"role": "user", "message": "Calculate the first 100 prime numbers and plot them"}],
    "codeInterpreter": true
  }
}
```

Use this for data analysis, chart generation, mathematical computation, or any task that benefits from running code.

### Extended thinking

Give the LLM a token budget for step-by-step reasoning before it responds. Useful for complex problems that benefit from chain-of-thought reasoning:

```json
{
  "type": "LLM_CHAT_COMPLETE",
  "inputParameters": {
    "llmProvider": "anthropic",
    "model": "claude-sonnet-4-20250514",
    "messages": [{"role": "user", "message": "Prove that there are infinitely many primes"}],
    "thinkingTokenLimit": 10000,
    "maxTokens": 16000
  }
}
```

Supported by Anthropic and Google Gemini.


## Vector database workflows

Built-in vector database integration enables RAG (retrieval-augmented generation) pipelines as standard vector database workflows.

| Vector Database | Store Embeddings | Index Text | Semantic Search |
|---|---|---|---|
| Pinecone | ✓ | ✓ | ✓ |
| pgvector (PostgreSQL) | ✓ | ✓ | ✓ |
| MongoDB Atlas Vector Search | ✓ | ✓ | ✓ |


### Example: RAG pipeline

A complete RAG workflow using native system tasks — index documents, search, and generate an answer. No custom workers required.

```json
{
  "name": "rag_pipeline",
  "description": "Index documents, search, and generate RAG answer",
  "version": 1,
  "schemaVersion": 2,
  "tasks": [
    {
      "name": "index_document",
      "taskReferenceName": "index_ref",
      "type": "LLM_INDEX_TEXT",
      "inputParameters": {
        "vectorDB": "postgres-prod",
        "index": "knowledge_base",
        "namespace": "docs",
        "docId": "${workflow.input.docId}",
        "text": "${workflow.input.text}",
        "embeddingModelProvider": "openai",
        "embeddingModel": "text-embedding-3-small",
        "dimensions": 1536,
        "metadata": "${workflow.input.metadata}"
      }
    },
    {
      "name": "search_index",
      "taskReferenceName": "search_ref",
      "type": "LLM_SEARCH_INDEX",
      "inputParameters": {
        "vectorDB": "postgres-prod",
        "index": "knowledge_base",
        "namespace": "docs",
        "query": "${workflow.input.question}",
        "embeddingModelProvider": "openai",
        "embeddingModel": "text-embedding-3-small",
        "dimensions": 1536,
        "maxResults": 3
      }
    },
    {
      "name": "generate_answer",
      "taskReferenceName": "answer_ref",
      "type": "LLM_CHAT_COMPLETE",
      "inputParameters": {
        "llmProvider": "openai",
        "model": "gpt-4o-mini",
        "messages": [
          {
            "role": "system",
            "message": "Answer the question using only the provided context."
          },
          {
            "role": "user",
            "message": "Context:\n${search_ref.output.result}\n\nQuestion: ${workflow.input.question}"
          }
        ],
        "temperature": 0.2
      }
    }
  ],
  "outputParameters": {
    "searchResults": "${search_ref.output.result}",
    "answer": "${answer_ref.output.result}"
  }
}
```

Every task type — `LLM_INDEX_TEXT`, `LLM_SEARCH_INDEX`, `LLM_CHAT_COMPLETE` — is a native Conductor system task. The vector database, embedding model, and LLM provider are all configuration parameters. Switch from pgvector to Pinecone or from OpenAI to Anthropic by changing a parameter value.


## Content generation

Native system tasks for multimodal content generation:

| Task | Type | Description |
|---|---|---|
| Generate Image | `GENERATE_IMAGE` | Text-to-image generation via AI models |
| Generate Audio | `GENERATE_AUDIO` | Text-to-speech synthesis |
| Generate Video | `GENERATE_VIDEO` | Text/image-to-video generation (async) |
| Generate PDF | `GENERATE_PDF` | Markdown-to-PDF document conversion |


## Examples

Ready-to-use workflow definitions for every AI task type. Each example is a complete JSON workflow you can register and run directly.

| Example | Task types used |
|---|---|
| [Chat Completion](https://github.com/conductor-oss/conductor/blob/main/ai/examples/01-chat-completion.json) | `LLM_CHAT_COMPLETE` |
| [Generate Embeddings](https://github.com/conductor-oss/conductor/blob/main/ai/examples/02-generate-embeddings.json) | `LLM_GENERATE_EMBEDDINGS` |
| [Image Generation](https://github.com/conductor-oss/conductor/blob/main/ai/examples/03-image-generation.json) | `GENERATE_IMAGE` |
| [Audio Generation](https://github.com/conductor-oss/conductor/blob/main/ai/examples/04-audio-generation.json) | `GENERATE_AUDIO` |
| [Semantic Search](https://github.com/conductor-oss/conductor/blob/main/ai/examples/05-semantic-search.json) | `LLM_SEARCH_INDEX` |
| [RAG Basic](https://github.com/conductor-oss/conductor/blob/main/ai/examples/06-rag-basic.json) | `LLM_SEARCH_INDEX`, `LLM_CHAT_COMPLETE` |
| [RAG Complete](https://github.com/conductor-oss/conductor/blob/main/ai/examples/07-rag-complete.json) | `LLM_INDEX_TEXT`, `LLM_SEARCH_INDEX`, `LLM_CHAT_COMPLETE` |
| [MCP List Tools](https://github.com/conductor-oss/conductor/blob/main/ai/examples/08-mcp-list-tools.json) | `LIST_MCP_TOOLS` |
| [MCP Call Tool](https://github.com/conductor-oss/conductor/blob/main/ai/examples/09-mcp-call-tool.json) | `CALL_MCP_TOOL` |
| [MCP AI Agent](https://github.com/conductor-oss/conductor/blob/main/ai/examples/10-mcp-ai-agent.json) | `LIST_MCP_TOOLS`, `LLM_CHAT_COMPLETE`, `CALL_MCP_TOOL` |
| [Video — OpenAI Sora](https://github.com/conductor-oss/conductor/blob/main/ai/examples/11-video-openai-sora.json) | `GENERATE_VIDEO` |
| [Video — Gemini Veo](https://github.com/conductor-oss/conductor/blob/main/ai/examples/12-video-gemini-veo.json) | `GENERATE_VIDEO` |
| [Image-to-Video Pipeline](https://github.com/conductor-oss/conductor/blob/main/ai/examples/13-image-to-video-pipeline.json) | `GENERATE_IMAGE`, `GENERATE_VIDEO` |
| [StabilityAI Image](https://github.com/conductor-oss/conductor/blob/main/ai/examples/14-stabilityai-image.json) | `GENERATE_IMAGE` |
| [PDF Generation](https://github.com/conductor-oss/conductor/blob/main/ai/examples/15-pdf-generation.json) | `GENERATE_PDF` |
| [LLM-to-PDF Pipeline](https://github.com/conductor-oss/conductor/blob/main/ai/examples/16-llm-to-pdf-pipeline.json) | `LLM_CHAT_COMPLETE`, `GENERATE_PDF` |
| [Web Search](https://github.com/conductor-oss/conductor/blob/main/ai/examples/17-web-search.json) | `LLM_CHAT_COMPLETE` (web search) |
| [Code Execution](https://github.com/conductor-oss/conductor/blob/main/ai/examples/18-code-execution.json) | `LLM_CHAT_COMPLETE` (code execution) |
| [Coding Agent](https://github.com/conductor-oss/conductor/blob/main/ai/examples/19-coding-agent.json) | `LLM_CHAT_COMPLETE` (code_interpreter) |
| [Extended Thinking](https://github.com/conductor-oss/conductor/blob/main/ai/examples/20-extended-thinking.json) | `LLM_CHAT_COMPLETE` (thinking) |
| [Web Research Agent](https://github.com/conductor-oss/conductor/blob/main/ai/examples/21-web-search-research-agent.json) | `LLM_CHAT_COMPLETE` (web search + thinking), `GENERATE_PDF` |
| [Multi-Turn Chain](https://github.com/conductor-oss/conductor/blob/main/ai/examples/22-multi-turn-chain.json) | `LLM_CHAT_COMPLETE` (previousResponseId) |

Browse all examples: [`ai/examples/`](https://github.com/conductor-oss/conductor/tree/main/ai/examples)


## Next steps

- **[Durable Agents](durable-agents.md)** &mdash; What persists, what gets retried, and why JSON is AI-native.
- **[Dynamic Workflows](dynamic-workflows.md)** &mdash; Agents that build their own execution plans at runtime.
- **[AI & LLM Recipes](../cookbook/ai-llm.md)** &mdash; Practical recipes for common LLM workflow patterns.
