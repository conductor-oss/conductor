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
import org.conductoross.conductor.common.metadata.agent.LongTermMemoryConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.model.WorkflowModel;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;

/** Eligibility and canonical-state boundary for completed-execution feedback. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "conductor.integrations.ai.enabled", havingValue = "true")
public class AgentFeedbackService {

    static final String CREDENTIAL_UNAVAILABLE = "OCG_CREDENTIAL_UNAVAILABLE";
    static final String UPSTREAM_REJECTED = "OCG_FEEDBACK_UPSTREAM_REJECTED";
    static final String UPSTREAM_UNAVAILABLE = "OCG_FEEDBACK_UPSTREAM_UNAVAILABLE";
    static final String UPSTREAM_TIMEOUT = "OCG_FEEDBACK_UPSTREAM_TIMEOUT";
    static final String INVALID_RESPONSE = "OCG_FEEDBACK_INVALID_RESPONSE";
    static final String FEEDBACK_REASON_REQUIRED = "FEEDBACK_REASON_REQUIRED";
    static final String FEEDBACK_REASON_TOO_LONG = "FEEDBACK_REASON_TOO_LONG";
    private static final int MAX_REASON_LENGTH = 2_000;
    private static final Set<WorkflowModel.Status> CAPTURED_TERMINAL_STATES =
            Set.of(
                    WorkflowModel.Status.COMPLETED,
                    WorkflowModel.Status.FAILED,
                    WorkflowModel.Status.TIMED_OUT,
                    WorkflowModel.Status.TERMINATED);

    private final ExecutionDAO executionDAO;
    private final ObjectMapper mapper;
    private final OcgClient ocgClient;

    public AgentFeedbackState get(String executionId) {
        WorkflowModel workflow = executionDAO.getWorkflow(executionId, false);
        if (workflow == null) {
            throw new AgentFeedbackException(HttpStatus.NOT_FOUND, "EXECUTION_NOT_FOUND");
        }
        return get(workflow);
    }

    public AgentFeedbackState set(String executionId, String rating, String reason) {
        validateRating(rating);
        String trimmedReason = validateReason(reason);
        WorkflowModel workflow = executionDAO.getWorkflow(executionId, false);
        if (workflow == null) {
            throw new AgentFeedbackException(HttpStatus.NOT_FOUND, "EXECUTION_NOT_FOUND");
        }
        return set(workflow, rating, trimmedReason);
    }

    public AgentExecutionMemoryState getMemory(String executionId) {
        WorkflowModel workflow = executionDAO.getWorkflow(executionId, false);
        if (workflow == null) {
            throw new AgentFeedbackException(HttpStatus.NOT_FOUND, "EXECUTION_NOT_FOUND");
        }
        return getMemory(workflow);
    }

    AgentExecutionMemoryState getMemory(WorkflowModel workflow) {
        AgentFeedbackState state = state(workflow);
        if (!state.enabled()) {
            throw new AgentFeedbackException(HttpStatus.CONFLICT, state.reason());
        }
        FeedbackContext context = feedbackContext(workflow);
        try {
            return new AgentExecutionMemoryState(
                    ocgClient.getExecutionMemory(context.config(), context.identity()).summary());
        } catch (OcgFeedbackClientException error) {
            throw map(error);
        }
    }

    AgentFeedbackState get(WorkflowModel workflow) {
        AgentFeedbackState state = state(workflow);
        if (!state.enabled()) return state;
        FeedbackContext context = feedbackContext(workflow);
        try {
            return enabled(ocgClient.getFeedback(context.config(), context.identity()));
        } catch (OcgFeedbackClientException error) {
            throw map(error);
        }
    }

    AgentFeedbackState set(WorkflowModel workflow, String rating, String reason) {
        OcgFeedbackRating validatedRating = validateRating(rating);
        String trimmedReason = validateReason(reason);
        AgentFeedbackState state = state(workflow);
        if (!state.enabled()) {
            throw new AgentFeedbackException(HttpStatus.CONFLICT, state.reason());
        }
        FeedbackContext context = feedbackContext(workflow);
        try {
            return enabled(
                    ocgClient.setFeedback(
                            context.config(), context.identity(), validatedRating, trimmedReason));
        } catch (OcgFeedbackClientException error) {
            throw map(error);
        }
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
        return new AgentFeedbackState(true, null, null, null);
    }

    @SuppressWarnings("unchecked")
    private FeedbackContext feedbackContext(WorkflowModel workflow) {
        Map<String, Object> agentDefinition =
                (Map<String, Object>)
                        workflow.getWorkflowDefinition().getMetadata().get("agentDef");
        LongTermMemoryConfig config =
                mapper.convertValue(
                        agentDefinition.get("longTermMemory"), LongTermMemoryConfig.class);
        return new FeedbackContext(config, OcgExecutionIdentity.from(workflow, config));
    }

    private static AgentFeedbackState enabled(OcgFeedback feedback) {
        return new AgentFeedbackState(
                true,
                feedback.rating() == null ? null : feedback.rating().value(),
                feedback.reason(),
                feedback.submittedAt());
    }

    private static AgentFeedbackException map(OcgFeedbackClientException error) {
        return switch (error.getFailure()) {
            case CREDENTIAL_UNAVAILABLE ->
                    new AgentFeedbackException(
                            HttpStatus.SERVICE_UNAVAILABLE, CREDENTIAL_UNAVAILABLE);
            case UPSTREAM_REJECTED ->
                    new AgentFeedbackException(HttpStatus.BAD_GATEWAY, UPSTREAM_REJECTED);
            case UPSTREAM_TIMEOUT ->
                    new AgentFeedbackException(HttpStatus.GATEWAY_TIMEOUT, UPSTREAM_TIMEOUT);
            case INVALID_RESPONSE ->
                    new AgentFeedbackException(HttpStatus.BAD_GATEWAY, INVALID_RESPONSE);
            case UPSTREAM_UNAVAILABLE ->
                    new AgentFeedbackException(
                            HttpStatus.SERVICE_UNAVAILABLE, UPSTREAM_UNAVAILABLE);
        };
    }

    private static boolean isBlank(Object value) {
        return value == null || String.valueOf(value).isBlank();
    }

    private static String validateReason(String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new AgentFeedbackException(HttpStatus.BAD_REQUEST, FEEDBACK_REASON_REQUIRED);
        }
        String trimmedReason = reason.trim();
        if (trimmedReason.length() > MAX_REASON_LENGTH) {
            throw new AgentFeedbackException(HttpStatus.BAD_REQUEST, FEEDBACK_REASON_TOO_LONG);
        }
        return trimmedReason;
    }

    private static OcgFeedbackRating validateRating(String rating) {
        if (rating == null || !Set.of("positive", "negative").contains(rating)) {
            throw new AgentFeedbackException(HttpStatus.BAD_REQUEST, "INVALID_FEEDBACK_RATING");
        }
        return OcgFeedbackRating.fromValue(rating);
    }

    private record FeedbackContext(LongTermMemoryConfig config, OcgExecutionIdentity identity) {}
}
