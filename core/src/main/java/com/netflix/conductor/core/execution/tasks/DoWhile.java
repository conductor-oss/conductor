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

import java.util.*;
import java.util.stream.Collectors;

import javax.script.ScriptException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.netflix.conductor.annotations.VisibleForTesting;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.utils.TaskUtils;
import com.netflix.conductor.core.events.ScriptEvaluator;
import com.netflix.conductor.core.exception.TerminateWorkflowException;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_DO_WHILE;

@Component(TASK_TYPE_DO_WHILE)
public class DoWhile extends WorkflowSystemTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(DoWhile.class);

    private final ParametersUtils parametersUtils;

    public DoWhile(ParametersUtils parametersUtils) {
        super(TASK_TYPE_DO_WHILE);
        this.parametersUtils = parametersUtils;
    }

    @Override
    public void cancel(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
        task.setStatus(TaskModel.Status.CANCELED);
    }

    @Override
    public boolean execute(
            WorkflowModel workflow, TaskModel doWhileTaskModel, WorkflowExecutor workflowExecutor) {

        boolean allDone = true;
        boolean hasFailures = false;
        StringBuilder failureReason = new StringBuilder();
        Map<String, Object> output = new HashMap<>();

        /*
         * Get the latest set of tasks (the ones that have the highest retry count). We don't want to evaluate any tasks
         * that have already failed if there is a more current one (a later retry count).
         */
        Map<String, TaskModel> relevantTasks = new LinkedHashMap<>();
        TaskModel relevantTask;
        for (TaskModel t : workflow.getTasks()) {
            if (doWhileTaskModel
                            .getWorkflowTask()
                            .has(TaskUtils.removeIterationFromTaskRefName(t.getReferenceTaskName()))
                    && !doWhileTaskModel.getReferenceTaskName().equals(t.getReferenceTaskName())
                    && doWhileTaskModel.getIteration() == t.getIteration()) {
                relevantTask = relevantTasks.get(t.getReferenceTaskName());
                if (relevantTask == null || t.getRetryCount() > relevantTask.getRetryCount()) {
                    relevantTasks.put(t.getReferenceTaskName(), t);
                }
            }
        }
        Collection<TaskModel> loopOverTasks = relevantTasks.values();
        LOGGER.debug(
                "Workflow {} waiting for tasks {} to complete iteration {}",
                workflow.getWorkflowId(),
                loopOverTasks.stream()
                        .map(TaskModel::getReferenceTaskName)
                        .collect(Collectors.toList()),
                doWhileTaskModel.getIteration());

        // if the loopOver collection is empty, no tasks inside the loop have been scheduled.
        // so schedule it and exit the method.
        if (loopOverTasks.isEmpty()) {
            doWhileTaskModel.setIteration(1);
            doWhileTaskModel.addOutput("iteration", doWhileTaskModel.getIteration());
            return scheduleNextIteration(doWhileTaskModel, workflow, workflowExecutor);
        }

        for (TaskModel loopOverTask : loopOverTasks) {
            TaskModel.Status taskStatus = loopOverTask.getStatus();
            hasFailures = !taskStatus.isSuccessful();
            if (hasFailures) {
                failureReason.append(loopOverTask.getReasonForIncompletion()).append(" ");
            }
            output.put(
                    TaskUtils.removeIterationFromTaskRefName(loopOverTask.getReferenceTaskName()),
                    loopOverTask.getOutputData());
            allDone = taskStatus.isTerminal();
            if (!allDone || hasFailures) {
                break;
            }
        }
        doWhileTaskModel
                .getOutputData()
                .put(String.valueOf(doWhileTaskModel.getIteration()), output);
        if (hasFailures) {
            LOGGER.debug(
                    "Task {} failed in {} iteration",
                    doWhileTaskModel.getTaskId(),
                    doWhileTaskModel.getIteration() + 1);
            return updateLoopTask(
                    doWhileTaskModel, TaskModel.Status.FAILED, failureReason.toString());
        } else if (!allDone) {
            return false;
        }
        boolean shouldContinue;
        try {
            shouldContinue = getEvaluatedCondition(workflow, doWhileTaskModel, workflowExecutor);
            LOGGER.debug(
                    "Task {} condition evaluated to {}",
                    doWhileTaskModel.getTaskId(),
                    shouldContinue);
            if (shouldContinue) {
                doWhileTaskModel.setIteration(doWhileTaskModel.getIteration() + 1);
                doWhileTaskModel.getOutputData().put("iteration", doWhileTaskModel.getIteration());
                return scheduleNextIteration(doWhileTaskModel, workflow, workflowExecutor);
            } else {
                LOGGER.debug(
                        "Task {} took {} iterations to complete",
                        doWhileTaskModel.getTaskId(),
                        doWhileTaskModel.getIteration() + 1);
                return markLoopTaskSuccess(doWhileTaskModel);
            }
        } catch (ScriptException e) {
            String message =
                    String.format(
                            "Unable to evaluate condition %s , exception %s",
                            doWhileTaskModel.getWorkflowTask().getLoopCondition(), e.getMessage());
            LOGGER.error(message);
            LOGGER.error("Marking task {} failed with error.", doWhileTaskModel.getTaskId());
            return updateLoopTask(
                    doWhileTaskModel, TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, message);
        }
    }

    boolean scheduleNextIteration(
            TaskModel task, WorkflowModel workflow, WorkflowExecutor workflowExecutor) {
        LOGGER.debug(
                "Scheduling loop tasks for task {} as condition {} evaluated to true",
                task.getTaskId(),
                task.getWorkflowTask().getLoopCondition());
        workflowExecutor.scheduleNextIteration(task, workflow);
        return true; // Return true even though status not changed. Iteration has to be updated in
        // execution DAO.
    }

    boolean updateLoopTask(TaskModel task, TaskModel.Status status, String failureReason) {
        task.setReasonForIncompletion(failureReason);
        task.setStatus(status);
        return true;
    }

    boolean markLoopTaskSuccess(TaskModel task) {
        LOGGER.debug(
                "task {} took {} iterations to complete",
                task.getTaskId(),
                task.getIteration() + 1);
        task.setStatus(TaskModel.Status.COMPLETED);
        return true;
    }

    @VisibleForTesting
    boolean getEvaluatedCondition(
            WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor)
            throws ScriptException {
        TaskDef taskDefinition = null;
        try {
            taskDefinition = workflowExecutor.getTaskDefinition(task);
        } catch (TerminateWorkflowException e) {
            // It is ok to not have a task definition for a DO_WHILE task
        }

        Map<String, Object> taskInput =
                parametersUtils.getTaskInputV2(
                        task.getWorkflowTask().getInputParameters(),
                        workflow,
                        task.getTaskId(),
                        taskDefinition);
        taskInput.put(task.getReferenceTaskName(), task.getOutputData());
        List<TaskModel> loopOver =
                workflow.getTasks().stream()
                        .filter(
                                t ->
                                        (task.getWorkflowTask()
                                                        .has(
                                                                TaskUtils
                                                                        .removeIterationFromTaskRefName(
                                                                                t
                                                                                        .getReferenceTaskName()))
                                                && !task.getReferenceTaskName()
                                                        .equals(t.getReferenceTaskName())))
                        .collect(Collectors.toList());

        for (TaskModel loopOverTask : loopOver) {
            taskInput.put(
                    TaskUtils.removeIterationFromTaskRefName(loopOverTask.getReferenceTaskName()),
                    loopOverTask.getOutputData());
        }
        String condition = task.getWorkflowTask().getLoopCondition();
        boolean shouldContinue = false;
        if (condition != null) {
            LOGGER.debug("Condition: {} is being evaluated", condition);
            // Evaluate the expression by using the Nashorn based script evaluator
            shouldContinue = ScriptEvaluator.evalBool(condition, taskInput);
        }
        return shouldContinue;
    }
}
