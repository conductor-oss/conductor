# Web research

**Derived from:** `ai/examples/21-web-search-research-agent.json` (cookbook name and prose only).

**Outcome:** create a source-linked web research brief and render it as a PDF.

```mermaid
flowchart LR
  T[Topic] --> R[LLM + web search]
  R --> P[GENERATE_PDF]
  P --> O[PDF reference]
```

## Prerequisites and contract

Configure an LLM provider with web search support. Input is `topic` and `audience`; output is markdown and a PDF location. Review the resulting Sources section before using it for regulated or high-stakes publication.

## Runnable definition

Save this as `web-research-brief.json`:

```json
--8<-- "docs/devguide/ai/cookbook/assets/web-research-brief.json"
```

## Register and run

```bash
conductor workflow create web-research-brief.json
conductor workflow start -w web_research_brief_pdf --sync -i '{"topic":"durable execution","audience":"engineering leadership"}'
```

Open **[Executions](http://localhost:8080/executions)** in the Conductor UI and select the new execution to review the task graph, and each task's inputs and outputs.

## Production notes

The workflow bounds provider rate, concurrency, retries, tokens, and end-to-end execution. It does not publish externally; add a `HUMAN` task before an email, CMS, or customer delivery action. Persist the generated PDF externally and return its URI rather than binary data. Reconcile duplicate delivery by campaign/report ID. Replace provider, model, PDF storage policy, and citation review process for your environment.
