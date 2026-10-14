---
description: "Conductor cookbook — route a request on an AI_DECISION answer with a SWITCH."
---

# AI decision routing

An `AI_DECISION` task asks a fixed choice question, and a regular `SWITCH` routes on the answer. The choices live in the definition, so the model can only pick a branch you declared. The decision call runs on the system task worker like an `HTTP` task, so it never blocks the decider.

## The shape

```text
classify_request (AI_DECISION) ──> route_request (SWITCH on selectedCase) ──>  billing   ──> selected_result (INLINE)
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

<!-- TODO: verify against live server -->
Open **Executions** in your Conductor UI and select the new execution. `classify_request.output.selectedCase` and the workflow's `selectedCase` output are `billing`, only `handle_billing` ran, and `result.nextAction` is `review_invoice`. The workflow also exposes `answers`, `usage`, `cost`, and `latencyMs` from the decision.

## Retries

Without a registered task definition, `AI_DECISION` is not retried. To retry rate limits and provider errors, register a task definition named `classify_request` with `retryCount`, `retryLogic` and `retryDelaySeconds`.
