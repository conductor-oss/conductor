package com.netflix.conductor.core.execution.mapper;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.events.ScriptEvaluator;
import com.netflix.conductor.core.execution.SystemTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.ScriptException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class DecisionTaskMapper implements TaskMapper {

    Logger logger = LoggerFactory.getLogger(DecisionTaskMapper.class);

    @Override
    public List<Task> getMappedTasks(TaskMapperContext taskMapperContext) {
        List<Task> tasksToBeScheduled = new LinkedList<>();
        WorkflowTask taskToSchedule = taskMapperContext.getTaskToSchedule();
        Workflow workflowInstance = taskMapperContext.getWorkflowInstance();
        WorkflowDef workflowDefinition = taskMapperContext.getWorkflowDefinition();
        Map<String, Object> taskInput = taskMapperContext.getTaskInput();
        int retryCount = taskMapperContext.getRetryCount();
        String taskId = taskMapperContext.getTaskId();

        //get the expression to be evaluated
        String expression = taskToSchedule.getCaseExpression();
        String caseValue;
        if (expression != null) {

            try {
                //Evaluate the expression by using the Nashhorn based script evaluator
                Object returnValue = ScriptEvaluator.eval(expression, taskInput);
                caseValue = (returnValue == null) ? "null" : returnValue.toString();
            } catch (ScriptException e) {
                logger.error(e.getMessage(), e);
                throw new RuntimeException("Error while evaluating the script " + expression, e);
            }

        } else {//In case of no case expression, get the caseValueParam and treat it as a string representation of caseValue
            String paramName = taskToSchedule.getCaseValueParam();
            caseValue = "" + taskInput.get(paramName);
        }

        //QQ why is the case value and the caseValue passed and caseOutput passes as the same ??
        Task decisionTask = SystemTask.decisionTask(workflowInstance, taskId, taskToSchedule, taskInput, caseValue, Arrays.asList(caseValue));
        tasksToBeScheduled.add(decisionTask);
        //get the list of tasks based on the decision
        List<WorkflowTask> selectedTasks = taskToSchedule.getDecisionCases().get(caseValue);
        //if the tasks returned are empty based on evaluated case value, then get the default case if there is one
        if (selectedTasks == null || selectedTasks.isEmpty()) {
            selectedTasks = taskToSchedule.getDefaultCase();
        }
        //
        if (selectedTasks != null && !selectedTasks.isEmpty()) {
            WorkflowTask selectedTask = selectedTasks.get(0);        //Schedule the first task to be executed...
            //TODO break out this recursive call using function composition of what needs to be done and then walk back the condition tree
            List<Task> caseTasks = taskMapperContext.getDeciderService()
                    .getTasksToBeScheduled(workflowDefinition, workflowInstance, selectedTask, retryCount, taskMapperContext.getRetryTaskId());
            tasksToBeScheduled.addAll(caseTasks);
            decisionTask.getInputData().put("hasChildren", "true");
        }
        return tasksToBeScheduled;
    }
}
