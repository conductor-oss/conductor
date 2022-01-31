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

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.exception.TerminateWorkflowException;
import com.netflix.conductor.core.utils.IDGenerator;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DynamicTaskMapperTest {

    @Rule public ExpectedException expectedException = ExpectedException.none();
    private ParametersUtils parametersUtils;
    private MetadataDAO metadataDAO;
    private DynamicTaskMapper dynamicTaskMapper;

    @Before
    public void setUp() {
        parametersUtils = mock(ParametersUtils.class);
        metadataDAO = mock(MetadataDAO.class);

        dynamicTaskMapper = new DynamicTaskMapper(parametersUtils, metadataDAO);
    }

    @Test
    public void getMappedTasks() {

        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setName("DynoTask");
        workflowTask.setDynamicTaskNameParam("dynamicTaskName");
        TaskDef taskDef = new TaskDef();
        taskDef.setName("DynoTask");
        workflowTask.setTaskDefinition(taskDef);

        Map<String, Object> taskInput = new HashMap<>();
        taskInput.put("dynamicTaskName", "DynoTask");

        when(parametersUtils.getTaskInput(
                        anyMap(), any(WorkflowModel.class), any(TaskDef.class), anyString()))
                .thenReturn(taskInput);

        String taskId = IDGenerator.generate();

        WorkflowModel workflow = new WorkflowModel();
        WorkflowDef workflowDef = new WorkflowDef();
        workflow.setWorkflowDefinition(workflowDef);

        TaskMapperContext taskMapperContext =
                TaskMapperContext.newBuilder()
                        .withWorkflowInstance(workflow)
                        .withWorkflowDefinition(workflowDef)
                        .withTaskDefinition(workflowTask.getTaskDefinition())
                        .withTaskToSchedule(workflowTask)
                        .withTaskInput(taskInput)
                        .withRetryCount(0)
                        .withTaskId(taskId)
                        .build();

        when(metadataDAO.getTaskDef("DynoTask")).thenReturn(new TaskDef());

        List<TaskModel> mappedTasks = dynamicTaskMapper.getMappedTasks(taskMapperContext);

        assertEquals(1, mappedTasks.size());

        TaskModel dynamicTask = mappedTasks.get(0);
        assertEquals(taskId, dynamicTask.getTaskId());
    }

    @Test
    public void getDynamicTaskName() {
        Map<String, Object> taskInput = new HashMap<>();
        taskInput.put("dynamicTaskName", "DynoTask");

        String dynamicTaskName = dynamicTaskMapper.getDynamicTaskName(taskInput, "dynamicTaskName");

        assertEquals("DynoTask", dynamicTaskName);
    }

    @Test
    public void getDynamicTaskNameNotAvailable() {
        Map<String, Object> taskInput = new HashMap<>();

        expectedException.expect(TerminateWorkflowException.class);
        expectedException.expectMessage(
                String.format(
                        "Cannot map a dynamic task based on the parameter and input. "
                                + "Parameter= %s, input= %s",
                        "dynamicTaskName", taskInput));

        dynamicTaskMapper.getDynamicTaskName(taskInput, "dynamicTaskName");
    }

    @Test
    public void getDynamicTaskDefinition() {
        // Given
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setName("Foo");
        TaskDef taskDef = new TaskDef();
        taskDef.setName("Foo");
        workflowTask.setTaskDefinition(taskDef);

        when(metadataDAO.getTaskDef(any())).thenReturn(new TaskDef());

        // when
        TaskDef dynamicTaskDefinition = dynamicTaskMapper.getDynamicTaskDefinition(workflowTask);

        assertEquals(dynamicTaskDefinition, taskDef);
    }

    @Test
    public void getDynamicTaskDefinitionNull() {

        // Given
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setName("Foo");

        expectedException.expect(TerminateWorkflowException.class);
        expectedException.expectMessage(
                String.format(
                        "Invalid task specified.  Cannot find task by name %s in the task definitions",
                        workflowTask.getName()));

        dynamicTaskMapper.getDynamicTaskDefinition(workflowTask);
    }
}
