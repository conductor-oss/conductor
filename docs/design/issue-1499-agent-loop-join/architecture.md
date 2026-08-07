# Architecture — Preserve Dynamic Tool Output Through Agent Loop JOIN

> `docs/design/architecture.md` is the repository-wide source of truth for the complete agent
> runtime layout, exact shared keys, types, and JOIN contract. This issue-specific document records
> the problem, scope, and implementation boundary for issue #1499 without redefining those shared
> contracts.

## 1. Problem

Tool-enabled agents compile an LLM turn into a `FORK_JOIN_DYNAMIC` followed by `JOIN`, an INLINE
state merge, and a `SET_VARIABLE` update inside a `DO_WHILE` loop. The generated tool tasks finish,
but their JOIN output can be empty. The merge task then receives no new observation, so
`workflow.variables._last_tool_results` does not change and the next LLM turn requests the same
tools until the loop reaches `max_turns`.

The failure is caused by a representation mismatch at the JOIN boundary:

1. `JavaScriptBuilder.enrichToolsScriptDynamic(...)` stamps the logical tool name into the generated
   `WorkflowTask.inputParameters` map under `_agent_tool_name`.
2. Dynamic task mapping creates the executable `TaskModel` and retains its `WorkflowTask`.
3. `Join.compactAgentOutput(TaskModel)` currently looks only in `TaskModel.inputData` for
   `_agent_tool_name`.
4. In the failing execution shape, the marker is absent from resolved `inputData` but remains in
   `TaskModel.workflowTask.inputParameters`.
5. Agent JOIN compaction sees neither `_state_updates` nor `state` in an ordinary HTTP or MCP tool
   result, returns an empty map, and omits the completed branch from JOIN output.

This is not a JOIN completion bug: the JOIN reaches `COMPLETED`. It is a loss of agent-specific
observation metadata while producing the JOIN output.

## 2. Scope

### In scope

- Resolve `_agent_tool_name` from the executable task input first and the retained workflow-task
  definition second.
- Preserve the completed tool's entire `Map<String,Object>` output under `_agent_tool_output`.
- Keep the JOIN output keyed by the actual, iteration-qualified fork task reference.
- Add focused unit coverage for the failing `DO_WHILE` dynamic-tool representation.
- Add consumer-level coverage proving the JOIN envelope becomes the next turn's tool result.

### Out of scope

- Changing `DO_WHILE` iteration scheduling or task-reference suffixing.
- Changing `FORK_JOIN_DYNAMIC` mapping or copying every workflow-task input into task input data.
- Changing JOIN terminal, failure, optional-task, or permissive-task semantics.
- Expanding agent JOIN output for unmarked branches.
- Changing non-agent JOIN output.
- Changing the agent prompt, LLM provider integration, tool dispatch, or maximum-turn behavior.
- Reproducing the issue against a live LLM, MCP server, or HTTP server in automated tests.

## 3. Existing and target flow

```text
LLM_CHAT_COMPLETE
  -> INLINE enrich tools
  -> FORK_JOIN_DYNAMIC
  -> dynamic HTTP / MCP / HUMAN / SIMPLE tool tasks
  -> JOIN
  -> INLINE merge state and observations
  -> SET_VARIABLE(_agent_state, _last_tool_results)
  -> next DO_WHILE iteration
```

The target change is confined to the JOIN output step. A marked task produces this value under its
actual joined reference:

```json
{
  "weather_0__1": {
    "_agent_tool_name": "weather",
    "_agent_tool_output": {
      "response": {
        "statusCode": 200,
        "body": {
          "temperature": 64
        }
      }
    }
  }
}
```

`JavaScriptBuilder.stateMergeScript()` already consumes this envelope and appends
`{"name": "weather", "output": {...}}` to `_last_tool_results`. No script contract change is
required.

## 4. Complete file layout

The implementation changes only the following production and test files:

| File | Responsibility |
|---|---|
| `core/src/main/java/com/netflix/conductor/core/execution/tasks/Join.java` | Add the workflow-task input fallback when resolving `_agent_tool_name`; retain existing compaction and status behavior. |
| `core/src/test/java/com/netflix/conductor/core/execution/tasks/JoinTest.java` | Cover fallback, precedence, unmarked-agent compatibility, non-agent compatibility, and null metadata. |
| `agentspan/src/test/java/org/conductoross/conductor/ai/agentspan/runtime/util/AgentToolJoinStateMergeScriptTest.java` | Execute the existing merge script with HTTP- and MCP-shaped JOIN envelopes and assert the next-turn tool-result shape. |

The design-doc set is:

| File | Responsibility |
|---|---|
| `docs/design/architecture.md` | Canonical shared runtime contract, names, types, and source layout. |
| `docs/design/data-model.md` | Canonical `_agent_tool_name` / `_agent_tool_output` envelope and invariants. |
| `docs/design/issue-1499-agent-loop-join/architecture.md` | Issue scope, root cause, compatibility boundary, and affected files. |
| `docs/design/issue-1499-agent-loop-join/implementation-plan.md` | Ordered implementation steps and exact algorithm. |
| `docs/design/issue-1499-agent-loop-join/testing.md` | Focused regression matrix and repository-evidenced commands. |

No public API, persistence schema, task-definition schema, or configuration property is added.

## 5. Exact contracts

The shared envelope and invariants are defined in
[`../data-model.md`](../data-model.md#5-agent-tool-join-envelope). The implementation uses the
existing concrete types:

| Symbol | Type | Relevant member |
|---|---|---|
| `TaskModel` | engine execution model | `Map<String,Object> getInputData()`, `Map<String,Object> getOutputData()`, `WorkflowTask getWorkflowTask()` |
| `WorkflowTask` | workflow task definition | `Map<String,Object> getInputParameters()` |
| `Join.compactAgentOutput(TaskModel)` | private static helper | returns `Map<String,Object>` |
| `_agent_tool_name` | internal marker | `String` logical tool name |
| `_agent_tool_output` | JOIN envelope value | complete `Map<String,Object>` task output |

Marker resolution order is deterministic:

1. Read `forkedTask.getInputData().get("_agent_tool_name")`.
2. If absent, read
   `forkedTask.getWorkflowTask().getInputParameters().get("_agent_tool_name")` when both objects
   exist.
3. If a marker is found, return a two-entry `LinkedHashMap` containing `_agent_tool_name` and
   `_agent_tool_output`.
4. If no marker is found, preserve the existing agent compaction of `_state_updates` and `state`.

Runtime `inputData` intentionally wins when both representations contain a marker because it is the
resolved execution input. The workflow-task value is a recovery path for the dynamic-task shape,
not an override.

## 6. Compatibility

- **Marked agent tool:** JOIN emits one non-empty observation with the complete output.
- **Unmarked agent branch:** JOIN continues to copy only `_state_updates` and `state`.
- **Non-agent workflow:** JOIN continues to copy the complete fork output directly.
- **Missing `WorkflowDef`, `WorkflowTask`, input map, or marker:** no exception and no accidental
  agent-tool envelope.
- **Iteration-qualified references:** unchanged; the envelope remains keyed by the reference already
  resolved by JOIN for the current loop iteration.

The change therefore repairs the missing observation without broadening general JOIN payloads or
altering orchestration decisions.
