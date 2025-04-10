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
package com.netflix.conductor.common.metadata.workflow;

import java.util.*;

import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.SchemaDef;

import static org.junit.jupiter.api.Assertions.*;

public class TestWorkflowDefPojoMethods {

    @Test
    public void testConstructor() {
        WorkflowDef workflowDef = new WorkflowDef();
        assertNotNull(workflowDef);
        assertEquals(1, workflowDef.getVersion());
        assertTrue(workflowDef.isRestartable());
        assertEquals(WorkflowDef.TimeoutPolicy.ALERT_ONLY, workflowDef.getTimeoutPolicy());
        assertTrue(workflowDef.isEnforceSchema());
    }

    @Test
    public void testSetAndGetName() {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("testWorkflow");
        assertEquals("testWorkflow", workflowDef.getName());
    }

    @Test
    public void testSetAndGetDescription() {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setDescription("Test Workflow Description");
        assertEquals("Test Workflow Description", workflowDef.getDescription());
    }

    @Test
    public void testSetAndGetTasks() {
        WorkflowDef workflowDef = new WorkflowDef();
        List<WorkflowTask> tasks = new ArrayList<>();
        WorkflowTask task = new WorkflowTask();
        task.setName("task1");
        tasks.add(task);

        workflowDef.setTasks(tasks);
        assertEquals(1, workflowDef.getTasks().size());
        assertEquals("task1", workflowDef.getTasks().get(0).getName());
    }

    @Test
    public void testSetAndGetInputParameters() {
        WorkflowDef workflowDef = new WorkflowDef();
        List<String> inputParams = Arrays.asList("param1", "param2");
        workflowDef.setInputParameters(inputParams);
        assertEquals(2, workflowDef.getInputParameters().size());
        assertTrue(workflowDef.getInputParameters().contains("param1"));
        assertTrue(workflowDef.getInputParameters().contains("param2"));
    }

    @Test
    public void testSetAndGetOutputParameters() {
        WorkflowDef workflowDef = new WorkflowDef();
        Map<String, Object> outputParams = new HashMap<>();
        outputParams.put("output1", "value1");

        workflowDef.setOutputParameters(outputParams);
        assertEquals(1, workflowDef.getOutputParameters().size());
        assertEquals("value1", workflowDef.getOutputParameters().get("output1"));
    }

    @Test
    public void testSetAndGetVersion() {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setVersion(2);
        assertEquals(2, workflowDef.getVersion());
    }

    @Test
    public void testSetAndGetFailureWorkflow() {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setFailureWorkflow("failureWorkflow");
        assertEquals("failureWorkflow", workflowDef.getFailureWorkflow());
    }

    @Test
    public void testSetAndGetRestartable() {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setRestartable(false);
        assertFalse(workflowDef.isRestartable());
    }

    @Test
    public void testSetAndGetSchemaVersion() {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setSchemaVersion(3);
        assertEquals(3, workflowDef.getSchemaVersion());
    }

    @Test
    public void testSetAndGetWorkflowStatusListenerEnabled() {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setWorkflowStatusListenerEnabled(true);
        assertTrue(workflowDef.isWorkflowStatusListenerEnabled());
    }

    @Test
    public void testSetAndGetOwnerEmail() {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setOwnerEmail("test@example.com");
        assertEquals("test@example.com", workflowDef.getOwnerEmail());
    }

    @Test
    public void testSetAndGetTimeoutPolicy() {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        assertEquals(WorkflowDef.TimeoutPolicy.TIME_OUT_WF, workflowDef.getTimeoutPolicy());
    }

    @Test
    public void testSetAndGetTimeoutSeconds() {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setTimeoutSeconds(120);
        assertEquals(120, workflowDef.getTimeoutSeconds());
    }

    @Test
    public void testSetAndGetVariables() {
        WorkflowDef workflowDef = new WorkflowDef();
        Map<String, Object> variables = new HashMap<>();
        variables.put("var1", "value1");

        workflowDef.setVariables(variables);
        assertEquals(1, workflowDef.getVariables().size());
        assertEquals("value1", workflowDef.getVariables().get("var1"));
    }

    @Test
    public void testSetAndGetInputTemplate() {
        WorkflowDef workflowDef = new WorkflowDef();
        Map<String, Object> inputTemplate = new HashMap<>();
        inputTemplate.put("template1", "value1");

        workflowDef.setInputTemplate(inputTemplate);
        assertEquals(1, workflowDef.getInputTemplate().size());
        assertEquals("value1", workflowDef.getInputTemplate().get("template1"));
    }

    @Test
    public void testKey() {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("testWorkflow");
        workflowDef.setVersion(2);

        assertEquals("testWorkflow.2", workflowDef.key());
    }

    @Test
    public void testGetKey() {
        assertEquals("workflow.3", WorkflowDef.getKey("workflow", 3));
    }

    @Test
    public void testSetAndGetWorkflowStatusListenerSink() {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setWorkflowStatusListenerSink("testSink");
        assertEquals("testSink", workflowDef.getWorkflowStatusListenerSink());
    }

