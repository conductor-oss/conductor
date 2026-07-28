---
description: Deploy, observe, scale, and recover Conductor workflows and agents in production.
---

# Operate durable systems

Conductor gives platform teams an execution system they can inspect and control after work has started. Use this section to deploy the platform, tune it for your environment, and operate workflows and agents through failure, approval, and change.

## Production path

- **[Best practices](../bestpractices.md)** take a first run through contracts, reliability, tests, deployment, and recovery.
- **[Production agent architecture](../ai/production-agent-architecture.md)** connects authoring, governance, evaluation, deployment, and operational recovery.
- **[Architecture](../architecture/index.md)** explains scheduling, queues, storage, and workers.
- **[Deploy Conductor](../running/deploy.md)** covers a containerized deployment; [run from source](../running/source.md) and [hosted deployment](../running/hosted.md) cover the other paths.
- **[Best practices](../bestpractices.md)** covers reliability, versioning, retries, payloads, and scale.
- **[Configuration](../../documentation/configuration/appconf.md)** and [metrics](../../documentation/metrics/server.md) provide the operational reference.

## Operate the execution, not just the code

An execution remains visible while it waits for a person, a timer, an external system, or a worker. Operators can search its state, inspect task inputs and outputs, pause or resume it, retry eligible failures, and terminate work that must stop.

For workflows, begin an incident with [searching workflows](../how-tos/Workflows/searching-workflows.md) and [debugging workflows](../how-tos/Workflows/debugging-workflows.md). For AI systems, begin with the [production agent architecture](../ai/production-agent-architecture.md). Together they connect policy boundaries, approval, retries, cancellation, auditability, and recovery into one operational path.
