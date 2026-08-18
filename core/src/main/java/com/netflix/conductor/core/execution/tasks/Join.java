/*
 * Copyright 2022 Conductor Authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.core.execution.tasks;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.netflix.conductor.annotations.VisibleForTesting;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.utils.TaskUtils;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_JOIN;

@Component(TASK_TYPE_JOIN)
public class Join extends WorkflowSystemTask {

    @VisibleForTesting static final double EVALUATION_OFFSET_BASE = 1.2;

    /**
     * Keys propagated from fork-branch outputs into the JOIN output for Conductor-Agents agent
     * executions. Only these are copied so the JOIN payload stays small for multi-agent merges —
     * full fork outputs are read directly from the individual tool tasks by the agent message
     * builder, so duplicating them in JOIN is unnecessary. This mirrors the Conductor-Agents JOIN
     * task; for non-agent workflows the full fork output is copied as before.
     */
    private static final Set<String> AGENT_PROPAGATED_KEYS = Set.of("_state_updates", "state");

    private final ConductorProperties properties;

    public Join(ConductorProperties properties) {
        super(TASK_TYPE_JOIN);
        this.properties = properties;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean execute(
            WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        StringBuilder failureReason = new StringBuilder();
        StringBuilder optionalTaskFailures = new StringBuilder();
        boolean agentExecution = isAgentExecution(workflow);
        List<String> joinOn = (List<String>) task.getInputData().get("joinOn");
        if (task.isLoopOverTask()) {
            // If join is part of loop over task, wait for specific iteration to get complete
            joinOn =
                    joinOn.stream()
                            .map(name -> TaskUtils.appendIteration(name, task.getIteration()))
                            .toList();
        }

        boolean allTasksTerminal =
                joinOn.stream()
                        .map(workflow::getTaskByRefName)
                        .allMatch(t -> t != null && t.getStatus().isTerminal());

        for (String joinOnRef : joinOn) {
            TaskModel forkedTask = workflow.getTaskByRefName(joinOnRef);
            if (forkedTask == null) {
                // Continue checking other tasks if a referenced task is not yet scheduled
                continue;
            }

            TaskModel.Status taskStatus = forkedTask.getStatus();

            // Only add to task output if it's not empty.
            if (!forkedTask.getOutputData().isEmpty()) {
                Map<String, Object> forkOutput = forkedTask.getOutputData();
                if (agentExecution) {
                    forkOutput = prepareAgentOutput(forkedTask);
                }
                task.addOutput(joinOnRef, forkOutput);
            }

            // Determine if the join task fails immediately due to a non-optional, non-permissive
            // task failure,
            // or waits for all tasks to be terminal if the failed task is permissive.
            var isJoinFailure =
                    !taskStatus.isSuccessful()
                            && !forkedTask.getWorkflowTask().isOptional()
                            && (!forkedTask.getWorkflowTask().isPermissive() || allTasksTerminal);
            if (isJoinFailure) {
                final String failureReasons =
                        joinOn.stream()
                                .map(workflow::getTaskByRefName)
                                .filter(Objects::nonNull)
                                .filter(t -> !t.getStatus().isSuccessful())
                                .map(TaskModel::getReasonForIncompletion)
                                .collect(Collectors.joining(" "));
                failureReason.append(failureReasons);
                task.setReasonForIncompletion(failureReason.toString());
                task.setStatus(TaskModel.Status.FAILED);
                return true;
            }

            // check for optional task failures
            if (forkedTask.getWorkflowTask().isOptional()
                    && taskStatus == TaskModel.Status.COMPLETED_WITH_ERRORS) {
                optionalTaskFailures
                        .append(
                                String.format(
                                        "%s/%s",
                                        forkedTask.getTaskDefName(), forkedTask.getTaskId()))
                        .append(" ");
            }
        }

        // Finalize the join task's status based on the outcomes of all referenced tasks.
        if (allTasksTerminal) {
            if (!optionalTaskFailures.isEmpty()) {
                task.setStatus(TaskModel.Status.COMPLETED_WITH_ERRORS);
                optionalTaskFailures.append("completed with errors");
                task.setReasonForIncompletion(optionalTaskFailures.toString());
            } else {
                task.setStatus(TaskModel.Status.COMPLETED);
            }
            return true;
        }

        // Task execution not complete, waiting on more tasks to reach terminal state.
        return false;
    }

    private static boolean isAgentExecution(WorkflowModel workflow) {
        WorkflowDef def = workflow.getWorkflowDefinition();
        return def != null && def.isAgent();
    }

    /** True when the fork branch carries state intended for a multi-agent state merge. */
    private static boolean carriesAgentState(Map<String, Object> output) {
        return output != null && AGENT_PROPAGATED_KEYS.stream().anyMatch(output::containsKey);
    }

    /**
     * Shapes a fork branch's output for a Conductor-Agents JOIN:
     *
     * <ul>
     *   <li>Branches marked with {@code _agent_tool_name} keep their tool identity: the full output
     *       is wrapped under {@code _agent_tool_output}. Identity takes precedence because a tool
     *       output may also contain state updates.
     *   <li>State-bearing branches (any {@link #AGENT_PROPAGATED_KEYS}) are compacted to just the
     *       merge keys to keep the JOIN payload small.
     *   <li>Unmarked outputs (e.g. MCP or HTTP tool results) pass through untouched for the ReAct
     *       state merge.
     * </ul>
     *
     * <p>Constructed maps are unmodifiable; the pass-through branch intentionally returns the
     * task's live output map to preserve default JOIN behavior.
     */
    private static Map<String, Object> prepareAgentOutput(TaskModel forkedTask) {
        Map<String, Object> output = forkedTask.getOutputData();
        Map<String, Object> compact = new LinkedHashMap<>();
        Object agentToolName = getAgentToolName(forkedTask);
        if (agentToolName != null) {
            // LinkedHashMap (not Map.of): tool outputs may legitimately contain null values.
            Map<String, Object> toolOutput = new LinkedHashMap<>();
            toolOutput.put("_agent_tool_name", agentToolName);
            toolOutput.put("_agent_tool_output", output);
            return Collections.unmodifiableMap(toolOutput);
        }

        if (carriesAgentState(output)) {
            return compactAgentOutput(output);
        }
        return output;
    }

    private static Map<String, Object> compactAgentOutput(Map<String, Object> output) {
        Map<String, Object> compact = new LinkedHashMap<>();
        if (output != null) {
            for (String key : AGENT_PROPAGATED_KEYS) {
                if (output.containsKey(key)) {
                    compact.put(key, output.get(key));
                }
            }
        }
        return Collections.unmodifiableMap(compact);
    }

    private static Object getAgentToolName(TaskModel forkedTask) {
        Map<String, Object> input = forkedTask.getInputData();
        Object agentToolName = input.get("_agent_tool_name");
        if (agentToolName != null) {
            return agentToolName;
        }

        // SUB_WORKFLOW tasks keep the original task input under workflowInput. Read the agent
        // dispatch metadata there without changing the mapper's established input contract.
        Object workflowInput = input.get("workflowInput");
        if (workflowInput instanceof Map<?, ?> nestedInput) {
            return nestedInput.get("_agent_tool_name");
        }
        return null;
    }

    @Override
    public Optional<Long> getEvaluationOffset(TaskModel taskModel, long maxOffset) {
        // Check if joinMode is set to SYNC — read directly from the workflow task definition
        // rather than from input data so the value is never duplicated into the task's payload.
        WorkflowTask workflowTask = taskModel.getWorkflowTask();
        if (workflowTask != null && WorkflowTask.JoinMode.SYNC == workflowTask.getJoinMode()) {
            // Synchronous mode: evaluate immediately every time (no backoff)
            return Optional.of(0L);
        }

        // Asynchronous mode (default): use exponential backoff
        int pollCount = taskModel.getPollCount();
        // Assuming pollInterval = 50ms and evaluationOffsetThreshold = 200 this will cause
        // a JOIN task to be evaluated continuously during the first 10 seconds and the FORK/JOIN
        // will end with minimal delay.
        if (pollCount <= properties.getSystemTaskPostponeThreshold()) {
            return Optional.of(0L);
        }

        double exp = pollCount - properties.getSystemTaskPostponeThreshold();
        return Optional.of(Math.min((long) Math.pow(EVALUATION_OFFSET_BASE, exp), maxOffset));
    }

    public boolean isAsync() {
        return true;
    }
}
