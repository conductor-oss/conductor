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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.netflix.conductor.annotations.VisibleForTesting;
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
     * Marker key present in an AgentSpan agent execution's workflow input/variables. When set, the
     * embedded AgentSpan runtime owns the workflow and the JOIN output is kept compact (see {@link
     * #AGENT_PROPAGATED_KEYS}).
     */
    private static final String AGENTSPAN_CTX = "__agentspan_ctx__";

    /**
     * Keys propagated from fork-branch outputs into the JOIN output for AgentSpan agent executions.
     * Only these are copied so the JOIN payload stays small for multi-agent merges — full fork
     * outputs are read directly from the individual tool tasks by the agent message builder, so
     * duplicating them in JOIN is unnecessary. This mirrors AgentSpan's own JOIN task; for
     * non-agent workflows the full fork output is copied as before.
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

            // Only add to task output if it's not empty. For AgentSpan agent executions, copy
            // only the agent merge keys (compact) to keep the JOIN payload small; otherwise copy
            // the full fork output (default Conductor behavior).
            if (!forkedTask.getOutputData().isEmpty()) {
                if (agentExecution) {
                    Map<String, Object> compact = compactAgentOutput(forkedTask.getOutputData());
                    if (!compact.isEmpty()) {
                        task.addOutput(joinOnRef, compact);
                    }
                } else {
                    task.addOutput(joinOnRef, forkedTask.getOutputData());
                }
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

    /**
     * True when this workflow is an embedded AgentSpan agent execution, detected via the {@code
     * __agentspan_ctx__} marker on the workflow input or variables. Inert for all other workflows.
     */
    private static boolean isAgentExecution(WorkflowModel workflow) {
        return (workflow.getInput() != null && workflow.getInput().containsKey(AGENTSPAN_CTX))
                || (workflow.getVariables() != null
                        && workflow.getVariables().containsKey(AGENTSPAN_CTX));
    }

    /** Returns a copy of {@code output} containing only {@link #AGENT_PROPAGATED_KEYS}. */
    private static Map<String, Object> compactAgentOutput(Map<String, Object> output) {
        Map<String, Object> compact = new LinkedHashMap<>();
        if (output != null) {
            for (String key : AGENT_PROPAGATED_KEYS) {
                if (output.containsKey(key)) {
                    compact.put(key, output.get(key));
                }
            }
        }
        return compact;
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
