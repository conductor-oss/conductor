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
package org.conductoross.conductor.ai.tasks.worker;

import org.apache.commons.lang3.StringUtils;
import org.conductoross.conductor.ai.a2a.A2AService;
import org.conductoross.conductor.ai.a2a.model.AgentCard;
import org.conductoross.conductor.ai.model.A2AAgentCardRequest;
import org.conductoross.conductor.config.AIIntegrationEnabledCondition;
import org.conductoross.conductor.core.execution.tasks.AnnotatedSystemTaskWorker;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import com.netflix.conductor.sdk.workflow.executor.task.NonRetryableException;
import com.netflix.conductor.sdk.workflow.task.OutputParam;
import com.netflix.conductor.sdk.workflow.task.WorkerTask;

import lombok.extern.slf4j.Slf4j;

/**
 * Synchronous worker tasks for interacting with remote A2A agents — discovery.
 *
 * <p>This is a quick request/response call, so (like {@code MCPWorkers}) it runs as an annotated
 * system task. {@code CANCEL_AGENT} is a plain {@link
 * com.netflix.conductor.core.execution.tasks.WorkflowSystemTask} ({@link
 * org.conductoross.conductor.ai.a2a.CancelAgentTask}) instead, since it needs the {@link
 * com.netflix.conductor.core.execution.WorkflowExecutor} the engine hands to system tasks rather
 * than one injected as a bean. The long-running {@code AGENT} operation instead uses {@link
 * org.conductoross.conductor.ai.a2a.AgentTask} for non-blocking polling.
 */
@Slf4j
@Component
@Conditional(AIIntegrationEnabledCondition.class)
public class A2AWorkers implements AnnotatedSystemTaskWorker {

    private final A2AService a2aService;

    public A2AWorkers(A2AService a2aService) {
        this.a2aService = a2aService;
        log.debug("A2A Workers initialized");
    }

    /**
     * Discovers a remote agent by fetching its Agent Card (identity, skills, capabilities,
     * endpoint).
     *
     * @param request the agent URL and optional headers
     * @return the agent's {@link AgentCard}
     */
    @WorkerTask("GET_AGENT_CARD")
    public @OutputParam("agentCard") AgentCard getAgentCard(A2AAgentCardRequest request) {
        requireA2a(request.getAgentType());
        if (StringUtils.isBlank(request.getAgentUrl())) {
            throw new NonRetryableException("GET_AGENT_CARD requires 'agentUrl'");
        }
        log.debug("Fetching agent card from {}", request.getAgentUrl());
        return a2aService.getAgentCard(request.getAgentUrl(), request.getHeaders());
    }

    private static void requireA2a(String agentType) {
        if (!A2AService.isA2aAgentType(agentType)) {
            throw new NonRetryableException(
                    "Unsupported agentType '" + agentType + "' (only 'a2a' is supported)");
        }
    }
}
