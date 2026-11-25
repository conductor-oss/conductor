# Decision-backed SWITCH

Decision inference uses the ordinary Conductor `SWITCH` task. `SwitchTaskMapper` calls the
provider synchronously and schedules the selected branch in the same mapping step.

```json
{
  "name": "decision_switch",
  "taskReferenceName": "classify_request",
  "type": "SWITCH",
  "evaluatorType": "decision",
  "expression": "route",
  "inputParameters": {
    "model": "jev-1.13",
    "state": "${workflow.input.request}",
    "questions": {
      "route": {
        "type": "choice",
        "instructions": "Choose the team that should handle this request.",
        "choices": {
          "billing": "Billing and payment requests",
          "support": "Product support requests"
        }
      }
    }
  },
  "decisionCases": {
    "billing": [],
    "support": []
  },
  "defaultCase": []
}
```

`expression` names the `choice` question used for routing. The task writes the chosen value to
`selectedCase`, preserves the provider response in its output, and runs the matching
`decisionCases` branch. An agent can be invoked from any branch in the same way as any other
workflow task; there is no separate decision-agent or decision-tool task type.

Output contains `provider`, `model`, `answers`, `selectedCase`, `latencyMs`, optional `requestId`,
and provider-reported `usage`. Evaluation failures use the ordinary built-in `SWITCH` failure
path; they fail the workflow without task retries.

## Server configuration

Configuration uses the `conductor.ai.decision` prefix. The default provider is `openrouter`; the
server properties also accept `DECISION_API_KEY` and `DECISION_PROVIDER`.

```properties
conductor.integrations.ai.enabled=true
conductor.ai.decision.api-key=${DECISION_API_KEY}
conductor.ai.decision.provider=openrouter
```

Provider-specific credentials, endpoints, and model overrides are configured under
`conductor.ai.decision.providers`. The built-in `system-one` adapter has default endpoints for
`openrouter` and `typesafe`; other adapters require an explicit endpoint and a registered
`DecisionApiAdapter` bean.
