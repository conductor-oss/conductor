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
package org.conductoross.conductor.ai.agentspan;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.conductoross.conductor.ai.agent.ConductorAgentExecution;
import org.conductoross.conductor.ai.agent.ConductorAgentStartRequest;
import org.conductoross.conductor.ai.agent.ConductorAgentState;
import org.conductoross.conductor.ai.agent.ConductorAgentSummary;
import org.conductoross.conductor.ai.agent.NonRetryableAgentException;
import org.conductoross.conductor.ai.agentspan.runtime.model.AgentSummary;
import org.conductoross.conductor.ai.agentspan.runtime.service.AgentService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ConductorAgentRuntimeAdapter}. Uses a hand-written {@link AgentService}
 * subclass (no mocking framework, per AGENTS.md) that records delegated calls and returns canned
 * status/agent snapshots, so the tests exercise the adapter's real type/state translation logic.
 */
class ConductorAgentRuntimeAdapterTest {

    /**
     * Hand-written {@link AgentService} stand-in. The real {@code AgentService} constructor
     * requires twelve collaborators; we pass nulls since these tests only drive the methods
     * overridden below.
     */
    private static final class RecordingAgentService extends AgentService {

        private Map<String, Object> statusToReturn;
        private List<AgentSummary> agentsToReturn;

        // When set, the corresponding method throws this instead of returning — lets a test drive
        // the adapter's translation of AgentService errors into the runtime contract exception.
        private RuntimeException getAgentDefException;
        private RuntimeException statusException;
        private RuntimeException respondException;

        private String respondedExecutionId;
        private Map<String, Object> respondedMessage;
        private String canceledExecutionId;
        private String cancelReason;

        RecordingAgentService() {
            super(null, null, null, null, null, null, null, null, null, null, null, null);
        }

        @Override
        public Map<String, Object> getAgentDef(String name, Integer version) {
            if (getAgentDefException != null) {
                throw getAgentDefException;
            }
            return Map.of();
        }

        @Override
        public Map<String, Object> getStatus(String executionId) {
            if (statusException != null) {
                throw statusException;
            }
            return statusToReturn;
        }

        @Override
        public List<AgentSummary> listAgents() {
            return agentsToReturn;
        }

        @Override
        public void respond(String executionId, Map<String, Object> output) {
            this.respondedExecutionId = executionId;
            this.respondedMessage = output;
            if (respondException != null) {
                throw respondException;
            }
        }

        @Override
        public void cancelAgent(String executionId, String reason) {
            this.canceledExecutionId = executionId;
            this.cancelReason = reason;
        }
    }