    @Test
    public void testSetAndGetRateLimitConfig() {
        WorkflowDef workflowDef = new WorkflowDef();
        RateLimitConfig rateLimitConfig = new RateLimitConfig();

        workflowDef.setRateLimitConfig(rateLimitConfig);
        assertNotNull(workflowDef.getRateLimitConfig());
    }

    @Test
    public void testSetAndGetInputSchema() {
        WorkflowDef workflowDef = new WorkflowDef();
        SchemaDef schemaDef = new SchemaDef();

        workflowDef.setInputSchema(schemaDef);
        assertNotNull(workflowDef.getInputSchema());
    }

    @Test
    public void testSetAndGetOutputSchema() {
        WorkflowDef workflowDef = new WorkflowDef();
        SchemaDef schemaDef = new SchemaDef();

        workflowDef.setOutputSchema(schemaDef);
        assertNotNull(workflowDef.getOutputSchema());
    }

    @Test
    public void testSetAndGetEnforceSchema() {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setEnforceSchema(false);
        assertFalse(workflowDef.isEnforceSchema());
    }

    @Test
    public void testContainsType() {
        WorkflowDef workflowDef = new WorkflowDef();
        WorkflowTask task = new WorkflowTask();
        task.setType("HTTP");
        List<WorkflowTask> tasks = new ArrayList<>();
        tasks.add(task);

        workflowDef.setTasks(tasks);
        assertTrue(workflowDef.containsType("HTTP"));
        assertFalse(workflowDef.containsType("LAMBDA"));
    }

    @Test
    public void testGetNextTask() {
        WorkflowDef workflowDef = new WorkflowDef();

        WorkflowTask task1 = new WorkflowTask();
        task1.setName("task1");
        task1.setTaskReferenceName("task1Ref");

        WorkflowTask task2 = new WorkflowTask();
        task2.setName("task2");
        task2.setTaskReferenceName("task2Ref");

        List<WorkflowTask> tasks = new ArrayList<>();
        tasks.add(task1);
        tasks.add(task2);

        workflowDef.setTasks(tasks);

        WorkflowTask nextTask = workflowDef.getNextTask("task1Ref");
        assertNotNull(nextTask);
        assertEquals("task2", nextTask.getName());
    }

    @Test
    public void testGetTaskByRefName() {
        WorkflowDef workflowDef = new WorkflowDef();

        WorkflowTask task = new WorkflowTask();
        task.setName("task1");
        task.setTaskReferenceName("task1Ref");

        List<WorkflowTask> tasks = new ArrayList<>();
        tasks.add(task);

        workflowDef.setTasks(tasks);

        WorkflowTask foundTask = workflowDef.getTaskByRefName("task1Ref");
        assertNotNull(foundTask);
        assertEquals("task1", foundTask.getName());

        WorkflowTask notFoundTask = workflowDef.getTaskByRefName("nonExistingRef");
        assertNull(notFoundTask);
    }

    @Test
    public void testCollectTasks() {
        WorkflowDef workflowDef = new WorkflowDef();

        WorkflowTask task1 = new WorkflowTask();
        task1.setName("task1");
        task1.setTaskReferenceName("task1Ref");

        WorkflowTask task2 = new WorkflowTask();
        task2.setName("task2");
        task2.setTaskReferenceName("task2Ref");

        List<WorkflowTask> tasks = new ArrayList<>();
        tasks.add(task1);
        tasks.add(task2);

        workflowDef.setTasks(tasks);

        List<WorkflowTask> collectedTasks = workflowDef.collectTasks();
        assertNotNull(collectedTasks);
        assertTrue(collectedTasks.size() >= 2); // Might collect more tasks from nested tasks
    }

    @Test
    public void testEquals() {
        WorkflowDef workflowDef1 = new WorkflowDef();
        workflowDef1.setName("workflow");
        workflowDef1.setVersion(1);

        WorkflowDef workflowDef2 = new WorkflowDef();
        workflowDef2.setName("workflow");
        workflowDef2.setVersion(1);

        WorkflowDef workflowDef3 = new WorkflowDef();
        workflowDef3.setName("workflow");
        workflowDef3.setVersion(2);

        assertEquals(workflowDef1, workflowDef2);
        assertNotEquals(workflowDef1, workflowDef3);
        assertNotEquals(workflowDef1, null);
        assertNotEquals(workflowDef1, "not a workflow");
    }

    @Test
    public void testHashCode() {
        WorkflowDef workflowDef1 = new WorkflowDef();
        workflowDef1.setName("workflow");
        workflowDef1.setVersion(1);

        WorkflowDef workflowDef2 = new WorkflowDef();
        workflowDef2.setName("workflow");
        workflowDef2.setVersion(1);

        assertEquals(workflowDef1.hashCode(), workflowDef2.hashCode());
    }

    @Test
    public void testToString() {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("testWorkflow");

        String toString = workflowDef.toString();
        assertNotNull(toString);
        assertTrue(toString.contains("testWorkflow"));
    }
}