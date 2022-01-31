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
package com.netflix.conductor.core.execution.mapper;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.script.ScriptException;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.events.ScriptEvaluator;
import com.netflix.conductor.core.exception.TerminateWorkflowException;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import com.google.common.annotations.VisibleForTesting;

/**
 * An implementation of {@link TaskMapper} to map a {@link WorkflowTask} of type {@link
 * TaskType#DECISION} to a List {@link TaskModel} starting with Task of type {@link
 * TaskType#DECISION} which is marked as IN_PROGRESS, followed by the list of {@link TaskModel}
 * based on the case expression evaluation in the Decision task.
 *
 * @deprecated {@link com.netflix.conductor.core.execution.tasks.Decision} is also deprecated. Use
 *     {@link com.netflix.conductor.core.execution.tasks.Switch} and so ${@link SwitchTaskMapper}
 *     will be used as a result.
 */
@Deprecated
@Component
public class DecisionTaskMapper implements TaskMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(DecisionTaskMapper.class);

    @Override
    public TaskType getTaskType() {
        return TaskType.DECISION;
    }

    /**
     * This method gets the list of tasks that need to scheduled when the task to scheduled is of
     * type {@link TaskType#DECISION}.
     *
     * @param taskMapperContext: A wrapper class containing the {@link WorkflowTask}, {@link
     *     WorkflowDef}, {@link WorkflowModel} and a string representation of the TaskId
     * @return List of tasks in the following order:
     *     <ul>
     *       <li>{@link TaskType#DECISION} with {@link TaskModel.Status#IN_PROGRESS}
     *       <li>List of task based on the evaluation of {@link WorkflowTask#getCaseExpression()}
     *           are scheduled.
     *       <li>In case of no matching result after the evaluation of the {@link
     *           WorkflowTask#getCaseExpression()}, the {@link WorkflowTask#getDefaultCase()} Tasks
     *           are scheduled.
     *     </ul>
     */
    @Override
    public List<TaskModel> getMappedTasks(TaskMapperContext taskMapperContext) {
        LOGGER.debug("TaskMapperContext {} in DecisionTaskMapper", taskMapperContext);
        List<TaskModel> tasksToBeScheduled = new LinkedList<>();
        WorkflowTask taskToSchedule = taskMapperContext.getTaskToSchedule();
        WorkflowModel workflowInstance = taskMapperContext.getWorkflowInstance();
        Map<String, Object> taskInput = taskMapperContext.getTaskInput();
        int retryCount = taskMapperContext.getRetryCount();
        String taskId = taskMapperContext.getTaskId();

        // get the expression to be evaluated
        String caseValue = getEvaluatedCaseValue(taskToSchedule, taskInput);

        // QQ why is the case value and the caseValue passed and caseOutput passes as the same ??
        TaskModel decisionTask = new TaskModel();
        decisionTask.setTaskType(TaskType.TASK_TYPE_DECISION);
        decisionTask.setTaskDefName(TaskType.TASK_TYPE_DECISION);
        decisionTask.setReferenceTaskName(taskToSchedule.getTaskReferenceName());
        decisionTask.setWorkflowInstanceId(workflowInstance.getWorkflowId());
        decisionTask.setWorkflowType(workflowInstance.getWorkflowName());
        decisionTask.setCorrelationId(workflowInstance.getCorrelationId());
        decisionTask.setScheduledTime(System.currentTimeMillis());
        decisionTask.getInputData().put("case", caseValue);
        decisionTask.getOutputData().put("caseOutput", Collections.singletonList(caseValue));
        decisionTask.setTaskId(taskId);
        decisionTask.setStartTime(System.currentTimeMillis());
        decisionTask.setStatus(TaskModel.Status.IN_PROGRESS);
        decisionTask.setWorkflowTask(taskToSchedule);
        decisionTask.setWorkflowPriority(workflowInstance.getPriority());
        tasksToBeScheduled.add(decisionTask);

        // get the list of tasks based on the decision
        List<WorkflowTask> selectedTasks = taskToSchedule.getDecisionCases().get(caseValue);
        // if the tasks returned are empty based on evaluated case value, then get the default case
        // if there is one
        if (selectedTasks == null || selectedTasks.isEmpty()) {
            selectedTasks = taskToSchedule.getDefaultCase();
        }
        // once there are selected tasks that need to proceeded as part of the decision, get the
        // next task to be scheduled by using the decider service
        if (selectedTasks != null && !selectedTasks.isEmpty()) {
            WorkflowTask selectedTask =
                    selectedTasks.get(0); // Schedule the first task to be executed...
            // TODO break out this recursive call using function composition of what needs to be
            // done and then walk back the condition tree
            List<TaskModel> caseTasks =
                    taskMapperContext
                            .getDeciderService()
                            .getTasksToBeScheduled(
                                    workflowInstance,
                                    selectedTask,
                                    retryCount,
                                    taskMapperContext.getRetryTaskId());
            tasksToBeScheduled.addAll(caseTasks);
            decisionTask.getInputData().put("hasChildren", "true");
        }
        return tasksToBeScheduled;
    }

    /**
     * This method evaluates the case expression of a decision task and returns a string
     * representation of the evaluated result.
     *
     * @param taskToSchedule: The decision task that has the case expression to be evaluated.
     * @param taskInput: the input which has the values that will be used in evaluating the case
     *     expression.
     * @return A String representation of the evaluated result
     */
    @VisibleForTesting
    String getEvaluatedCaseValue(WorkflowTask taskToSchedule, Map<String, Object> taskInput) {
        String expression = taskToSchedule.getCaseExpression();
        String caseValue;
        if (StringUtils.isNotBlank(expression)) {
            LOGGER.debug("Case being evaluated using decision expression: {}", expression);
            try {
                // Evaluate the expression by using the Nashhorn based script evaluator
                Object returnValue = ScriptEvaluator.eval(expression, taskInput);
                caseValue = (returnValue == null) ? "null" : returnValue.toString();
            } catch (ScriptException e) {
                String errorMsg = String.format("Error while evaluating script: %s", expression);
                LOGGER.error(errorMsg, e);
                throw new TerminateWorkflowException(errorMsg);
            }

        } else { // In case of no case expression, get the caseValueParam and treat it as a string
            // representation of caseValue
            LOGGER.debug(
                    "No Expression available on the decision task, case value being assigned as param name");
            String paramName = taskToSchedule.getCaseValueParam();
            caseValue = "" + taskInput.get(paramName);
        }
        return caseValue;
    }
}
