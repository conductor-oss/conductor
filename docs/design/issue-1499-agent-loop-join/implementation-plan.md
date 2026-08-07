# Implementation Plan — Issue #1499 Agent Loop JOIN Output

This plan implements the contract in [architecture.md](./architecture.md). Exact shared keys, types,
and invariants remain canonical in [`../architecture.md`](../architecture.md#7-agent-loop-join-contract)
and [`../data-model.md`](../data-model.md#5-agent-tool-join-envelope).

## 1. Update JOIN marker resolution

Edit `core/src/main/java/com/netflix/conductor/core/execution/tasks/Join.java` only inside
`compactAgentOutput(TaskModel)` and, if useful for readability, one private marker-resolution
helper.

The algorithm is:

```text
output = forkedTask.outputData
toolName = forkedTask.inputData["_agent_tool_name"]

if toolName is null and forkedTask.workflowTask is not null:
    definitionInputs = forkedTask.workflowTask.inputParameters
    if definitionInputs is not null:
        toolName = definitionInputs["_agent_tool_name"]

if toolName is not null:
    return linked map {
        "_agent_tool_name": toolName,
        "_agent_tool_output": output
    }

return existing compact map containing only present keys from:
    "_state_updates", "state"
```

Implementation constraints:

- Do not change the outer `Join.execute(...)` loop.
- Do not add output for a fork task whose `outputData` is empty; the existing guard remains the
  owner of that behavior.
- Do not parse the iteration-qualified reference to infer a tool name.
- Do not mutate `TaskModel.inputData`, `WorkflowTask.inputParameters`, or tool output.
- Do not add `_agent_tool_name` to `AGENT_PROPAGATED_KEYS`; marked tool output requires the
  namespaced envelope, while unmarked state branches retain compact propagation.
- Preserve `LinkedHashMap` so the emitted envelope remains deterministic in tests and serialized
  output.

## 2. Add engine regression tests

Extend `core/src/test/java/com/netflix/conductor/core/execution/tasks/JoinTest.java` with focused
tests using real `WorkflowDef`, `WorkflowTask`, `WorkflowModel`, and `TaskModel` values. Existing
test helpers may be extended, but production behavior must not be mocked.

Required cases:

1. **Workflow-task fallback:** agent workflow; completed fork task; non-empty HTTP-shaped output;
   `_agent_tool_name` only in `WorkflowTask.inputParameters`; JOIN output contains the complete
   two-key envelope.
2. **Runtime precedence:** both input locations contain different names; emitted name comes from
   `TaskModel.inputData`.
3. **Unmarked agent compatibility:** output containing `state`, `_state_updates`, and an unrelated
   key emits only the two allowed state keys.
4. **Non-agent compatibility:** the same fork output is copied unchanged.
5. **Null safety:** absent workflow definition or absent `WorkflowTask` does not throw and does not
   enable the agent-tool envelope.

The primary regression should model loop execution identity by using an iteration-qualified task
reference such as `weather_0__1` in both the fork task and JOIN `joinOn` resolution. The test need
not execute the complete `DO_WHILE` scheduler because the defect is isolated to JOIN compaction
after task-reference resolution.

## 3. Add merge-consumer tests

Create
`agentspan/src/test/java/org/conductoross/conductor/ai/agentspan/runtime/util/AgentToolJoinStateMergeScriptTest.java`.

Use the repository's existing GraalJS test pattern for `JavaScriptBuilder` scripts. Execute
`JavaScriptBuilder.stateMergeScript()` with:

- an HTTP-shaped `_agent_tool_output` containing `response.statusCode` and `response.body`;
- an MCP-shaped `_agent_tool_output` containing `content` and `isError`; and
- an existing `previousToolResults` list to prove observations append rather than replace history.

Assert that `toolResults` contains entries shaped as `Map<String,Object>` with exactly the logical
`name` and complete `output` value expected by the next LLM turn. Also assert that existing
`_state_updates` behavior remains intact.

No live service, network socket, credential, or full example execution is part of this test.

## 4. Documentation consistency

Keep these documents mutually consistent:

- `docs/design/architecture.md` owns the shared JOIN contract and source layout.
- `docs/design/data-model.md` owns the two-key envelope and invariants.
- This issue set owns rationale, implementation sequencing, and regression coverage.

Do not document a new API, configuration property, task type, or persisted field because the fix
introduces none.

## 5. Completion criteria

- A completed marked dynamic tool contributes a non-empty JOIN entry even when its marker exists
  only on `WorkflowTask.inputParameters`.
- The complete HTTP, MCP, HUMAN, SIMPLE, media, RAG, or sub-workflow tool output remains available
  to `stateMergeScript()` without provider-specific extraction in JOIN.
- Runtime marker precedence, unmarked agent compaction, non-agent JOIN output, and JOIN lifecycle
  behavior remain unchanged.
- Focused core and agentspan tests pass after formatting.
