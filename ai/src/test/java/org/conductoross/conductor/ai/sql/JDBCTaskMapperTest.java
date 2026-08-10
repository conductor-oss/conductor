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
package org.conductoross.conductor.ai.sql;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.execution.mapper.TaskMapperContext;
import com.netflix.conductor.core.utils.IDGenerator;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static org.junit.jupiter.api.Assertions.*;

class JDBCTaskMapperTest {

    private JDBCTaskMapper mapper;
    private IDGenerator idGenerator;

    @BeforeEach
    void setUp() {
        mapper = new JDBCTaskMapper();
        idGenerator = new IDGenerator();
    }

    @Test
    void testGetTaskType() {
        assertEquals("JDBC", mapper.getTaskType());
    }

    @Test
    void testGetMappedTasks() {
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setName("jdbc_task");
        workflowTask.setType("JDBC");
        workflowTask.setTaskDefinition(new TaskDef("jdbc_task"));

        String taskId = idGenerator.generate();
        String retriedTaskId = idGenerator.generate();

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(new WorkflowDef());

        Map<String, Object> input = new HashMap<>();
        input.put("connectionId", "mydb");
        input.put("type", "SELECT");
        input.put("statement", "SELECT * FROM users");

        TaskMapperContext context =
                TaskMapperContext.newBuilder()
                        .withWorkflowModel(workflow)
                        .withTaskDefinition(new TaskDef("jdbc_task"))
                        .withWorkflowTask(workflowTask)
                        .withTaskInput(input)
                        .withRetryCount(0)
                        .withRetryTaskId(retriedTaskId)
                        .withTaskId(taskId)
                        .build();

        List<TaskModel> mappedTasks = mapper.getMappedTasks(context);

        assertEquals(1, mappedTasks.size());
        TaskModel task = mappedTasks.get(0);
        assertEquals("JDBC", task.getTaskType());
        assertEquals(TaskModel.Status.SCHEDULED, task.getStatus());
        assertEquals(taskId, task.getTaskId());
        assertEquals(retriedTaskId, task.getRetriedTaskId());
        assertEquals(0, task.getRetryCount());
        assertEquals("mydb", task.getInputData().get("connectionId"));
        assertEquals("SELECT", task.getInputData().get("type"));
        assertEquals("SELECT * FROM users", task.getInputData().get("statement"));
    }

    @Test
    void testGetMappedTasksWithNoTaskDefinition() {
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setName("jdbc_task");
        workflowTask.setType("JDBC");
        // No task definition set

        String taskId = idGenerator.generate();

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(new WorkflowDef());

        Map<String, Object> input = new HashMap<>();
        input.put("connectionId", "mydb");
        input.put("type", "UPDATE");
        input.put("statement", "UPDATE users SET active = true");

        TaskMapperContext context =
                TaskMapperContext.newBuilder()
                        .withWorkflowModel(workflow)
                        .withWorkflowTask(workflowTask)
                        .withTaskInput(input)
                        .withRetryCount(2)
                        .withTaskId(taskId)
                        .build();

        List<TaskModel> mappedTasks = mapper.getMappedTasks(context);

        assertEquals(1, mappedTasks.size());
        TaskModel task = mappedTasks.get(0);
        assertEquals("JDBC", task.getTaskType());
        assertEquals(TaskModel.Status.SCHEDULED, task.getStatus());
        assertEquals(2, task.getRetryCount());
    }

    @Test
    void testGetMappedTasksWithIntegrationName() {
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setName("jdbc_task");
        workflowTask.setType("JDBC");
        workflowTask.setTaskDefinition(new TaskDef("jdbc_task"));

        String taskId = idGenerator.generate();

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(new WorkflowDef());

        Map<String, Object> input = new HashMap<>();
        input.put("integrationName", "prod-mysql");
        input.put("type", "SELECT");
        input.put("statement", "SELECT 1");

        TaskMapperContext context =
                TaskMapperContext.newBuilder()
                        .withWorkflowModel(workflow)
                        .withTaskDefinition(new TaskDef("jdbc_task"))
                        .withWorkflowTask(workflowTask)
                        .withTaskInput(input)
                        .withRetryCount(0)
                        .withTaskId(taskId)
                        .build();

        List<TaskModel> mappedTasks = mapper.getMappedTasks(context);

        assertEquals(1, mappedTasks.size());
        TaskModel task = mappedTasks.get(0);
        assertEquals("JDBC", task.getTaskType());
        assertEquals(TaskModel.Status.SCHEDULED, task.getStatus());
        assertEquals("prod-mysql", task.getInputData().get("integrationName"));
    }

    @Test
    void testTaskDefPropertiesPropagated() {
        TaskDef taskDef = new TaskDef("jdbc_task");
        taskDef.setResponseTimeoutSeconds(30);
        taskDef.setRateLimitPerFrequency(10);
        taskDef.setRateLimitFrequencyInSeconds(60);

        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setName("jdbc_task");
        workflowTask.setType("JDBC");
        workflowTask.setTaskDefinition(taskDef);
        workflowTask.setStartDelay(5);

        String taskId = idGenerator.generate();

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(new WorkflowDef());

        TaskMapperContext context =
                TaskMapperContext.newBuilder()
                        .withWorkflowModel(workflow)
                        .withTaskDefinition(taskDef)
                        .withWorkflowTask(workflowTask)
                        .withTaskInput(new HashMap<>())
                        .withRetryCount(0)
                        .withTaskId(taskId)
                        .build();

        List<TaskModel> mappedTasks = mapper.getMappedTasks(context);

        TaskModel task = mappedTasks.get(0);
        assertEquals(30, task.getResponseTimeoutSeconds());
        assertEquals(10, task.getRateLimitPerFrequency());
        assertEquals(60, task.getRateLimitFrequencyInSeconds());
        assertEquals(5, task.getCallbackAfterSeconds());
        assertEquals(5, task.getStartDelayInSeconds());
    }
}
