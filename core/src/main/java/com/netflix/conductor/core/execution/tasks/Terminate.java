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

public class Terminate extends WorkflowSystemTask {

    private static final Logger logger = LoggerFactory.getLogger(Lambda.class);
    private static final String TERMINATION_STATUS_PARAMETER = "terminationStatus";
    private static final String INPUT_PARAMETER = "input";
    public static final String TASK_NAME = "TERMINATE";

    public Terminate() {
        super(TASK_NAME);
        logger.info(TASK_NAME + " task initialized...");
    }

    @Override
    public boolean execute(Workflow workflow, Task task, WorkflowExecutor executor) {
        String returnStatus = (String) task.getInputData().get(TERMINATION_STATUS_PARAMETER);

        if(validateInputStatus(returnStatus)) {
            workflow.setStatus(Workflow.WorkflowStatus.valueOf(returnStatus));
            task.setOutputData(getInputFromParam(task.getInputData()));
            setWorkflowOutput(task.getOutputData(), workflow);
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

    public static Boolean validateInputStatus(String status) {
        return COMPLETED.name().equals(status) || FAILED.name().equals(status);
    }

    private void setWorkflowOutput(Map<String, Object> taskOutput, Workflow workflow) {
        if(!taskOutput.isEmpty()) {
            workflow.setOutput(taskOutput);
        }
    }

    private Map<String, Object> getInputFromParam(Map<String, Object> taskInput) {
        HashMap<String, Object> output = new HashMap<>();
        if(taskInput.get(INPUT_PARAMETER) == null) {
            return output;
        }
        if(taskInput.get(INPUT_PARAMETER) instanceof HashMap) {
            output.putAll((HashMap<String, Object>) taskInput.get(INPUT_PARAMETER));
            return output;
        }
        output.put("output", taskInput.get(INPUT_PARAMETER));
        return output;
    }
}
