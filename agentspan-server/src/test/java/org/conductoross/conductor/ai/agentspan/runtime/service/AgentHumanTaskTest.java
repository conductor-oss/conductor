/*
 * Copyright 2025 Conductor Authors.
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

import org.conductoross.conductor.ai.agentspan.runtime.model.AgentSSEEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentHumanTaskTest {

    private AgentStreamRegistry streamRegistry;
    private AgentHumanTask humanTask;

    @BeforeEach
    void setUp() {
        streamRegistry = mock(AgentStreamRegistry.class);
        humanTask = new AgentHumanTask(streamRegistry);
    }

    @Test
    void startSetsInProgressAndEmitsWaiting() {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("wf-1");

        TaskModel task = new TaskModel();
        task.setReferenceTaskName("hitl_approve");
        task.setInputData(
                Map.of("tool_name", "publish_article", "parameters", Map.of("title", "Test")));

        humanTask.start(workflow, task, null);

        assertThat(task.getStatus()).isEqualTo(TaskModel.Status.IN_PROGRESS);

        ArgumentCaptor<AgentSSEEvent> captor = ArgumentCaptor.forClass(AgentSSEEvent.class);
        verify(streamRegistry).send(eq("wf-1"), captor.capture());
        AgentSSEEvent event = captor.getValue();
        assertThat(event.getType()).isEqualTo("waiting");
        assertThat(event.getPendingTool()).containsEntry("tool_name", "publish_article");
        assertThat(event.getPendingTool()).containsEntry("taskRefName", "hitl_approve");
        assertThat(event.getExecutionId()).isEqualTo("wf-1");
    }

    @Test
    void startWithNullInputData() {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("wf-2");

        TaskModel task = new TaskModel();
        task.setReferenceTaskName("hitl_task");

        humanTask.start(workflow, task, null);

        assertThat(task.getStatus()).isEqualTo(TaskModel.Status.IN_PROGRESS);

        ArgumentCaptor<AgentSSEEvent> captor = ArgumentCaptor.forClass(AgentSSEEvent.class);
        verify(streamRegistry).send(eq("wf-2"), captor.capture());
        AgentSSEEvent event = captor.getValue();
        assertThat(event.getType()).isEqualTo("waiting");
        assertThat(event.getPendingTool()).containsEntry("taskRefName", "hitl_task");
    }

    @Test
    void startContinuesEvenIfSseFails() {
        doThrow(new RuntimeException("SSE send failed"))
                .when(streamRegistry)
                .send(anyString(), any());

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("wf-3");
        TaskModel task = new TaskModel();
        task.setReferenceTaskName("hitl");

        // Should not throw
        assertThatCode(() -> humanTask.start(workflow, task, null)).doesNotThrowAnyException();

        // Task should still be IN_PROGRESS even if SSE failed
        assertThat(task.getStatus()).isEqualTo(TaskModel.Status.IN_PROGRESS);
    }

    /**
     * Regression test for issue #226.
     *
     * <p>The compiler ({@code AgentCompiler} → {@code tool_calls = ${llm.output.toolCalls}}) writes
     * the plural {@code tool_calls} array into the HUMAN task's input — never the singular {@code
     * tool_name} / {@code parameters} keys the older tests fabricated. Production approval-gated
     * workflows therefore reach this method with input shaped like {@code {"tool_calls": [{"name":
     * ..., "inputParameters": ...}], ...}} and the SSE WAITING event must forward the array so SDK
     * consumers can tell which tool(s) are awaiting approval.
     *
     * <p>Uses the canonical {@code inputParameters} key — the same key every other runtime reader
     * of {@code llm.output.toolCalls} treats as primary (see {@code JavaScriptBuilder}: {@code
     * tc.inputParameters || tc.input}).
     */
    @Test
    void startForwardsCompilerEmittedToolCallsArray() {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("wf-tool-calls");

        TaskModel task = new TaskModel();
        task.setReferenceTaskName("agent_approval_human");
        // Shape matches what the compiler emits from llm.output.toolCalls at runtime.
        task.setInputData(
                Map.of(
                        "tool_calls",
                        List.of(
                                Map.of(
                                        "name",
                                        "publish_article",
                                        "inputParameters",
                                        Map.of("title", "Test")),
                                Map.of(
                                        "name",
                                        "send_email",
                                        "inputParameters",
                                        Map.of("to", "ops@example.com")))));

        humanTask.start(workflow, task, null);

        ArgumentCaptor<AgentSSEEvent> captor = ArgumentCaptor.forClass(AgentSSEEvent.class);
        verify(streamRegistry).send(eq("wf-tool-calls"), captor.capture());
        Map<String, Object> pendingTool = captor.getValue().getPendingTool();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> calls = (List<Map<String, Object>>) pendingTool.get("toolCalls");
        assertThat(calls).as("pendingTool.toolCalls forwarded from compiler input").hasSize(2);
        assertThat(calls.get(0)).containsEntry("name", "publish_article");
        assertThat(calls.get(0)).containsEntry("args", Map.of("title", "Test"));
        assertThat(calls.get(1)).containsEntry("name", "send_email");
        assertThat(calls.get(1)).containsEntry("args", Map.of("to", "ops@example.com"));
    }

    /**
     * The {@code input} key is accepted as a fallback for the args of a tool call (LLM-native
     * shape), but {@code inputParameters} wins when both are present — matching the runtime's
     * {@code tc.inputParameters || tc.input} precedence.
     */
    @Test
    void extractToolCallsPrefersInputParametersOverInput() {
        List<Map<String, Object>> calls =
                AgentHumanTask.extractToolCalls(
                        List.of(
                                Map.of("name", "only_input", "input", Map.of("a", 1)),
                                Map.of(
                                        "name",
                                        "both",
                                        "input",
                                        Map.of("ignored", true),
                                        "inputParameters",
                                        Map.of("canonical", true))));

        assertThat(calls).hasSize(2);
        assertThat(calls.get(0)).containsEntry("args", Map.of("a", 1));
        assertThat(calls.get(1)).containsEntry("args", Map.of("canonical", true));
    }

    @Test
    void cancelSetsCanceled() {
        WorkflowModel workflow = new WorkflowModel();
        TaskModel task = new TaskModel();

        humanTask.cancel(workflow, task, null);

        assertThat(task.getStatus()).isEqualTo(TaskModel.Status.CANCELED);
    }
}
