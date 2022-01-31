/*
 * Copyright 2022 Netflix, Inc.
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

import java.util.HashMap;
import java.util.List;

import org.junit.Test;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.utils.IDGenerator;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_WAIT;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class WaitTaskMapperTest {

    @Test
    public void getMappedTasks() {

        // Given
        WorkflowTask taskToSchedule = new WorkflowTask();
        taskToSchedule.setName("Wait_task");
        taskToSchedule.setType(TaskType.WAIT.name());
        String taskId = IDGenerator.generate();

        ParametersUtils parametersUtils = mock(ParametersUtils.class);
        WorkflowModel workflow = new WorkflowModel();
        WorkflowDef workflowDef = new WorkflowDef();
        workflow.setWorkflowDefinition(workflowDef);

        TaskMapperContext taskMapperContext =
                TaskMapperContext.newBuilder()
                        .withWorkflowDefinition(workflowDef)
                        .withWorkflowInstance(workflow)
                        .withTaskDefinition(new TaskDef())
                        .withTaskToSchedule(taskToSchedule)
                        .withTaskInput(new HashMap<>())
                        .withRetryCount(0)
                        .withTaskId(taskId)
                        .build();

        WaitTaskMapper waitTaskMapper = new WaitTaskMapper(parametersUtils);
        // When
        List<TaskModel> mappedTasks = waitTaskMapper.getMappedTasks(taskMapperContext);

        // Then
        assertEquals(1, mappedTasks.size());
        assertEquals(TASK_TYPE_WAIT, mappedTasks.get(0).getTaskType());
    }
}
