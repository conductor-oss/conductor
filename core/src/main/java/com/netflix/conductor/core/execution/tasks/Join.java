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

        // Track resolved references and whether all tasks are terminal
        // Resolution happens lazily - we exit early if any task isn't terminal yet
        List<String> resolvedJoinOn = new ArrayList<>();
        boolean allTasksTerminal = true;

        for (String joinOnRef : joinOn) {
            // Resolve this reference (container tasks like Switch resolve to their terminal
            // child)
            String resolvedRef = resolveTaskReference(workflow, joinOnRef);
            resolvedJoinOn.add(resolvedRef);

            TaskModel forkedTask = workflow.getTaskByRefName(resolvedRef);
            if (forkedTask == null) {
                // Task not yet scheduled - can't be terminal
                allTasksTerminal = false;
                continue;
            }

            TaskModel.Status taskStatus = forkedTask.getStatus();

            if (!taskStatus.isTerminal()) {
                // Task not terminal - exit early, no need to check remaining tasks
                allTasksTerminal = false;
                continue;
            }

            // Only add to task output if it's not empty
            if (!forkedTask.getOutputData().isEmpty()) {
                task.addOutput(resolvedRef, forkedTask.getOutputData());
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

        LOGGER.debug(
                "Join task {} - original joinOn: {}, resolved joinOn: {}, allTerminal: {}",
                task.getTaskId(),
                joinOn,
                resolvedJoinOn,
                allTasksTerminal);

        // If not all tasks are terminal, we're still waiting
        if (!allTasksTerminal) {
            return false;
        }

        // All tasks are terminal - check for any non-optional failures
        // (both permissive and non-permissive are handled the same now)
        boolean hasNonOptionalFailure =
                resolvedJoinOn.stream()
                        .map(workflow::getTaskByRefName)
                        .filter(Objects::nonNull)
                        .filter(t -> !t.getStatus().isSuccessful())
                        .anyMatch(t -> !t.getWorkflowTask().isOptional());

        if (hasNonOptionalFailure) {
            final String failureReasons =
                    resolvedJoinOn.stream()
                            .map(workflow::getTaskByRefName)
                            .filter(Objects::nonNull)
                            .filter(t -> !t.getStatus().isSuccessful())
                            .filter(t -> !t.getWorkflowTask().isOptional())
                            .map(TaskModel::getReasonForIncompletion)
                            .filter(Objects::nonNull)
                            .collect(Collectors.joining(" "));
            task.setReasonForIncompletion(failureReasons.trim());
            task.setStatus(TaskModel.Status.FAILED);
            return true;
        }

        // Finalize the join task's status based on the outcomes of all referenced
        // tasks.
        if (!optionalTaskFailures.isEmpty()) {
            task.setStatus(TaskModel.Status.COMPLETED_WITH_ERRORS);
            optionalTaskFailures.append("completed with errors");
            task.setReasonForIncompletion(optionalTaskFailures.toString());
        } else {
            task.setStatus(TaskModel.Status.COMPLETED);
        }
        return true;
    }

    /**
     * Resolves a single task reference to account for container tasks (Switch, etc.).
     *
     * <p>When a task reference points to a system task that has completed, this method uses the
     * polymorphic {@link WorkflowSystemTask#getTerminalTaskRef} to determine the actual terminal
     * task reference. This allows container tasks like Switch to define their own resolution logic
     * without hardcoding task type checks in Join.
     *
     * @param workflow the workflow model
     * @param taskRef the task reference to resolve
     * @return the resolved task reference (may be the same as input if no resolution needed)
     */
    @VisibleForTesting
    String resolveTaskReference(WorkflowModel workflow, String taskRef) {
        TaskModel task = workflow.getTaskByRefName(taskRef);
        if (task == null) {
            // Task not yet scheduled, keep the original reference
            LOGGER.debug("Resolution: {} -> {} (task not yet scheduled)", taskRef, taskRef);
            return taskRef;
        }

        // Only resolve tasks that are terminal (so container tasks know their state)
        if (task.getStatus().isTerminal() && systemTaskRegistry.isSystemTask(task.getTaskType())) {
            // Use polymorphic resolution - each system task knows its own terminal
            // reference
            WorkflowSystemTask systemTask = systemTaskRegistry.get(task.getTaskType());
            String terminalRef = systemTask.getTerminalTaskRef(workflow, task, systemTaskRegistry);

            LOGGER.debug(
                    "Resolution: {} (type={}, status={}) -> terminalRef={}",
                    taskRef,
                    task.getTaskType(),
                    task.getStatus(),
                    terminalRef);

            return terminalRef;
        } else {
            LOGGER.debug(
                    "Resolution: {} (type={}, status={}) -> {} (no resolution: terminal={}, isSystemTask={})",
                    taskRef,
                    task.getTaskType(),
                    task.getStatus(),
                    taskRef,
                    task.getStatus().isTerminal(),
                    systemTaskRegistry.isSystemTask(task.getTaskType()));
            return taskRef;
        }
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
