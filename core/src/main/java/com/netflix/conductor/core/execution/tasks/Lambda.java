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

import com.netflix.conductor.core.events.ScriptEvaluator;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_LAMBDA;

/**
 * @author X-Ultra
 *     <p>Task that enables execute Lambda script at workflow execution, For example,
 *     <pre>
 * ...
 * {
 *  "tasks": [
 *      {
 *          "name": "LAMBDA",
 *          "taskReferenceName": "lambda_test",
 *          "type": "LAMBDA",
 *          "inputParameters": {
 *              "input": "${workflow.input}",
 *              "scriptExpression": "if ($.input.a==1){return {testvalue: true}} else{return {testvalue: false} }"
 *          }
 *      }
 *  ]
 * }
 * ...
 * </pre>
 *     then to use task output, e.g. <code>script_test.output.testvalue</code>
 * @deprecated {@link Lambda} is deprecated. Use {@link Inline} task for inline expression
 *     evaluation. Also see ${@link com.netflix.conductor.common.metadata.workflow.WorkflowTask})
 */
@Deprecated
@Component(TASK_TYPE_LAMBDA)
public class Lambda extends WorkflowSystemTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(Lambda.class);
    private static final String QUERY_EXPRESSION_PARAMETER = "scriptExpression";
    public static final String NAME = "LAMBDA";

    public Lambda() {
        super(TASK_TYPE_LAMBDA);
    }

    @Override
    public boolean execute(
            WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        Map<String, Object> taskInput = task.getInputData();
        Map<String, Object> taskOutput = task.getOutputData();
        String scriptExpression;
        try {
            scriptExpression = (String) taskInput.get(QUERY_EXPRESSION_PARAMETER);
            if (StringUtils.isNotBlank(scriptExpression)) {
                String scriptExpressionBuilder =
                        "function scriptFun(){" + scriptExpression + "} scriptFun();";

                LOGGER.debug(
                        "scriptExpressionBuilder: {}, task: {}",
                        scriptExpressionBuilder,
                        task.getTaskId());
                Object returnValue = ScriptEvaluator.eval(scriptExpressionBuilder, taskInput);
                taskOutput.put("result", returnValue);
                task.setStatus(TaskModel.Status.COMPLETED);
            } else {
                LOGGER.error("Empty {} in Lambda task. ", QUERY_EXPRESSION_PARAMETER);
                task.setReasonForIncompletion(
                        "Empty '"
                                + QUERY_EXPRESSION_PARAMETER
                                + "' in Lambda task's input parameters. A non-empty String value must be provided.");
                task.setStatus(TaskModel.Status.FAILED);
            }
        } catch (Exception e) {
            LOGGER.error(
                    "Failed to execute Lambda Task: {} in workflow: {}",
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
}
