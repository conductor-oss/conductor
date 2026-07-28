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
package org.conductoross.conductor.ai.agentspan.a2a;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.conductoross.conductor.ai.a2a.model.A2AMessage;
import org.conductoross.conductor.ai.a2a.model.A2ATask;
import org.conductoross.conductor.ai.a2a.model.AgentCard;
import org.conductoross.conductor.ai.a2a.model.Part;
import org.conductoross.conductor.ai.a2a.model.TaskState;
import org.conductoross.conductor.ai.a2a.model.TaskStatus;
import org.conductoross.conductor.ai.a2a.server.A2AServerException;
import org.conductoross.conductor.ai.a2a.server.A2AServerProperties;
import org.conductoross.conductor.ai.agentspan.runtime.service.AgentService;
import org.conductoross.conductor.common.metadata.agent.AgentStartRequest;
import org.conductoross.conductor.common.metadata.agent.AgentStartResponse;
import org.conductoross.conductor.common.metadata.agent.AgentSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.Workflow.WorkflowStatus;
import com.netflix.conductor.service.TaskService;
import com.netflix.conductor.service.WorkflowService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class A2ANativeAgentFacadeTest {

    private AgentService agentService;
    private WorkflowService workflowService;
    private TaskService taskService;
    private A2AServerProperties properties;
    private A2ANativeAgentFacade facade;

    @BeforeEach
    void setUp() {
        agentService = mock(AgentService.class);
        workflowService = mock(WorkflowService.class);
        taskService = mock(TaskService.class);
        properties = new A2AServerProperties();
        facade =
                new A2ANativeAgentFacade(agentService, workflowService, taskService, properties);
    }

    private AgentSummary summary(String name) {
        return AgentSummary.builder()
                .name(name)
                .version(2)
                .description("A helpful greeter agent")
                .build();
    }

    private Workflow workflow(String id, WorkflowStatus status) {
        Workflow wf = new Workflow();
        wf.setWorkflowId(id);
        wf.setStatus(status);
        wf.setCorrelationId("ctx-1");
        return wf;
    }

    private A2AMessage userMessage() {
        Part part = new Part();
        part.setKind("text");
        part.setText("hello");
        A2AMessage message = new A2AMessage();
        message.setMessageId("m-1");
        message.setContextId("ctx-1");
        message.setParts(List.of(part));
        return message;
    }

    // ---- exposure ----------------------------------------------------------------------------

    @Test
    void isExposed_returnsTrueForRegisteredAgent() {
        when(agentService.listAgents()).thenReturn(List.of(summary("greeter")));
        assertTrue(facade.isExposed("greeter"));
    }

    @Test
    void isExposed_returnsFalseForUnknownAgent() {
        when(agentService.listAgents()).thenReturn(List.of(summary("greeter")));
        assertFalse(facade.isExposed("unknown"));
    }

    @Test
    void notExposed_throwsOnAgentCard() {
        when(agentService.listAgents()).thenReturn(List.of());
        assertThrows(A2AServerException.class, () -> facade.agentCard("greeter", "http://host"));
    }

    // ---- agent card --------------------------------------------------------------------------

    @Test
    void agentCard_builtFromAgentSummary() {
        when(agentService.listAgents()).thenReturn(List.of(summary("greeter")));

        AgentCard card = facade.agentCard("greeter", "http://host:8080");

        assertEquals("greeter", card.getName());
        assertEquals("2", card.getVersion());
        assertEquals("http://host:8080/api/a2a/agent/greeter", card.getUrl());
        assertEquals("A helpful greeter agent", card.getDescription());
        assertEquals(1, card.getSkills().size());
        assertEquals("greeter", card.getSkills().get(0).getId());
    }

    @Test
    void agentCard_fallbackDescription_whenNoneSet() {
        AgentSummary noDesc = AgentSummary.builder().name("greeter").version(1).build();
        when(agentService.listAgents()).thenReturn(List.of(noDesc));

        AgentCard card = facade.agentCard("greeter", "http://host");

        assertNotNull(card.getDescription());
        assertTrue(card.getDescription().contains("greeter"));
    }

    // ---- message/send ------------------------------------------------------------------------

    @Test
    void sendMessage_startsAgentWithIdempotencyKey() {
        when(agentService.listAgents()).thenReturn(List.of(summary("greeter")));
        when(agentService.start(any(AgentStartRequest.class)))
                .thenReturn(AgentStartResponse.builder().executionId("exec-1").build());
        when(workflowService.getExecutionStatus("exec-1", false))
                .thenReturn(workflow("exec-1", WorkflowStatus.RUNNING));

        A2ATask task = facade.sendMessage("greeter", userMessage());

        assertEquals("exec-1", task.getId());
        assertEquals(TaskState.WORKING, task.getStatus().getState());

        ArgumentCaptor<AgentStartRequest> captor =
                ArgumentCaptor.forClass(AgentStartRequest.class);
        verify(agentService).start(captor.capture());
        AgentStartRequest req = captor.getValue();
        assertEquals("greeter", req.getName());
        assertEquals("greeter:m-1", req.getIdempotencyKey());
        assertEquals("ctx-1", req.getSessionId());
        assertEquals("hello", req.getPrompt());
    }

    @Test
    void sendMessage_unknownAgent_throws() {
        when(agentService.listAgents()).thenReturn(List.of());
        assertThrows(
                A2AServerException.class, () -> facade.sendMessage("unknown", userMessage()));
    }

    // ---- message/send (multi-turn resume) ----------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void sendMessage_withExistingTaskId_resumesPausedExecution() {
        when(agentService.listAgents()).thenReturn(List.of(summary("greeter")));

        Workflow blocked = workflow("exec-1", WorkflowStatus.RUNNING);
        Task human = new Task();
        human.setTaskType("HUMAN");
        human.setReferenceTaskName("await_input");
        human.setStatus(Task.Status.IN_PROGRESS);
        blocked.setTasks(List.of(human));

        when(workflowService.getExecutionStatus("exec-1", true))
                .thenReturn(blocked)
                .thenReturn(workflow("exec-1", WorkflowStatus.COMPLETED));

        A2AMessage followUp = userMessage();
        followUp.setMessageId("m-2");
        followUp.setTaskId("exec-1");

        A2ATask task = facade.sendMessage("greeter", followUp);

        ArgumentCaptor<Map<String, Object>> output = ArgumentCaptor.forClass(Map.class);
        verify(taskService)
                .updateTask(
                        eq("exec-1"),
                        eq("await_input"),
                        eq(TaskResult.Status.COMPLETED),
                        eq("a2a-resume"),
                        output.capture());
        assertEquals("hello", output.getValue().get("_a2a_text"));
        verify(agentService, never()).start(any());
        assertEquals(TaskState.COMPLETED, task.getStatus().getState());
    }

    @Test
    void sendMessage_withTaskId_terminalExecution_returnsStateWithoutResuming() {
        when(agentService.listAgents()).thenReturn(List.of(summary("greeter")));
        when(workflowService.getExecutionStatus("exec-1", true))
                .thenReturn(workflow("exec-1", WorkflowStatus.COMPLETED));

        A2AMessage followUp = userMessage();
        followUp.setTaskId("exec-1");

        A2ATask task = facade.sendMessage("greeter", followUp);

        assertEquals(TaskState.COMPLETED, task.getStatus().getState());
        verify(taskService, never()).updateTask(anyString(), anyString(), any(), anyString(), any());
        verify(agentService, never()).start(any());
    }

    // ---- message/stream ----------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void streamMessage_emitsTaskThenArtifactThenFinalStatus() throws Exception {
        properties.setStreamPollIntervalMillis(1);
        when(agentService.listAgents()).thenReturn(List.of(summary("greeter")));
        when(agentService.start(any()))
                .thenReturn(AgentStartResponse.builder().executionId("exec-1").build());
        when(workflowService.getExecutionStatus("exec-1", false))
                .thenReturn(workflow("exec-1", WorkflowStatus.RUNNING));
        Workflow completed = workflow("exec-1", WorkflowStatus.COMPLETED);
        completed.setOutput(Map.of("reply", "hi there"));
        when(workflowService.getExecutionStatus("exec-1", true))
                .thenReturn(workflow("exec-1", WorkflowStatus.RUNNING))
                .thenReturn(completed);

        List<Object> events = new ArrayList<>();
        facade.streamMessage("greeter", userMessage(), 42, events::add);

        @SuppressWarnings("unchecked")
        Map<String, Object> firstEnvelope = (Map<String, Object>) events.get(0);
        assertEquals(42, firstEnvelope.get("id"));
        assertTrue(firstEnvelope.get("result") instanceof A2ATask);
        assertEquals(
                TaskState.WORKING,
                ((A2ATask) firstEnvelope.get("result")).getStatus().getState());

        boolean sawArtifact =
                events.stream()
                        .map(e -> ((Map<String, Object>) e).get("result"))
                        .filter(Map.class::isInstance)
                        .anyMatch(r -> "artifact-update".equals(((Map<?, ?>) r).get("kind")));
        assertTrue(sawArtifact, "expected artifact-update event; got " + events);

        Map<String, Object> lastResult =
                (Map<String, Object>)
                        ((Map<String, Object>) events.get(events.size() - 1)).get("result");
        assertEquals("status-update", lastResult.get("kind"));
        assertEquals(Boolean.TRUE, lastResult.get("final"));
        assertEquals(TaskState.COMPLETED, ((TaskStatus) lastResult.get("status")).getState());
    }

    // ---- tasks/get ---------------------------------------------------------------------------

    @Test
    void getTask_completed_mapsToCompletedWithArtifacts() {
        when(agentService.listAgents()).thenReturn(List.of(summary("greeter")));
        Workflow wf = workflow("exec-1", WorkflowStatus.COMPLETED);
        wf.setOutput(Map.of("reply", "hi"));
        when(workflowService.getExecutionStatus("exec-1", true)).thenReturn(wf);

        A2ATask task = facade.getTask("greeter", "exec-1");

        assertEquals(TaskState.COMPLETED, task.getStatus().getState());
        assertNotNull(task.getArtifacts());
        assertEquals(1, task.getArtifacts().size());
    }

    @Test
    void getTask_blockedOnHuman_mapsToInputRequired() {
        when(agentService.listAgents()).thenReturn(List.of(summary("greeter")));
        Workflow wf = workflow("exec-1", WorkflowStatus.RUNNING);
        Task human = new Task();
        human.setTaskType("HUMAN");
        human.setStatus(Task.Status.IN_PROGRESS);
        wf.setTasks(List.of(human));
        when(workflowService.getExecutionStatus("exec-1", true)).thenReturn(wf);

        A2ATask task = facade.getTask("greeter", "exec-1");

        assertEquals(TaskState.INPUT_REQUIRED, task.getStatus().getState());
        assertNotNull(task.getStatus().getMessage());
    }

    @Test
    void getTask_unknownAgent_throws() {
        when(agentService.listAgents()).thenReturn(List.of());
        assertThrows(A2AServerException.class, () -> facade.getTask("unknown", "exec-1"));
    }

    // ---- tasks/cancel ------------------------------------------------------------------------

    @Test
    void cancelTask_terminatesAndReturnsCanceled() {
        when(agentService.listAgents()).thenReturn(List.of(summary("greeter")));
        when(workflowService.getExecutionStatus("exec-1", true))
                .thenReturn(workflow("exec-1", WorkflowStatus.RUNNING));
        when(workflowService.getExecutionStatus("exec-1", false))
                .thenReturn(workflow("exec-1", WorkflowStatus.TERMINATED));

        A2ATask task = facade.cancelTask("greeter", "exec-1");

        verify(workflowService).terminateWorkflow(eq("exec-1"), any());
        assertEquals(TaskState.CANCELED, task.getStatus().getState());
    }

    @Test
    void cancelTask_alreadyTerminal_skipsTerminate() {
        when(agentService.listAgents()).thenReturn(List.of(summary("greeter")));
        when(workflowService.getExecutionStatus("exec-1", true))
                .thenReturn(workflow("exec-1", WorkflowStatus.COMPLETED));

        facade.cancelTask("greeter", "exec-1");

        verify(workflowService, never()).terminateWorkflow(anyString(), anyString());
    }

    // ---- status mapping ----------------------------------------------------------------------

    @Test
    void mapState_coversAllStatuses() {
        when(agentService.listAgents()).thenReturn(List.of(summary("greeter")));
        when(agentService.start(any()))
                .thenReturn(AgentStartResponse.builder().executionId("exec-1").build());

        assertEquals(
                TaskState.COMPLETED,
                taskState(workflow("exec-1", WorkflowStatus.COMPLETED)));
        assertEquals(
                TaskState.FAILED,
                taskState(workflow("exec-1", WorkflowStatus.FAILED)));
        assertEquals(
                TaskState.FAILED,
                taskState(workflow("exec-1", WorkflowStatus.TIMED_OUT)));
        assertEquals(
                TaskState.CANCELED,
                taskState(workflow("exec-1", WorkflowStatus.TERMINATED)));
        assertEquals(
                TaskState.WORKING,
                taskState(workflow("exec-1", WorkflowStatus.PAUSED)));
        assertEquals(
                TaskState.WORKING,
                taskState(workflow("exec-1", WorkflowStatus.RUNNING)));
    }

    private String taskState(Workflow wf) {
        when(workflowService.getExecutionStatus(wf.getWorkflowId(), true)).thenReturn(wf);
        when(agentService.listAgents()).thenReturn(List.of(summary("greeter")));
        return facade.getTask("greeter", wf.getWorkflowId()).getStatus().getState();
    }
}
