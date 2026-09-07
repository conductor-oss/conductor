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
package org.conductoross.conductor.ai.a2a;

import java.util.List;
import java.util.Map;

import org.conductoross.conductor.ai.a2a.A2AService.SendResult;
import org.conductoross.conductor.ai.a2a.model.A2AMessage;
import org.conductoross.conductor.ai.a2a.model.A2ATask;
import org.conductoross.conductor.ai.a2a.model.AgentCard;
import org.conductoross.conductor.ai.a2a.model.Artifact;
import org.conductoross.conductor.ai.a2a.model.Part;
import org.conductoross.conductor.ai.a2a.model.TaskState;
import org.conductoross.conductor.ai.a2a.model.TaskStatus;
import org.conductoross.conductor.ai.model.A2ACallResult;
import org.conductoross.conductor.ai.tasks.worker.A2AWorkers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.sdk.workflow.executor.task.NonRetryableException;

import static org.conductoross.conductor.ai.a2a.A2AWorkerTestSupport.invoke;
import static org.conductoross.conductor.ai.a2a.A2AWorkerTestSupport.invokeOutput;
import static org.conductoross.conductor.ai.a2a.A2AWorkerTestSupport.task;
import static org.conductoross.conductor.ai.a2a.A2AWorkerTestSupport.unusedAgentClient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class A2AAgentWorkerTest {

    private A2AService service;
    private A2AWorkers workers;

    @BeforeEach
    void setUp() {
        service = mock(A2AService.class);
        workers = new A2AWorkers(service, List.of(unusedAgentClient()));
    }

    private A2ATask agentTask(String state, String artifactText) {
        A2ATask agentTask = new A2ATask();
        agentTask.setId("agent-task-1");
        agentTask.setContextId("ctx-1");
        TaskStatus status = new TaskStatus();
        status.setState(state);
        agentTask.setStatus(status);
        if (artifactText != null) {
            Artifact artifact = new Artifact();
            artifact.setArtifactId("a1");
            artifact.setParts(List.of(textPart(artifactText)));
            agentTask.setArtifacts(List.of(artifact));
        }
        return agentTask;
    }

    private Part textPart(String text) {
        Part part = new Part();
        part.setKind("text");
        part.setText(text);
        return part;
    }

    @Test
    void directMessageReplyCompletes() {
        A2AMessage reply = new A2AMessage();
        reply.setContextId("ctx-1");
        reply.setParts(List.of(textPart("hello")));
        when(service.sendMessage(anyString(), any(), any(), any()))
                .thenReturn(SendResult.ofMessage(reply));

        Task task = task(Map.of("agentUrl", "http://agent", "text", "hi"));
        TaskResult result = invoke(workers, task);

        assertEquals(TaskResult.Status.COMPLETED, result.getStatus());
        assertEquals("message", result.getOutputData().get("state"));
        assertEquals("hello", result.getOutputData().get("text"));
    }

    @Test
    void resolvedAgentCardEndpointIsUsedForExecution() {
        A2AMessage reply = new A2AMessage();
        reply.setParts(List.of(textPart("planned")));
        when(service.sendMessage(anyString(), any(), any(), any()))
                .thenReturn(SendResult.ofMessage(reply));

        Task task =
                task(
                        Map.of(
                                "agentUrl",
                                "https://agent.example/.well-known/agent-card.json\n",
                                "text",
                                "plan this"));
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setMetadata(
                Map.of(
                        "agent",
                        Map.of(
                                "agentType",
                                "a2a",
                                "a2a",
                                Map.of(
                                        "agentCard",
                                        Map.of("url", "https://rpc.agent.example/a2a")))));
        task.setWorkflowTask(workflowTask);

        TaskResult result = invoke(workers, task);

        assertEquals(TaskResult.Status.COMPLETED, result.getStatus());
        verify(service).sendMessage(eq("https://rpc.agent.example/a2a"), any(), any(), any());
    }

    @Test
    void agentCardUrlIsDiscoveredWhenSnapshotIsUnavailable() {
        A2AMessage reply = new A2AMessage();
        reply.setParts(List.of(textPart("planned")));
        AgentCard card = new AgentCard();
        card.setUrl("https://rpc.agent.example/a2a\n");
        when(service.getAgentCard(eq("https://agent.example/.well-known/agent-card.json"), any()))
                .thenReturn(card);
        when(service.sendMessage(anyString(), any(), any(), any()))
                .thenReturn(SendResult.ofMessage(reply));

        TaskResult result =
                invoke(
                        workers,
                        task(
                                Map.of(
                                        "agentUrl",
                                        " https://agent.example/.well-known/agent-card.json\n",
                                        "text",
                                        "plan this")));

        assertEquals(TaskResult.Status.COMPLETED, result.getStatus());
        verify(service).sendMessage(eq("https://rpc.agent.example/a2a"), any(), any(), any());
    }

    @Test
    void terminalTaskCompletesWithArtifactText() {
        when(service.sendMessage(anyString(), any(), any(), any()))
                .thenReturn(SendResult.ofTask(agentTask(TaskState.COMPLETED, "42")));

        Task task = task(Map.of("agentUrl", "http://agent", "text", "convert"));
        A2ACallResult result = invokeOutput(workers, task);

        assertEquals(Task.Status.COMPLETED, task.getStatus());
        assertEquals(TaskState.COMPLETED, result.getState());
        assertEquals("42", result.getText());
        assertEquals("agent-task-1", result.getTaskId());
        assertEquals("ctx-1", result.getContextId());
        assertEquals("a1", result.getArtifacts().get(0).getArtifactId());
        assertEquals(TaskState.COMPLETED, result.getTask().getStatus().getState());
    }

    @Test
    void workingTaskReturnsInProgressWithConfiguredCallback() {
        when(service.sendMessage(anyString(), any(), any(), any()))
                .thenReturn(SendResult.ofTask(agentTask(TaskState.WORKING, null)));

        TaskResult result =
                invoke(
                        workers,
                        task(
                                Map.of(
                                        "agentUrl",
                                        "http://agent",
                                        "text",
                                        "convert",
                                        "pollIntervalSeconds",
                                        7)));

        assertEquals(TaskResult.Status.IN_PROGRESS, result.getStatus());
        assertEquals(7, result.getCallbackAfterSeconds());
        assertEquals("agent-task-1", result.getOutputData().get("taskId"));
        assertEquals("ctx-1", result.getOutputData().get("contextId"));
    }

    @Test
    void subsequentInvocationPollsUntilComplete() {
        Task task = task(Map.of("agentUrl", "http://agent"));
        task.getOutputData().put("taskId", "agent-task-1");
        when(service.getTask(anyString(), eq("agent-task-1"), any(), any()))
                .thenReturn(agentTask(TaskState.COMPLETED, "done"));

        TaskResult result = invoke(workers, task);

        assertEquals(TaskResult.Status.COMPLETED, result.getStatus());
        assertEquals("done", result.getOutputData().get("text"));
    }

    @Test
    void subsequentInvocationKeepsPollingWhileWorking() {
        Task task = task(Map.of("agentUrl", "http://agent"));
        task.getOutputData().put("taskId", "agent-task-1");
        when(service.getTask(anyString(), eq("agent-task-1"), any(), any()))
                .thenReturn(agentTask(TaskState.WORKING, null));

        TaskResult result = invoke(workers, task);

        assertEquals(TaskResult.Status.IN_PROGRESS, result.getStatus());
    }

    @Test
    void inputRequiredCompletesAndSurfacesQuestion() {
        A2ATask agentTask = agentTask(TaskState.INPUT_REQUIRED, null);
        A2AMessage question = new A2AMessage();
        question.setParts(List.of(textPart("Which currency?")));
        agentTask.getStatus().setMessage(question);
        when(service.sendMessage(anyString(), any(), any(), any()))
                .thenReturn(SendResult.ofTask(agentTask));

        TaskResult result =
                invoke(workers, task(Map.of("agentUrl", "http://agent", "text", "convert")));

        assertEquals(TaskResult.Status.COMPLETED, result.getStatus());
        assertEquals(TaskState.INPUT_REQUIRED, result.getOutputData().get("state"));
        assertEquals("Which currency?", result.getOutputData().get("text"));
        assertEquals("agent-task-1", result.getOutputData().get("taskId"));
        assertEquals("ctx-1", result.getOutputData().get("contextId"));
    }

    @Test
    void missingAgentUrlFailsTerminally() {
        TaskResult result = invoke(workers, task(Map.of("text", "hi")));

        assertEquals(TaskResult.Status.FAILED_WITH_TERMINAL_ERROR, result.getStatus());
    }

    @Test
    void nonRetryableErrorFailsTerminally() {
        when(service.sendMessage(anyString(), any(), any(), any()))
                .thenThrow(new NonRetryableException("method not found"));

        TaskResult result = invoke(workers, task(Map.of("agentUrl", "http://agent", "text", "hi")));

        assertEquals(TaskResult.Status.FAILED_WITH_TERMINAL_ERROR, result.getStatus());
    }

    @Test
    void retryableErrorFails() {
        when(service.sendMessage(anyString(), any(), any(), any()))
                .thenThrow(new A2AException("transient"));

        TaskResult result = invoke(workers, task(Map.of("agentUrl", "http://agent", "text", "hi")));

        assertEquals(TaskResult.Status.FAILED, result.getStatus());
    }

    @Test
    void cancellationPropagatesToRemote() {
        Task task = task(Map.of("agentUrl", "http://agent"));
        task.getOutputData().put("taskId", "agent-task-1");
        when(service.cancelTask(anyString(), eq("agent-task-1"), any()))
                .thenReturn(agentTask(TaskState.CANCELED, null));

        workers.cancel(task, "workflow canceled");

        verify(service).cancelTask(eq("http://agent"), eq("agent-task-1"), any());
    }

    @Test
    void streamingUsesStreamAndCompletes() {
        when(service.streamMessage(anyString(), any(), any(), any(), anyLong()))
                .thenReturn(SendResult.ofTask(agentTask(TaskState.COMPLETED, "streamed")));

        TaskResult result =
                invoke(
                        workers,
                        task(
                                Map.of(
                                        "agentUrl",
                                        "http://agent",
                                        "text",
                                        "convert",
                                        "streaming",
                                        true)));

        assertEquals(TaskResult.Status.COMPLETED, result.getStatus());
        assertEquals("streamed", result.getOutputData().get("text"));
        verify(service).streamMessage(anyString(), any(), any(), any(), anyLong());
    }
}
