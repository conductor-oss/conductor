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
package org.conductoross.conductor.ai.decision;

import org.conductoross.conductor.config.AIIntegrationEnabledCondition;
import org.conductoross.conductor.core.execution.tasks.AnnotatedSystemTaskWorker;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import com.netflix.conductor.sdk.workflow.task.WorkerTask;

@Component
@Conditional(AIIntegrationEnabledCondition.class)
public class DecisionModelWorker implements AnnotatedSystemTaskWorker {
    private final DecisionModelRegistry registry;

    public DecisionModelWorker(DecisionModelRegistry registry) {
        this.registry = registry;
    }

    @WorkerTask("DECISION_MODEL")
    public DecisionResult decide(DecisionRequest request) {
        DecisionValidation.request(request);
        DecisionResult result = registry.get(request.provider()).decide(request);
        DecisionValidation.result(request, result);
        return result;
    }
}
