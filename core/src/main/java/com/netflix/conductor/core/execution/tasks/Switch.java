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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.exception.NonTransientException;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.evaluators.Evaluator;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_SWITCH;

/**
 * {@link Switch} task is a replacement for now deprecated {@link Decision} task.
 *
 * <p>With a regular evaluator the SWITCH is evaluated by {@code SwitchTaskMapper} inside the
 * decider and this task only completes. With a {@link Evaluator#isDeferred() deferred} evaluator
 * the SWITCH is queued and evaluated here by the system task worker, so a blocking evaluator never
 * runs on the decider thread and a failure is retried like any other task.
 */
@Component(TASK_TYPE_SWITCH)
public class Switch extends WorkflowSystemTask {

    public static final String SELECTED_CASE = "selectedCase";
    public static final String EVALUATION_RESULT = "evaluationResult";
    public static final String HAS_CHILDREN = "hasChildren";
    public static final String DEFERRED_EVALUATOR = "_conductorDeferredEvaluator";

    private final Map<String, Evaluator> evaluators;

    public Switch(Map<String, Evaluator> evaluators) {
        super(TASK_TYPE_SWITCH);
        this.evaluators = evaluators;
    }

    /** The type has a queue poller; only tasks with a deferred evaluator are actually queued. */
    @Override
    public boolean isAsync() {
        return true;
    }

    @Override
    public boolean isAsync(TaskModel task) {
        return isDeferred(task);
    }

    @Override
    public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        if (isDeferred(task)) {
            evaluate(task);
        }
    }

    @Override
    public boolean execute(
            WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        task.setStatus(TaskModel.Status.COMPLETED);
        return true;
    }

    private boolean isDeferred(TaskModel task) {
        return Boolean.TRUE.equals(task.getInputData().get(DEFERRED_EVALUATOR));
    }

    private Evaluator deferredEvaluator(TaskModel task) {
        WorkflowTask workflowTask = task.getWorkflowTask();
        if (workflowTask == null || workflowTask.getEvaluatorType() == null) {
            throw new NonTransientException(
                    "Deferred SWITCH is missing its workflow task or evaluator type");
        }
        String evaluatorType = workflowTask.getEvaluatorType();
        Evaluator evaluator = evaluators.get(evaluatorType);
        if (evaluator == null) {
            throw new NonTransientException(
                    "Deferred SWITCH evaluator '" + evaluatorType + "' is not registered");
        }
        if (!evaluator.isDeferred()) {
            throw new NonTransientException(
                    "SWITCH evaluator '" + evaluatorType + "' is no longer deferred");
        }
        return evaluator;
    }

    private void evaluate(TaskModel task) {
        try {
            Map<String, Object> evaluationInput = new HashMap<>(task.getInputData());
            evaluationInput.remove(DEFERRED_EVALUATOR);
            Object result =
                    deferredEvaluator(task)
                            .evaluate(task.getWorkflowTask().getExpression(), evaluationInput);
            String selectedCase;
            if (result instanceof Map<?, ?> map && map.containsKey(SELECTED_CASE)) {
                // Preserve the response, then write the selected case as a string below.
                map.forEach(
                        (k, v) -> {
                            if (!SELECTED_CASE.equals(k)) {
                                task.addOutput(String.valueOf(k), v);
                            }
                        });
                selectedCase = String.valueOf(map.get(SELECTED_CASE));
            } else {
                selectedCase = String.valueOf(result);
            }
            task.getInputData().put("case", selectedCase);
            task.addOutput(EVALUATION_RESULT, List.of(selectedCase));
            task.addOutput(SELECTED_CASE, selectedCase);
            task.setStatus(TaskModel.Status.COMPLETED);
        } catch (NonTransientException | IllegalArgumentException e) {
            task.setStatus(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR);
            task.setReasonForIncompletion(e.getMessage());
        } catch (RuntimeException e) {
            task.setStatus(TaskModel.Status.FAILED);
            task.setReasonForIncompletion(e.getMessage());
        }
    }
}
