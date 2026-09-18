---
description: Every setting on a Conductor agent, and the one distinction that matters — what is baked in at deploy time versus what a caller can override per run.
---

# Agent Configuration

An agent has two kinds of settings, and mixing them up is the usual source of surprise.

- **Definition settings** are part of the agent. They are compiled into the workflow at `deploy()` and change only when you redeploy.
- **Run settings** are supplied by the caller on each `run()`, `start()`, or `stream()`. They never change the deployed agent.

A few things — the model, temperature, and token cap — can be set in *both* places. When that happens, **the run wins for that execution only.**

## Definition settings

Set on `Agent(...)`. Fixed until the next `deploy()`.

| Setting | Default | What it does |
|---|---|---|
| `name` | *required* | The name callers resolve. Changing it deploys a different agent |
| `model` | `""` | Provider-qualified model, e.g. `openai/gpt-4o` |
| `instructions` | `""` | The system prompt |
| `tools` | `[]` | Tools the model may call |
| `guardrails` | `[]` | Checks on input or output — see [Agent Guardrails](agent-guardrails.md) |
| `agents` | `[]` | Sub-agents for a multi-agent system |
| `strategy` | `handoff` | How sub-agents are orchestrated — see [Multi-Agent Architecture](multi-agent-architecture.md) |
| `max_turns` | `25` | Hard cap on model turns. The main runaway-loop control |
| `max_tokens` | `None` | Cap per model call |
| `temperature` | `None` | Sampling temperature |
| `context_window_budget` | `None` | Token budget before context is condensed |
| `metadata` | `{}` | Arbitrary labels carried with the definition |

### Capability settings

These decide what the agent can reach. They are deliberately definition-only — a caller must not be able to widen them at run time.

| Setting | Default | What it does |
|---|---|---|
| `cli_commands` | `False` | Attaches a sandboxed `run_command` tool |
| `cli_allowed_commands` | `[]` | The command allowlist. Anything else is refused |
| `cli_config` | `None` | Full `CliConfig` — `timeout`, `working_dir`, `allow_shell` |
| `local_code_execution` | `False` | Lets the agent execute code |
| `allowed_languages` | `[]` | Languages permitted for code execution |
| `code_execution` | `None` | Full code-execution configuration |
| `credentials` | `[]` | Secrets the server injects for the duration of a call |
| `prefill_tools` | `[]` | Tool results seeded before the first turn |

## Run settings

Passed to `run()`, `start()`, or `stream()`. They apply to one execution.

| Setting | What it does |
|---|---|
| `prompt` | The input for this run |
| `version` | Pin a specific deployed version |
| `media` | Files or images for this run |
| `session_id` | Ties runs together into a conversation |
| `idempotency_key` | Makes a retry return the original run instead of starting a new one |
| `timeout` | Wall-clock bound for this execution |
| `context` | Extra key-values available to the run |
| `credentials` | Secrets for this execution |
| `on_event` | Callback for streamed events |
| `run_settings` | Per-run model overrides — see below |

### Overriding the model for one run

`RunSettings` is the escape hatch for model choice without redeploying:

```python
from conductor.ai.agents import RunSettings

result = runtime.run(
    agent,
    "Summarise this incident.",
    run_settings=RunSettings(
        model="openai/gpt-4o",       # overrides the definition's model
        temperature=0.1,
        max_tokens=800,
        reasoning_effort="high",
        thinking_budget_tokens=2000,
    ),
)
```

`reasoning_effort` and `thinking_budget_tokens` are run-only — there is no definition equivalent.

## Which wins

| Setting | Definition | Run | Result |
|---|---|---|---|
| `model` | ✓ | ✓ (`RunSettings`) | Run wins, this execution only |
| `temperature` | ✓ | ✓ (`RunSettings`) | Run wins, this execution only |
| `max_tokens` | ✓ | ✓ (`RunSettings`) | Run wins, this execution only |
| `credentials` | ✓ | ✓ | Run adds to the definition's set |
| `max_turns`, `tools`, `guardrails`, `agents`, `strategy` | ✓ | — | Definition only. Redeploy to change |
| `reasoning_effort`, `thinking_budget_tokens` | — | ✓ | Run only |
| `session_id`, `idempotency_key`, `media`, `context` | — | ✓ | Run only |

## Production notes

- **Anything that widens reach is definition-only, by design.** Tools, guardrails, and CLI allowlists cannot be loosened by a caller.
- **`max_turns` is your loop bound.** The default of 25 is generous for a simple agent; lower it for anything running at volume.
- **Use `idempotency_key` for anything retried.** Without it, a retry is a second execution.
- **`session_id` is what makes a conversation.** Runs without one are independent.
- **Pin `version` for consequential callers,** so a redeploy can't change behaviour underneath them.
- **Put secrets in `credentials`, never in `instructions`.** They are injected for the call and not stored in the definition.

## Next steps

- [Deploying Agents](deploying-agents.md) — when definition settings actually take effect
- [Multi-Agent Architecture](multi-agent-architecture.md) — the `strategy` field in depth
- [Agent Guardrails](agent-guardrails.md) — the `guardrails` field in depth
