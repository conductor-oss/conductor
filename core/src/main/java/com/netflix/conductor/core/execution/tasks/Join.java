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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.netflix.conductor.annotations.VisibleForTesting;
import com.netflix.conductor.common.utils.TaskUtils;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_JOIN;

@Component(TASK_TYPE_JOIN)
public class Join extends WorkflowSystemTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(Join.class);

    @VisibleForTesting static final double EVALUATION_OFFSET_BASE = 1.2;

    private final ConductorProperties properties;
    private final SystemTaskRegistry systemTaskRegistry;

    public Join(ConductorProperties properties, @Lazy SystemTaskRegistry systemTaskRegistry) {
        super(TASK_TYPE_JOIN);
        this.properties = properties;
        this.systemTaskRegistry = systemTaskRegistry;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean execute(
            WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        StringBuilder failureReason = new StringBuilder();
        StringBuilder optionalTaskFailures = new StringBuilder();
        List<String> joinOn = (List<String>) task.getInputData().get("joinOn");
        if (task.isLoopOverTask()) {
            // If join is part of loop over task, wait for specific iteration to get
            // complete
            joinOn =
                    joinOn.stream()
                            .map(name -> TaskUtils.appendIteration(name, task.getIteration()))
                            .toList();
        }

        // Resolve joinOn references - container tasks (Switch, etc.) are resolved to
        // their terminal child tasks using the polymorphic getTerminalTaskRef() method
        List<String> resolvedJoinOn = resolveJoinOnReferences(workflow, joinOn);
        LOGGER.debug(
                "Join task {} - original joinOn: {}, resolved joinOn: {}",
                task.getTaskId(),
                joinOn,
                resolvedJoinOn);

        boolean allTasksTerminal =
                resolvedJoinOn.stream()
                        .map(workflow::getTaskByRefName)
                        .allMatch(t -> t != null && t.getStatus().isTerminal());

        for (String joinOnRef : resolvedJoinOn) {
            TaskModel forkedTask = workflow.getTaskByRefName(joinOnRef);
            if (forkedTask == null) {
                // Continue checking other tasks if a referenced task is not yet scheduled
                continue;
            }

            TaskModel.Status taskStatus = forkedTask.getStatus();

            // Only add to task output if it's not empty
            if (!forkedTask.getOutputData().isEmpty()) {
                task.addOutput(joinOnRef, forkedTask.getOutputData());
            }

            // Determine if the join task fails immediately due to a non-optional,
            // non-permissive
            // task failure,
            // or waits for all tasks to be terminal if the failed task is permissive.
            var isJoinFailure =
                    !taskStatus.isSuccessful()
                            && !forkedTask.getWorkflowTask().isOptional()
                            && (!forkedTask.getWorkflowTask().isPermissive() || allTasksTerminal);
            if (isJoinFailure) {
                final String failureReasons =
                        resolvedJoinOn.stream()
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

        // Finalize the join task's status based on the outcomes of all referenced
        // tasks.
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
     * Resolves joinOn task references to account for container tasks (Switch, etc.).
     *
     * <p>When a joinOn reference points to a system task that has completed, this method uses the
     * polymorphic {@link WorkflowSystemTask#getTerminalTaskRef} to determine the actual terminal
     * task reference. This allows container tasks like Switch to define their own resolution logic
     * without hardcoding task type checks in Join.
     *
     * @param workflow the workflow model
     * @param joinOn the original list of task references to join on
     * @return a list of resolved task references
     */
    @VisibleForTesting
    List<String> resolveJoinOnReferences(WorkflowModel workflow, List<String> joinOn) {
        List<String> resolved = new ArrayList<>();
        for (String taskRef : joinOn) {
            TaskModel task = workflow.getTaskByRefName(taskRef);
            if (task == null) {
                // Task not yet scheduled, keep the original reference
                LOGGER.info("Resolution: {} -> {} (task not yet scheduled)", taskRef, taskRef);
                resolved.add(taskRef);
                continue;
            }

            // Only resolve tasks that are terminal (so container tasks know their state)
            if (task.getStatus().isTerminal()
                    && systemTaskRegistry.isSystemTask(task.getTaskType())) {
                // Use polymorphic resolution - each system task knows its own terminal
                // reference
                WorkflowSystemTask systemTask = systemTaskRegistry.get(task.getTaskType());
                String terminalRef =
                        systemTask.getTerminalTaskRef(workflow, task, systemTaskRegistry);

                LOGGER.info(
                        "Resolution: {} (type={}, status={}) -> terminalRef={}",
                        taskRef,
                        task.getTaskType(),
                        task.getStatus(),
                        terminalRef);

                if (!taskRef.equals(terminalRef)) {
                    LOGGER.debug(
                            "Resolved {} task {} to terminal task {}",
                            task.getTaskType(),
                            taskRef,
                            terminalRef);
                }
                resolved.add(terminalRef);
            } else {
                LOGGER.info(
                        "Resolution: {} (type={}, status={}) -> {} (no resolution: terminal={}, isSystemTask={})",
                        taskRef,
                        task.getTaskType(),
                        task.getStatus(),
                        taskRef,
                        task.getStatus().isTerminal(),
                        systemTaskRegistry.isSystemTask(task.getTaskType()));
                resolved.add(taskRef);
            }
        }
        return resolved;
    }

    @Override
    public Optional<Long> getEvaluationOffset(TaskModel taskModel, long maxOffset) {
        int pollCount = taskModel.getPollCount();
        // Assuming pollInterval = 50ms and evaluationOffsetThreshold = 200 this will
        // cause
        // a JOIN task to be evaluated continuously during the first 10 seconds and the
        // FORK/JOIN
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
