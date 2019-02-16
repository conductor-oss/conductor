package com.netflix.conductor.contribs.lambda;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.events.ScriptEvaluator;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import javax.script.ScriptException;
import java.util.Map;

@Singleton
public class Lambda extends WorkflowSystemTask {

    private static final Logger logger = LoggerFactory.getLogger(Lambda.class);
    private static final String QUERY_EXPRESSION_PARAMETER = "scriptExpression";

    public Lambda() {
        super("LAMBDA");
        logger.info("LAMBDA task initialized...");
    }


    @Override
    public void start(Workflow workflow, Task task, WorkflowExecutor executor) {
        Map<String, Object> taskInput = task.getInputData();
        Map<String, Object> taskOutput = task.getOutputData();

        String scriptExpression = (String) taskInput.get(QUERY_EXPRESSION_PARAMETER);

        if(scriptExpression == null) {
            task.setReasonForIncompletion("Missing '" + QUERY_EXPRESSION_PARAMETER + "' in input parameters");
            task.setStatus(Task.Status.FAILED);
            return;
        }

        String scriptExpressionBuilder = "function scriptFun(){" +
                scriptExpression +
                "} scriptFun();";

        logger.info("scriptExpressionBuilder: {}" , scriptExpressionBuilder);

        try {
            Object returnValue = null;
            returnValue = ScriptEvaluator.eval(scriptExpressionBuilder, taskInput);
            taskOutput.put("result", returnValue);
            task.setStatus(Task.Status.COMPLETED);
        } catch (ScriptException e) {
            logger.error("Failed to execute  Lambda: {}", scriptExpression, e);
            task.setStatus(Task.Status.FAILED);
            task.setReasonForIncompletion(e.getMessage());
            taskOutput.put("error", e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
        }

    }


}
