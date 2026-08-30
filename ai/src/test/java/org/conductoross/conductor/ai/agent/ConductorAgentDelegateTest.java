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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.conductoross.conductor.ai.a2a.model.A2AMessage;
import org.conductoross.conductor.ai.a2a.model.A2ATask;
import org.conductoross.conductor.ai.a2a.model.TaskState;
import org.conductoross.conductor.ai.agent.tools.AgentToolDispatch;
import org.conductoross.conductor.ai.agent.tools.AgentToolDispatcher;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
                        .status(ConductorAgentState.COMPLETED)
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
                        .status(ConductorAgentState.WAITING)
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
                        .status(ConductorAgentState.CANCELED)
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
                        .status(ConductorAgentState.RUNNING)
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

    /**
     * A runtime with no status API — Bedrock streams the whole turn inside the invoke — knows the
     * outcome before startAgent returns. The delegate must record it rather than assuming RUNNING
     * and scheduling a poll no API can answer.
     */
    @Test
    void honoursATerminalStateReportedByStartAgent() {
        SynchronousAgentClient client = new SynchronousAgentClient();
        client.startState = ConductorAgentState.COMPLETED;
        client.startOutput = Map.of("result", "answered in one shot");
        ConductorAgentDelegate delegate = new ConductorAgentDelegate(client);

        TaskResult result = delegate.execute(task(Map.of("agentType", "bedrock", "prompt", "go")));

        assertEquals(TaskResult.Status.COMPLETED, result.getStatus());
        assertEquals("answered in one shot", result.getOutputData().get("text"));
        assertEquals(0, client.statusCalls, "a synchronous runtime must not be polled");
        assertNotNull(result.getOutputData().get("agentEndTime"));
    }

    /** The same runtime can also come back blocked on a tool without ever being polled. */
    @Test
    void honoursAWaitingStateReportedByStartAgent() {
        SynchronousAgentClient client = new SynchronousAgentClient();
        client.startState = ConductorAgentState.WAITING;
        client.startPendingTool = Map.of("tool_name", "lookup");
        ConductorAgentDelegate delegate = new ConductorAgentDelegate(client);

        TaskResult result = delegate.execute(task(Map.of("agentType", "bedrock", "prompt", "go")));

        assertEquals(TaskResult.Status.COMPLETED, result.getStatus());
        assertEquals(true, result.getOutputData().get("waiting"));
        assertEquals(Map.of("tool_name", "lookup"), result.getOutputData().get("pendingTool"));
        assertEquals(0, client.statusCalls);
    }

    /**
     * On resume, a status handed back by respond is used directly; the pending tool recorded by the
     * previous turn rides along on the request, since such a runtime keeps nothing itself.
     */
    @Test
    void usesTheStatusRespondHandsBackAndCarriesThePendingTool() {
        SynchronousAgentClient client = new SynchronousAgentClient();
        client.respondStatus =
                ConductorAgentStatusResponse.builder()
                        .executionId("session-1")
                        .status(ConductorAgentState.COMPLETED)
                        .complete(true)
                        .output(Map.of("result", "tool answered"))
                        .build();
        ConductorAgentDelegate delegate = new ConductorAgentDelegate(client);

        Task resumed =
                task(
                        Map.of(
                                "agentType", "bedrock",
                                "prompt", "here is the tool result",
                                "executionId", "session-1"));
        resumed.setOutputData(Map.of("pendingTool", Map.of("tool_name", "lookup")));

        TaskResult result = delegate.execute(resumed);

        assertEquals(TaskResult.Status.COMPLETED, result.getStatus());
        assertEquals("tool answered", result.getOutputData().get("text"));
        assertEquals(0, client.statusCalls, "respond already answered; no poll needed");
        assertEquals(
                Map.of("tool_name", "lookup"),
                client.respondedRequest.getPendingTool(),
                "the pending tool must be carried to a client that stores nothing");
    }

    /** Credentials and provider config reach respond and cancel, which carry no task input. */
    @Test
    void passesCredentialsAndConfigToRespondAndCancel() {
        SynchronousAgentClient client = new SynchronousAgentClient();
        ConductorAgentDelegate delegate = new ConductorAgentDelegate(client);

        Task resumed =
                task(
                        Map.of(
                                "agentType", "bedrock",
                                "prompt", "go on",
                                "executionId", "session-1",
                                "credentials", Map.of("accessKeyId", "AKIA"),
                                "rawConfig", Map.of("agentId", "agent-1")));
        delegate.execute(resumed);

        assertEquals(Map.of("accessKeyId", "AKIA"), client.respondedRequest.getCredentials());
        assertEquals(Map.of("agentId", "agent-1"), client.respondedRequest.getRawConfig());

        delegate.cancel(resumed, "parent terminated");
        assertEquals(Map.of("accessKeyId", "AKIA"), client.canceledRequest.getCredentials());
        assertEquals(Map.of("agentId", "agent-1"), client.canceledRequest.getRawConfig());
    }

    // --- tools run as workflow tasks (autoRunTools) --------------------------------------------

    /**
     * The point of autoRunTools: the agent and its tools stay one node in the workflow. The AGENT
     * task must stay IN_PROGRESS while the tools run, not complete and hand the request back.
     */
    @Test
    void schedulesRequestedToolsAndStaysInProgress() {
        ToolCallingAgentClient client = new ToolCallingAgentClient();
        client.pendingTools =
                List.of(
                        Map.of("tool_name", "get_revenue", "tool_call_id", "call-1"),
                        Map.of("tool_name", "get_headcount", "tool_call_id", "call-2"));
        RecordingToolDispatcher dispatcher = new RecordingToolDispatcher();
        ConductorAgentDelegate delegate = new ConductorAgentDelegate(client, dispatcher);

        TaskResult result = delegate.execute(autoRunToolsTask());

        assertEquals(TaskResult.Status.IN_PROGRESS, result.getStatus());
        assertEquals("dispatch-1", result.getOutputData().get("toolDispatchId"));
        // Drill-in from the agent to the tool run, both places the execution view may look.
        assertEquals("dispatch-1", result.getSubWorkflowId());
        assertEquals("dispatch-1", result.getOutputData().get("subWorkflowId"));
        // Both tools were scheduled — not just the first.
        assertEquals(2, dispatcher.request.toolCalls().size());
        assertEquals("agent_ref", dispatcher.request.taskRefName());
        assertEquals("wf-1", dispatcher.request.parentWorkflowId());
    }

    @Test
    void keepsWaitingWhileToolsAreStillRunning() {
        ToolCallingAgentClient client = new ToolCallingAgentClient();
        RecordingToolDispatcher dispatcher = new RecordingToolDispatcher();
        dispatcher.status = AgentToolDispatch.running("dispatch-1");
        ConductorAgentDelegate delegate = new ConductorAgentDelegate(client, dispatcher);

        Task task = autoRunToolsTask();
        task.setOutputData(
                new LinkedHashMap<>(
                        Map.of("executionId", "exec-1", "toolDispatchId", "dispatch-1")));

        TaskResult result = delegate.execute(task);

        assertEquals(TaskResult.Status.IN_PROGRESS, result.getStatus());
        assertEquals(0, client.respondCalls, "the agent must not be resumed early");
    }

    @Test
    void feedsToolResultsBackToTheAgentKeyedByCall() {
        ToolCallingAgentClient client = new ToolCallingAgentClient();
        client.respondStatus =
                ConductorAgentStatusResponse.builder()
                        .executionId("exec-1")
                        .status(ConductorAgentState.COMPLETED)
                        .complete(true)
                        .output(Map.of("result", "4.2M over 37 engineers"))
                        .build();
        RecordingToolDispatcher dispatcher = new RecordingToolDispatcher();
        dispatcher.status =
                AgentToolDispatch.completed(
                        "dispatch-1",
                        Map.of(
                                "call-1",
                                Map.of("revenue", "4.2M"),
                                "call-2",
                                Map.of("headcount", 37)));
        ConductorAgentDelegate delegate = new ConductorAgentDelegate(client, dispatcher);

        Task task = autoRunToolsTask();
        task.setOutputData(
                new LinkedHashMap<>(
                        Map.of("executionId", "exec-1", "toolDispatchId", "dispatch-1")));

        TaskResult result = delegate.execute(task);

        assertEquals(TaskResult.Status.COMPLETED, result.getStatus());
        assertEquals("4.2M over 37 engineers", result.getOutputData().get("text"));
        // Each tool's result reaches the agent under its own call id.
        assertEquals(
                Map.of("call-1", Map.of("revenue", "4.2M"), "call-2", Map.of("headcount", 37)),
                client.respondedRequest.getToolResults());
        // The finished batch is no longer advertised as outstanding work.
        assertFalse(result.getOutputData().containsKey("toolDispatchId"));
        assertFalse(result.getOutputData().containsKey("pendingTools"));
    }

    @Test
    void atransientRespondFailureKeepsTheBatchHandleSoToolsAreNotRerun() {
        // The tools have run and their results are in flight. If the handle is dropped before the
        // provider accepts them, the next poll sees a task that never dispatched anything, reads
        // the same outstanding calls back off the provider, and runs every tool a second time.
        ToolCallingAgentClient client = new ToolCallingAgentClient();
        client.respondFailure = new RuntimeException("connection reset");
        RecordingToolDispatcher dispatcher = new RecordingToolDispatcher();
        dispatcher.status =
                AgentToolDispatch.completed(
                        "dispatch-1", Map.of("call-1", Map.of("revenue", "4.2M")));
        ConductorAgentDelegate delegate = new ConductorAgentDelegate(client, dispatcher);

        Task task = autoRunToolsTask();
        task.setOutputData(
                new LinkedHashMap<>(
                        Map.of("executionId", "exec-1", "toolDispatchId", "dispatch-1")));

        TaskResult result = delegate.execute(task);

        assertEquals(TaskResult.Status.IN_PROGRESS, result.getStatus());
        assertEquals("dispatch-1", result.getOutputData().get("toolDispatchId"));
    }

    @Test
    void afailedToolRunFailsTheAgentTask() {
        ToolCallingAgentClient client = new ToolCallingAgentClient();
        RecordingToolDispatcher dispatcher = new RecordingToolDispatcher();
        dispatcher.status = AgentToolDispatch.failed("dispatch-1", "get_revenue timed out");
        ConductorAgentDelegate delegate = new ConductorAgentDelegate(client, dispatcher);

        Task task = autoRunToolsTask();
        task.setOutputData(
                new LinkedHashMap<>(
                        Map.of("executionId", "exec-1", "toolDispatchId", "dispatch-1")));

        TaskResult result = delegate.execute(task);

        assertEquals(TaskResult.Status.FAILED_WITH_TERMINAL_ERROR, result.getStatus());
        assertTrue(result.getReasonForIncompletion().contains("get_revenue timed out"));
    }

    @Test
    void asecondToolTurnIsDispatchedRatherThanTreatedAsAnError() {
        ToolCallingAgentClient client = new ToolCallingAgentClient();
        // Answering the first batch makes the model ask for another tool.
        client.respondStatus =
                ConductorAgentStatusResponse.builder()
                        .executionId("exec-1")
                        .status(ConductorAgentState.WAITING)
                        .waiting(true)
                        .pendingTools(
                                List.of(
                                        Map.of(
                                                "tool_name",
                                                "get_margin",
                                                "tool_call_id",
                                                "call-3")))
                        .build();
        RecordingToolDispatcher dispatcher = new RecordingToolDispatcher();
        dispatcher.status =
                AgentToolDispatch.completed(
                        "first-batch", Map.of("call-1", Map.of("revenue", "4.2M")));
        ConductorAgentDelegate delegate = new ConductorAgentDelegate(client, dispatcher);

        Task task = autoRunToolsTask();
        task.setOutputData(
                new LinkedHashMap<>(
                        Map.of("executionId", "exec-1", "toolDispatchId", "first-batch")));

        TaskResult result = delegate.execute(task);

        assertEquals(TaskResult.Status.IN_PROGRESS, result.getStatus());
        assertEquals(1, dispatcher.dispatches, "the new turn's tool should be scheduled");
        assertEquals("dispatch-1", result.getOutputData().get("toolDispatchId"));
        assertEquals("get_margin", dispatcher.request.toolCalls().get(0).get("tool_name"));
    }

    /** Without autoRunTools, the old hand-wired contract is untouched. */
    @Test
    void handsToolsBackToTheWorkflowWhenAutoRunIsOff() {
        ToolCallingAgentClient client = new ToolCallingAgentClient();
        RecordingToolDispatcher dispatcher = new RecordingToolDispatcher();
        ConductorAgentDelegate delegate = new ConductorAgentDelegate(client, dispatcher);

        TaskResult result =
                delegate.execute(task(Map.of("agentType", "conductor", "prompt", "go")));

        assertEquals(TaskResult.Status.COMPLETED, result.getStatus());
        assertEquals(true, result.getOutputData().get("waiting"));
        assertNull(dispatcher.request, "nothing should have been scheduled");
    }

    /** An SDK worker has no engine to schedule on, so it must fall back cleanly. */
    @Test
    void handsToolsBackWhenNoDispatcherIsAvailable() {
        ToolCallingAgentClient client = new ToolCallingAgentClient();
        ConductorAgentDelegate delegate = new ConductorAgentDelegate(client);

        TaskResult result = delegate.execute(autoRunToolsTask());

        assertEquals(TaskResult.Status.COMPLETED, result.getStatus());
        assertEquals(true, result.getOutputData().get("waiting"));
    }

    private static Task autoRunToolsTask() {
        return task(
                Map.of(
                        "agentType", "microsoft-foundry",
                        "prompt", "compare revenue per engineer",
                        "autoRunTools", true));
    }

    /** An agent that always comes back asking for tools. */
    @Test
    void toolsTheProviderRanItselfReachTheTaskOutput() {
        // A synchronous surface answers inside startAgent, so this is the only write the task gets.
        ConductorAgentClient client =
                new ConductorAgentClient() {
                    @Override
                    public String agentType() {
                        return "microsoft-foundry";
                    }

                    @Override
                    public ConductorAgentStartResponse startAgent(
                            ConductorAgentStartRequest request) {
                        return ConductorAgentStartResponse.builder()
                                .executionId("resp-1")
                                .agentName("analyst")
                                .state(ConductorAgentState.COMPLETED)
                                .output(Map.of("result", "GOOG rose 2.1%"))
                                .executedTools(
                                        List.of(
                                                Map.of(
                                                        "type",
                                                        "web_search_call",
                                                        "tool_call_id",
                                                        "ws_1")))
                                .build();
                    }

                    @Override
                    public ConductorAgentStatusResponse getAgentStatus(
                            String executionId, ConductorAgentRequest request) {
                        return null;
                    }

                    @Override
                    public void respond(ConductorAgentRespondRequest request) {}

                    @Override
                    public void cancelAgent(ConductorAgentCancelRequest request) {}
                };

        TaskResult result =
                new ConductorAgentDelegate(client)
                        .execute(
                                task(
                                        Map.of(
                                                "agentType",
                                                "microsoft-foundry",
                                                "prompt",
                                                "how did GOOG do?")));

        assertEquals(TaskResult.Status.COMPLETED, result.getStatus());
        assertEquals(
                List.of(Map.of("type", "web_search_call", "tool_call_id", "ws_1")),
                result.getOutputData().get("executedTools"));
    }

    private static final class ToolCallingAgentClient implements ConductorAgentClient {

        private List<Map<String, Object>> pendingTools =
                List.of(Map.of("tool_name", "get_revenue", "tool_call_id", "call-1"));
        private ConductorAgentStatusResponse respondStatus;
        private ConductorAgentRespondRequest respondedRequest;
        private RuntimeException respondFailure;
        private int respondCalls;

        @Override
        public String agentType() {
            return "microsoft-foundry";
        }

        @Override
        public ConductorAgentStartResponse startAgent(ConductorAgentStartRequest request) {
            return ConductorAgentStartResponse.builder()
                    .executionId("exec-1")
                    .agentName("analyst")
                    .state(ConductorAgentState.WAITING)
                    .pendingTool(pendingTools.get(0))
                    .pendingTools(pendingTools)
                    .build();
        }

        @Override
        public ConductorAgentStatusResponse getAgentStatus(
                String executionId, ConductorAgentRequest request) {
            return ConductorAgentStatusResponse.builder()
                    .executionId(executionId)
                    .status(ConductorAgentState.WAITING)
                    .waiting(true)
                    .pendingTool(pendingTools.get(0))
                    .pendingTools(pendingTools)
                    .build();
        }

        @Override
        public void respond(ConductorAgentRespondRequest request) {
            respondCalls++;
            respondedRequest = request;
            if (respondFailure != null) {
                throw respondFailure;
            }
        }

        @Override
        public ConductorAgentStatusResponse respondWithStatus(
                ConductorAgentRespondRequest request) {
            respond(request);
            return respondStatus;
        }

        @Override
        public void cancelAgent(ConductorAgentCancelRequest request) {}
    }

    private static final class RecordingToolDispatcher implements AgentToolDispatcher {

        private AgentToolDispatcher.Request request;
        private AgentToolDispatch status = AgentToolDispatch.running("dispatch-1");
        private String canceled;
        private int dispatches;

        @Override
        public AgentToolDispatch dispatch(AgentToolDispatcher.Request request) {
            this.request = request;
            return AgentToolDispatch.running("dispatch-" + (++dispatches));
        }

        @Override
        public AgentToolDispatch status(String dispatchId) {
            return status;
        }

        @Override
        public void cancel(String dispatchId) {
            canceled = dispatchId;
        }
    }

    /** Cancelling the agent must stop the tools running for it — nothing else will. */
    @Test
    void cancellingTheAgentStopsItsRunningTools() {
        ToolCallingAgentClient client = new ToolCallingAgentClient();
        RecordingToolDispatcher dispatcher = new RecordingToolDispatcher();
        ConductorAgentDelegate delegate = new ConductorAgentDelegate(client, dispatcher);

        Task task = autoRunToolsTask();
        task.setOutputData(
                new LinkedHashMap<>(
                        Map.of("executionId", "exec-1", "toolDispatchId", "dispatch-1")));

        delegate.cancel(task, "parent terminated");

        assertEquals("dispatch-1", dispatcher.canceled);
    }

    @Test
    void cancellingWithNoToolsRunningCancelsNothing() {
        ToolCallingAgentClient client = new ToolCallingAgentClient();
        RecordingToolDispatcher dispatcher = new RecordingToolDispatcher();
        ConductorAgentDelegate delegate = new ConductorAgentDelegate(client, dispatcher);

        Task task = autoRunToolsTask();
        task.setOutputData(new LinkedHashMap<>(Map.of("executionId", "exec-1")));

        delegate.cancel(task, "parent terminated");

        assertNull(dispatcher.canceled);
    }

    @Test
    void afailedToolRunAlsoStopsTheRestOfTheBatch() {
        ToolCallingAgentClient client = new ToolCallingAgentClient();
        RecordingToolDispatcher dispatcher = new RecordingToolDispatcher();
        dispatcher.status = AgentToolDispatch.failed("dispatch-1", "get_revenue timed out");
        ConductorAgentDelegate delegate = new ConductorAgentDelegate(client, dispatcher);

        Task task = autoRunToolsTask();
        task.setOutputData(
                new LinkedHashMap<>(
                        Map.of("executionId", "exec-1", "toolDispatchId", "dispatch-1")));

        delegate.execute(task);

        // One tool failing should not leave its siblings running.
        assertEquals("dispatch-1", dispatcher.canceled);
    }

    /** Stands in for a runtime that answers inside start/respond and cannot be polled. */
    private static final class SynchronousAgentClient implements ConductorAgentClient {

        private ConductorAgentState startState;
        private Map<String, Object> startOutput;
        private Map<String, Object> startPendingTool;
        private ConductorAgentStatusResponse respondStatus;
        private ConductorAgentRespondRequest respondedRequest;
        private ConductorAgentCancelRequest canceledRequest;
        private int statusCalls;

        @Override
        public String agentType() {
            return "bedrock";
        }

        @Override
        public ConductorAgentStartResponse startAgent(ConductorAgentStartRequest request) {
            return ConductorAgentStartResponse.builder()
                    .executionId("session-1")
                    .agentName("agent-1")
                    .state(startState)
                    .output(startOutput)
                    .pendingTool(startPendingTool)
                    .build();
        }

        // Non-terminal on purpose: cancelBestEffort probes status first and skips cancelling a run
        // that has already finished.
        @Override
        public ConductorAgentStatusResponse getAgentStatus(
                String executionId, ConductorAgentRequest request) {
            statusCalls++;
            return ConductorAgentStatusResponse.builder()
                    .executionId(executionId)
                    .status(ConductorAgentState.RUNNING)
                    .running(true)
                    .build();
        }

        @Override
        public void respond(ConductorAgentRespondRequest request) {
            respondedRequest = request;
        }

        @Override
        public ConductorAgentStatusResponse respondWithStatus(
                ConductorAgentRespondRequest request) {
            respondedRequest = request;
            return respondStatus;
        }

        @Override
        public void cancelAgent(ConductorAgentCancelRequest request) {
            canceledRequest = request;
        }
    }

    private static final class FakeConductorAgentClient implements ConductorAgentClient {

        @Override
        public String agentType() {
            return "conductor";
        }

        private ConductorAgentStartRequest startedRequest;
        private ConductorAgentStatusResponse status =
                ConductorAgentStatusResponse.builder()
                        .executionId("exec-1")
                        .status(ConductorAgentState.RUNNING)
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
        public ConductorAgentStatusResponse getAgentStatus(
                String executionId, ConductorAgentRequest request) {
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
