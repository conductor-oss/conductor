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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.SchemaDef;

import static org.junit.jupiter.api.Assertions.*;

class TestTaskDefPojoMethods {

    @Test
    void testDefaultConstructor() {
        TaskDef taskDef = new TaskDef();

        assertNull(taskDef.getName());
        assertNull(taskDef.getDescription());
        assertEquals(3, taskDef.getRetryCount());
        assertEquals(0, taskDef.getTimeoutSeconds());
        assertEquals(0, taskDef.getInputKeys().size());
        assertEquals(0, taskDef.getOutputKeys().size());
        assertEquals(TaskDef.TimeoutPolicy.TIME_OUT_WF, taskDef.getTimeoutPolicy());
        assertEquals(TaskDef.RetryLogic.FIXED, taskDef.getRetryLogic());
        assertEquals(60, taskDef.getRetryDelaySeconds());
        assertEquals(TaskDef.ONE_HOUR, taskDef.getResponseTimeoutSeconds());
        assertNull(taskDef.getConcurrentExecLimit());
        assertEquals(0, taskDef.getInputTemplate().size());
        assertEquals(0, taskDef.getRateLimitPerFrequency());
        assertEquals(1, taskDef.getRateLimitFrequencyInSeconds());
        assertNull(taskDef.getIsolationGroupId());
        assertNull(taskDef.getExecutionNameSpace());
        assertNull(taskDef.getOwnerEmail());
        assertNull(taskDef.getPollTimeoutSeconds());
        assertEquals(Integer.valueOf(1), taskDef.getBackoffScaleFactor());
        assertNull(taskDef.getBaseType());
        assertNull(taskDef.getInputSchema());
        assertNull(taskDef.getOutputSchema());
        assertFalse(taskDef.isEnforceSchema());
    }

    @Test
    void testNameConstructor() {
        String name = "testTask";
        TaskDef taskDef = new TaskDef(name);

        assertEquals(name, taskDef.getName());
    }

    @Test
    void testNameDescriptionConstructor() {
        String name = "testTask";
        String description = "Test task description";
        TaskDef taskDef = new TaskDef(name, description);

        assertEquals(name, taskDef.getName());
        assertEquals(description, taskDef.getDescription());
    }

    @Test
    void testNameDescriptionRetryTimeoutConstructor() {
        String name = "testTask";
        String description = "Test task description";
        int retryCount = 5;
        long timeoutSeconds = 120;

        TaskDef taskDef = new TaskDef(name, description, retryCount, timeoutSeconds);

        assertEquals(name, taskDef.getName());
        assertEquals(description, taskDef.getDescription());
        assertEquals(retryCount, taskDef.getRetryCount());
        assertEquals(timeoutSeconds, taskDef.getTimeoutSeconds());
    }

    @Test
    void testFullConstructor() {
        String name = "testTask";
        String description = "Test task description";
        String ownerEmail = "test@test.com";
        int retryCount = 5;
        long timeoutSeconds = 120;
        long responseTimeoutSeconds = 60;

        TaskDef taskDef = new TaskDef(name, description, ownerEmail, retryCount, timeoutSeconds, responseTimeoutSeconds);

        assertEquals(name, taskDef.getName());
        assertEquals(description, taskDef.getDescription());
        assertEquals(ownerEmail, taskDef.getOwnerEmail());
        assertEquals(retryCount, taskDef.getRetryCount());
        assertEquals(timeoutSeconds, taskDef.getTimeoutSeconds());
        assertEquals(responseTimeoutSeconds, taskDef.getResponseTimeoutSeconds());
    }

    @Test
    void testNameSetter() {
        TaskDef taskDef = new TaskDef();
        String name = "testTask";

        taskDef.setName(name);

        assertEquals(name, taskDef.getName());
    }

    @Test
    void testDescriptionSetter() {
        TaskDef taskDef = new TaskDef();
        String description = "Test task description";

        taskDef.setDescription(description);

        assertEquals(description, taskDef.getDescription());
    }

    @Test
    void testRetryCountSetter() {
        TaskDef taskDef = new TaskDef();
        int retryCount = 5;

        taskDef.setRetryCount(retryCount);

        assertEquals(retryCount, taskDef.getRetryCount());
    }

    @Test
    void testTimeoutSecondsSetter() {
        TaskDef taskDef = new TaskDef();
        long timeoutSeconds = 120;

        taskDef.setTimeoutSeconds(timeoutSeconds);

        assertEquals(timeoutSeconds, taskDef.getTimeoutSeconds());
    }

    @Test
    void testInputKeysSetter() {
        TaskDef taskDef = new TaskDef();
        var inputKeys = Arrays.asList("key1", "key2", "key3");

        taskDef.setInputKeys(inputKeys);

        assertEquals(inputKeys, taskDef.getInputKeys());
        assertEquals(3, taskDef.getInputKeys().size());
    }

