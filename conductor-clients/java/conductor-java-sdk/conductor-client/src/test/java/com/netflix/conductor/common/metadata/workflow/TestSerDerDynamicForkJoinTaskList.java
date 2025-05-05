/*
 * Copyright 2020 Conductor Authors.
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
package com.netflix.conductor.common.metadata.workflow;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.netflix.conductor.util.JsonTemplateSerDeserResolverUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

public class TestSerDerDynamicForkJoinTaskList {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testSerializationDeserialization() throws Exception {
        // 1. Unmarshal SERVER_JSON to SDK POJO
        String SERVER_JSON = JsonTemplateSerDeserResolverUtil.getJsonString("DynamicForkJoinTaskList");
        DynamicForkJoinTaskList dynamicForkJoinTaskList = objectMapper.readValue(SERVER_JSON, DynamicForkJoinTaskList.class);

        // 2. Assert that the fields are all correctly populated
        List<DynamicForkJoinTask> tasks = dynamicForkJoinTaskList.getDynamicTasks();
        assertNotNull(tasks);
        assertFalse(tasks.isEmpty());

        // Check the first task from the list
        DynamicForkJoinTask task = tasks.get(0);
        assertNotNull(task);
        // Here you would check the task's fields based on what DynamicForkJoinTask looks like
        // For example:
        assertNotNull(task.getTaskName());
        assertNotNull(task.getWorkflowName());
        assertNotNull(task.getReferenceName());

        Map<String, Object> input = task.getInput();
        assertNotNull(input);
        assertFalse(input.isEmpty());

        // 3. Marshall this POJO to JSON again
        String serializedJson = objectMapper.writeValueAsString(dynamicForkJoinTaskList);

        // 4. Compare the JSONs - nothing should be lost
        assertEquals(objectMapper.readTree(SERVER_JSON), objectMapper.readTree(serializedJson));
    }
}