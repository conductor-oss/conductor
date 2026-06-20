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
package org.conductoross.conductor.ai.a2a.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.conductoross.conductor.ai.a2a.model.A2AMessage;
import org.conductoross.conductor.ai.a2a.model.A2ATask;
import org.conductoross.conductor.ai.a2a.model.AgentCard;
import org.conductoross.conductor.ai.a2a.model.Part;
import org.conductoross.conductor.ai.a2a.model.TaskState;
import org.conductoross.conductor.ai.a2a.model.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.workflow.IdempotencyStrategy;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.Workflow.WorkflowStatus;
import com.netflix.conductor.service.MetadataService;
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

class A2AWorkflowAgentTest {

    private WorkflowService workflowService;
    private MetadataService metadataService;
    private TaskService taskService;
    private A2AServerProperties properties;
    private A2AWorkflowAgent agent;

    @BeforeEach
    void setUp() {
        workflowService = mock(WorkflowService.class);
        metadataService = mock(MetadataService.class);
        taskService = mock(TaskService.class);
        properties = new A2AServerProperties();
        agent = new A2AWorkflowAgent(workflowService, metadataService, taskService, properties);
    }

    private WorkflowDef def(String name) {
        WorkflowDef def = new WorkflowDef();
        def.setName(name);
        def.setVersion(3);
        def.setDescription("Orders a pizza");
        return def;
    }

    private Workflow workflow(String name, WorkflowStatus status) {
        Workflow wf = new Workflow();
        wf.setWorkflowId("wf-1");
        wf.setStatus(status);
        wf.setCorrelationId("ctx-1");
        wf.setWorkflowDefinition(def(name));
        return wf;
    }

    private A2AMessage userMessage() {
        Part part = new Part();
        part.setKind("text");
        part.setText("one large pepperoni");
        A2AMessage message = new A2AMessage();
        message.setMessageId("m-1");
        message.setContextId("ctx-1");
        message.setParts(List.of(part));
        return message;
    }

    // ---- exposure ----------------------------------------------------------------------------

    @Test
    void exposed_viaWhitelist() {
        properties.setExposedWorkflows(List.of("order_pizza"));
        when(metadataService.getWorkflowDef("order_pizza", null)).thenReturn(def("order_pizza"));
        when(metadataService.getWorkflowDef("secret_wf", null)).thenReturn(def("secret_wf"));

        assertTrue(agent.isExposed("order_pizza"));
        assertFalse(agent.isExposed("secret_wf"));
    }

    @Test
    void exposed_viaWorkflowMetadata() {
        WorkflowDef def = def("order_pizza");
        Map<String, Object> meta = new HashMap<>();
        meta.put("a2a.enabled", true);
        def.setMetadata(meta);
        when(metadataService.getWorkflowDef("order_pizza", null)).thenReturn(def);

        assertTrue(agent.isExposed("order_pizza"));
    }

    @Test
    void notExposed_throwsOnAgentCard() {
        when(metadataService.getWorkflowDef("order_pizza", null)).thenReturn(def("order_pizza"));
        assertThrows(A2AServerException.class, () -> agent.agentCard("order_pizza", "http://host"));
    }

    // ---- agent card --------------------------------------------------------------------------

    @Test
    void agentCard_builtFromWorkflowDef() {
        properties.setExposedWorkflows(List.of("order_pizza"));
        when(metadataService.getWorkflowDef("order_pizza", null)).thenReturn(def("order_pizza"));

        AgentCard card = agent.agentCard("order_pizza", "http://host:8080");

        assertEquals("order_pizza", card.getName());
        assertEquals("3", card.getVersion());
        assertEquals("http://host:8080/a2a/order_pizza", card.getUrl());
        assertEquals(1, card.getSkills().size());
        assertEquals("order_pizza", card.getSkills().get(0).getId());
    }

    // ---- message/send ------------------------------------------------------------------------

    @Test
    void sendMessage_startsWorkflowWithIdempotencyKey() {
        properties.setExposedWorkflows(List.of("order_pizza"));
        when(metadataService.getWorkflowDef("order_pizza", null)).thenReturn(def("order_pizza"));
        when(workflowService.startWorkflow(any(StartWorkflowRequest.class))).thenReturn("wf-1");
        when(workflowService.getExecutionStatus("wf-1", false))
                .thenReturn(workflow("order_pizza", WorkflowStatus.RUNNING));

        A2ATask task = agent.sendMessage("order_pizza", userMessage());

        assertEquals("wf-1", task.getId());
        assertEquals(TaskState.WORKING, task.getStatus().getState());

        ArgumentCaptor<StartWorkflowRequest> captor =
                ArgumentCaptor.forClass(StartWorkflowRequest.class);
        verify(workflowService).startWorkflow(captor.capture());
        StartWorkflowRequest req = captor.getValue();
        assertEquals("order_pizza", req.getName());
        assertEquals("order_pizza:m-1", req.getIdempotencyKey());
        assertEquals(IdempotencyStrategy.RETURN_EXISTING, req.getIdempotencyStrategy());
        assertEquals("ctx-1", req.getCorrelationId());
        assertEquals("one large pepperoni", req.getInput().get("_a2a_text"));
    }

