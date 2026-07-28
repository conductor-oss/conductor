---
description: Canonical, source-backed guidance for AI coding assistants that build or operate Conductor workflows and agents.
---

# Conductor for AI assistants

Use this page as the canonical starting point when an AI coding assistant needs to help build, review, run, or operate Conductor workflows.

## What Conductor is

Conductor is an open-source durable execution platform for workflows, adaptive agents, and AI systems. A workflow is a versioned graph of tasks. Conductor persists execution state and coordinates task scheduling; workers and built-in system tasks perform the work.

Conductor supports two complementary AI paths:

- **Native AI workflows:** compose LLM, MCP, vector, human approval, and control-flow system tasks in a workflow definition.
- **Framework-authored agents:** compile a supported SDK or framework agent—such as OpenAI Agents, Google ADK, LangChain, or LangGraph—into a Conductor graph, then use it in a larger workflow.

Use the [Agents & AI overview](index.md) for the product map and [framework agent recipes](agent-framework-recipes.md) for supported bridges.

## Safe authoring rules

1. Prefer a built-in system task when it matches the operation. Do not replace native LLM, MCP, vector, approval, wait, transform, or control-flow tasks with an HTTP wrapper or a custom worker.
2. Every external side effect must be idempotent. Conductor task delivery is at least once, so a task can be redelivered after failure or timeout.
3. Bound adaptive execution. Use loop iteration caps, task and workflow timeouts, bounded fan-out, and approved capability selection.
4. Do not put credentials in workflow input or prompts. Use the appropriate server-side integration, secret facility, or worker environment instead.
5. Treat a generated workflow definition as untrusted data. Validate its structure and capability allowlist before starting it with `workflowDef`.
6. Require approval before consequential writes. Use `HUMAN` directly or the SDK agent tool approval configuration.
7. Keep outputs intentionally small. Store large objects externally and pass references through the workflow.

## Choose the right starting point

| Goal | Start here |
|---|---|
| Create a durable service workflow | [First workflow](../../quickstart/first-workflow.md) |
| Build a governed plan/act/evaluate loop | [Durable Adaptive Graphs](dynamic-workflows.md) |
| Bring an existing framework agent (LangChain, ADK, and more) | [Framework Agent Recipes](agent-framework-recipes.md) |
| Add policy and approval | [Agent Guardrails](agent-guardrails.md) |
| Test routes, tools, and output quality | [Agent Evals](agent-evals.md) |
| Design a production agent system | [Production Agent Architecture](production-agent-architecture.md) |
| Check task and API fields | [Workflow definition reference](../../documentation/configuration/workflowdef/index.md) |

## Durable execution vocabulary

- **Workflow definition:** versioned task graph; a running workflow uses the definition version it started with.
- **Task:** a unit of work. Built-in system tasks run in the platform; `SIMPLE` tasks are executed by registered workers.
- **Workflow output:** a stable contract assembled from task output using `outputParameters`.
- **Retry:** task-scoped recovery. Retrying a failed `DO_WHILE` restarts that loop's iteration history.
- **Pause and approval:** `WAIT` and `HUMAN` hold durable execution state until they are resolved.
- **Dynamic task / fan-out:** `DYNAMIC` selects a task at runtime; `FORK_JOIN_DYNAMIC` creates runtime branches and is followed by `JOIN`.
- **Loop retention:** `keepLastN` bounds storage for long loops by intentionally removing older iteration history.

## Verify before advising

Treat source as the specification. Check Java task and API implementations for runtime semantics, then check the relevant SDK source for SDK-authored agent, guardrail, and eval behavior. Run a JSON syntax check, a strict docs build, link validation, and a local execution whenever the configured server and integrations are available.

For machine-readable discovery, start at [llms.txt](../../llms.txt). The curated [llms-full.txt](../../llms-full.txt) is generated from the source pages listed in the repository manifest.
