/*
 * Copyright 2026 Conductor Authors.
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
package org.conductoross.conductor.ai.agentspan.runtime.decision;

import java.util.Map;

import org.conductoross.conductor.config.AIIntegrationEnabledCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;
import com.netflix.conductor.sdk.workflow.executor.task.NonRetryableException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Asynchronous Decision inference exposing a decision for a downstream SWITCH task. */
@Component
@Conditional(AIIntegrationEnabledCondition.class)
public class AiDecisionTask extends WorkflowSystemTask {
    private final DecisionClient client;
    private final ObjectMapper mapper;

    public AiDecisionTask(DecisionClient client, ObjectMapper mapper) {
        super("AI_DECISION");
        this.client = client;
        this.mapper = mapper;
    }

    @Override
    public boolean isAsync() {
        return true;
    }

    @Override
    public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
        execute(workflow, task, executor);
    }

    @Override
    public boolean execute(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
        if (task.getStatus() != null && task.getStatus().isTerminal()) return false;
        try {
            DecisionRequest request =
                    mapper.convertValue(task.getInputData(), DecisionRequest.class);
            DecisionValidation.request(request);
            if (request.questions().size() != 1
                    || request.questions().values().iterator().next().type()
                            != DecisionQuestion.Type.CHOICE) {
                throw new NonRetryableException("AI_DECISION requires exactly one choice question");
            }
            DecisionResult result = client.decide(request);
            DecisionValidation.result(request, result);
            task.setOutputData(
                    mapper.convertValue(result, new TypeReference<Map<String, Object>>() {}));
            // Keep the full structured response, with a selectedCase field for a downstream SWITCH.
            task.getOutputData()
                    .put("selectedCase", result.answers().values().iterator().next().choice());
            task.setStatus(TaskModel.Status.COMPLETED);
        } catch (NonRetryableException | IllegalArgumentException e) {
            task.setStatus(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR);
            task.setReasonForIncompletion(e.getMessage());
        } catch (RuntimeException e) {
            task.setStatus(TaskModel.Status.FAILED);
            task.setReasonForIncompletion(e.getMessage());
        }
        return true;
    }

    @Override
    public void cancel(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
        task.setStatus(TaskModel.Status.CANCELED);
    }
}
