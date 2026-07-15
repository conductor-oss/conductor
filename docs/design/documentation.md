# Conductor Agents — Documentation Updates

Addresses review item 4 ("update docs `docs/`"). All content is derived from the shipped
source per `CLAUDE.md`/`AGENTS.md`; field names and the `agentType` selector come from
[`architecture.md`](./architecture.md).

## 1. New page: `docs/devguide/ai/conductor-agents.md`

A sibling to the existing `docs/devguide/ai/a2a-integration.md`. Structure:

1. **What it is** — the `AGENT` task's `conductor` branch starts a registered agent workflow
   through Conductor's core executor, in contrast to the `a2a` branch that calls a remote agent.
2. **Task input** — table of the `conductor`-branch fields from `A2ACallRequest`
   (`architecture.md` §4.2). Include the prompt-resolution order (`message` → `parts` →
   `text` → `prompt`).
3. **Task output** — table of the `ConductorAgentResults` keys (`architecture.md` §4.3) and
   the state → status mapping (§4.5).
4. **Minimal workflow** — the `AGENT` task JSON from `examples.md` §2 (agentType `conductor`).
5. **Human-in-the-loop / resume** — explain the `WAITING` → resume flow: the task COMPLETES
   with `waiting=true` + `pendingTool`, and the workflow resumes with a second `AGENT` call
   carrying the same `executionId`. Link to example `32`.
6. **Durability** — the deterministic idempotency key (`architecture.md` §4.6), the absolute
   deadline (`maxDurationSeconds`, default 86400), and the poll-failure cap (`maxPollFailures`,
   default 30). Note these mirror the `a2a` branch's guards.
7. **Examples** — link to `ai/examples/31` and `ai/examples/32`.

Every code block is copied from an example JSON or the source, not paraphrased.

## 2. Edit: `docs/devguide/ai/a2a-integration.md`

The `AGENT` task now has two runtimes. Add a short **"Choosing a runtime"** note near where
`agentType` is first introduced:

- `agentType: "a2a"` (default) — call a remote Agent2Agent endpoint (`agentUrl`).
- `agentType: "conductor"` — run a registered Conductor agent workflow (`agentName`);
  see [conductor-agents.md](./conductor-agents.md).

Only add the cross-reference and the two-value clarification; do not rewrite existing A2A
content (per the "editing an existing section" rule — verify surrounding blocks while there).

## 3. Edit: `docs/devguide/ai/index.md`

Add `conductor-agents.md` to the AI dev-guide index/navigation list alongside
`a2a-integration.md`, so the new page is discoverable. One line, matching the existing entry
format.

## 4. Verification checklist

- `agentType` values and field names match `A2ACallRequest` / `A2AService.AGENT_TYPE_CONDUCTOR`.
- Output keys match `ConductorAgentResults`.
- The registered-agent requirement is stated consistently: the named agent definition must already
  exist in Conductor before an `AGENT` task can start it.
- The execute endpoint in any curl example is `POST /api/workflow/{name}` (as used in the
  existing examples README) — no invented routes.
- If a live server is unavailable to confirm output blocks, mark them
  `<!-- TODO: verify against live server -->` and call it out in the PR description.

## 5. Out of scope

- No changes to REST controllers, SDK, or the API reference — this branch adds no new HTTP
  endpoint; it is driven entirely through the existing `AGENT` task definition.
- No renaming of existing docs.
