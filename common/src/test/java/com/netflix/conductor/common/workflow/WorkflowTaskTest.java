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
package com.netflix.conductor.common.workflow;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class WorkflowTaskTest {

    @Test
    public void test() {
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setWorkflowTaskType(TaskType.DECISION);

        assertNotNull(workflowTask.getType());
        assertEquals(TaskType.DECISION.name(), workflowTask.getType());

        workflowTask = new WorkflowTask();
        workflowTask.setWorkflowTaskType(TaskType.SWITCH);

        assertNotNull(workflowTask.getType());
        assertEquals(TaskType.SWITCH.name(), workflowTask.getType());
    }

    @Test
    public void testOptional() {
        WorkflowTask task = new WorkflowTask();
        assertFalse(task.isOptional());

        task.setOptional(Boolean.FALSE);
        assertFalse(task.isOptional());

        task.setOptional(Boolean.TRUE);
        assertTrue(task.isOptional());
    }

    @Test
    public void testWorkflowTaskName() {
        WorkflowTask taskDef = new WorkflowTask(); // name is null
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        Validator validator = factory.getValidator();
        Set<ConstraintViolation<Object>> result = validator.validate(taskDef);
        assertEquals(2, result.size());

        List<String> validationErrors = new ArrayList<>();
        result.forEach(e -> validationErrors.add(e.getMessage()));

        assertTrue(validationErrors.contains("WorkflowTask name cannot be empty or null"));
        assertTrue(
                validationErrors.contains(
                        "WorkflowTask taskReferenceName name cannot be empty or null"));
    }

    @Test
    public void testCircuitBreakerConfig() {
        WorkflowTask task = new WorkflowTask();
        assertNull(task.getCircuitBreakerConfig());

        WorkflowTask.CircuitBreakerConfig config = new WorkflowTask.CircuitBreakerConfig();
        config.setFailureRateThreshold(50);
        config.setSlidingWindowSize(100);
        config.setMinimumNumberOfCalls(10);
        config.setWaitDurationInOpenState(60);
        config.setPermittedNumberOfCallsInHalfOpenState(5);

        task.setCircuitBreakerConfig(config);

        assertNotNull(task.getCircuitBreakerConfig());
        assertEquals(50, task.getCircuitBreakerConfig().getFailureRateThreshold());
        assertEquals(100, task.getCircuitBreakerConfig().getSlidingWindowSize());
        assertEquals(10, task.getCircuitBreakerConfig().getMinimumNumberOfCalls());
        assertEquals(60, task.getCircuitBreakerConfig().getWaitDurationInOpenState());
        assertEquals(5, task.getCircuitBreakerConfig().getPermittedNumberOfCallsInHalfOpenState());
    }

    @Test
    public void testBulkheadConfig() {
        WorkflowTask task = new WorkflowTask();
        assertNull(task.getBulkheadConfig());

        WorkflowTask.BulkheadConfig config = new WorkflowTask.BulkheadConfig();
        config.setMaxConcurrentCalls(20);
        config.setMaxWaitDuration(1000);

        task.setBulkheadConfig(config);

        assertNotNull(task.getBulkheadConfig());
        assertEquals(20, task.getBulkheadConfig().getMaxConcurrentCalls());
        assertEquals(1000, task.getBulkheadConfig().getMaxWaitDuration());
    }

    @Test
    public void testCircuitBreakerConfigBuilder() {
        WorkflowTask.CircuitBreakerConfig config = new WorkflowTask.CircuitBreakerConfig();

        // Test individual setters
        config.setFailureRateThreshold(75);
        assertEquals(75, config.getFailureRateThreshold());

        config.setSlidingWindowSize(200);
        assertEquals(200, config.getSlidingWindowSize());

        config.setMinimumNumberOfCalls(20);
        assertEquals(20, config.getMinimumNumberOfCalls());

        config.setWaitDurationInOpenState(30);
        assertEquals(30, config.getWaitDurationInOpenState());

        config.setPermittedNumberOfCallsInHalfOpenState(3);
        assertEquals(3, config.getPermittedNumberOfCallsInHalfOpenState());
    }

    @Test
    public void testBulkheadConfigBuilder() {
        WorkflowTask.BulkheadConfig config = new WorkflowTask.BulkheadConfig();

        // Test individual setters
        config.setMaxConcurrentCalls(50);
        assertEquals(50, config.getMaxConcurrentCalls());

        config.setMaxWaitDuration(2000);
        assertEquals(2000, config.getMaxWaitDuration());
    }
}
