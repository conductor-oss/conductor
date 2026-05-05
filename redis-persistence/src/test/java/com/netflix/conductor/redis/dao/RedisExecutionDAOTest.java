/*
 * Copyright 2022 Conductor Authors.
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
package com.netflix.conductor.redis.dao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.events.EventExecution;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.ExecutionDAOTest;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;
import com.netflix.conductor.redis.config.RedisProperties;
import com.netflix.conductor.redis.jedis.JedisProxy;
import com.netflix.conductor.redis.jedis.JedisStandalone;

import com.fasterxml.jackson.databind.ObjectMapper;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.*;

public class RedisExecutionDAOTest extends ExecutionDAOTest {

    private static final GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private static RedisExecutionDAO executionDAO;
    private static JedisPool jedisPool;

    @BeforeClass
    public static void startRedis() {
        redis.start();

        JedisPoolConfig config = new JedisPoolConfig();
        config.setMinIdle(2);
        config.setMaxTotal(10);

        jedisPool = new JedisPool(config, redis.getHost(), redis.getFirstMappedPort());
        JedisProxy jedisProxy = new JedisProxy(new JedisStandalone(jedisPool));
        ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();
        ConductorProperties conductorProperties = new ConductorProperties();
        RedisProperties redisProperties = new RedisProperties(conductorProperties);

        executionDAO =
                new RedisExecutionDAO(
                        jedisProxy, objectMapper, conductorProperties, redisProperties);
    }

    @AfterClass
    public static void stopRedis() {
        redis.stop();
    }

    @Before
    public void cleanUp() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.flushAll();
        }
    }

    @Override
    protected ExecutionDAO getExecutionDAO() {
        return executionDAO;
    }

    @Test
    public void testCorrelateTaskToWorkflowInDS() {
        String workflowId = "workflowId";
        String taskId = "taskId1";
        String taskDefName = "task1";

        TaskDef def = new TaskDef();
        def.setName("task1");
        def.setConcurrentExecLimit(1);

        TaskModel task = new TaskModel();
        task.setTaskId(taskId);
        task.setWorkflowInstanceId(workflowId);
        task.setReferenceTaskName("ref_name");
        task.setTaskDefName(taskDefName);
        task.setTaskType(taskDefName);
        task.setStatus(TaskModel.Status.IN_PROGRESS);
        List<TaskModel> tasks = executionDAO.createTasks(Collections.singletonList(task));
        assertNotNull(tasks);
        assertEquals(1, tasks.size());

        executionDAO.correlateTaskToWorkflowInDS(taskId, workflowId);
        tasks = executionDAO.getTasksForWorkflow(workflowId);
        assertNotNull(tasks);
        assertEquals(workflowId, tasks.get(0).getWorkflowInstanceId());
        assertEquals(taskId, tasks.get(0).getTaskId());
    }

    @Test
    public void testGetTasksForWorkflow() {
        WorkflowModel workflow = createRunningWorkflow();
        executionDAO.createWorkflow(workflow);

        int taskCount = 5;
        List<TaskModel> tasks = new ArrayList<>();
        for (int i = 0; i < taskCount; i++) {
            TaskModel task = new TaskModel();
            task.setTaskDefName("task_" + i);
            task.setStatus(TaskModel.Status.SCHEDULED);
            task.setTaskId(UUID.randomUUID().toString());
            task.setWorkflowInstanceId(workflow.getWorkflowId());
            task.setReferenceTaskName("ref_task_" + i);
            task.setSeq(i);
            tasks.add(task);
        }
        executionDAO.createTasks(tasks);

        List<TaskModel> retrievedTasks = executionDAO.getTasksForWorkflow(workflow.getWorkflowId());
        assertEquals(taskCount, retrievedTasks.size());

        tasks.sort(Comparator.comparing(TaskModel::getTaskId));
        retrievedTasks.sort(Comparator.comparing(TaskModel::getTaskId));

        for (int i = 0; i < taskCount; i++) {
            assertEquals(tasks.get(i).getTaskId(), retrievedTasks.get(i).getTaskId());
            assertEquals(tasks.get(i).getTaskDefName(), retrievedTasks.get(i).getTaskDefName());
        }
    }

    @Test
    public void testPendingWorkflowCount() {
        String workflowName = "count_workflow_" + UUID.randomUUID();
        int workflowCount = 5;

        for (int i = 0; i < workflowCount; i++) {
            WorkflowModel workflow = new WorkflowModel();
            workflow.setStatus(WorkflowModel.Status.RUNNING);
            workflow.setWorkflowId(UUID.randomUUID().toString());
            workflow.setCreateTime(System.currentTimeMillis());
            WorkflowDef def = new WorkflowDef();
            def.setName(workflowName);
            def.setVersion(1);
            workflow.setWorkflowDefinition(def);
            executionDAO.createWorkflow(workflow);
        }

        long count = executionDAO.getPendingWorkflowCount(workflowName);
        assertEquals(workflowCount, count);
    }

    @Test
    public void testEventExecutionCRUD() {
        String eventHandlerName = "test_handler";
        String eventName = "test_event";
        String messageId = "msg1";

        EventExecution ee = new EventExecution(messageId + "_0", messageId);
        ee.setName(eventHandlerName);
        ee.setEvent(eventName);
        ee.setStatus(EventExecution.Status.IN_PROGRESS);

        boolean added = executionDAO.addEventExecution(ee);
        assertTrue(added);

        List<EventExecution> executions =
                executionDAO.getEventExecutions(eventHandlerName, eventName, messageId, 1);
        assertEquals(1, executions.size());
        assertEquals(ee.getId(), executions.get(0).getId());
        assertEquals(EventExecution.Status.IN_PROGRESS, executions.get(0).getStatus());

        ee.setStatus(EventExecution.Status.COMPLETED);
        executionDAO.updateEventExecution(ee);

        executions = executionDAO.getEventExecutions(eventHandlerName, eventName, messageId, 1);
        assertEquals(1, executions.size());
        assertEquals(EventExecution.Status.COMPLETED, executions.get(0).getStatus());

        executionDAO.removeEventExecution(ee);
        executions = executionDAO.getEventExecutions(eventHandlerName, eventName, messageId, 1);
        assertEquals(0, executions.size());
    }

    @Test
    public void testRemoveWorkflow() {
        WorkflowModel workflow = createRunningWorkflow();
        executionDAO.createWorkflow(workflow);

        int taskCount = 3;
        List<TaskModel> tasks = new ArrayList<>();
        for (int i = 0; i < taskCount; i++) {
            TaskModel task = new TaskModel();
            task.setTaskDefName("task_" + i);
            task.setStatus(TaskModel.Status.COMPLETED);
            task.setTaskId(UUID.randomUUID().toString());
            task.setWorkflowInstanceId(workflow.getWorkflowId());
            task.setReferenceTaskName("ref_task_" + i);
            task.setSeq(i);
            tasks.add(task);
        }
        executionDAO.createTasks(tasks);

        assertNotNull(executionDAO.getWorkflow(workflow.getWorkflowId(), true));
        assertEquals(taskCount, executionDAO.getTasksForWorkflow(workflow.getWorkflowId()).size());

        boolean removed = executionDAO.removeWorkflow(workflow.getWorkflowId());
        assertTrue(removed);
        assertNull(executionDAO.getWorkflow(workflow.getWorkflowId(), false));
        assertEquals(0, executionDAO.getTasksForWorkflow(workflow.getWorkflowId()).size());
    }

    @Test
    public void testRemoveWorkflowWithExpiry() {
        WorkflowModel workflow = createRunningWorkflow();
        executionDAO.createWorkflow(workflow);

        TaskModel task = new TaskModel();
        task.setTaskDefName("expiry_task");
        task.setStatus(TaskModel.Status.COMPLETED);
        task.setTaskId(UUID.randomUUID().toString());
        task.setWorkflowInstanceId(workflow.getWorkflowId());
        task.setReferenceTaskName("ref_expiry_task");
        task.setSeq(0);
        executionDAO.createTasks(List.of(task));

        assertNotNull(executionDAO.getWorkflow(workflow.getWorkflowId(), false));

        executionDAO.removeWorkflowWithExpiry(workflow.getWorkflowId(), 1);

        // Workflow should still exist briefly (TTL not expired yet)
        // Wait for expiry
        await().atMost(3, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() -> executionDAO.getWorkflow(workflow.getWorkflowId(), false) == null);

        // After TTL, workflow should be gone
        assertNull(executionDAO.getWorkflow(workflow.getWorkflowId(), false));
    }

    @Test
    public void testGetRunningWorkflowIds() {
        String workflowName = "running_wf_" + UUID.randomUUID();
        int workflowCount = 3;
        List<String> workflowIds = new ArrayList<>();

        for (int i = 0; i < workflowCount; i++) {
            WorkflowModel workflow = new WorkflowModel();
            workflow.setStatus(WorkflowModel.Status.RUNNING);
            workflow.setWorkflowId(UUID.randomUUID().toString());
            workflow.setCreateTime(System.currentTimeMillis());
            WorkflowDef def = new WorkflowDef();
            def.setName(workflowName);
            def.setVersion(i + 1);
            workflow.setWorkflowDefinition(def);
            executionDAO.createWorkflow(workflow);
            workflowIds.add(workflow.getWorkflowId());
        }

        List<String> runningIds = executionDAO.getRunningWorkflowIds(workflowName, 0);
        assertEquals(workflowCount, runningIds.size());
        assertTrue(new HashSet<>(runningIds).containsAll(workflowIds));
    }

    @Test
    public void testWorkflowWithTasks() {
        WorkflowModel workflow = createRunningWorkflow();
        executionDAO.createWorkflow(workflow);

        int taskCount = 6;
        List<TaskModel> tasks = new ArrayList<>();
        for (int i = 0; i < taskCount; i++) {
            TaskModel task = new TaskModel();
            task.setTaskDefName("task_type");
            task.setStatus(TaskModel.Status.SCHEDULED);
            task.setTaskId(UUID.randomUUID().toString());
            task.setWorkflowInstanceId(workflow.getWorkflowId());
            task.setReferenceTaskName("task_" + i);
            task.setSeq(i);
            executionDAO.createTasks(List.of(task));
            tasks.add(task);
        }

        // Complete all tasks but the last one
        for (int i = 0; i < taskCount - 1; i++) {
            TaskModel task = tasks.get(i);
            task.setStatus(TaskModel.Status.COMPLETED);
            executionDAO.updateTask(task);
        }
        TaskModel lastTask = tasks.get(taskCount - 1);
        lastTask.setStatus(TaskModel.Status.FAILED);
        executionDAO.updateTask(lastTask);

        WorkflowModel found = executionDAO.getWorkflow(workflow.getWorkflowId(), true);
        List<TaskModel> foundTasks = executionDAO.getTasksForWorkflow(workflow.getWorkflowId());
        foundTasks.sort(Comparator.comparingInt(TaskModel::getSeq));

        assertNotNull(found);
        assertEquals(taskCount, found.getTasks().size());
        assertEquals(taskCount, foundTasks.size());
        assertEquals(WorkflowModel.Status.RUNNING, found.getStatus());
        assertEquals(TaskModel.Status.COMPLETED, foundTasks.get(0).getStatus());
        assertEquals(TaskModel.Status.FAILED, foundTasks.get(taskCount - 1).getStatus());

        // Mark workflow as failed
        workflow.setStatus(WorkflowModel.Status.FAILED);
        executionDAO.updateWorkflow(workflow);

        found = executionDAO.getWorkflow(workflow.getWorkflowId(), true);
        assertEquals(WorkflowModel.Status.FAILED, found.getStatus());
    }

    private WorkflowModel createRunningWorkflow() {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setStatus(WorkflowModel.Status.RUNNING);
        workflow.setWorkflowId(UUID.randomUUID().toString());
        workflow.setCreateTime(System.currentTimeMillis());
        WorkflowDef def = new WorkflowDef();
        def.setName("test_workflow");
        def.setVersion(1);
        workflow.setWorkflowDefinition(def);
        return workflow;
    }
}
