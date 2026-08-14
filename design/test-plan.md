# Agent Worker Test Plan

This matrix records the required coverage for the portable annotation-backed agent workers.

| Concern | Primary coverage |
|---|---|
| Annotation registration | `A2AWorkersTest`, core annotation scanner tests |
| Remote A2A direct reply | `A2AAgentWorkerTest` |
| Remote A2A start and poll | `A2AAgentWorkerTest`, `A2AEndToEndTest` |
| Streaming | `A2AAgentWorkerTest`, `A2ADurabilityTest` |
| Push callback and backstop | `A2ACallbackResourceTest`, `A2ADurabilityTest` |
| Deterministic A2A message ID | `A2ADurabilityTest` |
| Crash-safe persisted resume | `A2ADurabilityTest` |
| Explicit A2A cancellation | `A2ACancelWorkerTest` |
| Parent cancellation hook | `A2AAgentWorkerTest`, core annotated-task cancellation tests |
| Conductor-agent start and poll | `ConductorAgentDelegateTest`, test-harness integration |
| Conductor-agent waiting/resume | `ConductorAgentDelegateTest`, test-harness integration |
| Conductor-agent cancellation | `A2ACancelWorkerTest`, `ConductorAgentDelegateTest` |
| SDK portability | `A2ASdkInteropTest`, Java SDK module tests |

## Required assertions

- A running agent returns `IN_PROGRESS` and a positive callback delay.
- A later invocation resumes solely from persisted task input/output.
- Retry-stable identity produces the same idempotency key across task attempts.
- Terminal remote and Conductor-agent states map to the documented `TaskResult` status.
- Invalid input returns `FAILED_WITH_TERMINAL_ERROR`.
- Transient transport errors remain retryable until the configured failure cap.
- Embedded cancellation propagates best-effort cleanup without changing the portable worker API.
- The Conductor branch only uses the injected `AgentClient`.

## Verification

Run formatting and the complete AI suite:

```bash
./gradlew spotlessApply
./gradlew :conductor-ai:test
```

For changes to annotation mapping or cancellation, also run:

```bash
./gradlew :conductor-core:test
```
