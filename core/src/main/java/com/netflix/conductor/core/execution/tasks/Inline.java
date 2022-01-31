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

    public Inline(Map<String, Evaluator> evaluators) {
        super(TASK_TYPE_INLINE);
        this.evaluators = evaluators;
    }

    @Override
    public boolean execute(
            WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        Map<String, Object> taskInput = task.getInputData();
        Map<String, Object> taskOutput = task.getOutputData();
        String evaluatorType = (String) taskInput.get(QUERY_EVALUATOR_TYPE);
        String expression = (String) taskInput.get(QUERY_EXPRESSION_PARAMETER);

        try {
            checkEvaluatorType(evaluatorType);
            checkExpression(expression);
            Evaluator evaluator = evaluators.get(evaluatorType);
            Object evalResult = evaluator.evaluate(expression, taskInput);
            taskOutput.put("result", evalResult);
            task.setStatus(TaskModel.Status.COMPLETED);
        } catch (Exception e) {
            LOGGER.error(
                    "Failed to execute Inline Task: {} in workflow: {}",
                    task.getTaskId(),
                    workflow.getWorkflowId(),
                    e);
            task.setStatus(TaskModel.Status.FAILED);
            task.setReasonForIncompletion(e.getMessage());
            taskOutput.put(
                    "error", e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
        }

        return true;
    }

    private void checkEvaluatorType(String evaluatorType) {
        if (StringUtils.isBlank(evaluatorType)) {
            LOGGER.error("Empty {} in Inline task. ", QUERY_EVALUATOR_TYPE);
            throw new TerminateWorkflowException(
                    "Empty '"
                            + QUERY_EVALUATOR_TYPE
                            + "' in Inline task's input parameters. A non-empty String value must be provided.");
        }
        if (evaluators.get(evaluatorType) == null) {
            LOGGER.error("Evaluator {} for Inline task not registered", evaluatorType);
            throw new TerminateWorkflowException(
                    "Unknown evaluator '" + evaluatorType + "' in Inline task.");
        }
    }

    private void checkExpression(String expression) {
        if (StringUtils.isBlank(expression)) {
            LOGGER.error("Empty {} in Inline task. ", QUERY_EXPRESSION_PARAMETER);
            throw new TerminateWorkflowException(
                    "Empty '"
                            + QUERY_EXPRESSION_PARAMETER
                            + "' in Inline task's input parameters. A non-empty String value must be provided.");
        }
    }
}
