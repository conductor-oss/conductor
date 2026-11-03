---
description: "Conductor cookbook — route a request with a decision-backed SWITCH."
---

# AI decision routing

One `SWITCH` asks a fixed choice question and routes to the selected branch. The choices live in the definition, so the model can only pick a branch you declared.

## The shape

```text
route_request (SWITCH, evaluatorType: decision)  ──>  billing   ──> selected_result (INLINE)
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
CONDUCTOR_API="${CONDUCTOR_SERVER_URL:-http://localhost:8080/api}"
curl --fail-with-body -X POST "$CONDUCTOR_API/metadata/workflow?overwrite=true" \
  -H 'Content-Type: application/json' --data-binary @ai-decision-routing.json
curl --fail-with-body -X POST "$CONDUCTOR_API/workflow/ai_decision_routing?version=1" \
  -H 'Content-Type: application/json' \
  -d '{"request":"I was charged twice on my latest invoice."}'
```

Open **Executions** in your Conductor UI and select the new execution. `route_request.output.selectedCase` and the workflow's `selectedCase` output are `billing`, only `handle_billing` ran, and `result.nextAction` is `review_invoice`. The workflow also exposes `answers`, `usage`, `cost`, and `latencyMs` from the decision.
