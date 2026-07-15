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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.conductoross.conductor.common.metadata.agent.CreateTrackingWorkflowRequest;
import org.conductoross.conductor.common.metadata.agent.CreateTrackingWorkflowResponse;
import org.conductoross.conductor.common.metadata.agent.InjectTaskRequest;
import org.conductoross.conductor.common.metadata.agent.InjectTaskResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentDagServiceTest {

    @Mock ExecutionDAO executionDAO;

    AgentDagService service;

    @BeforeEach
    void setUp() {
        service = new AgentDagService(executionDAO);
    }

    // ── injectTask ──────────────────────────────────────────────────────────

    @Test
    void injectTask_callsCreateTasksWithCorrectFields() {
        WorkflowModel wf = makeWorkflow("wf-1", "my-workflow", 0);
        when(executionDAO.getWorkflow("wf-1", true)).thenReturn(wf);

        InjectTaskRequest req = new InjectTaskRequest();
        req.setTaskDefName("Bash");
        req.setReferenceTaskName("bash_tool_use_id_abc");
        req.setType("SIMPLE");
        req.setInputData(Map.of("command", "ls"));
        req.setStatus("IN_PROGRESS");

        InjectTaskResponse resp = service.injectTask("wf-1", req);

        assertThat(resp.getTaskId()).isNotBlank();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TaskModel>> captor = ArgumentCaptor.forClass(List.class);
        verify(executionDAO).createTasks(captor.capture());
        TaskModel created = captor.getValue().get(0);

        assertThat(created.getTaskDefName()).isEqualTo("Bash");
        assertThat(created.getReferenceTaskName()).isEqualTo("bash_tool_use_id_abc");
        assertThat(created.getTaskType()).isEqualTo("SIMPLE");
        assertThat(created.getStatus()).isEqualTo(TaskModel.Status.IN_PROGRESS);
        assertThat(created.getWorkflowInstanceId()).isEqualTo("wf-1");
        assertThat(created.getWorkflowType()).isEqualTo("my-workflow");
        assertThat(created.getInputData()).containsEntry("command", "ls");
        assertThat(created.getSeq()).isEqualTo(1); // 0 tasks + 1
        assertThat(created.getTaskId()).isEqualTo(resp.getTaskId());

        // Verify WorkflowTask added to definition and workflow updated
        verify(executionDAO).updateWorkflow(wf);
        List<WorkflowTask> defTasks = wf.getWorkflowDefinition().getTasks();
        assertThat(defTasks).hasSize(1);
        WorkflowTask wt = defTasks.get(0);
        assertThat(wt.getName()).isEqualTo("Bash");
        assertThat(wt.getTaskReferenceName()).isEqualTo("bash_tool_use_id_abc");
        assertThat(wt.getType()).isEqualTo("SIMPLE");
    }

    @Test
    void injectTask_subWorkflow_setsSubWorkflowId() {
        WorkflowModel wf = makeWorkflow("wf-2", "my-workflow", 2);
        when(executionDAO.getWorkflow("wf-2", true)).thenReturn(wf);

        InjectTaskRequest req = new InjectTaskRequest();
        req.setTaskDefName("claude-sub-agent");
        req.setReferenceTaskName("agent_tool_use_id_xyz");
        req.setType("SUB_WORKFLOW");
        req.setInputData(Map.of("prompt", "do something"));

        InjectTaskRequest.SubWorkflowParam swp = new InjectTaskRequest.SubWorkflowParam();
        swp.setName("my-sub-workflow");
        swp.setVersion(1);
        swp.setExecutionId("sub-wf-id-999");
        req.setSubWorkflowParam(swp);

        service.injectTask("wf-2", req);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TaskModel>> captor = ArgumentCaptor.forClass(List.class);
        verify(executionDAO).createTasks(captor.capture());
        TaskModel created = captor.getValue().get(0);

        assertThat(created.getSubWorkflowId()).isEqualTo("sub-wf-id-999");
        assertThat(created.getSeq()).isEqualTo(3); // 2 existing tasks + 1

        // Verify WorkflowTask with SubWorkflowParams added to definition
        verify(executionDAO).updateWorkflow(wf);
        List<WorkflowTask> defTasks = wf.getWorkflowDefinition().getTasks();
        assertThat(defTasks).hasSize(1);
        WorkflowTask wt = defTasks.get(0);
        assertThat(wt.getName()).isEqualTo("claude-sub-agent");
        assertThat(wt.getType()).isEqualTo("SUB_WORKFLOW");
        assertThat(wt.getSubWorkflowParam()).isNotNull();
        assertThat(wt.getSubWorkflowParam().getName()).isEqualTo("my-sub-workflow");
        assertThat(wt.getSubWorkflowParam().getVersion()).isEqualTo(1);
    }

    @Test
    void injectTask_unknownWorkflow_throws404() {
        when(executionDAO.getWorkflow("bad-id", true)).thenReturn(null);

        InjectTaskRequest req = new InjectTaskRequest();
        req.setTaskDefName("Bash");
        req.setType("SIMPLE");

        assertThatThrownBy(() -> service.injectTask("bad-id", req))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Execution not found");
    }

    // ── createTrackingWorkflow ───────────────────────────────────────────────

    @Test
    void createTrackingWorkflow_createsWorkflowViaDAO() {
        CreateTrackingWorkflowRequest req = new CreateTrackingWorkflowRequest();
        req.setWorkflowName("my-sub-agent");
        req.setInput(Map.of("prompt", "run the build"));

        CreateTrackingWorkflowResponse resp = service.createTrackingWorkflow(req);

        assertThat(resp.getExecutionId()).isNotBlank();

        ArgumentCaptor<WorkflowModel> captor = ArgumentCaptor.forClass(WorkflowModel.class);
        verify(executionDAO).createWorkflow(captor.capture());
        WorkflowModel created = captor.getValue();

        assertThat(created.getWorkflowDefinition().getName()).isEqualTo("my-sub-agent");
        assertThat(created.getInput()).containsEntry("prompt", "run the build");
        assertThat(created.getStatus()).isEqualTo(WorkflowModel.Status.RUNNING);
    }

    @Test
    void createTrackingWorkflow_setsParentLinkage() {
        CreateTrackingWorkflowRequest req = new CreateTrackingWorkflowRequest();
        req.setWorkflowName("child-agent");
        req.setInput(Map.of());
        req.setParentWorkflowId("parent-wf-123");
        req.setParentWorkflowTaskId("parent-task-456");

        CreateTrackingWorkflowResponse resp = service.createTrackingWorkflow(req);
        assertThat(resp.getExecutionId()).isNotBlank();

        ArgumentCaptor<WorkflowModel> captor = ArgumentCaptor.forClass(WorkflowModel.class);
        verify(executionDAO).createWorkflow(captor.capture());
        WorkflowModel created = captor.getValue();

        assertThat(created.getParentWorkflowId()).isEqualTo("parent-wf-123");
        assertThat(created.getParentWorkflowTaskId()).isEqualTo("parent-task-456");
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private WorkflowModel makeWorkflow(String id, String name, int taskCount) {
        WorkflowModel wf = new WorkflowModel();
        wf.setWorkflowId(id);
        WorkflowDef def = new WorkflowDef();
        def.setName(name);
        def.setTasks(new ArrayList<>());
        wf.setWorkflowDefinition(def);
        List<TaskModel> tasks = new ArrayList<>();
        for (int i = 0; i < taskCount; i++) {
            tasks.add(new TaskModel());
        }
        wf.setTasks(tasks);
        return wf;
    }
}
