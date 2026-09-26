# Decision flows

Two ways to use a decision. Both call the same `HttpDecisionClient`, which
resolves provider, endpoint, API key, and API shape from `conductor.ai.decision`
configuration and never returns credentials to the workflow.

## 1. Deterministic workflow: `AI_DECISION` + `SWITCH`

You author a normal workflow definition. The `AI_DECISION` system task asks one
`choice` question and writes `selectedCase` to its output. A plain `SWITCH` task
routes on that value. No server-side rewriting of the definition happens.

```json
{
  "name": "route_request",
  "taskReferenceName": "route_request",
  "type": "AI_DECISION",
  "inputParameters": {
    "model": "jev-1.13",
    "state": "${workflow.input.request}",
    "questions": {
      "route": {
        "type": "choice",
        "instructions": "Choose the team best suited to handle this request.",
        "choices": {
          "billing": "Payments, invoices, refunds, or subscriptions.",
          "technical": "Errors, outages, or product troubleshooting."
        }
      }
    }
  }
},
{
  "name": "dispatch",
  "taskReferenceName": "dispatch",
  "type": "SWITCH",
  "evaluatorType": "value-param",
  "expression": "selectedCase",
  "inputParameters": { "selectedCase": "${route_request.output.selectedCase}" },
  "decisionCases": {
    "billing":   [ /* your tasks */ ],
    "technical": [ /* your tasks */ ]
  },
  "defaultCase": [ /* your fallback */ ]
}
```

```mermaid
flowchart TD
    A([Workflow starts]) --> B[AI_DECISION task<br/>ref: route_request]
    B --> C{Input has exactly one<br/>question of type choice?}
    C -- no --> F1[FAILED_WITH_TERMINAL_ERROR]
    C -- yes --> D[DecisionValidation.request]
    D -- invalid --> F1
    D -- valid --> E[HttpDecisionClient.decide]

    subgraph client [HttpDecisionClient]
        E --> E1[DecisionConfiguration.resolve<br/>provider + model → endpoint, apiKey, apiShape]
        E1 --> E2[DecisionApiAdapter.encode<br/>default shape: system-one]
        E2 --> E3[POST endpoint<br/>Authorization: Bearer]
        E3 --> E4{HTTP status}
        E4 -- 429 or 5xx --> R1[IllegalStateException<br/>task FAILED, retried by TaskDef]
        E4 -- other 4xx --> R2[NonRetryableException<br/>FAILED_WITH_TERMINAL_ERROR]
        E4 -- 2xx --> E5[DecisionApiAdapter.decode<br/>DecisionValidation.result]
    end

    E5 --> G[Task output = DecisionResult<br/>provider, model, answers, usage, latencyMs<br/>+ selectedCase = answers.route.choice]
    G --> H[SWITCH task<br/>evaluatorType: value-param<br/>expression: selectedCase]
    H --> I{selectedCase}
    I -- billing --> J1[billing branch tasks]
    I -- technical --> J2[technical branch tasks]
    I -- anything else --> J3[defaultCase tasks]
    J1 --> Z([Workflow continues])
    J2 --> Z
    J3 --> Z
```

## 2. Agent way: `kind: "decision"` and the decision router

### 2a. Standalone decision agent

An agent definition with `kind: "decision"` compiles (`AgentCompiler.compileDecision`)
to a one-task workflow containing a `DECISION_AGENT` system task with a retry
`TaskDef` (3 retries, exponential backoff). `DecisionAgentTask` refuses to run
unless the workflow metadata is a compiled decision agent, so it cannot be dropped
into a hand-written workflow. The result is the full `DecisionResult`, and the
`AgentEventListener` emits a `decision` SSE event.

### 2b. Decision router (parent agent with a decision selector)

A parent agent whose selector is a `kind: "decision"` agent with one `choice`
question whose choice names exactly equal the child agent names compiles
(`MultiAgentCompiler.compileDecisionRouter`) to: router sub-agent → `SWITCH` →
selected child sub-agent → `SET_VARIABLE`.

```mermaid
flowchart TD
    A([Agent definition<br/>kind: decision or router with decision selector]) --> B{AgentCompiler}

    B -- "kind: decision" --> C[compileDecision]
    C --> C1{model set and no tools,<br/>agents, memory, schemas, guardrails?}
    C1 -- no --> CX[IllegalArgumentException]
    C1 -- yes --> C2[Workflow with one task<br/>DECISION_AGENT + retry TaskDef]

    B -- "router with decision selector" --> D[MultiAgentCompiler.compileDecisionRouter]
    D --> D1{selector has one choice question<br/>and choices == child agent names?}
    D1 -- no --> DX[IllegalArgumentException]
    D1 -- yes --> D2["Workflow: router SUB_WORKFLOW<br/>→ SWITCH (graaljs: $.answers[$.question].choice)"]

    C2 --> E([Execution])
    D2 --> E

    E --> F[DECISION_AGENT task]
    F --> F1{Workflow metadata is a compiled<br/>decision agent and this is its only task?}
    F1 -- no --> FX[FAILED_WITH_TERMINAL_ERROR]
    F1 -- yes --> F2[DecisionValidation.request]
    F2 --> G[HttpDecisionClient.decide<br/>same resolve / encode / POST / decode as flow 1]
    G -- 429 or 5xx --> G1[FAILED → TaskDef retry<br/>up to 3x, exponential backoff]
    G -- other 4xx / invalid JSON --> FX
    G -- ok --> H[Task output = DecisionResult]
    H --> H1[AgentEventListener emits<br/>SSE event type: decision]

    H1 --> I{Standalone or router?}
    I -- standalone --> Z1([Agent result = DecisionResult])
    I -- router --> J[SWITCH on answers.question.choice]
    J -- child name --> K[SUB_WORKFLOW: selected child agent]
    K --> L[SET_VARIABLE<br/>result, selectedAgent]
    L --> Z2([Workflow output:<br/>result, selectedAgent, routing])
    J -- unknown --> M[TERMINATE FAILED<br/>Decision selected an unknown agent]
```

## Side by side

| | Deterministic (`AI_DECISION`) | Agent (`DECISION_AGENT`) |
| --- | --- | --- |
| Authored as | Task in a workflow definition | Agent definition, `kind: decision` |
| Questions | Exactly one `choice` question | Any number of `choice`, `score`, `boolean` |
| Routing | You write the `SWITCH` on `selectedCase` | Router compiler writes the `SWITCH` for you |
| Branches | Any tasks | Child agents, one per choice name |
| Retry | Whatever the task's `TaskDef` says | 3 retries, exponential backoff, built in |
| Result | `DecisionResult` + `selectedCase` | `DecisionResult` (standalone) or `result`, `selectedAgent`, `routing` (router) |
| Events | None | `decision` SSE event |
