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
package com.netflix.conductor.core.execution.mapper;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.utils.IDGenerator;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_GDRIVE_READ;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class GDriveReadTaskMapperTest {

    @Test
    public void getMappedTasks() {
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setName("read_g_drive");
        workflowTask.setType(TaskType.GDRIVE_READ.name());
        workflowTask.setTaskReferenceName("read_g_drive_ref");

        String taskId = new IDGenerator().generate();
        Map<String, Object> input =
                Map.of("connectionId", "gdrive-prod", "folderId", "folder-123", "maxFiles", 10);

        ParametersUtils parametersUtils = mock(ParametersUtils.class);
        doReturn(input).when(parametersUtils).getTaskInputV2(any(), any(), any(), any());

        WorkflowDef workflowDef = new WorkflowDef();
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(workflowDef);

        TaskMapperContext taskMapperContext =
                TaskMapperContext.newBuilder()
                        .withWorkflowModel(workflow)
                        .withTaskDefinition(new TaskDef("read_g_drive"))
                        .withWorkflowTask(workflowTask)
                        .withRetryCount(0)
                        .withTaskId(taskId)
                        .build();

        List<TaskModel> mappedTasks =
                new GDriveReadTaskMapper(parametersUtils).getMappedTasks(taskMapperContext);

        assertNotNull(mappedTasks);
        assertEquals(1, mappedTasks.size());
        assertEquals(TASK_TYPE_GDRIVE_READ, mappedTasks.get(0).getTaskType());
        assertEquals("read_g_drive", mappedTasks.get(0).getTaskDefName());
        assertEquals(TaskModel.Status.IN_PROGRESS, mappedTasks.get(0).getStatus());
        assertEquals(input, mappedTasks.get(0).getInputData());
    }
}
