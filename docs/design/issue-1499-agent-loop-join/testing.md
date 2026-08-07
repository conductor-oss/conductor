# Testing — Issue #1499 Agent Loop JOIN Output

This test plan verifies the implementation described in [architecture.md](./architecture.md) and
[implementation-plan.md](./implementation-plan.md).

## 1. Regression matrix

| Case | Setup | Expected result |
|---|---|---|
| Dynamic marker fallback | Agent `WorkflowDef`; completed iteration-qualified fork task; `_agent_tool_name` only in `WorkflowTask.inputParameters` | JOIN completes and emits `_agent_tool_name` plus complete `_agent_tool_output`. |
| Runtime marker precedence | Different marker values in `TaskModel.inputData` and `WorkflowTask.inputParameters` | JOIN uses the runtime `inputData` value. |
| HTTP observation | Envelope wraps `response.statusCode` and `response.body` | Merge script appends `{name, output}` without dropping response fields. |
| MCP observation | Envelope wraps `content` and `isError` | Merge script appends `{name, output}` without provider-specific conversion. |
| Prior observations | `previousToolResults` already contains an entry | Merge script retains prior entries and appends the current turn. |
| State update compatibility | Tool output includes `_state_updates` | Merge script preserves existing shallow state-merge behavior. |
| Unmarked agent branch | Output contains state keys plus unrelated data | JOIN emits only `_state_updates` and `state`. |
| Non-agent workflow | Same completed fork task without agent metadata | JOIN copies the full output unchanged. |
| Missing metadata | Null workflow definition or null workflow task | JOIN does not throw and does not create a marked-tool envelope. |

## 2. Why focused tests are sufficient

The observed workflow already proves that dynamic tools reach terminal status. The defect occurs
after JOIN resolves the current iteration's task references and before the existing merge script
receives the task output. A real `Join.execute(...)` unit test covers that engine boundary without
requiring an LLM, an MCP transport, an HTTP endpoint, or repeated `DO_WHILE` scheduling.

The script test covers the next boundary independently: once JOIN emits the documented envelope,
`stateMergeScript()` must append the observation to `_last_tool_results`. Together these tests cover
the complete data path that failed in issue #1499 while remaining deterministic.

## 3. Repository-evidenced commands

Run from the repository root, in this order:

```bash
./gradlew spotlessApply
./gradlew :conductor-core:test --tests com.netflix.conductor.core.execution.tasks.JoinTest
./gradlew :conductor-agentspan:test --tests org.conductoross.conductor.ai.agentspan.runtime.util.AgentToolJoinStateMergeScriptTest
```

These commands are already used by the repository design guidance for the affected modules. Do not
replace them with custom Gradle configuration or sandbox workarounds.

## 4. Acceptance criteria

1. `JoinTest` proves the workflow-task marker fallback and compatibility cases.
2. `AgentToolJoinStateMergeScriptTest` proves HTTP- and MCP-shaped observations reach the next-turn
   tool-result list with complete outputs.
3. Spotless completes without changing unrelated files.
4. The two focused test commands pass.
5. If a command is blocked by sandbox restrictions on networking, sockets, file locks, or another
   operating-system facility, report the exact command and blocker and stop without changing
   Gradle, cache, daemon, wrapper, environment, or project configuration.
