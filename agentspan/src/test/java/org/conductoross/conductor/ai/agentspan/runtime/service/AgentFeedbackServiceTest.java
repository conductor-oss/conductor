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

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.conductoross.conductor.common.metadata.agent.LongTermMemoryConfig;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.netflix.conductor.common.metadata.workflow.WorkflowClassifier;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.model.WorkflowModel;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentFeedbackServiceTest {

    private final RecordingOcgClient ocgClient = new RecordingOcgClient();
    private final AgentFeedbackService service =
            new AgentFeedbackService(null, new ObjectMapper(), ocgClient);

    @Test
    void readsUnratedAndExistingCanonicalFeedbackIncludingReason() {
        ocgClient.feedback = new OcgFeedback(null, null, null);
        assertThat(service.get(workflow("configured-user")))
                .isEqualTo(new AgentFeedbackState(true, null, null, null));

        Instant submittedAt = Instant.parse("2026-07-31T20:15:00Z");
        ocgClient.feedback =
                new OcgFeedback(OcgFeedbackRating.POSITIVE, "It resolved the issue.", submittedAt);
        assertThat(service.get(workflow("configured-user")))
                .isEqualTo(
                        new AgentFeedbackState(
                                true, "positive", "It resolved the issue.", submittedAt));
    }

    @Test
    void upsertsBothRatingsWithTrimmedReasonsAndReturnsCanonicalState() {
        Instant submittedAt = Instant.parse("2026-07-31T20:15:00Z");
        ocgClient.feedback =
                new OcgFeedback(OcgFeedbackRating.POSITIVE, "Resolved the issue.", submittedAt);
        assertThat(
                        service.set(
                                        workflow("configured-user"),
                                        "positive",
                                        "  Resolved the issue.  ")
                                .rating())
                .isEqualTo("positive");
        assertThat(ocgClient.rating).isEqualTo(OcgFeedbackRating.POSITIVE);
        assertThat(ocgClient.reason).isEqualTo("Resolved the issue.");

        ocgClient.feedback =
                new OcgFeedback(
                        OcgFeedbackRating.NEGATIVE,
                        "The cited source was incorrect.",
                        submittedAt.plusSeconds(1));
        assertThat(
                        service.set(
                                        workflow("configured-user"),
                                        "negative",
                                        "The cited source was incorrect.")
                                .reason())
                .isEqualTo("The cited source was incorrect.");
        assertThat(ocgClient.rating).isEqualTo(OcgFeedbackRating.NEGATIVE);
    }

    @Test
    void derivesTrustedExecutionIdentityFromConfigurationAndStoredExecution() {
        service.get(workflow("configured-user"));

        assertThat(ocgClient.identity)
                .isEqualTo(
                        new OcgExecutionIdentity(
                                "trusted-agent",
                                "user:configured-user",
                                "stored-session",
                                "root-workflow"));

        WorkflowModel executionUser = workflow(null);
        executionUser.getInput().put("user", "execution-user");
        service.get(executionUser);
        assertThat(ocgClient.identity.user()).isEqualTo("user:execution-user");
    }

    @Test
    void readsExecutionMemoryWithTrustedIdentity() {
        ocgClient.memory = new OcgExecutionMemory("The agent resolved the incident.");

        assertThat(service.getMemory(workflow("configured-user")))
                .isEqualTo(
                        new AgentExecutionMemoryState(
                                "The agent resolved the incident.", null, null));
        assertThat(ocgClient.identity)
                .isEqualTo(
                        new OcgExecutionIdentity(
                                "trusted-agent",
                                "user:configured-user",
                                "stored-session",
                                "root-workflow"));
    }

    @Test
    void ignoresExecutionFieldsThatAttemptToOverrideOcgRoutingIdentity() {
        WorkflowModel workflow = workflow("configured-user");
        workflow.getInput().put("ocgUrl", "https://attacker.invalid");
        workflow.getInput().put("credential", "ATTACKER_KEY");
        workflow.getInput().put("agent", "attacker-agent");
        workflow.getInput().put("execution_id", "attacker-execution");

        service.get(workflow);

        assertThat(ocgClient.config.getOcgUrl()).isEqualTo("https://ocg.example");
        assertThat(ocgClient.config.getCredential()).isEqualTo("OCG_KEY");
        assertThat(ocgClient.identity.agent()).isEqualTo("trusted-agent");
        assertThat(ocgClient.identity.sessionId()).isEqualTo("stored-session");
        assertThat(ocgClient.identity.executionId()).isEqualTo("root-workflow");
    }

    @Test
    void rejectsChildNonTerminalNonAgentAndMissingMemoryExecutions() {
        WorkflowModel child = workflow("user");
        child.setParentWorkflowId("parent");
        assertThat(service.state(child).reason()).isEqualTo("CHILD_EXECUTION");

        WorkflowModel running = workflow("user");
        running.setStatus(WorkflowModel.Status.RUNNING);
        assertThat(service.state(running).reason()).isEqualTo("EXECUTION_NOT_TERMINAL");

        WorkflowModel ordinary = workflow("user");
        ordinary.getWorkflowDefinition().setMetadata(Map.of());
        assertThat(service.state(ordinary).reason()).isEqualTo("NOT_AGENT_EXECUTION");

        WorkflowModel withoutMemory = workflow("user");
        withoutMemory
                .getWorkflowDefinition()
                .setMetadata(Map.of("classifier", WorkflowClassifier.AGENT));
        assertThat(service.state(withoutMemory).reason()).isEqualTo("OCG_MEMORY_NOT_CONFIGURED");
    }

    @Test
    void rejectsInvalidRatingAndReasonsBeforeExecutionLookup() {
        assertClientError(
                () -> service.set("execution", "useful", "reason"), "INVALID_FEEDBACK_RATING");
        assertClientError(
                () -> service.set("execution", "positive", null),
                AgentFeedbackService.FEEDBACK_REASON_REQUIRED);
        assertClientError(
                () -> service.set("execution", "positive", "  "),
                AgentFeedbackService.FEEDBACK_REASON_REQUIRED);
        assertClientError(
                () -> service.set("execution", "positive", "x".repeat(2_001)),
                AgentFeedbackService.FEEDBACK_REASON_TOO_LONG);
    }

    @Test
    void mapsOcgFailuresToStableApiErrors() {
        ocgClient.failure =
                new OcgFeedbackClientException(
                        OcgFeedbackClientException.Failure.UPSTREAM_TIMEOUT, null, null);

        assertThatThrownBy(() -> service.get(workflow("user")))
                .isInstanceOfSatisfying(
                        AgentFeedbackException.class,
                        error -> {
                            assertThat(error.getStatus()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
                            assertThat(error.getCode())
                                    .isEqualTo(AgentFeedbackService.UPSTREAM_TIMEOUT);
                        });
    }

    private static void assertClientError(ThrowingCall call, String code) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(
                        AgentFeedbackException.class,
                        error -> {
                            assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                            assertThat(error.getCode()).isEqualTo(code);
                        });
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run();
    }

    private static WorkflowModel workflow(String configuredUser) {
        Map<String, Object> memory = new LinkedHashMap<>();
        memory.put("ocgUrl", "https://ocg.example");
        memory.put("credential", "OCG_KEY");
        memory.put("agent", "trusted-agent");
        if (configuredUser != null) memory.put("user", configuredUser);
        Map<String, Object> agentDefinition = new LinkedHashMap<>();
        agentDefinition.put("longTermMemory", memory);
        WorkflowDef definition = new WorkflowDef();
        definition.setMetadata(
                Map.of("classifier", WorkflowClassifier.AGENT, "agentDef", agentDefinition));

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("root-workflow");
        workflow.setStatus(WorkflowModel.Status.COMPLETED);
        workflow.setWorkflowDefinition(definition);
        workflow.setInput(new LinkedHashMap<>(Map.of("session_id", "stored-session")));
        return workflow;
    }

    private static final class RecordingOcgClient implements OcgClient {
        private LongTermMemoryConfig config;
        private OcgExecutionIdentity identity;
        private OcgFeedbackRating rating;
        private String reason;
        private OcgFeedback feedback = new OcgFeedback(null, null, null);
        private OcgExecutionMemory memory = new OcgExecutionMemory(null);
        private OcgFeedbackClientException failure;

        @Override
        public CompletionStage<Void> exportAgentRun(
                LongTermMemoryConfig config, Map<String, Object> payload) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public OcgFeedback getFeedback(LongTermMemoryConfig config, OcgExecutionIdentity identity) {
            record(config, identity, null, null);
            return feedback;
        }

        @Override
        public OcgExecutionMemory getExecutionMemory(
                LongTermMemoryConfig config, OcgExecutionIdentity identity) {
            record(config, identity, null, null);
            return memory;
        }

        @Override
        public OcgFeedback setFeedback(
                LongTermMemoryConfig config,
                OcgExecutionIdentity identity,
                OcgFeedbackRating rating,
                String reason) {
            record(config, identity, rating, reason);
            return feedback;
        }

        private void record(
                LongTermMemoryConfig config,
                OcgExecutionIdentity identity,
                OcgFeedbackRating rating,
                String reason) {
            if (failure != null) throw failure;
            this.config = config;
            this.identity = identity;
            this.rating = rating;
            this.reason = reason;
        }
    }
}