    @Test
    void testOutputKeysSetter() {
        TaskDef taskDef = new TaskDef();
        var outputKeys = Arrays.asList("key1", "key2", "key3");

        taskDef.setOutputKeys(outputKeys);

        assertEquals(outputKeys, taskDef.getOutputKeys());
        assertEquals(3, taskDef.getOutputKeys().size());
    }

    @Test
    void testTimeoutPolicySetter() {
        TaskDef taskDef = new TaskDef();
        TaskDef.TimeoutPolicy timeoutPolicy = TaskDef.TimeoutPolicy.ALERT_ONLY;

        taskDef.setTimeoutPolicy(timeoutPolicy);

        assertEquals(timeoutPolicy, taskDef.getTimeoutPolicy());
    }

    @Test
    void testRetryLogicSetter() {
        TaskDef taskDef = new TaskDef();
        TaskDef.RetryLogic retryLogic = TaskDef.RetryLogic.EXPONENTIAL_BACKOFF;

        taskDef.setRetryLogic(retryLogic);

        assertEquals(retryLogic, taskDef.getRetryLogic());
    }

    @Test
    void testRetryDelaySecondsSetter() {
        TaskDef taskDef = new TaskDef();
        int retryDelaySeconds = 90;

        taskDef.setRetryDelaySeconds(retryDelaySeconds);

        assertEquals(retryDelaySeconds, taskDef.getRetryDelaySeconds());
    }

    @Test
    void testResponseTimeoutSecondsSetter() {
        TaskDef taskDef = new TaskDef();
        long responseTimeoutSeconds = 90;

        taskDef.setResponseTimeoutSeconds(responseTimeoutSeconds);

        assertEquals(responseTimeoutSeconds, taskDef.getResponseTimeoutSeconds());
    }

    @Test
    void testConcurrentExecLimitSetter() {
        TaskDef taskDef = new TaskDef();
        Integer concurrentExecLimit = 10;

        taskDef.setConcurrentExecLimit(concurrentExecLimit);

        assertEquals(concurrentExecLimit, taskDef.getConcurrentExecLimit());
    }

    @Test
    void testInputTemplateSetter() {
        TaskDef taskDef = new TaskDef();
        Map<String, Object> inputTemplate = new HashMap<>();
        inputTemplate.put("key1", "value1");
        inputTemplate.put("key2", 123);

        taskDef.setInputTemplate(inputTemplate);

        assertEquals(inputTemplate, taskDef.getInputTemplate());
        assertEquals(2, taskDef.getInputTemplate().size());
    }

    @Test
    void testRateLimitPerFrequencySetter() {
        TaskDef taskDef = new TaskDef();
        Integer rateLimitPerFrequency = 100;

        taskDef.setRateLimitPerFrequency(rateLimitPerFrequency);

        assertEquals(rateLimitPerFrequency, taskDef.getRateLimitPerFrequency());
    }

    @Test
    void testRateLimitPerFrequencyDefaultValue() {
        TaskDef taskDef = new TaskDef();

        assertEquals(0, taskDef.getRateLimitPerFrequency());
    }

    @Test
    void testRateLimitFrequencyInSecondsSetter() {
        TaskDef taskDef = new TaskDef();
        Integer rateLimitFrequencyInSeconds = 60;

        taskDef.setRateLimitFrequencyInSeconds(rateLimitFrequencyInSeconds);

        assertEquals(rateLimitFrequencyInSeconds, taskDef.getRateLimitFrequencyInSeconds());
    }

    @Test
    void testRateLimitFrequencyInSecondsDefaultValue() {
        TaskDef taskDef = new TaskDef();

        assertEquals(1, taskDef.getRateLimitFrequencyInSeconds());

        taskDef.setRateLimitFrequencyInSeconds(null);
        assertEquals(1, taskDef.getRateLimitFrequencyInSeconds());
    }

    @Test
    void testIsolationGroupIdSetter() {
        TaskDef taskDef = new TaskDef();
        String isolationGroupId = "group1";

        taskDef.setIsolationGroupId(isolationGroupId);

        assertEquals(isolationGroupId, taskDef.getIsolationGroupId());
    }

    @Test
    void testExecutionNameSpaceSetter() {
        TaskDef taskDef = new TaskDef();
        String executionNameSpace = "namespace1";

        taskDef.setExecutionNameSpace(executionNameSpace);

        assertEquals(executionNameSpace, taskDef.getExecutionNameSpace());
    }

    @Test
    void testOwnerEmailSetter() {
        TaskDef taskDef = new TaskDef();
        String ownerEmail = "test@test.com";

        taskDef.setOwnerEmail(ownerEmail);

        assertEquals(ownerEmail, taskDef.getOwnerEmail());
    }

