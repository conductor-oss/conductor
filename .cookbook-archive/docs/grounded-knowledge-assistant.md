# Grounded knowledge

**Outcome:** retrieve relevant knowledge and return an answer with source identifiers that a caller can render as citations.

```mermaid
flowchart LR
  Q[Question] --> S[LLM_SEARCH_INDEX]
  S --> A[LLM_CHAT_COMPLETE]
  A --> O[Answer + citations]
```

## Prerequisites and contract

Configure the vector DB and OpenAI provider. Input is `question`, `vectorDB`, `index`, and `namespace`; output is `answer`, `citations`, and retrieved `sources`. The index-time embedding model must match the query model. Keep long documents in object storage or the Files API and index their metadata and references, not multi-megabyte workflow inputs.

## Runnable definition

Save this as `grounded-knowledge-assistant.json`:

```json
--8<-- "docs/devguide/ai/cookbook/assets/grounded-knowledge-assistant.json"
```

## Register and run

```bash
conductor workflow create grounded-knowledge-assistant.json
conductor workflow start -w grounded_knowledge_assistant --sync -i '{"question":"What is our retention policy?","vectorDB":"REPLACE_VECTOR_DB","index":"REPLACE_INDEX","namespace":"REPLACE_NAMESPACE"}'
```

Open **[Executions](http://localhost:8080/executions)** in the Conductor UI and select the new execution to review the task graph, and each task's inputs and outputs.

## Production notes

The definition bounds retries, tokens, request rate, concurrency, and total run time. There is no external write. Log the source IDs and model token use, retain the source snapshot used for an answer, and retry only retrieval/model failures—not a caller-side publication of the answer. Replace provider, models, source schema, and citation renderer for your environment.
