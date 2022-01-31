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
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.utils.TaskUtils;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_EXCLUSIVE_JOIN;

@Component(TASK_TYPE_EXCLUSIVE_JOIN)
public class ExclusiveJoin extends WorkflowSystemTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExclusiveJoin.class);

    private static final String DEFAULT_EXCLUSIVE_JOIN_TASKS = "defaultExclusiveJoinTask";

    public ExclusiveJoin() {
        super(TASK_TYPE_EXCLUSIVE_JOIN);
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean execute(
            WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {

        boolean foundExlusiveJoinOnTask = false;
        boolean hasFailures = false;
        StringBuilder failureReason = new StringBuilder();
        TaskModel.Status taskStatus;
        List<String> joinOn = (List<String>) task.getInputData().get("joinOn");
        if (task.isLoopOverTask()) {
            // If exclusive join is part of loop over task, wait for specific iteration to get
            // complete
            joinOn =
                    joinOn.stream()
                            .map(name -> TaskUtils.appendIteration(name, task.getIteration()))
                            .collect(Collectors.toList());
        }
        TaskModel exclusiveTask = null;
        for (String joinOnRef : joinOn) {
            LOGGER.debug("Exclusive Join On Task {} ", joinOnRef);
            exclusiveTask = workflow.getTaskByRefName(joinOnRef);
            if (exclusiveTask == null || exclusiveTask.getStatus() == TaskModel.Status.SKIPPED) {
                LOGGER.debug("The task {} is either not scheduled or skipped.", joinOnRef);
                continue;
            }
            taskStatus = exclusiveTask.getStatus();
            foundExlusiveJoinOnTask = taskStatus.isTerminal();
            hasFailures = !taskStatus.isSuccessful();
            if (hasFailures) {
                failureReason.append(exclusiveTask.getReasonForIncompletion()).append(" ");
            }

            break;
        }

        if (!foundExlusiveJoinOnTask) {
            List<String> defaultExclusiveJoinTasks =
                    (List<String>) task.getInputData().get(DEFAULT_EXCLUSIVE_JOIN_TASKS);
            LOGGER.info(
                    "Could not perform exclusive on Join Task(s). Performing now on default exclusive join task(s) {}, workflow: {}",
                    defaultExclusiveJoinTasks,
                    workflow.getWorkflowId());
            if (defaultExclusiveJoinTasks != null && !defaultExclusiveJoinTasks.isEmpty()) {
                for (String defaultExclusiveJoinTask : defaultExclusiveJoinTasks) {
                    // Pick the first task that we should join on and break.
                    exclusiveTask = workflow.getTaskByRefName(defaultExclusiveJoinTask);
                    if (exclusiveTask == null
                            || exclusiveTask.getStatus() == TaskModel.Status.SKIPPED) {
                        LOGGER.debug(
                                "The task {} is either not scheduled or skipped.",
                                defaultExclusiveJoinTask);
                        continue;
                    }

                    taskStatus = exclusiveTask.getStatus();
                    foundExlusiveJoinOnTask = taskStatus.isTerminal();
                    hasFailures = !taskStatus.isSuccessful();
                    if (hasFailures) {
                        failureReason.append(exclusiveTask.getReasonForIncompletion()).append(" ");
                    }
                    break;
                }
            } else {
                LOGGER.debug(
                        "Could not evaluate last tasks output. Verify the task configuration in the workflow definition.");
            }
        }

        LOGGER.debug(
                "Status of flags: foundExlusiveJoinOnTask: {}, hasFailures {}",
                foundExlusiveJoinOnTask,
                hasFailures);
        if (foundExlusiveJoinOnTask || hasFailures) {
            if (hasFailures) {
                task.setReasonForIncompletion(failureReason.toString());
                task.setStatus(TaskModel.Status.FAILED);
            } else {
                task.setOutputData(exclusiveTask.getOutputData());
                task.setStatus(TaskModel.Status.COMPLETED);
            }
            LOGGER.debug("Task: {} status is: {}", task.getTaskId(), task.getStatus());
            return true;
        }
        return false;
    }
}