    @Test
    void testPollTimeoutSecondsSetter() {
        TaskDef taskDef = new TaskDef();
        Integer pollTimeoutSeconds = 30;

        taskDef.setPollTimeoutSeconds(pollTimeoutSeconds);

        assertEquals(pollTimeoutSeconds, taskDef.getPollTimeoutSeconds());
    }

    @Test
    void testBackoffScaleFactorSetter() {
        TaskDef taskDef = new TaskDef();
        Integer backoffScaleFactor = 2;

        taskDef.setBackoffScaleFactor(backoffScaleFactor);

        assertEquals(backoffScaleFactor, taskDef.getBackoffScaleFactor());
    }

    @Test
    void testBaseTypeSetter() {
        TaskDef taskDef = new TaskDef();
        String baseType = "testBaseType";

        taskDef.setBaseType(baseType);

        assertEquals(baseType, taskDef.getBaseType());
    }

    @Test
    void testInputSchemaSetter() {
        TaskDef taskDef = new TaskDef();
        SchemaDef inputSchema = new SchemaDef();

        taskDef.setInputSchema(inputSchema);

        assertEquals(inputSchema, taskDef.getInputSchema());
    }

    @Test
    void testOutputSchemaSetter() {
        TaskDef taskDef = new TaskDef();
        SchemaDef outputSchema = new SchemaDef();

        taskDef.setOutputSchema(outputSchema);

        assertEquals(outputSchema, taskDef.getOutputSchema());
    }

    @Test
    void testEnforceSchemaSetter() {
        TaskDef taskDef = new TaskDef();
        boolean enforceSchema = true;

        taskDef.setEnforceSchema(enforceSchema);

        assertEquals(enforceSchema, taskDef.isEnforceSchema());
    }

    @Test
    void testConcurrencyLimit() {
        TaskDef taskDef = new TaskDef();

        // Default value should be 0 when concurrentExecLimit is null
        assertEquals(0, taskDef.concurrencyLimit());

        Integer concurrentExecLimit = 5;
        taskDef.setConcurrentExecLimit(concurrentExecLimit);
        assertEquals(concurrentExecLimit.intValue(), taskDef.concurrencyLimit());
    }

    @Test
    void testToString() {
        String name = "testTask";
        TaskDef taskDef = new TaskDef(name);

        assertEquals(name, taskDef.toString());
    }

    private final SchemaDef inputSchema = new SchemaDef();
    private final SchemaDef outputSchema = new SchemaDef();

    @Test
    void testEqualsAndHashCode() {
        TaskDef taskDef1 = createFullTaskDef("task1");
        TaskDef taskDef2 = createFullTaskDef("task1");
        TaskDef taskDef3 = createFullTaskDef("task2");

        // Test reflexivity
        assertEquals(taskDef1, taskDef1);
        assertEquals(taskDef1.hashCode(), taskDef1.hashCode());

        // Test symmetry
        assertEquals(taskDef1, taskDef2);
        assertEquals(taskDef2, taskDef1);
        assertEquals(taskDef1.hashCode(), taskDef2.hashCode());

        // Test with different values
        assertNotEquals(taskDef1, taskDef3);
        assertNotEquals(taskDef3, taskDef1);
        assertNotEquals(taskDef1.hashCode(), taskDef3.hashCode());

        // Test with null and different types
        assertNotEquals(taskDef1, null);
        assertNotEquals(taskDef1, "Not a TaskDef");
    }

    private TaskDef createFullTaskDef(String name) {
        TaskDef taskDef = new TaskDef();
        taskDef.setName(name);
        taskDef.setDescription("Description for " + name);
        taskDef.setRetryCount(3);
        taskDef.setTimeoutSeconds(120);
        taskDef.setInputKeys(Arrays.asList("input1", "input2"));
        taskDef.setOutputKeys(Arrays.asList("output1", "output2"));
        taskDef.setTimeoutPolicy(TaskDef.TimeoutPolicy.RETRY);
        taskDef.setRetryLogic(TaskDef.RetryLogic.EXPONENTIAL_BACKOFF);
        taskDef.setRetryDelaySeconds(30);
        taskDef.setResponseTimeoutSeconds(60);
        taskDef.setConcurrentExecLimit(5);

        Map<String, Object> inputTemplate = new HashMap<>();
        inputTemplate.put("key1", "value1");
        taskDef.setInputTemplate(inputTemplate);

        taskDef.setRateLimitPerFrequency(100);
        taskDef.setRateLimitFrequencyInSeconds(60);
        taskDef.setIsolationGroupId("group1");
        taskDef.setExecutionNameSpace("namespace1");
        taskDef.setOwnerEmail("test@test.com");
        taskDef.setPollTimeoutSeconds(30);
        taskDef.setBackoffScaleFactor(2);
        taskDef.setBaseType("baseType");

        // Use the shared schema instances
        taskDef.setInputSchema(inputSchema);
        taskDef.setOutputSchema(outputSchema);

        taskDef.setEnforceSchema(true);

        return taskDef;
    }
}