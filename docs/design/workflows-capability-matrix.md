# Workflows documentation capability matrix

This working matrix records the source used for the Build → Run → Trigger → Operate rewrite. It is intentionally outside the public navigation. `source-inspected` means the implementation/controller was read; `existing-test-backed` means repository tests assert the behavior; `live-tested` is reserved for a local end-to-end run.

| Capability | Runtime/default | REST evidence | CLI/SDK surface | Module/configuration | Evidence |
|---|---|---|---|---|---|
| Validate definition | Metadata validation; no worker/connectivity check | `POST /api/metadata/workflow/validate`, empty 200; `MetadataResource` | REST; CLI create validates during registration | Core server | live-tested |
| Mock workflow test | Task mocks are lists by task reference; nested subworkflow requests supported | `POST /api/workflow/test`; `WorkflowTestRequest` | REST; SDK-owned test helpers vary | Core server | live-tested |
| Start workflow | Async returns ID; sync execute returns execution | `WorkflowResource` `/api/workflow`, `/{name}`, `/execute/{name}/{version}` | CLI and Java/Python/TS/Go start support | Core server | live-tested |
| Schedule | Single or multi-cron; multi array wins; UTC default; catchup and bounds | `SchedulerResource` `/api/scheduler/*`, all success status 200 | CLI simple CRUD; REST is complete portable surface; high-level SDK parity not assumed | Scheduler core plus persistence; `conductor.scheduler.*` | live-tested/existing-test-backed |
| Scheduler metadata | Five injected input fields; correlation copied literally | No separate endpoint | Visible on started execution input | Scheduler core | live-tested/existing-test-backed |
| Schedule preview | At most five; server scheduler timezone; no zone query | `GET /api/scheduler/nextFewSchedules` | REST | Scheduler core | existing-test-backed |
| Event publish | EVENT publishes task output minus `event_produced`; `asyncComplete` may leave task in progress | Workflow/task APIs expose state | Workflow definition system task | Core plus selected event queue | existing-test-backed/source-inspected |
| Event handler | Inactive by default; conditions/action templates use payload root | `/api/event` CRUD/list; empty 200 mutations | REST; no cross-SDK parity claim | Core plus selected provider | existing-test-backed/source-inspected |
| Event actions | OSS implements start, complete task, fail task only; concurrent/non-atomic | Handler model is accepted by `/api/event` | REST configuration | Core `SimpleActionProcessor` | source-inspected |
| Event providers | First-colon split; exact keys documented | Provider-specific URI is opaque to controller | Workflow/event handler configuration | core, kafka-event-queue, awssqs-event-queue, nats, nats-streaming, amqp | source-inspected |

## Live-test status

On 2026-07-23, a local server smoke test validated definition validation, mocked testing, registration, synchronous execution, schedule CRUD/firing, pause/resume, and all five injected scheduler fields. The cached CLI server distribution could not run the internal event smoke test because `ConductorObservableQueue` was absent from its runtime classpath; event claims remain source- and existing-test-backed. External brokers were not provisioned.
