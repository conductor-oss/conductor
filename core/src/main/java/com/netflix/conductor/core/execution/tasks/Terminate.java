package com.netflix.conductor.core.execution.tasks;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static com.netflix.conductor.common.run.Workflow.WorkflowStatus.COMPLETED;
import static com.netflix.conductor.common.run.Workflow.WorkflowStatus.FAILED;

/**
 * Task that can terminate a workflow with a given status and modify the workflow's output with a given parameter,
 * it can act as a "return" statement for conditions where you simply want to terminate your workflow. For example,
 * if you have a decision where the first condition is met, you want to execute some tasks, otherwise you want to finish
 * your workflow.
 * <pre>
...
 {
     "tasks": [{
         "name": "terminate",
         "taskReferenceName": "terminate0",
         "inputParameters": {
             "terminationStatus": "COMPLETED",
             "workflowOutput": "${task0.output}"
         },
         "type": "TERMINATE",
         "startDelay": 0,
         "optional": false
     }]
 }
...
 * </pre>
 * This task has some validations on creation and execution, they are:
 * - the "terminationStatus" parameter is mandatory and it can only receive the values "COMPLETED" or "FAILED"
 * - the terminate task cannot be optional
 */
public class Terminate extends WorkflowSystemTask {

    private static final Logger logger = LoggerFactory.getLogger(Terminate.class);
    private static final String TERMINATION_STATUS_PARAMETER = "terminationStatus";
    private static final String TERMINATION_WORKFLOW_OUTPUT = "workflowOutput";
    public static final String TASK_NAME = "TERMINATE";

    public Terminate() {
        super(TASK_NAME);
        logger.info(TASK_NAME + " task initialized...");
    }

    @Override
    public boolean execute(Workflow workflow, Task task, WorkflowExecutor executor) {
        String returnStatus = (String) task.getInputData().get(TERMINATION_STATUS_PARAMETER);

        if(validateInputStatus(returnStatus)) {
            task.setOutputData(getInputFromParam(task.getInputData()));
            task.setStatus(Task.Status.COMPLETED);
            return true;
        }
        task.setReasonForIncompletion("given termination status is not valid");
        task.setStatus(Task.Status.FAILED);
        return false;
    }

    public static String getTerminationStatusParameter() {
        return TERMINATION_STATUS_PARAMETER;
    }

    public static String getTerminationWorkflowOutputParameter() {
        return TERMINATION_WORKFLOW_OUTPUT;
    }

    public static Boolean validateInputStatus(String status) {
        return COMPLETED.name().equals(status) || FAILED.name().equals(status);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getInputFromParam(Map<String, Object> taskInput) {
        HashMap<String, Object> output = new HashMap<>();
        if(taskInput.get(TERMINATION_WORKFLOW_OUTPUT) == null) {
            return output;
        }
        if(taskInput.get(TERMINATION_WORKFLOW_OUTPUT) instanceof HashMap) {
            output.putAll((HashMap<String, Object>) taskInput.get(TERMINATION_WORKFLOW_OUTPUT));
            return output;
        }
        output.put("output", taskInput.get(TERMINATION_WORKFLOW_OUTPUT));
        return output;
    }
}
