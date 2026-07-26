# Agent Worker Documentation Notes

Public documentation should describe task contracts, not the runtime registration mechanism.

## AGENT

The task supports two runtimes:

- `agentType: "a2a"` or blank calls a remote Agent2Agent endpoint.
- `agentType: "conductor"` calls a deployed Conductor agent through the agent control plane.

The Conductor branch uses the same start fields as `POST /api/agent/start`, plus
`executionId`, `pollIntervalSeconds`, `maxDurationSeconds`, and `maxPollFailures`.

Document that a running invocation is durable and non-blocking: the worker returns
`IN_PROGRESS`, persists its execution identifier in task output, and is invoked again after the
callback delay.

## CANCEL_AGENT

`CANCEL_AGENT` explicitly propagates cancellation to either a remote A2A task or a Conductor agent
execution. Parent-workflow cancellation also propagates when the workers are embedded in the
Conductor server.

## Implementation references

When updating examples or field tables, derive them from:

- `A2AWorkers`
- `A2ACallRequest` and `A2ACancelRequest`
- `ConductorAgentRequest`
- `ConductorAgentResults`
- `AgentController`

Do not document engine-internal lifecycle methods or direct `WorkflowExecutor` access; neither is
part of the portable worker contract.
