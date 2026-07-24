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

## 4. Commands

```bash
./gradlew spotlessApply
./gradlew :conductor-ai:test
```

Credentialed or live-server integration tests remain opt-in and skip when their prerequisites are
not configured.
