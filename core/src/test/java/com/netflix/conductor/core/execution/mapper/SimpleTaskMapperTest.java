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
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.exception.TerminateWorkflowException;
import com.netflix.conductor.core.utils.IDGenerator;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;

public class SimpleTaskMapperTest {

    private SimpleTaskMapper simpleTaskMapper;

    @Rule public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void setUp() {
        ParametersUtils parametersUtils = mock(ParametersUtils.class);
        simpleTaskMapper = new SimpleTaskMapper(parametersUtils);
    }

    @Test
    public void getMappedTasks() {

        WorkflowTask taskToSchedule = new WorkflowTask();
        taskToSchedule.setName("simple_task");
        taskToSchedule.setTaskDefinition(new TaskDef("simple_task"));

        String taskId = IDGenerator.generate();
        String retriedTaskId = IDGenerator.generate();

        WorkflowDef workflowDef = new WorkflowDef();
        WorkflowModel workflow = new WorkflowModel();
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

        List<TaskModel> mappedTasks = simpleTaskMapper.getMappedTasks(taskMapperContext);
        assertNotNull(mappedTasks);
        assertEquals(1, mappedTasks.size());
    }

    @Test
    public void getMappedTasksException() {

        // Given
        WorkflowTask taskToSchedule = new WorkflowTask();
        taskToSchedule.setName("simple_task");
        String taskId = IDGenerator.generate();
        String retriedTaskId = IDGenerator.generate();

        WorkflowDef workflowDef = new WorkflowDef();
        WorkflowModel workflow = new WorkflowModel();
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

        // then
        expectedException.expect(TerminateWorkflowException.class);
        expectedException.expectMessage(
                String.format(
                        "Invalid task. Task %s does not have a definition",
                        taskToSchedule.getName()));

        // when
        simpleTaskMapper.getMappedTasks(taskMapperContext);
    }
}
