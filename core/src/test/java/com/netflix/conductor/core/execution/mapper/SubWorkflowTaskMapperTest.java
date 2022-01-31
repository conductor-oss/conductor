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

import com.netflix.conductor.common.metadata.workflow.SubWorkflowParams;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.exception.TerminateWorkflowException;
import com.netflix.conductor.core.execution.DeciderService;
import com.netflix.conductor.core.utils.IDGenerator;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_SUB_WORKFLOW;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SubWorkflowTaskMapperTest {

    private SubWorkflowTaskMapper subWorkflowTaskMapper;
    private ParametersUtils parametersUtils;
    private DeciderService deciderService;

    @Rule public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void setUp() {
        parametersUtils = mock(ParametersUtils.class);
        MetadataDAO metadataDAO = mock(MetadataDAO.class);
        subWorkflowTaskMapper = new SubWorkflowTaskMapper(parametersUtils, metadataDAO);
        deciderService = mock(DeciderService.class);
    }

    @Test
    public void getMappedTasks() {
        // Given
        WorkflowDef workflowDef = new WorkflowDef();
        WorkflowModel workflowInstance = new WorkflowModel();
        workflowInstance.setWorkflowDefinition(workflowDef);
        WorkflowTask taskToSchedule = new WorkflowTask();
        SubWorkflowParams subWorkflowParams = new SubWorkflowParams();
        subWorkflowParams.setName("Foo");
        subWorkflowParams.setVersion(2);
        taskToSchedule.setSubWorkflowParam(subWorkflowParams);
        taskToSchedule.setStartDelay(30);
        Map<String, Object> taskInput = new HashMap<>();
        Map<String, String> taskToDomain =
                new HashMap<>() {
                    {
                        put("*", "unittest");
                    }
                };

        Map<String, Object> subWorkflowParamMap = new HashMap<>();
        subWorkflowParamMap.put("name", "FooWorkFlow");
        subWorkflowParamMap.put("version", 2);
        subWorkflowParamMap.put("taskToDomain", taskToDomain);
        when(parametersUtils.getTaskInputV2(anyMap(), any(WorkflowModel.class), any(), any()))
                .thenReturn(subWorkflowParamMap);

        // When
        TaskMapperContext taskMapperContext =
                TaskMapperContext.newBuilder()
                        .withWorkflowDefinition(workflowDef)
                        .withWorkflowInstance(workflowInstance)
                        .withTaskToSchedule(taskToSchedule)
                        .withTaskInput(taskInput)
                        .withRetryCount(0)
                        .withTaskId(IDGenerator.generate())
                        .withDeciderService(deciderService)
                        .build();

        List<TaskModel> mappedTasks = subWorkflowTaskMapper.getMappedTasks(taskMapperContext);

        // Then
        assertFalse(mappedTasks.isEmpty());
        assertEquals(1, mappedTasks.size());

        TaskModel subWorkFlowTask = mappedTasks.get(0);
        assertEquals(TaskModel.Status.SCHEDULED, subWorkFlowTask.getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, subWorkFlowTask.getTaskType());
        assertEquals(30, subWorkFlowTask.getCallbackAfterSeconds());
        assertEquals(taskToDomain, subWorkFlowTask.getInputData().get("subWorkflowTaskToDomain"));
    }

    @Test
    public void testTaskToDomain() {
        // Given
        WorkflowDef workflowDef = new WorkflowDef();
        WorkflowModel workflowInstance = new WorkflowModel();
        workflowInstance.setWorkflowDefinition(workflowDef);
        WorkflowTask taskToSchedule = new WorkflowTask();
        Map<String, String> taskToDomain =
                new HashMap<>() {
                    {
                        put("*", "unittest");
                    }
                };
        SubWorkflowParams subWorkflowParams = new SubWorkflowParams();
        subWorkflowParams.setName("Foo");
        subWorkflowParams.setVersion(2);
        subWorkflowParams.setTaskToDomain(taskToDomain);
        taskToSchedule.setSubWorkflowParam(subWorkflowParams);
        Map<String, Object> taskInput = new HashMap<>();

        Map<String, Object> subWorkflowParamMap = new HashMap<>();
        subWorkflowParamMap.put("name", "FooWorkFlow");
        subWorkflowParamMap.put("version", 2);

        when(parametersUtils.getTaskInputV2(anyMap(), any(WorkflowModel.class), any(), any()))
                .thenReturn(subWorkflowParamMap);

        // When
        TaskMapperContext taskMapperContext =
                TaskMapperContext.newBuilder()
                        .withWorkflowDefinition(workflowDef)
                        .withWorkflowInstance(workflowInstance)
                        .withTaskToSchedule(taskToSchedule)
                        .withTaskInput(taskInput)
                        .withRetryCount(0)
                        .withTaskId(IDGenerator.generate())
                        .withDeciderService(deciderService)
                        .build();

        List<TaskModel> mappedTasks = subWorkflowTaskMapper.getMappedTasks(taskMapperContext);

        // Then
        assertFalse(mappedTasks.isEmpty());
        assertEquals(1, mappedTasks.size());

        TaskModel subWorkFlowTask = mappedTasks.get(0);
        assertEquals(TaskModel.Status.SCHEDULED, subWorkFlowTask.getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, subWorkFlowTask.getTaskType());
    }

    @Test
    public void getSubWorkflowParams() {
        WorkflowTask workflowTask = new WorkflowTask();
        SubWorkflowParams subWorkflowParams = new SubWorkflowParams();
        subWorkflowParams.setName("Foo");
        subWorkflowParams.setVersion(2);
        workflowTask.setSubWorkflowParam(subWorkflowParams);

        assertEquals(subWorkflowParams, subWorkflowTaskMapper.getSubWorkflowParams(workflowTask));
    }

    @Test
    public void getExceptionWhenNoSubWorkflowParamsPassed() {
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setName("FooWorkFLow");

        expectedException.expect(TerminateWorkflowException.class);
        expectedException.expectMessage(
                String.format(
                        "Task %s is defined as sub-workflow and is missing subWorkflowParams. "
                                + "Please check the blueprint",
                        workflowTask.getName()));

        subWorkflowTaskMapper.getSubWorkflowParams(workflowTask);
    }
}
