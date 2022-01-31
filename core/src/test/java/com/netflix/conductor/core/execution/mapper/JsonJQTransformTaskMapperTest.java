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
import org.junit.Test;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.utils.IDGenerator;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;

public class JsonJQTransformTaskMapperTest {

    private ParametersUtils parametersUtils;
    private MetadataDAO metadataDAO;

    @Before
    public void setUp() {
        parametersUtils = mock(ParametersUtils.class);
        metadataDAO = mock(MetadataDAO.class);
    }

    @Test
    public void getMappedTasks() {

        WorkflowTask taskToSchedule = new WorkflowTask();
        taskToSchedule.setName("json_jq_transform_task");
        taskToSchedule.setType(TaskType.JSON_JQ_TRANSFORM.name());
        taskToSchedule.setTaskDefinition(new TaskDef("json_jq_transform_task"));

        Map<String, Object> taskInput = new HashMap<>();
        taskInput.put("in1", new String[] {"a", "b"});
        taskInput.put("in2", new String[] {"c", "d"});
        taskInput.put("queryExpression", "{ out: (.in1 + .in2) }");
        taskToSchedule.setInputParameters(taskInput);

        String taskId = IDGenerator.generate();

        WorkflowDef workflowDef = new WorkflowDef();
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(workflowDef);

        TaskMapperContext taskMapperContext =
                TaskMapperContext.newBuilder()
                        .withWorkflowDefinition(workflowDef)
                        .withWorkflowInstance(workflow)
                        .withTaskDefinition(new TaskDef())
                        .withTaskToSchedule(taskToSchedule)
                        .withTaskInput(taskInput)
                        .withRetryCount(0)
                        .withTaskId(taskId)
                        .build();

        List<TaskModel> mappedTasks =
                new JsonJQTransformTaskMapper(parametersUtils, metadataDAO)
                        .getMappedTasks(taskMapperContext);

        assertEquals(1, mappedTasks.size());
        assertNotNull(mappedTasks);
        assertEquals(TaskType.JSON_JQ_TRANSFORM.name(), mappedTasks.get(0).getTaskType());
    }

    @Test
    public void getMappedTasks_WithoutTaskDef() {
        WorkflowTask taskToSchedule = new WorkflowTask();
        taskToSchedule.setName("json_jq_transform_task");
        taskToSchedule.setType(TaskType.JSON_JQ_TRANSFORM.name());

        Map<String, Object> taskInput = new HashMap<>();
        taskInput.put("in1", new String[] {"a", "b"});
        taskInput.put("in2", new String[] {"c", "d"});
        taskInput.put("queryExpression", "{ out: (.in1 + .in2) }");
        taskToSchedule.setInputParameters(taskInput);

        String taskId = IDGenerator.generate();

        WorkflowDef workflowDef = new WorkflowDef();
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(workflowDef);

        TaskMapperContext taskMapperContext =
                TaskMapperContext.newBuilder()
                        .withWorkflowDefinition(workflowDef)
                        .withWorkflowInstance(workflow)
                        .withTaskDefinition(null)
                        .withTaskToSchedule(taskToSchedule)
                        .withTaskInput(taskInput)
                        .withRetryCount(0)
                        .withTaskId(taskId)
                        .build();

        List<TaskModel> mappedTasks =
                new JsonJQTransformTaskMapper(parametersUtils, metadataDAO)
                        .getMappedTasks(taskMapperContext);

        assertEquals(1, mappedTasks.size());
        assertNotNull(mappedTasks);
        assertEquals(TaskType.JSON_JQ_TRANSFORM.name(), mappedTasks.get(0).getTaskType());
    }
}
