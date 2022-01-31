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
package com.netflix.conductor.redis.dao;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.conductor.common.config.TestObjectMapperConfiguration;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.ExecutionDAOTest;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.redis.config.RedisProperties;
import com.netflix.conductor.redis.jedis.JedisMock;
import com.netflix.conductor.redis.jedis.JedisProxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import redis.clients.jedis.commands.JedisCommands;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ContextConfiguration(classes = {TestObjectMapperConfiguration.class})
@RunWith(SpringRunner.class)
public class RedisExecutionDAOTest extends ExecutionDAOTest {

    private RedisExecutionDAO executionDAO;

    @Autowired private ObjectMapper objectMapper;

    @Before
    public void init() {
        ConductorProperties conductorProperties = mock(ConductorProperties.class);
        RedisProperties properties = mock(RedisProperties.class);
        when(properties.getEventExecutionPersistenceTTL()).thenReturn(Duration.ofSeconds(5));
        JedisCommands jedisMock = new JedisMock();
        JedisProxy jedisProxy = new JedisProxy(jedisMock);

        executionDAO =
                new RedisExecutionDAO(jedisProxy, objectMapper, conductorProperties, properties);
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

    @Override
    protected ExecutionDAO getExecutionDAO() {
        return executionDAO;
    }
}
