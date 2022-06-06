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

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_HUMAN;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class HumanTaskMapperTest {

    @Test
    public void getMappedTasks() {

        // Given
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setName("human_task");
        workflowTask.setType(TaskType.HUMAN.name());
        String taskId = new IDGenerator().generate();

        ParametersUtils parametersUtils = mock(ParametersUtils.class);
        WorkflowModel workflow = new WorkflowModel();
        WorkflowDef workflowDef = new WorkflowDef();
        workflow.setWorkflowDefinition(workflowDef);

        TaskMapperContext taskMapperContext =
                TaskMapperContext.newBuilder()
                        .withWorkflowModel(workflow)
                        .withTaskDefinition(new TaskDef())
                        .withWorkflowTask(workflowTask)
                        .withTaskInput(new HashMap<>())
                        .withRetryCount(0)
                        .withTaskId(taskId)
                        .build();

        HumanTaskMapper humanTaskMapper = new HumanTaskMapper(parametersUtils);
        // When
        List<TaskModel> mappedTasks = humanTaskMapper.getMappedTasks(taskMapperContext);

        // Then
        assertEquals(1, mappedTasks.size());
        assertEquals(TASK_TYPE_HUMAN, mappedTasks.get(0).getTaskType());
    }
}
