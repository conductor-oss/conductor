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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.dal.ExecutionDAOFacade;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tools an agent asked for become tasks in the agent's own workflow, so the run stays open until a
 * worker has done them.
 */
class InlineAgentToolDispatcherTest {

    private ExecutionDAOFacade executionDAO;
    private QueueDAO queueDAO;
    private WorkflowExecutor workflowExecutor;
    private WorkflowModel workflow;
    private InlineAgentToolDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        executionDAO = mock(ExecutionDAOFacade.class);
        queueDAO = mock(QueueDAO.class);
        workflowExecutor = mock(WorkflowExecutor.class);
        workflow = new WorkflowModel();
        workflow.setWorkflowId("wf-1");
        workflow.setStatus(WorkflowModel.Status.RUNNING);
        workflow.setTasks(new ArrayList<>());
        when(executionDAO.getWorkflowModelFromExecutionDAO(anyString(), anyBoolean()))
                .thenReturn(workflow);
        dispatcher =
                new InlineAgentToolDispatcher(
                        provider(executionDAO), provider(queueDAO), provider(workflowExecutor));
    }

    @Test
    void aToolBecomesATaskOfItsOwnNameInTheAgentsWorkflow() {
        scheduleReturns(1);

        AgentToolDispatch dispatch = dispatcher.dispatch(request(call("get_revenue", "call-1")));

        assertThat(dispatch.state()).isEqualTo(AgentToolDispatch.State.RUNNING);

        WorkflowTask scheduled = capturedTasks().get(0);
        // The name is the tool's, so a worker already registered for it needs no configuration.
        assertThat(scheduled.getName()).isEqualTo("get_revenue");
        assertThat(scheduled.getType()).isEqualTo("SIMPLE");
        assertThat(scheduled.getTaskReferenceName()).isEqualTo("agent_ref__t1__get_revenue");
        assertThat(scheduled.getInputParameters())
                .containsEntry("quarter", "Q3")
                .containsEntry("_toolCallId", "call-1")
                .containsEntry("_toolName", "get_revenue");
        // Scheduled into the agent's own workflow, not a child of it.
        assertThat(capturedWorkflow().getWorkflowId()).isEqualTo("wf-1");
    }

    @Test
    void asecondTurnGetsNamesOfItsOwn() {
        // The engine drops a repeated reference name without saying so, so round two must not
        // reuse round one's.
        workflow.getTasks()
                .add(taskNamed("agent_ref__t1__get_revenue", TaskModel.Status.COMPLETED));
        scheduleReturns(1);

        dispatcher.dispatch(request(call("get_revenue", "call-2")));

        assertThat(capturedTasks().get(0).getTaskReferenceName())
                .isEqualTo("agent_ref__t2__get_revenue");
    }

    @Test
    void thebatchIsRunningUntilEveryToolIsDone() {
        workflow.getTasks()
                .add(taskNamed("agent_ref__t1__get_revenue", TaskModel.Status.COMPLETED));
        workflow.getTasks()
                .add(
                        taskNamed(
                                "agent_ref__t1__get_revenue_2",
                                TaskModel.Status.SCHEDULED,
                                "call-2"));

        assertThat(dispatcher.status("inline:wf-1|agent_ref|1").state())
                .isEqualTo(AgentToolDispatch.State.RUNNING);
    }

    @Test
    void resultsComeBackKeyedByTheCallTheyAnswer() {
        TaskModel done = taskNamed("agent_ref__t1__get_revenue", TaskModel.Status.COMPLETED);
        done.setOutputData(Map.of("revenue", "4.2M"));
        workflow.getTasks().add(done);

        AgentToolDispatch dispatch = dispatcher.status("inline:wf-1|agent_ref|1");

        assertThat(dispatch.state()).isEqualTo(AgentToolDispatch.State.COMPLETED);
        assertThat(dispatch.resultsByToolCallId())
                .isEqualTo(Map.of("call-1", Map.of("revenue", "4.2M")));
    }

    @Test
    void afailedToolIsReportedToTheAgentRatherThanFailingTheBatch() {
        TaskModel failed = taskNamed("agent_ref__t1__get_revenue", TaskModel.Status.FAILED);
        failed.setReasonForIncompletion("no such quarter");
        workflow.getTasks().add(failed);

        AgentToolDispatch dispatch = dispatcher.status("inline:wf-1|agent_ref|1");

        // A tool that exhausted its retries is something the agent can work with - try another
        // tool, or say it could not find out. Failing the batch would decide that for it.
        assertThat(dispatch.state()).isEqualTo(AgentToolDispatch.State.COMPLETED);
        assertThat(dispatch.resultsByToolCallId())
                .isEqualTo(Map.of("call-1", Map.of("error", "no such quarter")));
    }

    @Test
    void aretriedToolIsJudgedOnItsRetryNotItsFirstAttempt() {
        // A retry keeps the reference name, so the workflow holds the failed original beside the
        // new attempt. Reading both would make one failure fatal however many retries remained.
        TaskModel firstAttempt =
                taskNamed("agent_ref__t1__get_revenue", TaskModel.Status.FAILED, "call-1");
        firstAttempt.setRetryCount(0);
        TaskModel retry =
                taskNamed("agent_ref__t1__get_revenue", TaskModel.Status.COMPLETED, "call-1");
        retry.setRetryCount(1);
        retry.setOutputData(Map.of("revenue", "4.2M"));
        workflow.getTasks().add(firstAttempt);
        workflow.getTasks().add(retry);

        AgentToolDispatch dispatch = dispatcher.status("inline:wf-1|agent_ref|1");

        assertThat(dispatch.state()).isEqualTo(AgentToolDispatch.State.COMPLETED);
        assertThat(dispatch.resultsByToolCallId())
                .isEqualTo(Map.of("call-1", Map.of("revenue", "4.2M")));
    }

    @Test
    void atoolTaskCannotFailTheWorkflow() {
        scheduleReturns(1);

        dispatcher.dispatch(request(call("get_revenue", "call-1")));

        // The agent is told about a failed tool and decides what to do, so the run has to survive
        // one. Optional is what stops the decider terminating the workflow on exhausted retries.
        assertThat(capturedTasks().get(0).isOptional()).isTrue();
    }

    @Test
    void thesameToolTwiceInOneTurnGetsDistinctTasks() {
        scheduleReturns(2);

        dispatcher.dispatch(
                new AgentToolDispatcher.Request(
                        "wf-1",
                        "task-1",
                        "agent_ref",
                        "exec-1",
                        List.of(call("get_revenue", "call-1"), call("get_revenue", "call-2")),
                        null,
                        10));

        assertThat(capturedTasks())
                .extracting(WorkflowTask::getTaskReferenceName)
                .containsExactly("agent_ref__t1__get_revenue", "agent_ref__t1__get_revenue_2");
    }

    @Test
    void anagentThatKeepsAskingForToolsIsStopped() {
        for (int turn = 1; turn <= 10; turn++) {
            workflow.getTasks()
                    .add(
                            taskNamed(
                                    "agent_ref__t" + turn + "__get_revenue",
                                    TaskModel.Status.COMPLETED,
                                    "call-" + turn));
        }

        AgentToolDispatch dispatch = dispatcher.dispatch(request(call("get_revenue", "call-11")));

        assertThat(dispatch.state()).isEqualTo(AgentToolDispatch.State.FAILED);
        assertThat(dispatch.reason()).contains("maxToolTurns");
    }

    @Test
    void atoolTaskWithNoCallIdIsRefusedRatherThanKeyedOnNull() {
        TaskModel done = new TaskModel();
        done.setReferenceTaskName("agent_ref__t1__get_revenue");
        done.setStatus(TaskModel.Status.COMPLETED);
        done.setInputData(Map.of());
        workflow.getTasks().add(done);

        // "null" as a key is one the provider rejects, and two of them would collapse into one.
        assertThatThrownBy(() -> dispatcher.status("inline:wf-1|agent_ref|1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be matched");
    }

    @Test
    void cancellingATooldequeuesItSoNoWorkerStillRunsIt() {
        TaskModel running =
                taskNamed("agent_ref__t1__get_revenue", TaskModel.Status.SCHEDULED, "call-1");
        running.setTaskId("t-1");
        running.setTaskDefName("get_revenue");
        // The queue name is derived from the task, so it has to look like a real one.
        running.setTaskType("get_revenue");
        workflow.getTasks().add(running);

        dispatcher.cancel("inline:wf-1|agent_ref|1");

        verify(queueDAO).remove(eq("get_revenue"), eq("t-1"));
        assertThat(running.getStatus()).isEqualTo(TaskModel.Status.CANCELED);
    }

    @Test
    void statusIsResolvedFromTheIdAloneSoAnyReplicaCanServeIt() {
        TaskModel done = taskNamed("agent_ref__t1__get_revenue", TaskModel.Status.COMPLETED);
        done.setOutputData(Map.of("revenue", "4.2M"));
        workflow.getTasks().add(done);

        // A different instance, holding nothing from the dispatch.
        InlineAgentToolDispatcher elsewhere =
                new InlineAgentToolDispatcher(
                        provider(executionDAO), provider(queueDAO), provider(workflowExecutor));

        assertThat(elsewhere.status("inline:wf-1|agent_ref|1").state())
                .isEqualTo(AgentToolDispatch.State.COMPLETED);
    }

    @Test
    void aworkflowThatWillNotTakeTheTasksFailsTheBatchRatherThanWaitingForever() {
        // scheduleDynamicTasks returns what it actually scheduled; nothing means nothing will run.
        scheduleReturns(0);

        AgentToolDispatch dispatch = dispatcher.dispatch(request(call("get_revenue", "call-1")));

        assertThat(dispatch.state()).isEqualTo(AgentToolDispatch.State.FAILED);
        assertThat(dispatch.reason()).contains("0 of 1");
    }

    private void scheduleReturns(int count) {
        List<TaskModel> scheduled = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            scheduled.add(new TaskModel());
        }
        when(workflowExecutor.scheduleDynamicTasks(any(), any())).thenReturn(scheduled);
    }

    @SuppressWarnings("unchecked")
    private List<WorkflowTask> capturedTasks() {
        ArgumentCaptor<List<WorkflowTask>> captor = ArgumentCaptor.forClass(List.class);
        verify(workflowExecutor).scheduleDynamicTasks(any(), captor.capture());
        return captor.getValue();
    }

    private WorkflowModel capturedWorkflow() {
        ArgumentCaptor<WorkflowModel> captor = ArgumentCaptor.forClass(WorkflowModel.class);
        verify(workflowExecutor).scheduleDynamicTasks(captor.capture(), any());
        return captor.getValue();
    }

    private static TaskModel taskNamed(String referenceName, TaskModel.Status status) {
        return taskNamed(referenceName, status, "call-1");
    }

    private static TaskModel taskNamed(
            String referenceName, TaskModel.Status status, String toolCallId) {
        TaskModel task = new TaskModel();
        task.setReferenceTaskName(referenceName);
        task.setStatus(status);
        task.setInputData(Map.of(AgentToolNaming.TOOL_CALL_ID, toolCallId));
        return task;
    }

    private static Map<String, Object> call(String toolName, String toolCallId) {
        return Map.of(
                "tool_name", toolName,
                "tool_call_id", toolCallId,
                "arguments", "{\"quarter\":\"Q3\"}");
    }

    private static AgentToolDispatcher.Request request(Map<String, Object> toolCall) {
        return new AgentToolDispatcher.Request(
                "wf-1", "task-1", "agent_ref", "exec-1", List.of(toolCall), null, 10);
    }

    private static <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(value);
        return provider;
    }
}
