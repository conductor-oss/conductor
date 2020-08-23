/*
 * Copyright 2018 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.conductor.core.execution.mapper;

import com.google.common.annotations.VisibleForTesting;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.workflow.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.events.ScriptEvaluator;
import com.netflix.conductor.core.execution.SystemTaskType;
import com.netflix.conductor.core.execution.TerminateWorkflowException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.ScriptException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;


/**
 * An implementation of {@link TaskMapper} to map a {@link WorkflowTask} of type {@link TaskType#DECISION}
 * to a List {@link Task} starting with Task of type {@link SystemTaskType#DECISION} which is marked as IN_PROGRESS,
 * followed by the list of {@link Task} based on the case expression evaluation in the Decision task.
 */
public class DecisionTaskMapper implements TaskMapper {

    private static final Logger logger = LoggerFactory.getLogger(DecisionTaskMapper.class);

    /**
     * This method gets the list of tasks that need to scheduled when the task to scheduled is of type {@link TaskType#DECISION}.
     *
     * @param taskMapperContext: A wrapper class containing the {@link WorkflowTask}, {@link WorkflowDef}, {@link Workflow} and a string representation of the TaskId
     * @return List of tasks in the following order:
     * <ul>
     * <li>
     * {@link SystemTaskType#DECISION} with {@link Task.Status#IN_PROGRESS}
     * </li>
     * <li>
     * List of task based on the evaluation of {@link WorkflowTask#getCaseExpression()} are scheduled.
     * </li>
     * <li>
     * In case of no matching result after the evaluation of the {@link WorkflowTask#getCaseExpression()}, the {@link WorkflowTask#getDefaultCase()}
     * Tasks are scheduled.
     * </li>
     * </ul>
     */
    @Override
    public List<Task> getMappedTasks(TaskMapperContext taskMapperContext) {
        logger.debug("TaskMapperContext {} in DecisionTaskMapper", taskMapperContext);
        List<Task> tasksToBeScheduled = new LinkedList<>();
        WorkflowTask taskToSchedule = taskMapperContext.getTaskToSchedule();
        Workflow workflowInstance = taskMapperContext.getWorkflowInstance();
        Map<String, Object> taskInput = taskMapperContext.getTaskInput();
        int retryCount = taskMapperContext.getRetryCount();
        String taskId = taskMapperContext.getTaskId();

        //get the expression to be evaluated
        String caseValue = getEvaluatedCaseValue(taskToSchedule, taskInput);

        //QQ why is the case value and the caseValue passed and caseOutput passes as the same ??
        Task decisionTask = new Task();
        decisionTask.setTaskType(SystemTaskType.DECISION.name());
        decisionTask.setTaskDefName(SystemTaskType.DECISION.name());
        decisionTask.setReferenceTaskName(taskToSchedule.getTaskReferenceName());
        decisionTask.setWorkflowInstanceId(workflowInstance.getWorkflowId());
        decisionTask.setWorkflowType(workflowInstance.getWorkflowName());
        decisionTask.setCorrelationId(workflowInstance.getCorrelationId());
        decisionTask.setScheduledTime(System.currentTimeMillis());
        decisionTask.getInputData().put("case", caseValue);
        decisionTask.getOutputData().put("caseOutput", Collections.singletonList(caseValue));
        decisionTask.setTaskId(taskId);
        decisionTask.setStartTime(System.currentTimeMillis());
        decisionTask.setStatus(Task.Status.IN_PROGRESS);
        decisionTask.setWorkflowTask(taskToSchedule);
        decisionTask.setWorkflowPriority(workflowInstance.getPriority());
        tasksToBeScheduled.add(decisionTask);

        //get the list of tasks based on the decision
        List<WorkflowTask> selectedTasks = taskToSchedule.getDecisionCases().get(caseValue);
        //if the tasks returned are empty based on evaluated case value, then get the default case if there is one
        if (selectedTasks == null || selectedTasks.isEmpty()) {
            selectedTasks = taskToSchedule.getDefaultCase();
        }
        //once there are selected tasks that need to proceeded as part of the decision, get the next task to be
        // scheduled by using the decider service
        if (selectedTasks != null && !selectedTasks.isEmpty()) {
            WorkflowTask selectedTask = selectedTasks.get(0);        //Schedule the first task to be executed...
            //TODO break out this recursive call using function composition of what needs to be done and then walk back the condition tree
            List<Task> caseTasks = taskMapperContext.getDeciderService().getTasksToBeScheduled(workflowInstance, selectedTask, retryCount, taskMapperContext.getRetryTaskId());
            tasksToBeScheduled.addAll(caseTasks);
            decisionTask.getInputData().put("hasChildren", "true");
        }
        return tasksToBeScheduled;
    }

    /**
     * This method evaluates the case expression of a decision task and returns a string representation of the evaluated result.
     *
     * @param taskToSchedule: The decision task that has the case expression to be evaluated.
     * @param taskInput:      the input which has the values that will be used in evaluating the case expression.
     * @return A String representation of the evaluated result
     */
    @VisibleForTesting
    String getEvaluatedCaseValue(WorkflowTask taskToSchedule, Map<String, Object> taskInput) {
        String expression = taskToSchedule.getCaseExpression();
        String caseValue;
        if (StringUtils.isNotBlank(expression)) {
            logger.debug("Case being evaluated using decision expression: {}", expression);
            try {
                //Evaluate the expression by using the Nashhorn based script evaluator
                Object returnValue = ScriptEvaluator.eval(expression, taskInput);
                caseValue = (returnValue == null) ? "null" : returnValue.toString();
            } catch (ScriptException e) {
                String errorMsg = String.format("Error while evaluating script: %s", expression);
                logger.error(errorMsg, e);
                throw new TerminateWorkflowException(errorMsg);
            }

        } else {//In case of no case expression, get the caseValueParam and treat it as a string representation of caseValue
            logger.debug("No Expression available on the decision task, case value being assigned as param name");
            String paramName = taskToSchedule.getCaseValueParam();
            caseValue = "" + taskInput.get(paramName);
        }
        return caseValue;
    }
}
