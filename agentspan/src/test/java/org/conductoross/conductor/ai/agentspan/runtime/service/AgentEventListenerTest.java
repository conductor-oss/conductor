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

import java.util.Map;

import org.conductoross.conductor.ai.agent.AgentEventStream;
import org.conductoross.conductor.common.metadata.agent.AgentSSEEvent;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises listener-to-stream delivery using the concrete buffered stream registry. It verifies
 * SDK-visible events rather than interactions with an internal collaborator.
 */
class AgentEventListenerTest {

    @Test
    void scheduledLlmAndCompletedToolPublishOrderedEventsToTheRealStream() {
        AgentStreamRegistry registry = new AgentStreamRegistry();
        AgentEventListener listener = new AgentEventListener(registry, new SimpleMeterRegistry());
        AgentEventStream stream = registry.openStream("wf-events", null);

        TaskModel llm = task("wf-events", "LLM_CHAT_COMPLETE", "agent_llm");
        listener.onTaskScheduled(llm);
        TaskModel tool = task("wf-events", "SIMPLE", "call_abc123__1");
        tool.setInputData(Map.of("method", "get_weather", "city", "NYC"));
        tool.setOutputData(Map.of("result", "72F and sunny"));
        listener.onTaskCompleted(tool);

        AgentSSEEvent thinking = stream.nextEvent();
        AgentSSEEvent toolCall = stream.nextEvent();
        AgentSSEEvent toolResult = stream.nextEvent();
        assertThat(thinking.getType()).isEqualTo("thinking");
        assertThat(thinking.getContent()).isEqualTo("agent_llm");
        assertThat(toolCall.getType()).isEqualTo("tool_call");
        assertThat(toolCall.getToolName()).isEqualTo("get_weather");
        assertThat(toolResult.getType()).isEqualTo("tool_result");
        assertThat(toolResult.getResult()).isEqualTo("72F and sunny");
        stream.close();
    }

    @Test
    void handoffAliasForwardsChildEventsAndRootCompletionClosesTheRealStream() {
        AgentStreamRegistry registry = new AgentStreamRegistry();
        AgentEventListener listener = new AgentEventListener(registry, new SimpleMeterRegistry());
        AgentEventStream parentStream = registry.openStream("parent", null);

        WorkflowModel child = workflow("child", "support_wf");
        child.setParentWorkflowId("parent");
        listener.onWorkflowStartedIfEnabled(child);
        TaskModel childTool = task("child", "SIMPLE", "child_lookup");
        childTool.setOutputData(Map.of("result", "found"));
        listener.onTaskCompleted(childTool);

        AgentSSEEvent handoff = parentStream.nextEvent();
        AgentSSEEvent toolCall = parentStream.nextEvent();
        AgentSSEEvent toolResult = parentStream.nextEvent();
        assertThat(handoff.getType()).isEqualTo("handoff");
        assertThat(handoff.getTarget()).isEqualTo("support");
        assertThat(toolCall.getExecutionId()).isEqualTo("child");
        assertThat(toolResult.getExecutionId()).isEqualTo("child");

        WorkflowModel root = workflow("parent", "parent_agent");
        root.setOutput(Map.of("result", "complete"));
        listener.onWorkflowCompletedIfEnabled(root);
        AgentSSEEvent done = parentStream.nextEvent();
        assertThat(done.getType()).isEqualTo("done");
        assertThat(done.getOutput()).isEqualTo(Map.of("result", "complete"));
        assertThat(parentStream.nextEvent()).isNull();
    }

    @Test
    void guardrailFailuresAndTaskFailuresReachTheSdkStream() {
        AgentStreamRegistry registry = new AgentStreamRegistry();
        AgentEventListener listener = new AgentEventListener(registry, new SimpleMeterRegistry());
        AgentEventStream stream = registry.openStream("wf-errors", null);

        TaskModel guardrail = task("wf-errors", "INLINE", "safety_guardrail");
        guardrail.setOutputData(Map.of("passed", false, "message", "Unsafe content"));
        listener.onTaskCompleted(guardrail);
        TaskModel failed = task("wf-errors", "SIMPLE", "lookup");
        failed.setReasonForIncompletion("Connection timeout");
        listener.onTaskFailed(failed);

        AgentSSEEvent guardrailEvent = stream.nextEvent();
        AgentSSEEvent failure = stream.nextEvent();
        assertThat(guardrailEvent.getType()).isEqualTo("guardrail_fail");
        assertThat(guardrailEvent.getContent()).isEqualTo("Unsafe content");
        assertThat(failure.getType()).isEqualTo("error");
        assertThat(failure.getContent()).isEqualTo("Connection timeout");
        stream.close();
    }

    private static TaskModel task(String workflowId, String type, String reference) {
        TaskModel task = new TaskModel();
        task.setWorkflowInstanceId(workflowId);
        task.setTaskType(type);
        task.setReferenceTaskName(reference);
        return task;
    }

    private static WorkflowModel workflow(String workflowId, String name) {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId(workflowId);
        var definition = new com.netflix.conductor.common.metadata.workflow.WorkflowDef();
        definition.setName(name);
        workflow.setWorkflowDefinition(definition);
        return workflow;
    }
}
