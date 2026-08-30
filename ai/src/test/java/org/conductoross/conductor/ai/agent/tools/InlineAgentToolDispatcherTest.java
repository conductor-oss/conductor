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
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tools an agent asked for become tasks in the agent's own workflow, so the run stays open until a
 * worker has done them.
 */
class InlineAgentToolDispatcherTest {

    private ExecutionDAOFacade executionDAO;
    private WorkflowExecutor workflowExecutor;
    private WorkflowModel workflow;
    private InlineAgentToolDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        executionDAO = mock(ExecutionDAOFacade.class);
        workflowExecutor = mock(WorkflowExecutor.class);
        workflow = new WorkflowModel();
        workflow.setWorkflowId("wf-1");
        workflow.setStatus(WorkflowModel.Status.RUNNING);
        workflow.setTasks(new ArrayList<>());
        when(executionDAO.getWorkflowModel(anyString(), anyBoolean())).thenReturn(workflow);
        dispatcher =
                new InlineAgentToolDispatcher(provider(executionDAO), provider(workflowExecutor));
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
        assertThat(scheduled.getTaskReferenceName()).isEqualTo("agent_ref__t1__call_1");
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
        workflow.getTasks().add(taskNamed("agent_ref__t1__call_1", TaskModel.Status.COMPLETED));
        scheduleReturns(1);

        dispatcher.dispatch(request(call("get_revenue", "call-2")));

        assertThat(capturedTasks().get(0).getTaskReferenceName())
                .isEqualTo("agent_ref__t2__call_2");
    }

    @Test
    void thebatchIsRunningUntilEveryToolIsDone() {
        workflow.getTasks().add(taskNamed("agent_ref__t1__call_1", TaskModel.Status.COMPLETED));
        workflow.getTasks()
                .add(taskNamed("agent_ref__t1__call_2", TaskModel.Status.SCHEDULED, "call-2"));

        assertThat(dispatcher.status("inline:wf-1|agent_ref|1").state())
                .isEqualTo(AgentToolDispatch.State.RUNNING);
    }

    @Test
    void resultsComeBackKeyedByTheCallTheyAnswer() {
        TaskModel done = taskNamed("agent_ref__t1__call_1", TaskModel.Status.COMPLETED);
        done.setOutputData(Map.of("revenue", "4.2M"));
        workflow.getTasks().add(done);

        AgentToolDispatch dispatch = dispatcher.status("inline:wf-1|agent_ref|1");

        assertThat(dispatch.state()).isEqualTo(AgentToolDispatch.State.COMPLETED);
        assertThat(dispatch.resultsByToolCallId())
                .isEqualTo(Map.of("call-1", Map.of("revenue", "4.2M")));
    }

    @Test
    void afailedToolFailsTheBatchWithItsReason() {
        TaskModel failed = taskNamed("agent_ref__t1__call_1", TaskModel.Status.FAILED);
        failed.setReasonForIncompletion("no such quarter");
        workflow.getTasks().add(failed);

        AgentToolDispatch dispatch = dispatcher.status("inline:wf-1|agent_ref|1");

        assertThat(dispatch.state()).isEqualTo(AgentToolDispatch.State.FAILED);
        assertThat(dispatch.reason()).contains("no such quarter");
    }

    @Test
    void statusIsResolvedFromTheIdAloneSoAnyReplicaCanServeIt() {
        TaskModel done = taskNamed("agent_ref__t1__call_1", TaskModel.Status.COMPLETED);
        done.setOutputData(Map.of("revenue", "4.2M"));
        workflow.getTasks().add(done);

        // A different instance, holding nothing from the dispatch.
        InlineAgentToolDispatcher elsewhere =
                new InlineAgentToolDispatcher(provider(executionDAO), provider(workflowExecutor));

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
                "wf-1", "task-1", "agent_ref", "exec-1", List.of(toolCall), null);
    }

    private static <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(value);
        return provider;
    }
}
