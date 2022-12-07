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
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.utils.TaskUtils;
import com.netflix.conductor.core.events.ScriptEvaluator;
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

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                    "Workflow {} waiting for tasks {} to complete iteration {}",
                    workflow.getWorkflowId(),
                    loopOverTasks.stream()
                            .map(TaskModel::getReferenceTaskName)
                            .collect(Collectors.toList()),
                    doWhileTaskModel.getIteration());
        }

        // if the loopOverTasks collection is empty, no tasks inside the loop have been scheduled.
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
            if (hasFailures) {
                break;
            }
        }
        doWhileTaskModel.addOutput(String.valueOf(doWhileTaskModel.getIteration()), output);

        if (hasFailures) {
            LOGGER.debug(
                    "Task {} failed in {} iteration",
                    doWhileTaskModel.getTaskId(),
                    doWhileTaskModel.getIteration() + 1);
            return markTaskFailure(
                    doWhileTaskModel, TaskModel.Status.FAILED, failureReason.toString());
        }

        if (!isIterationComplete(doWhileTaskModel, relevantTasks)) {
            // current iteration is not complete (all tasks inside the loop are not terminal)
            return false;
        }

        // if we are here, the iteration is complete, and we need to check if there is a next
        // iteration by evaluating the loopCondition
        boolean shouldContinue;
        try {
            shouldContinue = evaluateCondition(workflow, doWhileTaskModel);
            LOGGER.debug(
                    "Task {} condition evaluated to {}",
                    doWhileTaskModel.getTaskId(),
                    shouldContinue);
            if (shouldContinue) {
                doWhileTaskModel.setIteration(doWhileTaskModel.getIteration() + 1);
                doWhileTaskModel.addOutput("iteration", doWhileTaskModel.getIteration());
                return scheduleNextIteration(doWhileTaskModel, workflow, workflowExecutor);
            } else {
                LOGGER.debug(
                        "Task {} took {} iterations to complete",
                        doWhileTaskModel.getTaskId(),
                        doWhileTaskModel.getIteration() + 1);
                return markTaskSuccess(doWhileTaskModel);
            }
        } catch (ScriptException e) {
            String message =
                    String.format(
                            "Unable to evaluate condition %s, exception %s",
                            doWhileTaskModel.getWorkflowTask().getLoopCondition(), e.getMessage());
            LOGGER.error(message);
            return markTaskFailure(
                    doWhileTaskModel, TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, message);
        }
    }

    /**
     * Check if all tasks in the current iteration have reached terminal state.
     *
     * @param doWhileTaskModel The {@link TaskModel} of DO_WHILE.
     * @param referenceNameToModel Map of taskReferenceName to {@link TaskModel}.
     * @return true if all tasks in DO_WHILE.loopOver are in <code>referenceNameToModel</code> and
     *     reached terminal state.
     */
    private boolean isIterationComplete(
            TaskModel doWhileTaskModel, Map<String, TaskModel> referenceNameToModel) {
        List<WorkflowTask> workflowTasksInsideDoWhile =
                doWhileTaskModel.getWorkflowTask().getLoopOver();
        int iteration = doWhileTaskModel.getIteration();
        boolean allTasksTerminal = true;
        for (WorkflowTask workflowTaskInsideDoWhile : workflowTasksInsideDoWhile) {
            String taskReferenceName =
                    TaskUtils.appendIteration(
                            workflowTaskInsideDoWhile.getTaskReferenceName(), iteration);
            if (referenceNameToModel.containsKey(taskReferenceName)) {
                TaskModel taskModel = referenceNameToModel.get(taskReferenceName);
                if (!taskModel.getStatus().isTerminal()) {
                    allTasksTerminal = false;
                    break;
                }
            } else {
                allTasksTerminal = false;
                break;
            }
        }

        if (!allTasksTerminal) {
            // Cases where tasks directly inside loop over are not completed.
            // loopOver -> [task1 -> COMPLETED, task2 -> IN_PROGRESS]
            return false;
        }

        // Check all the tasks in referenceNameToModel are completed or not. These are set of tasks
        // which are not directly inside loopOver tasks, but they are under hierarchy
        // loopOver -> [decisionTask -> COMPLETED [ task1 -> COMPLETED, task2 -> IN_PROGRESS]]
        return referenceNameToModel.values().stream()
                .noneMatch(taskModel -> !taskModel.getStatus().isTerminal());
    }

    boolean scheduleNextIteration(
            TaskModel doWhileTaskModel, WorkflowModel workflow, WorkflowExecutor workflowExecutor) {
        LOGGER.debug(
                "Scheduling loop tasks for task {} as condition {} evaluated to true",
                doWhileTaskModel.getTaskId(),
                doWhileTaskModel.getWorkflowTask().getLoopCondition());
        workflowExecutor.scheduleNextIteration(doWhileTaskModel, workflow);
        return true; // Return true even though status not changed. Iteration has to be updated in
        // execution DAO.
    }

    boolean markTaskFailure(TaskModel taskModel, TaskModel.Status status, String failureReason) {
        LOGGER.error("Marking task {} failed with error.", taskModel.getTaskId());
        taskModel.setReasonForIncompletion(failureReason);
        taskModel.setStatus(status);
        return true;
    }

    boolean markTaskSuccess(TaskModel taskModel) {
        LOGGER.debug(
                "Task {} took {} iterations to complete",
                taskModel.getTaskId(),
                taskModel.getIteration() + 1);
        taskModel.setStatus(TaskModel.Status.COMPLETED);
        return true;
    }

    @VisibleForTesting
    boolean evaluateCondition(WorkflowModel workflow, TaskModel task) throws ScriptException {
        TaskDef taskDefinition = task.getTaskDefinition().orElse(null);
        // Use paramUtils to compute the task input
        Map<String, Object> conditionInput =
                parametersUtils.getTaskInputV2(
                        task.getWorkflowTask().getInputParameters(),
                        workflow,
                        task.getTaskId(),
                        taskDefinition);
        conditionInput.put(task.getReferenceTaskName(), task.getOutputData());
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
            conditionInput.put(
                    TaskUtils.removeIterationFromTaskRefName(loopOverTask.getReferenceTaskName()),
                    loopOverTask.getOutputData());
        }

        String condition = task.getWorkflowTask().getLoopCondition();
        boolean result = false;
        if (condition != null) {
            LOGGER.debug("Condition: {} is being evaluated", condition);
            // Evaluate the expression by using the Nashorn based script evaluator
            result = ScriptEvaluator.evalBool(condition, conditionInput);
        }
        return result;
    }
}
