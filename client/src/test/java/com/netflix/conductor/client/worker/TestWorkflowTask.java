/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.conductor.client.worker;

import java.io.InputStream;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class TestWorkflowTask {

    private ObjectMapper objectMapper;

    @Before
    public void setup() {
        objectMapper = new ObjectMapperProvider().getObjectMapper();
    }

    @Test
    public void test() throws Exception {
        WorkflowTask task = new WorkflowTask();
        task.setType("Hello");
        task.setName("name");

        String json = objectMapper.writeValueAsString(task);

        WorkflowTask read = objectMapper.readValue(json, WorkflowTask.class);
        assertNotNull(read);
        assertEquals(task.getName(), read.getName());
        assertEquals(task.getType(), read.getType());

        task = new WorkflowTask();
        task.setWorkflowTaskType(TaskType.SUB_WORKFLOW);
        task.setName("name");

        json = objectMapper.writeValueAsString(task);

        read = objectMapper.readValue(json, WorkflowTask.class);
        assertNotNull(read);
        assertEquals(task.getName(), read.getName());
        assertEquals(task.getType(), read.getType());
        assertEquals(TaskType.SUB_WORKFLOW.name(), read.getType());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testObjectMapper() throws Exception {
        try (InputStream stream = TestWorkflowTask.class.getResourceAsStream("/tasks.json")) {
            List<Task> tasks = objectMapper.readValue(stream, List.class);
            assertNotNull(tasks);
            assertEquals(1, tasks.size());
        }
    }
}