    private static Map<String, Object> status(String executionId, String statusName) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("executionId", executionId);
        m.put("status", statusName);
        return m;
    }

    @Test
    void getStatusMapsWaitingBeforeTerminalFlags() {
        RecordingAgentService service = new RecordingAgentService();
        Map<String, Object> status = status("e1", "RUNNING");
        status.put("isWaiting", true);
        Map<String, Object> pendingTool = Map.of("taskRefName", "ask_human");
        status.put("pendingTool", pendingTool);
        service.statusToReturn = status;

        ConductorAgentExecution execution =
                new ConductorAgentRuntimeAdapter(service).getStatus("e1");

        assertThat(execution.getExecutionId()).isEqualTo("e1");
        assertThat(execution.getState()).isEqualTo(ConductorAgentState.WAITING);
        assertThat(execution.getPendingTool()).isEqualTo(pendingTool);
        assertThat(execution.isWaiting()).isTrue();
    }

    @Test
    void getStatusMapsCompleted() {
        RecordingAgentService service = new RecordingAgentService();
        Map<String, Object> status = status("e2", "COMPLETED");
        status.put("isComplete", true);
        Map<String, Object> output = Map.of("result", "done");
        status.put("output", output);
        service.statusToReturn = status;

        ConductorAgentExecution execution =
                new ConductorAgentRuntimeAdapter(service).getStatus("e2");

        assertThat(execution.getState()).isEqualTo(ConductorAgentState.COMPLETED);
        assertThat(execution.getOutput()).isEqualTo(output);
        assertThat(execution.isComplete()).isTrue();
    }

    @Test
    void getStatusSurfacesFinalTextFromCompletedOutput() {
        RecordingAgentService service = new RecordingAgentService();
        Map<String, Object> status = status("e10", "COMPLETED");
        status.put("isComplete", true);
        // The compiled agent WorkflowDef emits a canonical "result" output parameter (the LLM's
        // output.result); for an unstructured run it resolves to the agent's final text string.
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("result", "The weather is sunny.");
        output.put("finishReason", "STOP");
        status.put("output", output);
        service.statusToReturn = status;

        ConductorAgentExecution execution =
                new ConductorAgentRuntimeAdapter(service).getStatus("e10");

        assertThat(execution.getState()).isEqualTo(ConductorAgentState.COMPLETED);
        assertThat(execution.getOutput()).isEqualTo(output);
        assertThat(execution.getText()).isEqualTo("The weather is sunny.");
    }

    @Test
    void getStatusLeavesTextNullForStructuredResult() {
        RecordingAgentService service = new RecordingAgentService();
        Map<String, Object> status = status("e11", "COMPLETED");
        status.put("isComplete", true);
        // A schema-typed run resolves "result" to a structured map, not a text string; it stays in
        // output only and text is left null.
        Map<String, Object> structured = Map.of("temperature", 72, "condition", "sunny");
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("result", structured);
        status.put("output", output);
        service.statusToReturn = status;

        ConductorAgentExecution execution =
                new ConductorAgentRuntimeAdapter(service).getStatus("e11");

        assertThat(execution.getState()).isEqualTo(ConductorAgentState.COMPLETED);
        assertThat(execution.getOutput()).isEqualTo(output);
        assertThat(execution.getText()).isNull();
    }

    @Test
    void getStatusMapsFailedAndTimedOut() {
        RecordingAgentService service = new RecordingAgentService();

        Map<String, Object> failed = status("e3", "FAILED");
        failed.put("isComplete", true);
        failed.put("reasonForIncompletion", "boom");
        service.statusToReturn = failed;
        ConductorAgentExecution failedExec =
                new ConductorAgentRuntimeAdapter(service).getStatus("e3");
        assertThat(failedExec.getState()).isEqualTo(ConductorAgentState.FAILED);
        assertThat(failedExec.getReasonForIncompletion()).isEqualTo("boom");

        service.statusToReturn = status("e4", "TIMED_OUT");
        assertThat(new ConductorAgentRuntimeAdapter(service).getStatus("e4").getState())
                .isEqualTo(ConductorAgentState.FAILED);
    }

    @Test
    void getStatusMapsCanceled() {
        RecordingAgentService service = new RecordingAgentService();

        service.statusToReturn = status("e5", "TERMINATED");
        assertThat(new ConductorAgentRuntimeAdapter(service).getStatus("e5").getState())
                .isEqualTo(ConductorAgentState.CANCELED);

        service.statusToReturn = status("e6", "CANCELED");
        assertThat(new ConductorAgentRuntimeAdapter(service).getStatus("e6").getState())
                .isEqualTo(ConductorAgentState.CANCELED);
    }

    @Test
    void getStatusCarriesAgentNameAndSessionId() {
        RecordingAgentService service = new RecordingAgentService();
        Map<String, Object> status = status("e10", "RUNNING");
        status.put("agentName", "planner");
        status.put("sessionId", "sess-1");
        service.statusToReturn = status;

        ConductorAgentExecution execution =
                new ConductorAgentRuntimeAdapter(service).getStatus("e10");

        assertThat(execution.getAgentName()).isEqualTo("planner");
        assertThat(execution.getSessionId()).isEqualTo("sess-1");
    }

    @Test
    void getStatusLeavesIdentityNullWhenAbsent() {
        RecordingAgentService service = new RecordingAgentService();
        service.statusToReturn = status("e11", "RUNNING");

        ConductorAgentExecution execution =
                new ConductorAgentRuntimeAdapter(service).getStatus("e11");

        assertThat(execution.getAgentName()).isNull();
        assertThat(execution.getSessionId()).isNull();
    }

    @Test
    void getStatusDefaultsToRunning() {
        RecordingAgentService service = new RecordingAgentService();
        service.statusToReturn = status("e7", "RUNNING");

        ConductorAgentExecution execution =
                new ConductorAgentRuntimeAdapter(service).getStatus("e7");

        assertThat(execution.getState()).isEqualTo(ConductorAgentState.RUNNING);
        assertThat(execution.isRunning()).isTrue();
    }

    @Test
    void listAgentsMapsEverySummaryField() {
        RecordingAgentService service = new RecordingAgentService();
        service.agentsToReturn =
                List.of(
                        AgentSummary.builder()
                                .name("weather")
                                .version(3)
                                .type("conductor")
                                .tags(List.of("demo", "weather"))
                                .createTime(100L)
                                .updateTime(200L)
                                .description("A weather agent")
                                .checksum("abc123")
                                .build());

        List<ConductorAgentSummary> agents = new ConductorAgentRuntimeAdapter(service).listAgents();

        assertThat(agents).hasSize(1);
        ConductorAgentSummary agent = agents.get(0);
        assertThat(agent.getName()).isEqualTo("weather");
        assertThat(agent.getVersion()).isEqualTo(3);
        assertThat(agent.getType()).isEqualTo("conductor");
        assertThat(agent.getTags()).containsExactly("demo", "weather");
        assertThat(agent.getCreateTime()).isEqualTo(100L);
        assertThat(agent.getUpdateTime()).isEqualTo(200L);
        assertThat(agent.getDescription()).isEqualTo("A weather agent");
        assertThat(agent.getChecksum()).isEqualTo("abc123");
    }

    @Test
    void respondDelegatesThrough() {
        RecordingAgentService service = new RecordingAgentService();
        Map<String, Object> message = Map.of("answer", "yes");

        new ConductorAgentRuntimeAdapter(service).respond("e8", message);

        assertThat(service.respondedExecutionId).isEqualTo("e8");
        assertThat(service.respondedMessage).isEqualTo(message);
    }

    // Finding 1: AgentService signals a permanent misconfiguration (unknown agent) with an
    // IllegalArgumentException; the adapter must translate it into the non-retryable runtime
    // contract exception so the AGENT task fails terminally instead of retrying forever.
    @Test
    void startTranslatesIllegalArgumentToNonRetryable() {
        RecordingAgentService service = new RecordingAgentService();
        service.getAgentDefException = new IllegalArgumentException("Agent not found: typo");

        ConductorAgentStartRequest request =
                ConductorAgentStartRequest.builder().agentName("typo").prompt("hi").build();

        assertThatThrownBy(() -> new ConductorAgentRuntimeAdapter(service).start(request))
                .isInstanceOf(NonRetryableAgentException.class)
                .hasMessage("Agent not found: typo");
    }

    // Finding 1: a resume against an execution with nothing pending surfaces as an
    // IllegalStateException from respond(); the adapter must translate it too.
    @Test
    void respondTranslatesIllegalStateToNonRetryable() {
        RecordingAgentService service = new RecordingAgentService();
        service.respondException =
                new IllegalStateException("No pending HUMAN task found for execution e1");

        assertThatThrownBy(
                        () ->
                                new ConductorAgentRuntimeAdapter(service)
                                        .respond("e1", Map.of("result", "x")))
                .isInstanceOf(NonRetryableAgentException.class)
                .hasMessage("No pending HUMAN task found for execution e1");
    }

    // Finding 1: an unknown execution id surfaced from getStatus() is likewise permanent.
    @Test
    void getStatusTranslatesIllegalArgumentToNonRetryable() {
        RecordingAgentService service = new RecordingAgentService();
        service.statusException = new IllegalArgumentException("Unknown execution: e1");

        assertThatThrownBy(() -> new ConductorAgentRuntimeAdapter(service).getStatus("e1"))
                .isInstanceOf(NonRetryableAgentException.class)
                .hasMessage("Unknown execution: e1");
    }

    @Test
    void cancelDelegatesThrough() {
        RecordingAgentService service = new RecordingAgentService();

        new ConductorAgentRuntimeAdapter(service).cancel("e9", "user requested");

        assertThat(service.canceledExecutionId).isEqualTo("e9");
        assertThat(service.cancelReason).isEqualTo("user requested");
    }
}
