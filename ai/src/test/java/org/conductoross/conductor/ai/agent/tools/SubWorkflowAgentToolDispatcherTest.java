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
package org.conductoross.conductor.ai.agent.tools;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.StartWorkflowInput;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.service.WorkflowService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The tools an agent asks for become real scheduled tasks: one per call, in parallel, named so an
 * ordinary worker picks them up. These cover the shape that gets scheduled and how results find
 * their way back to the call that asked.
 */
class SubWorkflowAgentToolDispatcherTest {

    private WorkflowService workflowService;
    private WorkflowExecutor workflowExecutor;
    private SubWorkflowAgentToolDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        workflowService = mock(WorkflowService.class);
        workflowExecutor = mock(WorkflowExecutor.class);
        dispatcher =
                new SubWorkflowAgentToolDispatcher(
                        workflowService, new SingletonProvider<>(workflowExecutor));
    }

    private static AgentToolDispatcher.Request twoToolRequest() {
        return new AgentToolDispatcher.Request(
                "wf-1",
                "task-1",
                "ask_the_analyst",
                "thread-1",
                List.of(
                        Map.of(
                                "tool_name", "get_revenue",
                                "tool_call_id", "call-1",
                                "arguments", "{\"quarter\":\"Q3\"}"),
                        Map.of(
                                "tool_name", "get_headcount",
                                "tool_call_id", "call-2",
                                "arguments", "{\"dept\":\"eng\"}")),
                null,
                10);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstToolInput() {
        return (Map<String, Object>) dynamicTasksOf(capturedStart()).get(0).get("inputParameters");
    }

    private StartWorkflowInput capturedStart() {
        ArgumentCaptor<StartWorkflowInput> captor =
                ArgumentCaptor.forClass(StartWorkflowInput.class);
        org.mockito.Mockito.verify(workflowExecutor).startWorkflow(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> dynamicTasksOf(StartWorkflowInput start) {
        return (List<Map<String, Object>>) start.getWorkflowInput().get("dynamicTasks");
    }

    /** Stands in for Spring's lazy lookup without pulling in a container. */
    private record SingletonProvider<T>(T value)
            implements org.springframework.beans.factory.ObjectProvider<T> {
        @Override
        public T getObject() {
            return value;
        }

        @Override
        public T getObject(Object... args) {
            return value;
        }

        @Override
        public T getIfAvailable() {
            return value;
        }

        @Override
        public T getIfUnique() {
            return value;
        }
    }

    @Test
    void schedulesOneTaskPerToolCall() {
        when(workflowExecutor.startWorkflow(any(StartWorkflowInput.class))).thenReturn("tools-1");

        AgentToolDispatch dispatch = dispatcher.dispatch(twoToolRequest());

        assertThat(dispatch.dispatchId()).isEqualTo("tools-1");
        assertThat(dispatch.state()).isEqualTo(AgentToolDispatch.State.RUNNING);

        List<Map<String, Object>> tasks = dynamicTasksOf(capturedStart());
        assertThat(tasks).hasSize(2);
        // A tool runs as a task of its own name, so a worker registered for get_revenue serves it
        // with no configuration at all.
        assertThat(tasks)
                .extracting(t -> t.get("name"))
                .containsExactly("get_revenue", "get_headcount");
        assertThat(tasks).extracting(t -> t.get("type")).containsOnly("SIMPLE");
    }

    @Test
    void tasksFanOutInParallelUnderADynamicFork() {
        when(workflowExecutor.startWorkflow(any(StartWorkflowInput.class))).thenReturn("tools-1");

        dispatcher.dispatch(twoToolRequest());

        List<WorkflowTask> definition = capturedStart().getWorkflowDefinition().getTasks();
        assertThat(definition)
                .extracting(WorkflowTask::getType)
                .containsExactly("FORK_JOIN_DYNAMIC", "JOIN");
        assertThat(definition.get(0).getDynamicForkTasksParam()).isEqualTo("dynamicTasks");
    }

    @Test
    void toolArgumentsBecomeTheTasksInput() {
        when(workflowExecutor.startWorkflow(any(StartWorkflowInput.class))).thenReturn("tools-1");

        dispatcher.dispatch(twoToolRequest());

        Map<String, Object> input =
                (Map<String, Object>) dynamicTasksOf(capturedStart()).get(0).get("inputParameters");
        // A worker sees the tool's own arguments as ordinary parameters, not a payload to parse.
        assertThat(input).containsEntry("quarter", "Q3");
        assertThat(input).containsEntry("_toolName", "get_revenue");
        assertThat(input).containsEntry("_agentExecutionId", "thread-1");
        // Carried so the result can be matched back to the call that asked.
        assertThat(input).containsEntry(SubWorkflowAgentToolDispatcher.TOOL_CALL_ID, "call-1");
    }

    @Test
    void aToolCanBeMappedToADifferentTaskName() {
        when(workflowExecutor.startWorkflow(any(StartWorkflowInput.class))).thenReturn("tools-1");
        AgentToolDispatcher.Request request =
                new AgentToolDispatcher.Request(
                        "wf-1",
                        "task-1",
                        "ask_the_analyst",
                        "thread-1",
                        List.of(Map.of("tool_name", "get_revenue", "tool_call_id", "call-1")),
                        Map.of("get_revenue", "finance_revenue_lookup"),
                        10);

        dispatcher.dispatch(request);

        assertThat(dynamicTasksOf(capturedStart()).get(0).get("name"))
                .isEqualTo("finance_revenue_lookup");
    }

    @Test
    void nonJsonArgumentsAreStillPassedThrough() {
        when(workflowExecutor.startWorkflow(any(StartWorkflowInput.class))).thenReturn("tools-1");
        AgentToolDispatcher.Request request =
                new AgentToolDispatcher.Request(
                        "wf-1",
                        "task-1",
                        "ask_the_analyst",
                        "thread-1",
                        List.of(
                                Map.of(
                                        "tool_name", "echo",
                                        "tool_call_id", "call-1",
                                        "arguments", "not json")),
                        null,
                        10);

        dispatcher.dispatch(request);

        Map<String, Object> input =
                (Map<String, Object>) dynamicTasksOf(capturedStart()).get(0).get("inputParameters");
        // Better than failing the turn over a shape we did not expect.
        assertThat(input).containsEntry("arguments", "not json");
    }

    @Test
    void aconductorExpressionInToolArgumentsIsPassedThroughAsText() {
        // The arguments are written by a model, from a prompt that may carry text from anywhere.
        // Unescaped, this reads the workflow's own input and hands it to the tool as though the
        // author had asked for that.
        dispatcher.dispatch(
                new AgentToolDispatcher.Request(
                        "wf-1",
                        "task-1",
                        "agent_ref",
                        "exec-1",
                        List.of(
                                Map.of(
                                        "tool_name",
                                        "lookup",
                                        "tool_call_id",
                                        "call-1",
                                        "arguments",
                                        "{\"q\":\"${workflow.input.customer_ssn}\","
                                                + "\"nested\":{\"a\":[\"${x.output.y}\"]}}")),
                        null,
                        10));

        Map<String, Object> input = firstToolInput();
        assertThat(input.get("q")).isEqualTo("$${workflow.input.customer_ssn}");
        // Nested values are reached too - an escape that only covers the top level is no escape.
        Map<String, Object> nested = (Map<String, Object>) input.get("nested");
        assertThat(((List<?>) nested.get("a")).get(0)).isEqualTo("$${x.output.y}");
    }

    @Test
    void ordinaryArgumentsAreLeftExactlyAsTheyAre() {
        dispatcher.dispatch(
                new AgentToolDispatcher.Request(
                        "wf-1",
                        "task-1",
                        "agent_ref",
                        "exec-1",
                        List.of(
                                Map.of(
                                        "tool_name", "lookup",
                                        "tool_call_id", "call-1",
                                        "arguments", "{\"q\":\"cost is $100\",\"n\":3}")),
                        null,
                        10));

        Map<String, Object> input = firstToolInput();
        assertThat(input.get("q")).isEqualTo("cost is $100");
        assertThat(input.get("n")).isEqualTo(3);
    }

    @Test
    void theToolRunIsLinkedToTheTaskThatAskedForIt() {
        when(workflowExecutor.startWorkflow(any(StartWorkflowInput.class))).thenReturn("tools-1");

        dispatcher.dispatch(twoToolRequest());

        // Without linkage the tool run is an orphan that outlives a terminated parent.
        StartWorkflowInput start = capturedStart();
        assertThat(start.getParentWorkflowId()).isEqualTo("wf-1");
        assertThat(start.getParentWorkflowTaskId()).isEqualTo("task-1");
    }

    @Test
    void cancelTerminatesTheToolRun() {
        dispatcher.cancel("tools-1");

        org.mockito.Mockito.verify(workflowExecutor)
                .terminateWorkflow(eq("tools-1"), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void cancelIsBestEffortAndDoesNotThrow() {
        org.mockito.Mockito.doThrow(new IllegalStateException("already gone"))
                .when(workflowExecutor)
                .terminateWorkflow(eq("tools-1"), org.mockito.ArgumentMatchers.anyString());

        // The caller is ending regardless; a failure here must not mask that.
        dispatcher.cancel("tools-1");
    }

    @Test
    void staysRunningWhileTheToolsAreInFlight() {
        Workflow running = new Workflow();
        running.setStatus(Workflow.WorkflowStatus.RUNNING);
        when(workflowService.getExecutionStatus(eq("tools-1"), anyBoolean())).thenReturn(running);

        assertThat(dispatcher.status("tools-1").state()).isEqualTo(AgentToolDispatch.State.RUNNING);
    }

    @Test
    void resultsComeBackKeyedByToolCall() {
        Workflow completed = new Workflow();
        completed.setStatus(Workflow.WorkflowStatus.COMPLETED);
        completed.setTasks(
                List.of(
                        forkTask(),
                        toolTask("call-1", Map.of("revenue", "4.2M")),
                        toolTask("call-2", Map.of("headcount", 37))));
        when(workflowService.getExecutionStatus(eq("tools-1"), anyBoolean())).thenReturn(completed);

        AgentToolDispatch dispatch = dispatcher.status("tools-1");

        assertThat(dispatch.state()).isEqualTo(AgentToolDispatch.State.COMPLETED);
        // Keyed by call, so each answer reaches the question that asked it — and the fork task
        // itself contributes nothing.
        assertThat(dispatch.resultsByToolCallId())
                .containsOnlyKeys("call-1", "call-2")
                .containsEntry("call-1", Map.of("revenue", "4.2M"));
    }

    @Test
    void aFailedToolRunIsReportedWithItsReason() {
        Workflow failed = new Workflow();
        failed.setStatus(Workflow.WorkflowStatus.FAILED);
        failed.setReasonForIncompletion("get_revenue exhausted retries");
        when(workflowService.getExecutionStatus(eq("tools-1"), anyBoolean())).thenReturn(failed);

        AgentToolDispatch dispatch = dispatcher.status("tools-1");

        assertThat(dispatch.state()).isEqualTo(AgentToolDispatch.State.FAILED);
        assertThat(dispatch.reason()).contains("get_revenue exhausted retries");
    }

    private static Task forkTask() {
        Task fork = new Task();
        fork.setReferenceTaskName("agent_tools_fork");
        fork.setInputData(Map.of());
        return fork;
    }

    private static Task toolTask(String toolCallId, Map<String, Object> output) {
        Task task = new Task();
        task.setReferenceTaskName("tool_" + toolCallId);
        task.setInputData(Map.of(SubWorkflowAgentToolDispatcher.TOOL_CALL_ID, toolCallId));
        task.setOutputData(output);
        return task;
    }
}
