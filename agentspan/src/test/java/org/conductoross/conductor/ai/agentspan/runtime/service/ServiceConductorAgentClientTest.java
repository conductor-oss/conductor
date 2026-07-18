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

import java.util.List;
import java.util.Map;

import org.conductoross.conductor.ai.agent.ConductorAgentCancelRequest;
import org.conductoross.conductor.ai.agent.ConductorAgentRespondRequest;
import org.conductoross.conductor.ai.agent.ConductorAgentStartRequest;
import org.conductoross.conductor.ai.agent.ConductorAgentStartResponse;
import org.conductoross.conductor.ai.agent.ConductorAgentState;
import org.conductoross.conductor.ai.agent.ConductorAgentStatusResponse;
import org.conductoross.conductor.common.metadata.agent.AgentStartRequest;
import org.conductoross.conductor.common.metadata.agent.AgentStartResponse;
import org.conductoross.conductor.common.metadata.agent.AgentStatusResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServiceConductorAgentClientTest {

    @Test
    void translatesStartModelsAtTheServiceBoundary() {
        AgentService agentService = mock(AgentService.class);
        when(agentService.start(any(AgentStartRequest.class)))
                .thenReturn(
                        AgentStartResponse.builder()
                                .executionId("exec-1")
                                .agentName("planner")
                                .requiredWorkers(List.of("lookup"))
                                .build());
        ServiceConductorAgentClient client = new ServiceConductorAgentClient(agentService);

        ConductorAgentStartResponse response =
                client.startAgent(
                        ConductorAgentStartRequest.builder()
                                .name("planner")
                                .version(3)
                                .prompt("plan this")
                                .model("openai:gpt-5")
                                .sessionId("session-1")
                                .context(Map.of("tenant", "acme"))
                                .idempotencyKey("key-1")
                                .timeoutSeconds(60)
                                .staticPlan(Map.of("steps", List.of()))
                                .build());

        ArgumentCaptor<AgentStartRequest> requestCaptor =
                ArgumentCaptor.forClass(AgentStartRequest.class);
        verify(agentService).start(requestCaptor.capture());
        AgentStartRequest serviceRequest = requestCaptor.getValue();
        assertEquals("planner", serviceRequest.getName());
        assertEquals(3, serviceRequest.getVersion());
        assertEquals("plan this", serviceRequest.getPrompt());
        assertEquals("openai:gpt-5", serviceRequest.getModel());
        assertEquals("session-1", serviceRequest.getSessionId());
        assertEquals(Map.of("tenant", "acme"), serviceRequest.getContext());
        assertEquals("key-1", serviceRequest.getIdempotencyKey());
        assertEquals(60, serviceRequest.getTimeoutSeconds());
        assertEquals(Map.of("steps", List.of()), serviceRequest.getStaticPlan());
        assertEquals("exec-1", response.getExecutionId());
        assertEquals("planner", response.getAgentName());
        assertEquals(List.of("lookup"), response.getRequiredWorkers());
    }

    @Test
    void translatesStatusAndCommandModelsAtTheServiceBoundary() {
        AgentService agentService = mock(AgentService.class);
        when(agentService.getStatus("exec-1"))
                .thenReturn(
                        AgentStatusResponse.builder()
                                .executionId("exec-1")
                                .status("COMPLETED")
                                .complete(true)
                                .output(Map.of("result", "done"))
                                .pendingTool(
                                        Map.of(
                                                "tool_name", "publish_article",
                                                "taskRefName", "approve_publish"))
                                .startTime(10L)
                                .endTime(20L)
                                .build());
        ServiceConductorAgentClient client = new ServiceConductorAgentClient(agentService);

        ConductorAgentStatusResponse status = client.getAgentStatus("exec-1");
        client.respond(
                ConductorAgentRespondRequest.builder()
                        .executionId("exec-1")
                        .body(Map.of("result", "approved"))
                        .build());
        client.cancelAgent(
                ConductorAgentCancelRequest.builder()
                        .executionId("exec-1")
                        .reason("parent canceled")
                        .build());

        assertEquals("exec-1", status.getExecutionId());
        assertEquals(ConductorAgentState.COMPLETED, status.getStatus());
        assertTrue(status.isComplete());
        assertEquals(Map.of("result", "done"), status.getOutput());
        assertEquals("publish_article", status.getPendingToolName());
        assertEquals("approve_publish", status.getPendingToolTaskRefName());
        assertEquals(10L, status.getStartTime());
        assertEquals(20L, status.getEndTime());
        verify(agentService).respond("exec-1", Map.of("result", "approved"));
        verify(agentService).cancelAgent("exec-1", "parent canceled");
    }

    @Test
    void statusContractUsesEnumAndPrimitiveTimestamps() throws Exception {
        assertEquals(
                ConductorAgentState.class,
                ConductorAgentStatusResponse.class.getDeclaredField("status").getType());
        assertEquals(
                long.class,
                ConductorAgentStatusResponse.class.getDeclaredField("startTime").getType());
        assertEquals(
                long.class,
                ConductorAgentStatusResponse.class.getDeclaredField("endTime").getType());
    }

    @Test
    void normalizesWorkflowStatusesAndMissingPendingToolFields() {
        AgentService agentService = mock(AgentService.class);
        when(agentService.getStatus("exec-2"))
                .thenReturn(
                        AgentStatusResponse.builder()
                                .executionId("exec-2")
                                .status("TIMED_OUT")
                                .pendingTool(Map.of("taskRefName", "review_result"))
                                .build());
        ServiceConductorAgentClient client = new ServiceConductorAgentClient(agentService);

        ConductorAgentStatusResponse status = client.getAgentStatus("exec-2");

        assertEquals(ConductorAgentState.FAILED, status.getStatus());
        assertEquals("review_result", status.getPendingToolName());
        assertEquals("review_result", status.getPendingToolTaskRefName());
        assertEquals(0L, status.getStartTime());
        assertEquals(0L, status.getEndTime());
    }
}
