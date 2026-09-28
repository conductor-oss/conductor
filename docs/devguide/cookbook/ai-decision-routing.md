---
description: "Conductor cookbook — route a request with one AI_DECISION task and a SWITCH on selectedCase."
---

# AI decision routing

One `AI_DECISION` task answers a fixed choice question. A `SWITCH` routes on `selectedCase`. The choices live in the definition, so the model can only pick a branch you declared.

## The shape

```text
decision (AI_DECISION)  ──>  route_request (SWITCH)  ──>  billing   ──> selected_result (INLINE)
                                                     └──>  technical ──┘
```

## Prerequisites

`conductor.integrations.ai.enabled=true` and `DECISION_API_KEY` set on the server.

## Runnable definition

Save this as `ai-decision-routing.json`:

```json
--8<-- "docs/devguide/cookbook/examples/ai-decision-routing.json"
```

## Register and run

```bash
conductor workflow create ai-decision-routing.json
conductor workflow start -w ai_decision_routing \
  -i '{"request":"I was charged twice on my latest invoice."}'
```

Open **[Executions](http://localhost:8080/executions)** in the Conductor UI and select the new execution. `decision.output.selectedCase` is `billing`, only `handle_billing` ran, and `result.nextAction` is `review_invoice`.
