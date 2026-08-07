# Agent Worker Testing

Tests invoke the annotation-backed worker methods directly with public `Task` objects. This mirrors
both runtime modes: embedded annotated system tasks and external Java SDK workers.

## 1. Worker-level tests

| Test | Coverage |
|---|---|
| `A2AWorkersTest` | Agent-card discovery and validation |
| `A2AAgentWorkerTest` | Remote A2A start, poll, streaming, interruption, failures, and cancellation hook |
| `A2ACancelWorkerTest` | Explicit remote and Conductor-agent cancellation |
| `A2AEndToEndTest` | Full remote A2A lifecycle through `A2AWorkers.agent` |
| `A2ADurabilityTest` | Persistence round-trip, deterministic IDs, deadlines, failure caps, and push backstop |
| `A2ASdkInteropTest` | Interoperability with the A2A SDK |
| `A2ARealAgentIntegrationTest` | Opt-in live remote-agent coverage |

`A2AWorkerTestSupport` applies each returned `TaskResult` to the `Task`, modeling the state the
engine persists before the next worker invocation.

## 2. Conductor-agent tests

`ConductorAgentDelegateTest` uses a small in-memory `AgentClient` implementation. It proves that:

- a run starts once and later invocations poll it;
- deterministic idempotency data is sent on start;
- waiting output surfaces the pending request;
- completed and canceled statuses map to the expected task result; and
- cancellation uses `AgentClient`, not `WorkflowExecutor`.

`A2AAgentWorkerTest` and `A2ACancelWorkerTest` additionally prove that `A2AWorkers` dispatches the
`conductor` branch to the injected client.

The test-harness `ConductorAgentEndToEndTest` covers the complete embedded runtime with real
services.

## 3. Annotation runtime tests

Core annotation tests cover:

- injection of the public `Task` parameter;
- mapping returned `TaskResult` fields back onto the engine task;
- callback delays and sub-workflow IDs; and
- the embedded cancellation hook.

These tests keep the reusable worker contract independent from engine-internal task models.

## 4. Agent loop JOIN regression

`JoinTest` covers the engine boundary with real `WorkflowDef`, `WorkflowTask`, `WorkflowModel`, and
`TaskModel` instances. Add a focused regression for the issue #1499 execution shape:

1. Mark the workflow as an agent with `metadata.agentDef`.
2. Create a completed dynamic tool task whose iteration-qualified reference is joined from a
   `DO_WHILE` iteration.
3. Put `_agent_tool_name` only in `TaskModel.workflowTask.inputParameters`; omit it from
   `TaskModel.inputData` to model the failing runtime representation.
4. Give the task a non-empty HTTP- or MCP-shaped output.
5. Execute JOIN and assert that it completes with a non-empty entry containing the logical name and
   the complete output under `_agent_tool_output`.

The same test class retains explicit compatibility cases:

- a marker already present in `TaskModel.inputData` takes precedence;
- an unmarked agent branch still propagates only `_state_updates` and `state`;
- a non-agent JOIN still copies the full fork output; and
- missing workflow metadata does not throw or enable agent compaction.

`AgentToolJoinStateMergeScriptTest` exercises the consumer boundary by passing the JOIN envelope to
`stateMergeScript()` and asserting that `toolResults` contains `{name, output}` for the next agent
turn. This test uses both an HTTP-shaped response and an MCP-shaped response so the contract stays
provider-neutral.

No live LLM, MCP server, HTTP server, or full example execution is required for the regression.

## 5. Commands

```bash
./gradlew spotlessApply
./gradlew :conductor-core:test --tests com.netflix.conductor.core.execution.tasks.JoinTest
./gradlew :conductor-agentspan:test --tests org.conductoross.conductor.ai.agentspan.runtime.util.AgentToolJoinStateMergeScriptTest
```

Credentialed or live-server integration tests remain opt-in and skip when their prerequisites are
not configured.
