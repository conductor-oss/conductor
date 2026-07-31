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
package org.conductoross.conductor.ai.agentspan.runtime.service;

import java.util.Map;
import java.util.Set;

import org.conductoross.conductor.ai.agentspan.runtime.util.WorkflowClassifiers;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.model.WorkflowModel;

import lombok.RequiredArgsConstructor;

/** Eligibility and canonical-state boundary for completed-execution feedback. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "conductor.integrations.ai.enabled", havingValue = "true")
public class AgentFeedbackService {

    static final String UPSTREAM_UNAVAILABLE = "OCG_FEEDBACK_CONTRACT_UNAVAILABLE";
    private static final Set<WorkflowModel.Status> CAPTURED_TERMINAL_STATES =
            Set.of(
                    WorkflowModel.Status.COMPLETED,
                    WorkflowModel.Status.FAILED,
                    WorkflowModel.Status.TIMED_OUT,
                    WorkflowModel.Status.TERMINATED);

    private final ExecutionDAO executionDAO;

    public AgentFeedbackState get(String executionId) {
        WorkflowModel workflow = executionDAO.getWorkflow(executionId, false);
        if (workflow == null) {
            throw new AgentFeedbackException(HttpStatus.NOT_FOUND, "EXECUTION_NOT_FOUND");
        }
        return state(workflow);
    }

    public AgentFeedbackState set(String executionId, String rating) {
        if (rating == null || !Set.of("positive", "negative").contains(rating)) {
            throw new AgentFeedbackException(HttpStatus.BAD_REQUEST, "INVALID_FEEDBACK_RATING");
        }
        AgentFeedbackState state = get(executionId);
        if (!state.enabled()) {
            throw new AgentFeedbackException(HttpStatus.CONFLICT, state.reason());
        }
        // OCG feature/memory-rework currently exposes memory-key JWT/capability feedback only.
        // Turn-identity API-key read/upsert is required before this path can safely be enabled.
        throw new AgentFeedbackException(HttpStatus.CONFLICT, UPSTREAM_UNAVAILABLE);
    }

    AgentFeedbackState state(WorkflowModel workflow) {
        if (workflow.hasParent()) return AgentFeedbackState.disabled("CHILD_EXECUTION");
        if (!CAPTURED_TERMINAL_STATES.contains(workflow.getStatus())) {
            return AgentFeedbackState.disabled("EXECUTION_NOT_TERMINAL");
        }
        WorkflowDef definition = workflow.getWorkflowDefinition();
        if (definition == null || !WorkflowClassifiers.isAgent(definition.getMetadata())) {
            return AgentFeedbackState.disabled("NOT_AGENT_EXECUTION");
        }
        Map<String, Object> metadata = definition.getMetadata();
        Object agentDefinition = metadata.get("agentDef");
        if (!(agentDefinition instanceof Map<?, ?> agentMap)
                || !(agentMap.get("longTermMemory") instanceof Map<?, ?> memory)
                || isBlank(memory.get("ocgUrl"))
                || isBlank(memory.get("credential"))
                || isBlank(memory.get("agent"))) {
            return AgentFeedbackState.disabled("OCG_MEMORY_NOT_CONFIGURED");
        }
        return AgentFeedbackState.disabled(UPSTREAM_UNAVAILABLE);
    }

    private static boolean isBlank(Object value) {
        return value == null || String.valueOf(value).isBlank();
    }
}
