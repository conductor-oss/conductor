/*
 * Copyright 2022 Netflix, Inc.
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

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.netflix.conductor.common.utils.TaskUtils;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_JOIN;

@Component(TASK_TYPE_JOIN)
public class Join extends WorkflowSystemTask {

    public Join() {
        super(TASK_TYPE_JOIN);
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean execute(
            WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {

        boolean allDone = true;
        boolean hasFailures = false;
        StringBuilder failureReason = new StringBuilder();
        StringBuilder optionalTaskFailures = new StringBuilder();
        List<String> joinOn = (List<String>) task.getInputData().get("joinOn");
        if (task.isLoopOverTask()) {
            // If join is part of loop over task, wait for specific iteration to get complete
            joinOn =
                    joinOn.stream()
                            .map(name -> TaskUtils.appendIteration(name, task.getIteration()))
                            .collect(Collectors.toList());
        }
        for (String joinOnRef : joinOn) {
            TaskModel forkedTask = workflow.getTaskByRefName(joinOnRef);
            if (forkedTask == null) {
                // Task is not even scheduled yet
                allDone = false;
                break;
            }
            TaskModel.Status taskStatus = forkedTask.getStatus();
            hasFailures = !taskStatus.isSuccessful() && !forkedTask.getWorkflowTask().isOptional();
            if (hasFailures) {
                failureReason.append(forkedTask.getReasonForIncompletion()).append(" ");
            }
            // Only add to task output if it's not empty
            if (!forkedTask.getOutputData().isEmpty()) {
                task.addOutput(joinOnRef, forkedTask.getOutputData());
            }
            if (!taskStatus.isTerminal()) {
                allDone = false;
            }
            if (hasFailures) {
                break;
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
        if (allDone || hasFailures || optionalTaskFailures.length() > 0) {
            if (hasFailures) {
                task.setReasonForIncompletion(failureReason.toString());
                task.setStatus(TaskModel.Status.FAILED);
            } else if (optionalTaskFailures.length() > 0) {
                task.setStatus(TaskModel.Status.COMPLETED_WITH_ERRORS);
                optionalTaskFailures.append("completed with errors");
                task.setReasonForIncompletion(optionalTaskFailures.toString());
            } else {
                task.setStatus(TaskModel.Status.COMPLETED);
            }
            return true;
        }
        return false;
    }

    @Override
    public Optional<Long> getEvaluationOffset(TaskModel taskModel, long defaultOffset) {
        int index = taskModel.getPollCount() > 0 ? taskModel.getPollCount() - 1 : 0;
        if (index == 0) {
            return Optional.of(0L);
        }
        return Optional.of(Math.min((long) Math.pow(2, index), defaultOffset));
    }

    public boolean isAsync() {
        return true;
    }
}