    // ---- message/send (multi-turn resume) ----------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void sendMessage_withExistingTaskId_resumesPausedWorkflowInsteadOfStartingNew() {
        properties.setExposedWorkflows(List.of("order_pizza"));
        when(metadataService.getWorkflowDef("order_pizza", null)).thenReturn(def("order_pizza"));

        // The paused execution: RUNNING, blocked on a HUMAN task awaiting input.
        Workflow blocked = workflow("order_pizza", WorkflowStatus.RUNNING);
        Task human = new Task();
        human.setTaskType("HUMAN");
        human.setReferenceTaskName("await_topping");
        human.setStatus(Task.Status.IN_PROGRESS);
        blocked.setTasks(List.of(human));
        // After resume the execution has progressed to COMPLETED.
        when(workflowService.getExecutionStatus("wf-1", true))
                .thenReturn(blocked)
                .thenReturn(workflow("order_pizza", WorkflowStatus.COMPLETED));

        A2AMessage followUp = userMessage();
        followUp.setMessageId("m-2");
        followUp.setTaskId("wf-1"); // resume this execution

        A2ATask task = agent.sendMessage("order_pizza", followUp);

        // The follow-up completed the pending HUMAN task with its content as the input...
        ArgumentCaptor<Map<String, Object>> output = ArgumentCaptor.forClass(Map.class);
        verify(taskService)
                .updateTask(
                        eq("wf-1"),
                        eq("await_topping"),
                        eq(TaskResult.Status.COMPLETED),
                        eq("a2a-resume"),
                        output.capture());
        assertEquals("one large pepperoni", output.getValue().get("_a2a_text"));
        // ...and did NOT start a duplicate workflow.
        verify(workflowService, never()).startWorkflow(any(StartWorkflowRequest.class));
        assertEquals(TaskState.COMPLETED, task.getStatus().getState());
    }

    @Test
    void sendMessage_withTaskId_terminalWorkflow_returnsStateWithoutResuming() {
        properties.setExposedWorkflows(List.of("order_pizza"));
        when(metadataService.getWorkflowDef("order_pizza", null)).thenReturn(def("order_pizza"));
        when(workflowService.getExecutionStatus("wf-1", true))
                .thenReturn(workflow("order_pizza", WorkflowStatus.COMPLETED));

        A2AMessage followUp = userMessage();
        followUp.setTaskId("wf-1");

        A2ATask task = agent.sendMessage("order_pizza", followUp);

        assertEquals(TaskState.COMPLETED, task.getStatus().getState());
        verify(taskService, never())
                .updateTask(anyString(), anyString(), any(), anyString(), any());
        verify(workflowService, never()).startWorkflow(any(StartWorkflowRequest.class));
    }

    // ---- message/stream ----------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void streamMessage_emitsTaskThenArtifactThenFinalStatus() throws Exception {
        properties.setExposedWorkflows(List.of("order_pizza"));
        properties.setStreamPollIntervalMillis(1); // keep the test fast
        when(metadataService.getWorkflowDef("order_pizza", null)).thenReturn(def("order_pizza"));
        when(workflowService.startWorkflow(any(StartWorkflowRequest.class))).thenReturn("wf-1");
        // sendMessage() loads without tasks -> RUNNING (working).
        when(workflowService.getExecutionStatus("wf-1", false))
                .thenReturn(workflow("order_pizza", WorkflowStatus.RUNNING));
        // poll loop loads with tasks: still working, then completed with output.
        Workflow completed = workflow("order_pizza", WorkflowStatus.COMPLETED);
        completed.setOutput(Map.of("orderId", "ORD-1"));
        when(workflowService.getExecutionStatus("wf-1", true))
                .thenReturn(workflow("order_pizza", WorkflowStatus.RUNNING))
                .thenReturn(completed);

        List<Object> events = new ArrayList<>();
        agent.streamMessage("order_pizza", userMessage(), 7, events::add);

        // First event is the initial Task (working) and carries our JSON-RPC id.
        Map<String, Object> firstEnvelope = (Map<String, Object>) events.get(0);
        assertEquals(7, firstEnvelope.get("id"));
        Object firstResult = firstEnvelope.get("result");
        assertTrue(firstResult instanceof A2ATask);
        assertEquals(TaskState.WORKING, ((A2ATask) firstResult).getStatus().getState());

        // An artifact-update carries the workflow output.
        boolean sawArtifact =
                events.stream()
                        .map(e -> ((Map<String, Object>) e).get("result"))
                        .filter(Map.class::isInstance)
                        .anyMatch(r -> "artifact-update".equals(((Map<?, ?>) r).get("kind")));
        assertTrue(sawArtifact, "expected an artifact-update event; got " + events);

        // Last event is a final status-update with state completed.
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
        properties.setExposedWorkflows(List.of("order_pizza"));
        when(metadataService.getWorkflowDef("order_pizza", null)).thenReturn(def("order_pizza"));
        Workflow wf = workflow("order_pizza", WorkflowStatus.COMPLETED);
        wf.setOutput(Map.of("orderId", "ORD-42"));
        when(workflowService.getExecutionStatus("wf-1", true)).thenReturn(wf);

        A2ATask task = agent.getTask("order_pizza", "wf-1");

        assertEquals(TaskState.COMPLETED, task.getStatus().getState());
        assertNotNull(task.getArtifacts());
        assertEquals(1, task.getArtifacts().size());
    }

    @Test
    void getTask_blockedOnHuman_mapsToInputRequired() {
        properties.setExposedWorkflows(List.of("order_pizza"));
        when(metadataService.getWorkflowDef("order_pizza", null)).thenReturn(def("order_pizza"));
        Workflow wf = workflow("order_pizza", WorkflowStatus.RUNNING);
        Task human = new Task();
        human.setTaskType("HUMAN");
        human.setStatus(Task.Status.IN_PROGRESS);
        wf.setTasks(List.of(human));
        when(workflowService.getExecutionStatus("wf-1", true)).thenReturn(wf);

        A2ATask task = agent.getTask("order_pizza", "wf-1");

        assertEquals(TaskState.INPUT_REQUIRED, task.getStatus().getState());
        assertNotNull(task.getStatus().getMessage());
    }

    @Test
    void getTask_wrongAgent_throwsNotFound() {
        properties.setExposedWorkflows(List.of("order_pizza"));
        when(metadataService.getWorkflowDef("order_pizza", null)).thenReturn(def("order_pizza"));
        // The execution belongs to a different workflow.
        when(workflowService.getExecutionStatus("wf-1", true))
                .thenReturn(workflow("other_wf", WorkflowStatus.RUNNING));

        assertThrows(A2AServerException.class, () -> agent.getTask("order_pizza", "wf-1"));
    }

    @Test
    void getTask_unverifiableOwnership_failsClosed() {
        properties.setExposedWorkflows(List.of("order_pizza"));
        when(metadataService.getWorkflowDef("order_pizza", null)).thenReturn(def("order_pizza"));
        // Execution has no workflow definition (e.g. archived) — ownership can't be verified.
        Workflow wf = new Workflow();
        wf.setWorkflowId("wf-1");
        wf.setStatus(WorkflowStatus.RUNNING);
        when(workflowService.getExecutionStatus("wf-1", true)).thenReturn(wf);

        assertThrows(A2AServerException.class, () -> agent.getTask("order_pizza", "wf-1"));
    }

    // ---- tasks/cancel ------------------------------------------------------------------------

    @Test
    void cancelTask_terminatesAndReturnsCanceled() {
        properties.setExposedWorkflows(List.of("order_pizza"));
        when(metadataService.getWorkflowDef("order_pizza", null)).thenReturn(def("order_pizza"));
        when(workflowService.getExecutionStatus("wf-1", true))
                .thenReturn(workflow("order_pizza", WorkflowStatus.RUNNING));
        when(workflowService.getExecutionStatus("wf-1", false))
                .thenReturn(workflow("order_pizza", WorkflowStatus.TERMINATED));

        A2ATask task = agent.cancelTask("order_pizza", "wf-1");

        verify(workflowService).terminateWorkflow(eq("wf-1"), any());
        assertEquals(TaskState.CANCELED, task.getStatus().getState());
    }

    // ---- status mapping ----------------------------------------------------------------------

    @Test
    void mapState_coversAllStatuses() {
        assertEquals(TaskState.COMPLETED, agent.mapState(workflow("w", WorkflowStatus.COMPLETED)));
        assertEquals(TaskState.FAILED, agent.mapState(workflow("w", WorkflowStatus.FAILED)));
        assertEquals(TaskState.FAILED, agent.mapState(workflow("w", WorkflowStatus.TIMED_OUT)));
        assertEquals(TaskState.CANCELED, agent.mapState(workflow("w", WorkflowStatus.TERMINATED)));
        assertEquals(TaskState.WORKING, agent.mapState(workflow("w", WorkflowStatus.PAUSED)));
        assertEquals(TaskState.WORKING, agent.mapState(workflow("w", WorkflowStatus.RUNNING)));
    }
}
