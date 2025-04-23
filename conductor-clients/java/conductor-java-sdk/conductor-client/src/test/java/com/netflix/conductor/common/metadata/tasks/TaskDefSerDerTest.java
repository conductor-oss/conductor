/*
 * Copyright 2025 Conductor Authors.
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
package com.netflix.conductor.common.metadata.tasks;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.tasks.TaskDef.RetryLogic;
import com.netflix.conductor.common.metadata.tasks.TaskDef.TimeoutPolicy;
import com.netflix.conductor.util.JsonTemplateSerDeserResolverUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

//todo add fields in the sdk pojo TaskDef - it will pass this test
class TaskDefSerDerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testSerializeDeserialize() throws Exception {
        // 1. Unmarshal SERVER_JSON to SDK POJO
        String SERVER_JSON = JsonTemplateSerDeserResolverUtil.getJsonString("TaskDef");
        TaskDef taskDef = objectMapper.readValue(SERVER_JSON, TaskDef.class);

        // 2. Assert that the fields are all correctly populated
        assertNotNull(taskDef);
        assertEquals("sample_name", taskDef.getName());
        assertEquals("sample_description", taskDef.getDescription());
        assertEquals(123, taskDef.getRetryCount());
        assertEquals(123L, taskDef.getTimeoutSeconds());

        // Verify lists
        List<String> inputKeys = taskDef.getInputKeys();
        assertNotNull(inputKeys);
        assertEquals(1, inputKeys.size());
        assertTrue(inputKeys.contains("sample_inputKeys") );

        List<String> outputKeys = taskDef.getOutputKeys();
        assertNotNull(outputKeys);
        assertEquals(1, outputKeys.size());
        assertTrue(outputKeys.contains("sample_outputKeys") );

        // Verify enums
        assertEquals(TimeoutPolicy.RETRY, taskDef.getTimeoutPolicy());
        assertEquals(RetryLogic.FIXED, taskDef.getRetryLogic());

        // Verify maps
        Map<String, Object> inputTemplate = taskDef.getInputTemplate();
        assertNotNull(inputTemplate);
        assertEquals(1, inputTemplate.size());
        assertTrue(inputTemplate.containsKey("sample_key"));

        // Verify other fields
        assertEquals(123, taskDef.getRetryDelaySeconds());
        assertEquals(123, taskDef.getResponseTimeoutSeconds());
        assertNotNull(taskDef.getConcurrentExecLimit());
        assertNotNull(taskDef.getRateLimitPerFrequency());
        assertNotNull(taskDef.getRateLimitFrequencyInSeconds());
        assertEquals("sample_isolationGroupId", taskDef.getIsolationGroupId());
        assertEquals("sample_executionNameSpace", taskDef.getExecutionNameSpace());
        assertEquals("sample_ownerEmail", taskDef.getOwnerEmail());
        assertNotNull(taskDef.getPollTimeoutSeconds());
        assertEquals(123, taskDef.getBackoffScaleFactor());
        assertEquals("sample_baseType", taskDef.getBaseType());

        // 3. Marshall this POJO to JSON again
        String serializedJson = objectMapper.writeValueAsString(taskDef);

        // 4. Compare the JSONs - nothing should be lost
        assertEquals(
                objectMapper.readTree(SERVER_JSON),
                objectMapper.readTree(serializedJson)
        );
    }
}