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
package org.conductoross.conductor.ai.agent;

import java.util.Map;

import org.conductoross.conductor.ai.a2a.model.A2AMessage;
import org.conductoross.conductor.ai.a2a.model.A2ATask;
import org.conductoross.conductor.ai.a2a.model.TaskState;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Behavior coverage for the client-backed durable Conductor-agent state machine. */
class ConductorAgentDelegateTest {

    @Test
    void startsOnceThenPollsToCompletion() {
        FakeConductorAgentClient client = new FakeConductorAgentClient();
        ConductorAgentDelegate delegate = new ConductorAgentDelegate(client);
        Task first =
                task(
                        Map.of(
                                "agentType",
                                "conductor",
                                "name",
                                "planner",
                                "prompt",
                                "go",
                                "sessionId",
                                "session-1",
                                "pollIntervalSeconds",
                                11));

        TaskResult started = delegate.execute(first);

        assertEquals(TaskResult.Status.IN_PROGRESS, started.getStatus());
        assertEquals(11, started.getCallbackAfterSeconds());
        assertEquals("exec-1", started.getOutputData().get("executionId"));
        assertEquals(TaskState.WORKING, started.getOutputData().get("state"));
        assertEquals("exec-1", started.getOutputData().get("taskId"));
        assertEquals("session-1", started.getOutputData().get("contextId"));
        A2ATask workingTask = protocolTask(started);
        assertEquals("task", workingTask.getKind());
        assertEquals(TaskState.WORKING, workingTask.getStatus().getState());
        assertNotNull(started.getOutputData().get("agentStartTime"));
        assertFalse(started.getOutputData().containsKey("agentEndTime"));
        assertNotNull(client.startedRequest.getIdempotencyKey());
        assertTrue(client.startedRequest.getIdempotencyKey().contains("wf-1:agent_ref:2"));

        Task polled = task(first.getInputData());
        polled.setOutputData(started.getOutputData());
        client.status =
                ConductorAgentStatusResponse.builder()
                        .executionId("exec-1")
                        .status("COMPLETED")
                        .complete(true)
                        .output(Map.of("context", Map.of("language", "en"), "result", "done"))
                        .startTime(1000L)
                        .endTime(2000L)
                        .build();

        TaskResult completed = delegate.execute(polled);

        assertEquals(TaskResult.Status.COMPLETED, completed.getStatus());
        assertEquals("done", ((Map<?, ?>) completed.getOutputData().get("output")).get("result"));
        assertEquals("done", completed.getOutputData().get("text"));
        assertEquals(TaskState.COMPLETED, completed.getOutputData().get("state"));
        A2ATask protocolTask = protocolTask(completed);
        assertEquals("exec-1", protocolTask.getId());
        assertEquals("session-1", protocolTask.getContextId());
        assertEquals(TaskState.COMPLETED, protocolTask.getStatus().getState());
        assertEquals("agent", protocolTask.getStatus().getMessage().getRole());
        assertEquals("message", protocolTask.getStatus().getMessage().getKind());
        assertEquals("done", protocolTask.getStatus().getMessage().getParts().getFirst().getText());
        assertEquals("text", protocolTask.getArtifacts().getFirst().getParts().get(0).getKind());
        assertEquals("data", protocolTask.getArtifacts().getFirst().getParts().get(1).getKind());
        assertEquals(1000L, completed.getOutputData().get("agentStartTime"));
        assertEquals(2000L, completed.getOutputData().get("agentEndTime"));
        assertFalse(completed.getOutputData().containsKey("agentStartedAt"));
        assertEquals(1, client.startCalls);
        assertEquals(1, client.statusCalls);
    }

