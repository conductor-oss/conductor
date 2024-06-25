/*
 * Copyright 2023 Conductor Authors.
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

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_WHILE;

@Component(TASK_TYPE_WHILE)
public class While extends WorkflowSystemTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(While.class);

    private final ParametersUtils parametersUtils;

    public While(ParametersUtils parametersUtils) {
        super(TASK_TYPE_WHILE);
        this.parametersUtils = parametersUtils;
    }

    @Override
    public void cancel(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
        task.setStatus(TaskModel.Status.CANCELED);
    }

    @Override
    public boolean execute(
            WorkflowModel workflow, TaskModel whileTaskModel, WorkflowExecutor workflowExecutor) {
        // It evaluates the loop condition before executing the task list, and if the condition is
        // initially false, the task list will not be executed.
        if (whileTaskModel.getIteration() == 0) {
            try {
                boolean shouldContinue = evaluateCondition(workflow, whileTaskModel);
                LOGGER.debug(
                        "Task {} condition evaluated to {}",
                        whileTaskModel.getTaskId(),
                        shouldContinue);
                if (!shouldContinue) {
                    LOGGER.debug(
                            "Task {} took {} iterations to complete",
                            whileTaskModel.getTaskId(),
                            whileTaskModel.getIteration());
                    whileTaskModel.addOutput("iteration", whileTaskModel.getIteration());
                    return markTaskSuccess(whileTaskModel);
                }
            } catch (ScriptException e) {
                String message =
                        String.format(
                                "Unable to evaluate condition %s, exception %s",
                                whileTaskModel.getWorkflowTask().getLoopCondition(),
                                e.getMessage());
                LOGGER.error(message);
                return markTaskFailure(
                        whileTaskModel, TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, message);
            }
        }

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
            if (whileTaskModel
                            .getWorkflowTask()
                            .has(TaskUtils.removeIterationFromTaskRefName(t.getReferenceTaskName()))
                    && !Objects.equals(
                            whileTaskModel.getReferenceTaskName(), t.getReferenceTaskName())
                    && whileTaskModel.getIteration() == t.getIteration()) {
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
                    whileTaskModel.getIteration());
        }

        // if the loopOverTasks collection is empty, no tasks inside the loop have been scheduled.
        // so schedule it and exit the method.
        if (loopOverTasks.isEmpty()) {
            whileTaskModel.setIteration(1);
            whileTaskModel.addOutput("iteration", whileTaskModel.getIteration());
            return scheduleNextIteration(whileTaskModel, workflow, workflowExecutor);
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
        whileTaskModel.addOutput(String.valueOf(whileTaskModel.getIteration()), output);

        if (hasFailures) {
            LOGGER.debug(
                    "Task {} failed in {} iteration",
                    whileTaskModel.getTaskId(),
                    whileTaskModel.getIteration() + 1);
            return markTaskFailure(
                    whileTaskModel, TaskModel.Status.FAILED, failureReason.toString());
        }

        if (!isIterationComplete(whileTaskModel, relevantTasks)) {
            // current iteration is not complete (all tasks inside the loop are not terminal)
            return false;
        }

        // if we are here, the iteration is complete, and we need to check if there is a next
        // iteration by evaluating the loopCondition
        try {
            boolean shouldContinue = evaluateCondition(workflow, whileTaskModel);
            LOGGER.debug(
                    "Task {} condition evaluated to {}",
                    whileTaskModel.getTaskId(),
                    shouldContinue);
            if (shouldContinue) {
                whileTaskModel.setIteration(whileTaskModel.getIteration() + 1);
                whileTaskModel.addOutput("iteration", whileTaskModel.getIteration());
                return scheduleNextIteration(whileTaskModel, workflow, workflowExecutor);
            } else {
                LOGGER.debug(
                        "Task {} took {} iterations to complete",
                        whileTaskModel.getTaskId(),
                        whileTaskModel.getIteration() + 1);
                return markTaskSuccess(whileTaskModel);
            }
        } catch (ScriptException e) {
            String message =
                    String.format(
                            "Unable to evaluate condition %s, exception %s",
                            whileTaskModel.getWorkflowTask().getLoopCondition(), e.getMessage());
            LOGGER.error(message);
            return markTaskFailure(
                    whileTaskModel, TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, message);
        }
    }

    /**
     * Check if all tasks in the current iteration have reached terminal state.
     *
     * @param whileTaskModel The {@link TaskModel} of WHILE.
     * @param referenceNameToModel Map of taskReferenceName to {@link TaskModel}.
     * @return true if all tasks in WHILE.loopOver are in <code>referenceNameToModel</code> and
     *     reached terminal state.
     */
    private boolean isIterationComplete(
            TaskModel whileTaskModel, Map<String, TaskModel> referenceNameToModel) {
        List<WorkflowTask> workflowTasksInsideWhile =
                whileTaskModel.getWorkflowTask().getLoopOver();
        int iteration = whileTaskModel.getIteration();
        boolean allTasksTerminal = true;
        for (WorkflowTask workflowTaskInsideWhile : workflowTasksInsideWhile) {
            String taskReferenceName =
                    TaskUtils.appendIteration(
                            workflowTaskInsideWhile.getTaskReferenceName(), iteration);
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
            TaskModel whileTaskModel, WorkflowModel workflow, WorkflowExecutor workflowExecutor) {
        LOGGER.debug(
                "Scheduling loop tasks for task {} as condition {} evaluated to true",
                whileTaskModel.getTaskId(),
                whileTaskModel.getWorkflowTask().getLoopCondition());
        workflowExecutor.scheduleNextIteration(whileTaskModel, workflow);
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
        Map<String, Object> outputData = new HashMap<>(task.getOutputData());
        outputData.putIfAbsent("iteration", task.getIteration());
        conditionInput.put(task.getReferenceTaskName(), outputData);
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
                                                && !Objects.equals(
                                                        task.getReferenceTaskName(),
                                                        t.getReferenceTaskName())))
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
