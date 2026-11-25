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

import org.conductoross.conductor.ai.agent.AgentEventStream;
import org.conductoross.conductor.common.metadata.agent.AgentSSEEvent;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the HUMAN task against the real stream registry and its in-process subscriber protocol.
 * This proves that an SDK-facing stream actually receives the event, rather than asserting an
 * interaction with a mocked registry.
 */
class AgentHumanTaskTest {

    @Test
    void startMarksTaskInProgressAndPublishesWaitingEventToRealStream() {
        AgentStreamRegistry registry = new AgentStreamRegistry();
        AgentHumanTask humanTask = new AgentHumanTask(registry);
        AgentEventStream stream = registry.openStream("wf-1", null);

        WorkflowModel workflow = workflow("wf-1");
        TaskModel task = task("hitl_approve");
        task.setInputData(
                Map.of("tool_name", "publish_article", "parameters", Map.of("title", "Test")));
        humanTask.start(workflow, task, null);

        AgentSSEEvent event = stream.nextEvent();
        assertThat(task.getStatus()).isEqualTo(TaskModel.Status.IN_PROGRESS);
        assertThat(event.getType()).isEqualTo("waiting");
        assertThat(event.getExecutionId()).isEqualTo("wf-1");
        assertThat(event.getPendingTool())
                .containsEntry("tool_name", "publish_article")
                .containsEntry("taskRefName", "hitl_approve");
        stream.close();
    }

    @Test
    void compilerEmittedBatchToolCallsReachTheSdkStreamWithCanonicalArguments() {
        AgentStreamRegistry registry = new AgentStreamRegistry();
        AgentHumanTask humanTask = new AgentHumanTask(registry);
        AgentEventStream stream = registry.openStream("wf-tool-calls", null);
        TaskModel task = task("agent_approval_human");
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
                                        "input",
                                        Map.of("to", "ops@example.com")))));

        humanTask.start(workflow("wf-tool-calls"), task, null);

        AgentSSEEvent event = stream.nextEvent();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> calls =
                (List<Map<String, Object>>) event.getPendingTool().get("toolCalls");
        assertThat(calls)
                .containsExactly(
                        Map.of("name", "publish_article", "args", Map.of("title", "Test")),
                        Map.of("name", "send_email", "args", Map.of("to", "ops@example.com")));
        stream.close();
    }

    @Test
    void nullInputAndCancellationHavePredictableTaskState() {
        AgentHumanTask humanTask = new AgentHumanTask(new AgentStreamRegistry());
        WorkflowModel workflow = workflow("wf-null-input");
        TaskModel task = task("hitl_task");

        humanTask.start(workflow, task, null);
        assertThat(task.getStatus()).isEqualTo(TaskModel.Status.IN_PROGRESS);
        humanTask.cancel(workflow, task, null);
        assertThat(task.getStatus()).isEqualTo(TaskModel.Status.CANCELED);
    }

    @Test
    void inputParametersTakePrecedenceOverProviderNativeInput() {
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

        assertThat(calls)
                .containsExactly(
                        Map.of("name", "only_input", "args", Map.of("a", 1)),
                        Map.of("name", "both", "args", Map.of("canonical", true)));
    }

    private static WorkflowModel workflow(String workflowId) {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId(workflowId);
        return workflow;
    }

    private static TaskModel task(String referenceName) {
        TaskModel task = new TaskModel();
        task.setReferenceTaskName(referenceName);
        return task;
    }
}