    @Test
    void waitingExecutionCompletesTaskWithPendingTool() {
        FakeConductorAgentClient client = new FakeConductorAgentClient();
        client.status =
                ConductorAgentStatusResponse.builder()
                        .executionId("exec-1")
                        .status("RUNNING")
                        .waiting(true)
                        .pendingTool(Map.of("taskRefName", "approval"))
                        .build();
        Task task = task(Map.of("agentType", "conductor"));
        task.setOutputData(Map.of("executionId", "exec-1"));

        TaskResult result = new ConductorAgentDelegate(client).execute(task);

        assertEquals(TaskResult.Status.COMPLETED, result.getStatus());
        assertEquals(true, result.getOutputData().get("waiting"));
        assertEquals(TaskState.INPUT_REQUIRED, result.getOutputData().get("state"));
        A2AMessage statusMessage = protocolTask(result).getStatus().getMessage();
        assertEquals("data", statusMessage.getParts().getFirst().getKind());
        assertEquals(
                "approval",
                ((Map<?, ?>)
                                ((Map<?, ?>) statusMessage.getParts().getFirst().getData())
                                        .get("pendingTool"))
                        .get("taskRefName"));
        assertEquals(
                "approval",
                ((Map<?, ?>) result.getOutputData().get("pendingTool")).get("taskRefName"));
    }

    @Test
    void canceledExecutionIsTerminalAndPreservesState() {
        FakeConductorAgentClient client = new FakeConductorAgentClient();
        client.status =
                ConductorAgentStatusResponse.builder()
                        .executionId("exec-1")
                        .status("TERMINATED")
                        .complete(true)
                        .reasonForIncompletion("parent canceled")
                        .startTime(1000L)
                        .endTime(2500L)
                        .build();
        Task task = task(Map.of("agentType", "conductor"));
        task.setOutputData(Map.of("executionId", "exec-1"));

        TaskResult result = new ConductorAgentDelegate(client).execute(task);

        assertEquals(TaskResult.Status.CANCELED, result.getStatus());
        assertEquals(TaskState.CANCELED, result.getOutputData().get("state"));
        assertEquals(TaskState.CANCELED, protocolTask(result).getStatus().getState());
        assertEquals(
                "parent canceled",
                protocolTask(result).getStatus().getMessage().getParts().getFirst().getText());
        assertEquals("parent canceled", result.getReasonForIncompletion());
        assertEquals(1000L, result.getOutputData().get("agentStartTime"));
        assertEquals(2500L, result.getOutputData().get("agentEndTime"));
    }

    @Test
    void cancellationUsesAgentClientInsteadOfWorkflowExecutor() {
        FakeConductorAgentClient client = new FakeConductorAgentClient();
        client.status =
                ConductorAgentStatusResponse.builder()
                        .executionId("exec-1")
                        .status("RUNNING")
                        .build();
        Task task = task(Map.of("agentType", "conductor"));
        task.setOutputData(Map.of("executionId", "exec-1"));

        new ConductorAgentDelegate(client).cancel(task, "workflow canceled");

        assertEquals("exec-1", client.canceledExecutionId);
        assertEquals("workflow canceled", client.cancelReason);
    }

    private static Task task(Map<String, Object> input) {
        Task task = new Task();
        task.setTaskId("task-1");
        task.setWorkflowInstanceId("wf-1");
        task.setReferenceTaskName("agent_ref");
        task.setIteration(2);
        task.setStatus(Task.Status.IN_PROGRESS);
        task.setInputData(input);
        return task;
    }

    private static A2ATask protocolTask(TaskResult result) {
        return new ObjectMapper().convertValue(result.getOutputData().get("task"), A2ATask.class);
    }

    private static final class FakeConductorAgentClient implements ConductorAgentClient {

        private ConductorAgentStartRequest startedRequest;
        private ConductorAgentStatusResponse status =
                ConductorAgentStatusResponse.builder()
                        .executionId("exec-1")
                        .status("RUNNING")
                        .running(true)
                        .build();
        private int startCalls;
        private int statusCalls;
        private String canceledExecutionId;
        private String cancelReason;

        @Override
        public ConductorAgentStartResponse startAgent(ConductorAgentStartRequest request) {
            startedRequest = request;
            startCalls++;
            return ConductorAgentStartResponse.builder()
                    .executionId("exec-1")
                    .agentName("planner")
                    .build();
        }

        @Override
        public ConductorAgentStatusResponse getAgentStatus(ConductorAgentStatusRequest request) {
            statusCalls++;
            return status;
        }

        @Override
        public void respond(ConductorAgentRespondRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void cancelAgent(ConductorAgentCancelRequest request) {
            canceledExecutionId = request.getExecutionId();
            cancelReason = request.getReason();
        }
    }
}
