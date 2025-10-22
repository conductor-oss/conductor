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

import java.lang.reflect.Method;
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

import com.fasterxml.jackson.databind.ObjectMapper;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_INLINE;

/**
 * @author X-Ultra
 *     <p>Task that enables execute inline script at workflow execution. For example,
 *     <pre>
 * ...
 * {
 *  "tasks": [
 *      {
 *          "name": "INLINE",
 *          "taskReferenceName": "inline_test",
 *          "type": "INLINE",
 *          "inputParameters": {
 *              "input": "${workflow.input}",
 *              "evaluatorType": "javascript"
 *              "expression": "if ($.input.a==1){return {testvalue: true}} else{return {testvalue: false} }"
 *          }
 *      }
 *  ]
 * }
 * ...
 * </pre>
 *     then to use task output, e.g. <code>script_test.output.testvalue</code> {@link Inline} is a
 *     replacement for deprecated {@link Lambda}
 */
@Component(TASK_TYPE_INLINE)
public class Inline extends WorkflowSystemTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(Inline.class);
    private static final String QUERY_EVALUATOR_TYPE = "evaluatorType";
    private static final String QUERY_EXPRESSION_PARAMETER = "expression";
    public static final String NAME = "INLINE";

    private final Map<String, Evaluator> evaluators;
    private final ObjectMapper objectMapper;

    public Inline(Map<String, Evaluator> evaluators, ObjectMapper objectMapper) {
        super(TASK_TYPE_INLINE);
        this.evaluators = evaluators;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean execute(
            WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        Map<String, Object> taskInput = task.getInputData();
        String evaluatorType = (String) taskInput.get(QUERY_EVALUATOR_TYPE);
        String expression = (String) taskInput.get(QUERY_EXPRESSION_PARAMETER);

        try {
            checkEvaluatorType(evaluatorType);
            checkExpression(expression);
            Evaluator evaluator = evaluators.get(evaluatorType);
            Object evalResult = evaluator.evaluate(expression, taskInput);
            task.addOutput("result", normalizeResult(evalResult));
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

    /**
     * Normalizes the evaluator result to a plain Java object. This ensures that JavaScript engine
     * wrapper objects (like Nashorn's ScriptObjectMirror or GraalJS's ProxyObject) are converted to
     * standard Java Maps/Lists, preventing issues when the result is used directly in subsequent
     * tasks like FORK_JOIN_DYNAMIC.
     *
     * <p>This method is engine-agnostic and uses reflection to detect array-like objects from any
     * JavaScript engine implementation.
     *
     * @param result The raw result from the evaluator
     * @return A normalized plain Java object (Map, List, or primitive)
     */
    private Object normalizeResult(Object result) {
        if (result == null) {
            return null;
        }

        // Use reflection to check if this is a JavaScript array wrapper (engine-agnostic)
        // Both Nashorn (ScriptObjectMirror) and GraalJS (ProxyObject) have isArray() methods
        try {
            Method isArrayMethod = result.getClass().getMethod("isArray");
            Boolean isArray = (Boolean) isArrayMethod.invoke(result);
            if (isArray != null && isArray) {
                // Convert to Java List by extracting values
                java.util.List<Object> list = new java.util.ArrayList<>();
                Method valuesMethod = result.getClass().getMethod("values");
                Object valuesCollection = valuesMethod.invoke(result);
                if (valuesCollection instanceof Iterable) {
                    for (Object value : (Iterable<?>) valuesCollection) {
                        list.add(normalizeResult(value)); // Recursively normalize nested objects
                    }
                }
                return list;
            }
        } catch (Exception e) {
            // No isArray() method or invocation failed - not a JS array, continue
        }

        // Serialize to JSON and deserialize back to ensure proper Java types
        // This preserves arrays as Lists and objects as Maps
        try {
            String json = objectMapper.writeValueAsString(result);
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            // Fallback to direct conversion if serialization fails
            return objectMapper.convertValue(result, Object.class);
        }
    }
}
