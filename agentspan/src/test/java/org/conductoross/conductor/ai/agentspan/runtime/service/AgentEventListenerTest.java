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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.conductoross.conductor.ai.agent.AgentEventStream;
import org.conductoross.conductor.common.metadata.agent.AgentSSEEvent;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
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
        AgentEventListener listener = listener(registry);
        AgentEventStream stream = registry.openStream("wf-events", null);

        TaskModel llm = task("wf-events", "LLM_CHAT_COMPLETE", "agent_llm");
        listener.onTaskScheduled(llm);
        TaskModel tool = workerTask("wf-events", "get_weather", "call_abc123_0");
        tool.setInputData(Map.of("method", "get_weather", "city", "NYC"));
        tool.setOutputData(Map.of("result", "72F and sunny"));
        listener.onTaskCompleted(tool);

        AgentSSEEvent thinking = next(stream);
        AgentSSEEvent toolCall = next(stream);
        AgentSSEEvent toolResult = next(stream);
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
        AgentEventListener listener = listener(registry);
        AgentEventStream parentStream = registry.openStream("parent", null);

        WorkflowModel child = workflow("child", "support_wf");
        child.setParentWorkflowId("parent");
        listener.onWorkflowStartedIfEnabled(child);
        TaskModel childTool = workerTask("child", "child_lookup", "child_lookup_0");
        childTool.setOutputData(Map.of("result", "found"));
        listener.onTaskCompleted(childTool);

        AgentSSEEvent handoff = next(parentStream);
        AgentSSEEvent toolCall = next(parentStream);
        AgentSSEEvent toolResult = next(parentStream);
        assertThat(handoff.getType()).isEqualTo("handoff");
        assertThat(handoff.getTarget()).isEqualTo("support");
        assertThat(toolCall.getExecutionId()).isEqualTo("child");
        assertThat(toolResult.getExecutionId()).isEqualTo("child");

        WorkflowModel root = workflow("parent", "parent_agent");
        root.setOutput(Map.of("result", "complete"));
        listener.onWorkflowCompletedIfEnabled(root);
        AgentSSEEvent done = next(parentStream);
        assertThat(done.getType()).isEqualTo("done");
        assertThat(done.getOutput()).isEqualTo(Map.of("result", "complete"));
        assertThat(next(parentStream)).isNull();
    }

    @Test
    void guardrailFailuresAndTaskFailuresReachTheSdkStream() {
        AgentStreamRegistry registry = new AgentStreamRegistry();
        AgentEventListener listener = listener(registry);
        AgentEventStream stream = registry.openStream("wf-errors", null);

        TaskModel guardrail = task("wf-errors", "INLINE", "safety_guardrail");
        guardrail.setOutputData(Map.of("passed", false, "message", "Unsafe content"));
        listener.onTaskCompleted(guardrail);
        TaskModel failed = workerTask("wf-errors", "lookup", "lookup_0");
        failed.setReasonForIncompletion("Connection timeout");
        listener.onTaskFailed(failed);

        AgentSSEEvent guardrailEvent = next(stream);
        AgentSSEEvent failure = next(stream);
        assertThat(guardrailEvent.getType()).isEqualTo("guardrail_fail");
        assertThat(guardrailEvent.getContent()).isEqualTo("Unsafe content");
        assertThat(failure.getType()).isEqualTo("error");
        assertThat(failure.getContent()).isEqualTo("Connection timeout");
        stream.close();
    }

    @Test
    void mcpAndHumanToolCompletionsAreReportedUnderTheirDeclaredToolNames() {
        AgentStreamRegistry registry = new AgentStreamRegistry();
        AgentEventListener listener = listener(registry);
        AgentEventStream stream = registry.openStream("wf-tools", null);

        listener.onTaskCompleted(mcpToolCall("wf-tools"));

        TaskModel human = task("wf-tools", "HUMAN", "ask_question_0");
        human.setTaskDefName("ask_question");
        human.setInputData(Map.of("_agent_tool_name", "ask_question"));
        human.setOutputData(Map.of("result", "yes"));
        listener.onTaskCompleted(human);

        assertThat(next(stream).getToolName()).isEqualTo("math_add");
        assertThat(next(stream).getToolName()).isEqualTo("math_add");
        assertThat(next(stream).getToolName()).isEqualTo("ask_question");
        assertThat(next(stream).getToolName()).isEqualTo("ask_question");
        stream.close();
    }

    /** HTTP tools complete as async system tasks and do not reach this listener yet. */
    @Test
    void httpToolIsNamedByItsToolNameRatherThanItsHttpVerb() {
        AgentStreamRegistry registry = new AgentStreamRegistry();
        AgentEventListener listener = listener(registry);
        AgentEventStream stream = registry.openStream("wf-http", null);

        TaskModel http = task("wf-http", "HTTP", "get_weather_0");
        http.setTaskDefName("get_weather");
        http.setInputData(
                Map.of(
                        "http_request",
                        Map.of("uri", "https://example.test/weather", "method", "GET"),
                        "_agent_tool_name",
                        "get_weather"));
        http.setOutputData(Map.of("result", "72F"));
        listener.onTaskCompleted(http);

        AgentSSEEvent toolCall = next(stream);
        assertThat(toolCall.getType()).isEqualTo("tool_call");
        assertThat(toolCall.getToolName()).isEqualTo("get_weather");
        assertThat(next(stream).getToolName()).isEqualTo("get_weather");
        stream.close();
    }

    @Test
    void agentAsToolIsAToolCallWhileAStrategyHandoffIsNot() {
        AgentStreamRegistry registry = new AgentStreamRegistry();
        AgentEventListener listener = listener(registry);
        AgentEventStream stream = registry.openStream("wf-sub", null);

        TaskModel handoff = task("wf-sub", "SUB_WORKFLOW", "support_handoff_billing");
        handoff.setTaskDefName("billing_wf");
        handoff.setOutputData(Map.of("result", "handled"));
        listener.onTaskCompleted(handoff);

        TaskModel agentTool = task("wf-sub", "SUB_WORKFLOW", "research_0");
        agentTool.setTaskDefName("research_agent_wf");
        agentTool.setInputData(Map.of("_agent_tool_name", "research", "prompt", "hi"));
        agentTool.setOutputData(Map.of("result", "done"));
        listener.onTaskCompleted(agentTool);

        // The handoff emitted nothing, so the first event on the stream is the agent tool's.
        AgentSSEEvent toolCall = next(stream);
        assertThat(toolCall.getType()).isEqualTo("tool_call");
        assertThat(toolCall.getToolName()).isEqualTo("research");
        assertThat(next(stream).getType()).isEqualTo("tool_result");
        stream.close();
    }

    @Test
    void theMcpDiscoveryTaskIsNotReportedAsAToolCall() {
        AgentStreamRegistry registry = new AgentStreamRegistry();
        AgentEventListener listener = listener(registry);
        AgentEventStream stream = registry.openStream("wf-discovery", null);

        TaskModel discovery = task("wf-discovery", "LIST_MCP_TOOLS", "list_tools_ref");
        discovery.setTaskDefName("LIST_MCP_TOOLS");
        discovery.setInputData(Map.of("mcpServer", "http://mcp"));
        discovery.setOutputData(Map.of("tools", List.of()));
        listener.onTaskCompleted(discovery);

        assertThat(next(stream)).isNull();
        stream.close();
    }

    @Test
    void orchestrationTasksAreNotReportedAsToolCalls() {
        AgentStreamRegistry registry = new AgentStreamRegistry();
        AgentEventListener listener = listener(registry);
        AgentEventStream stream = registry.openStream("wf-plumbing", null);

        for (String taskType :
                List.of(
                        "SWITCH",
                        "DO_WHILE",
                        "INLINE",
                        "SET_VARIABLE",
                        "FORK_JOIN_DYNAMIC",
                        "JOIN",
                        "TERMINATE",
                        "LLM_CHAT_COMPLETE",
                        "AGENT")) {
            TaskModel plumbing = task("wf-plumbing", taskType, taskType.toLowerCase() + "_ref");
            plumbing.setTaskDefName(taskType);
            plumbing.setOutputData(Map.of("result", "x"));
            listener.onTaskCompleted(plumbing);
        }

        assertThat(next(stream)).isNull();
        stream.close();
    }

    @Test
    void aToolWhoseConfigNamesItsOwnTaskTypeIsStillAToolCall() {
        AgentStreamRegistry registry = new AgentStreamRegistry();
        AgentEventListener listener = listener(registry);
        AgentEventStream stream = registry.openStream("wf-media", null);

        // A media tool may carry taskType in its own config, so no static allowlist covers it.
        TaskModel media = task("wf-media", "GENERATE_DIAGRAM", "make_diagram_0");
        media.setTaskDefName("generate_diagram");
        media.setInputData(Map.of("prompt", "a box", "_agent_tool_name", "make_diagram"));
        media.setOutputData(Map.of("result", "diagram.png"));
        listener.onTaskCompleted(media);

        AgentSSEEvent toolCall = next(stream);
        assertThat(toolCall.getType()).isEqualTo("tool_call");
        assertThat(toolCall.getToolName()).isEqualTo("make_diagram");
        assertThat(next(stream).getToolName()).isEqualTo("make_diagram");
        stream.close();
    }

    @Test
    void frameworkPassthroughWrappersStayOffTheStream() {
        AgentStreamRegistry registry = new AgentStreamRegistry();
        AgentEventListener listener = listener(registry);
        AgentEventStream stream = registry.openStream("wf-fw", null);

        TaskModel wrapper = workerTask("wf-fw", "get_weather", "_fw_get_weather_0");
        wrapper.setInputData(Map.of("_agent_tool_name", "get_weather"));
        wrapper.setOutputData(Map.of("result", "72F"));
        listener.onTaskCompleted(wrapper);

        assertThat(next(stream)).isNull();
        stream.close();
    }

    /** Next event, or {@code null} if none. The bound stops a blocking take hanging the test. */
    private static AgentSSEEvent next(AgentEventStream stream) {
        ExecutorService reader = Executors.newSingleThreadExecutor();
        try {
            return reader.submit(stream::nextEvent).get(500, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        } catch (ExecutionException e) {
            throw new AssertionError(e.getCause());
        } finally {
            reader.shutdownNow();
        }
    }

    private static TaskModel mcpToolCall(String workflowId) {
        TaskModel mcp = task(workflowId, "CALL_MCP_TOOL", "math_call_0");
        mcp.setTaskDefName("call_mcp_tool");
        mcp.setInputData(Map.of("mcpServer", "http://mcp", "method", "math_add"));
        mcp.setOutputData(Map.of("result", 5));
        return mcp;
    }

    private static AgentEventListener listener(AgentStreamRegistry registry) {
        return new AgentEventListener(registry, new SimpleMeterRegistry());
    }

    /** A worker tool: {@code SimpleTaskMapper} sets the executed task's type to its own name. */
    private static TaskModel workerTask(String workflowId, String name, String reference) {
        TaskModel task = task(workflowId, name, reference);
        task.setTaskDefName(name);
        return task;
    }

    /**
     * A scheduled task with a {@code TaskDef} present, as {@code MetadataMapperService} leaves
     * every named task.
     */
    private static TaskModel task(String workflowId, String type, String reference) {
        TaskModel task = new TaskModel();
        task.setWorkflowInstanceId(workflowId);
        task.setTaskType(type);
        task.setReferenceTaskName(reference);
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setName(type);
        workflowTask.setType(type);
        workflowTask.setTaskReferenceName(reference);
        workflowTask.setTaskDefinition(new TaskDef(type));
        task.setWorkflowTask(workflowTask);
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
