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
import java.util.Optional;

import org.conductoross.conductor.ai.a2a.A2AService.SendResult;
import org.conductoross.conductor.ai.a2a.model.A2AMessage;
import org.conductoross.conductor.ai.a2a.model.A2ATask;
import org.conductoross.conductor.ai.a2a.model.Artifact;
import org.conductoross.conductor.ai.a2a.model.Part;
import org.conductoross.conductor.ai.a2a.model.TaskState;
import org.conductoross.conductor.ai.a2a.model.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.sdk.workflow.executor.task.NonRetryableException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CallAgentTaskTest {

    private A2AService service;
    private CallAgentTask task;

    @BeforeEach
    void setUp() {
        service = mock(A2AService.class);
        // No callback URL configured -> push disabled, polling used.
        Environment environment = mock(Environment.class);
        task = new CallAgentTask(service, environment);
    }

    private TaskModel taskModel(Map<String, Object> input) {
        TaskModel model = new TaskModel();
        model.setInputData(input);
        model.setTaskId("conductor-task-1");
        return model;
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
    void start_directMessageReply_completes() {
        A2AMessage reply = new A2AMessage();
        reply.setContextId("ctx-1");
        reply.setParts(List.of(textPart("hello")));
        when(service.sendMessage(anyString(), any(), any(), any()))
                .thenReturn(SendResult.ofMessage(reply));

        TaskModel model = taskModel(Map.of("agentUrl", "http://agent", "text", "hi"));
        task.start(null, model, null);

        assertEquals(TaskModel.Status.COMPLETED, model.getStatus());
        assertEquals("message", model.getOutputData().get("state"));
        assertEquals("hello", model.getOutputData().get("text"));
    }

    @Test
    void start_terminalTask_completesWithArtifactText() {
        when(service.sendMessage(anyString(), any(), any(), any()))
                .thenReturn(SendResult.ofTask(agentTask(TaskState.COMPLETED, "42")));

        TaskModel model = taskModel(Map.of("agentUrl", "http://agent", "text", "convert"));
        task.start(null, model, null);

        assertEquals(TaskModel.Status.COMPLETED, model.getStatus());
        assertEquals(TaskState.COMPLETED, model.getOutputData().get("state"));
        assertEquals("42", model.getOutputData().get("text"));
        assertEquals("agent-task-1", model.getOutputData().get("taskId"));
    }

    @Test
    void start_workingTask_movesToInProgress() {
        when(service.sendMessage(anyString(), any(), any(), any()))
                .thenReturn(SendResult.ofTask(agentTask(TaskState.WORKING, null)));

        TaskModel model = taskModel(Map.of("agentUrl", "http://agent", "text", "convert"));
        task.start(null, model, null);

        assertEquals(TaskModel.Status.IN_PROGRESS, model.getStatus());
        assertEquals("agent-task-1", model.getOutputData().get("taskId"));
        assertEquals("ctx-1", model.getOutputData().get("contextId"));
    }

    @Test
    void execute_pollsUntilComplete() {
        TaskModel model = taskModel(Map.of("agentUrl", "http://agent"));
        model.addOutput("taskId", "agent-task-1");
        when(service.getTask(anyString(), eq("agent-task-1"), any(), any()))
                .thenReturn(agentTask(TaskState.COMPLETED, "done"));

        boolean changed = task.execute(null, model, null);

        assertTrue(changed);
        assertEquals(TaskModel.Status.COMPLETED, model.getStatus());
        assertEquals("done", model.getOutputData().get("text"));
    }

    @Test
    void execute_stillWorking_keepsPolling() {
        TaskModel model = taskModel(Map.of("agentUrl", "http://agent"));
        model.addOutput("taskId", "agent-task-1");
        when(service.getTask(anyString(), eq("agent-task-1"), any(), any()))
                .thenReturn(agentTask(TaskState.WORKING, null));

        boolean changed = task.execute(null, model, null);

        assertFalse(changed);
        assertEquals(TaskModel.Status.IN_PROGRESS, model.getStatus());
    }

    @Test
    void start_inputRequired_completesAndSurfacesQuestion() {
        A2ATask agentTask = agentTask(TaskState.INPUT_REQUIRED, null);
        A2AMessage question = new A2AMessage();
        question.setParts(List.of(textPart("Which currency?")));
        agentTask.getStatus().setMessage(question);
        when(service.sendMessage(anyString(), any(), any(), any()))
                .thenReturn(SendResult.ofTask(agentTask));

        TaskModel model = taskModel(Map.of("agentUrl", "http://agent", "text", "convert"));
        task.start(null, model, null);

        assertEquals(TaskModel.Status.COMPLETED, model.getStatus());
        assertEquals(TaskState.INPUT_REQUIRED, model.getOutputData().get("state"));
        assertEquals("Which currency?", model.getOutputData().get("text"));
        assertEquals("agent-task-1", model.getOutputData().get("taskId"));
        assertEquals("ctx-1", model.getOutputData().get("contextId"));
    }

    @Test
    void start_missingAgentUrl_failsTerminally() {
        TaskModel model = taskModel(Map.of("text", "hi"));
        task.start(null, model, null);

        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, model.getStatus());
    }

    @Test
    void start_nonRetryableError_failsTerminally() {
        when(service.sendMessage(anyString(), any(), any(), any()))
                .thenThrow(new NonRetryableException("method not found"));

        TaskModel model = taskModel(Map.of("agentUrl", "http://agent", "text", "hi"));
        task.start(null, model, null);

        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, model.getStatus());
    }

    @Test
    void start_retryableError_fails() {
        when(service.sendMessage(anyString(), any(), any(), any()))
                .thenThrow(new A2AException("transient"));

        TaskModel model = taskModel(Map.of("agentUrl", "http://agent", "text", "hi"));
        task.start(null, model, null);

        assertEquals(TaskModel.Status.FAILED, model.getStatus());
    }

    @Test
    void getEvaluationOffset_usesConfiguredPollInterval() {
        TaskModel model = taskModel(Map.of("agentUrl", "http://agent", "pollIntervalSeconds", 7));
        assertEquals(Optional.of(7L), task.getEvaluationOffset(model, 30));
    }

    @Test
    void cancel_propagatesToRemoteAndSetsCanceled() {
        TaskModel model = taskModel(Map.of("agentUrl", "http://agent"));
        model.addOutput("taskId", "agent-task-1");
        when(service.cancelTask(anyString(), eq("agent-task-1"), any()))
                .thenReturn(agentTask(TaskState.CANCELED, null));

        task.cancel(null, model, null);

        verify(service).cancelTask(eq("http://agent"), eq("agent-task-1"), any());
        assertEquals(TaskModel.Status.CANCELED, model.getStatus());
    }

    @Test
    void start_streaming_usesStreamAndCompletes() {
        when(service.streamMessage(anyString(), any(), any(), any()))
                .thenReturn(SendResult.ofTask(agentTask(TaskState.COMPLETED, "streamed")));

        TaskModel model =
                taskModel(Map.of("agentUrl", "http://agent", "text", "convert", "streaming", true));
        task.start(null, model, null);

        assertEquals(TaskModel.Status.COMPLETED, model.getStatus());
        assertEquals("streamed", model.getOutputData().get("text"));
        verify(service).streamMessage(anyString(), any(), any(), any());
    }

    @Test
    void isAsync_isTrue() {
        assertTrue(task.isAsync());
    }
}
