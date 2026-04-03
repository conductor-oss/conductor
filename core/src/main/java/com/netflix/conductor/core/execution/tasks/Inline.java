/*
 * Copyright 2022 Conductor Authors.
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

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.netflix.conductor.core.exception.TerminateWorkflowException;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.evaluators.Evaluator;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_INLINE;

// @formatter:off
/**
 * @author X-Ultra
 *     <p>Task that enables execute inline script at workflow execution.
 *     <p>Example: { "tasks": [ { "name": "INLINE", "taskReferenceName": "inline_test", "type":
 *     "INLINE", "inputParameters": { "input": "${workflow.input}", "evaluatorType": "javascript",
 *     "expression": "if ($.input.a==1){return {testvalue: true}} else{return {testvalue: false} }"
 *     } } ] }
 *     <p>The evaluatorType parameter is optional and defaults to "javascript" for backward
 *     compatibility. Supported values include: - "javascript" - JavaScript evaluation using GraalJS
 *     engine (default) - "graaljs" - Explicit GraalJS evaluation (same as "javascript") - "python"
 *     - Python evaluation using GraalVM Python
 *     <p>To use task output, reference it as script_test.output.testvalue This is a replacement for
 *     the deprecated Lambda task.
 */
// @formatter:on
@Component(TASK_TYPE_INLINE)
public class Inline extends WorkflowSystemTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(Inline.class);
    private static final String QUERY_EVALUATOR_TYPE = "evaluatorType";
    private static final String QUERY_EXPRESSION_PARAMETER = "expression";
    public static final String NAME = "INLINE";

    private final Map<String, Evaluator> evaluators;

    public Inline(Map<String, Evaluator> evaluators) {
        super(TASK_TYPE_INLINE);
        this.evaluators = evaluators;
    }

    @Override
    public boolean execute(
            WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        Map<String, Object> taskInput = task.getInputData();
        // Get evaluatorType, default to "javascript" for backward compatibility if missing
        String evaluatorType = (String) taskInput.get(QUERY_EVALUATOR_TYPE);
        if (evaluatorType == null) {
            evaluatorType = "javascript";
        }
        String expression = (String) taskInput.get(QUERY_EXPRESSION_PARAMETER);

        try {
            checkEvaluatorType(evaluatorType);
            checkExpression(expression);
            Evaluator evaluator = evaluators.get(evaluatorType);
            Object evalResult = evaluator.evaluate(expression, taskInput);
            task.addOutput("result", evalResult);
            task.setStatus(TaskModel.Status.COMPLETED);
        } catch (Exception e) {
            String errorMessage = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            LOGGER.error(
                    "Failed to execute Inline Task: {} in workflow: {}",
                    task.getTaskId(),
                    workflow.getWorkflowId(),
                    e);
            // TerminateWorkflowException is thrown when the script evaluation fails
            // Retry will result in the same error, so FAILED_WITH_TERMINAL_ERROR status is used.
            task.setStatus(
                    e instanceof TerminateWorkflowException
                            ? TaskModel.Status.FAILED_WITH_TERMINAL_ERROR
                            : TaskModel.Status.FAILED);
            task.setReasonForIncompletion(errorMessage);
            task.addOutput("error", errorMessage);
        }

        return true;
    }

    private void checkEvaluatorType(String evaluatorType) {
        // evaluatorType is now optional with "javascript" as default, but must not be blank if
        // provided
        if (StringUtils.isBlank(evaluatorType)) {
            LOGGER.error("Empty {} in INLINE task. ", QUERY_EVALUATOR_TYPE);
            throw new TerminateWorkflowException(
                    "Empty '"
                            + QUERY_EVALUATOR_TYPE
                            + "' in INLINE task's input parameters. A non-empty String value must be provided.");
        }
        if (evaluators.get(evaluatorType) == null) {
            LOGGER.error("Evaluator {} for INLINE task not registered", evaluatorType);
            throw new TerminateWorkflowException(
                    "Unknown evaluator '" + evaluatorType + "' in INLINE task.");
        }
    }

    private void checkExpression(String expression) {
        if (StringUtils.isBlank(expression)) {
            LOGGER.error("Empty {} in INLINE task. ", QUERY_EXPRESSION_PARAMETER);
            throw new TerminateWorkflowException(
                    "Empty '"
                            + QUERY_EXPRESSION_PARAMETER
                            + "' in Inline task's input parameters. A non-empty String value must be provided.");
        }
    }
}
