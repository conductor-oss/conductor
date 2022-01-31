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
import java.util.Map;

import org.junit.Test;
import org.mockito.Mockito;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.utils.IDGenerator;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class EventTaskMapperTest {

    @Test
    public void getMappedTasks() {
        ParametersUtils parametersUtils = Mockito.mock(ParametersUtils.class);
        EventTaskMapper eventTaskMapper = new EventTaskMapper(parametersUtils);

        WorkflowTask taskToBeScheduled = new WorkflowTask();
        taskToBeScheduled.setSink("SQSSINK");
        String taskId = IDGenerator.generate();

        Map<String, Object> eventTaskInput = new HashMap<>();
        eventTaskInput.put("sink", "SQSSINK");

        when(parametersUtils.getTaskInput(
                        anyMap(), any(WorkflowModel.class), any(TaskDef.class), anyString()))
                .thenReturn(eventTaskInput);

        WorkflowDef workflowDef = new WorkflowDef();
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(workflowDef);

        TaskMapperContext taskMapperContext =
                TaskMapperContext.newBuilder()
                        .withWorkflowDefinition(workflowDef)
                        .withWorkflowInstance(workflow)
                        .withTaskDefinition(new TaskDef())
                        .withTaskToSchedule(taskToBeScheduled)
                        .withRetryCount(0)
                        .withTaskId(taskId)
                        .build();

        List<TaskModel> mappedTasks = eventTaskMapper.getMappedTasks(taskMapperContext);
        assertEquals(1, mappedTasks.size());

        TaskModel eventTask = mappedTasks.get(0);
        assertEquals(taskId, eventTask.getTaskId());
    }
}
