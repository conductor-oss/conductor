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
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.springframework.stereotype.Component;

import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.sdk.workflow.executor.task.NonRetryableException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Shared lifecycle for Decision-backed system tasks. */
@Component
public final class DecisionTaskSupport {
    private final DecisionClient client;
    private final ObjectMapper mapper;

    public DecisionTaskSupport(DecisionClient client, ObjectMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    public boolean execute(
            TaskModel task,
            Runnable precondition,
            Consumer<DecisionRequest> requestPolicy,
            BiConsumer<Map<String, Object>, DecisionResult> outputDecorator) {
        if (task.getStatus() != null && task.getStatus().isTerminal()) return false;
        try {
            precondition.run();
            DecisionRequest request =
                    mapper.convertValue(task.getInputData(), DecisionRequest.class);
            requestPolicy.accept(request);
            DecisionResult result = client.decide(request);
            Map<String, Object> output =
                    mapper.convertValue(result, new TypeReference<Map<String, Object>>() {});
            outputDecorator.accept(output, result);
            task.setOutputData(output);
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
}
