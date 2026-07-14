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
import org.conductoross.conductor.ai.agent.ConductorAgentState;
import org.conductoross.conductor.ai.agent.ConductorAgentSummary;
import org.conductoross.conductor.ai.agentspan.runtime.model.AgentSummary;
import org.conductoross.conductor.ai.agentspan.runtime.service.AgentService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ConductorAgentRuntimeAdapter}. Uses a hand-written {@link AgentService}
 * subclass (no mocking framework, per AGENTS.md) that records delegated calls and returns canned
 * status/agent snapshots, so the tests exercise the adapter's real type/state translation logic.
 */
class ConductorAgentRuntimeAdapterTest {

    /**
     * Hand-written {@link AgentService} stand-in. The real {@code AgentService} constructor requires
     * twelve collaborators; we pass nulls since these tests only drive the methods overridden below.
     */
    private static final class RecordingAgentService extends AgentService {

        private Map<String, Object> statusToReturn;
        private List<AgentSummary> agentsToReturn;

        private String respondedExecutionId;
        private Map<String, Object> respondedMessage;
        private String canceledExecutionId;
        private String cancelReason;

        RecordingAgentService() {
            super(null, null, null, null, null, null, null, null, null, null, null, null);
        }

        @Override
        public Map<String, Object> getStatus(String executionId) {
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

        List<ConductorAgentSummary> agents =
                new ConductorAgentRuntimeAdapter(service).listAgents();

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

    @Test
    void cancelDelegatesThrough() {
        RecordingAgentService service = new RecordingAgentService();

        new ConductorAgentRuntimeAdapter(service).cancel("e9", "user requested");

        assertThat(service.canceledExecutionId).isEqualTo("e9");
        assertThat(service.cancelReason).isEqualTo("user requested");
    }
}
