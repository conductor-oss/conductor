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

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.exception.TerminateWorkflowException;
import com.netflix.conductor.core.utils.IDGenerator;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class UserDefinedTaskMapperTest {

    private UserDefinedTaskMapper userDefinedTaskMapper;

    @Rule public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void setUp() {
        ParametersUtils parametersUtils = mock(ParametersUtils.class);
        MetadataDAO metadataDAO = mock(MetadataDAO.class);
        userDefinedTaskMapper = new UserDefinedTaskMapper(parametersUtils, metadataDAO);
    }

    @Test
    public void getMappedTasks() {
        // Given
        WorkflowTask taskToSchedule = new WorkflowTask();
        taskToSchedule.setName("user_task");
        taskToSchedule.setType(TaskType.USER_DEFINED.name());
        taskToSchedule.setTaskDefinition(new TaskDef("user_task"));
        String taskId = IDGenerator.generate();
        String retriedTaskId = IDGenerator.generate();

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
                        .withRetryTaskId(retriedTaskId)
                        .withTaskId(taskId)
                        .build();

        // when
        List<TaskModel> mappedTasks = userDefinedTaskMapper.getMappedTasks(taskMapperContext);

        // Then
        assertEquals(1, mappedTasks.size());
        assertEquals(TaskType.USER_DEFINED.name(), mappedTasks.get(0).getTaskType());
    }

    @Test
    public void getMappedTasksException() {
        // Given
        WorkflowTask taskToSchedule = new WorkflowTask();
        taskToSchedule.setName("user_task");
        taskToSchedule.setType(TaskType.USER_DEFINED.name());
        String taskId = IDGenerator.generate();
        String retriedTaskId = IDGenerator.generate();

        WorkflowModel workflow = new WorkflowModel();
        WorkflowDef workflowDef = new WorkflowDef();
        workflow.setWorkflowDefinition(workflowDef);

        TaskMapperContext taskMapperContext =
                TaskMapperContext.newBuilder()
                        .withWorkflowDefinition(workflowDef)
                        .withWorkflowInstance(workflow)
                        .withTaskToSchedule(taskToSchedule)
                        .withTaskInput(new HashMap<>())
                        .withRetryCount(0)
                        .withRetryTaskId(retriedTaskId)
                        .withTaskId(taskId)
                        .build();

        // then
        expectedException.expect(TerminateWorkflowException.class);
        expectedException.expectMessage(
                String.format(
                        "Invalid task specified. Cannot find task by name %s in the task definitions",
                        taskToSchedule.getName()));
        // when
        userDefinedTaskMapper.getMappedTasks(taskMapperContext);
    }
}
