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
package com.netflix.conductor.common.tasks;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import com.netflix.conductor.common.metadata.tasks.TaskDef;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TaskDefTest {

    private Validator validator;

    @Before
    public void setup() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        this.validator = factory.getValidator();
    }

    @Test
    public void test() {
        String name = "test1";
        String description = "desc";
        int retryCount = 10;
        int timeout = 100;
        TaskDef def = new TaskDef(name, description, retryCount, timeout);
        assertEquals(36_00, def.getResponseTimeoutSeconds());
        assertEquals(name, def.getName());
        assertEquals(description, def.getDescription());
        assertEquals(retryCount, def.getRetryCount());
        assertEquals(timeout, def.getTimeoutSeconds());
    }

    @Test
    public void testTaskDef() {
        TaskDef taskDef = new TaskDef();
        taskDef.setName("task1");
        taskDef.setRetryCount(-1);
        taskDef.setTimeoutSeconds(1000);
        taskDef.setResponseTimeoutSeconds(1001);

        Set<ConstraintViolation<Object>> result = validator.validate(taskDef);
        assertEquals(3, result.size());

        List<String> validationErrors = new ArrayList<>();
        result.forEach(e -> validationErrors.add(e.getMessage()));

        assertTrue(
                validationErrors.contains(
                        "TaskDef: task1 responseTimeoutSeconds: 1001 must be less than timeoutSeconds: 1000"));
        assertTrue(validationErrors.contains("TaskDef retryCount: 0 must be >= 0"));
        assertTrue(validationErrors.contains("ownerEmail cannot be empty"));
    }

    @Test
    public void testTaskDefTotalTimeOutSeconds() {
        TaskDef taskDef = new TaskDef();
        taskDef.setName("test-task");
        taskDef.setRetryCount(1);
        taskDef.setTimeoutSeconds(1000);
        taskDef.setTotalTimeoutSeconds(900);
        taskDef.setResponseTimeoutSeconds(1);
        taskDef.setOwnerEmail("blah@gmail.com");

        Set<ConstraintViolation<Object>> result = validator.validate(taskDef);
        assertEquals(1, result.size());

        List<String> validationErrors = new ArrayList<>();
        result.forEach(e -> validationErrors.add(e.getMessage()));

        assertTrue(
                validationErrors.toString(),
                validationErrors.contains(
                        "TaskDef: test-task timeoutSeconds: 1000 must be less than or equal to totalTimeoutSeconds: 900"));
    }

    @Test
    public void testTaskDefInvalidEmail() {
        TaskDef taskDef = new TaskDef();
        taskDef.setName("test-task");
        taskDef.setRetryCount(1);
        taskDef.setTimeoutSeconds(1000);
        taskDef.setResponseTimeoutSeconds(1);

        Set<ConstraintViolation<Object>> result = validator.validate(taskDef);
        assertEquals(1, result.size());

        List<String> validationErrors = new ArrayList<>();
        result.forEach(e -> validationErrors.add(e.getMessage()));

        assertTrue(
                validationErrors.toString(),
                validationErrors.contains("ownerEmail cannot be empty"));
    }

    @Test
    public void testTaskDefValidEmail() {
        TaskDef taskDef = new TaskDef();
        taskDef.setName("test-task");
        taskDef.setRetryCount(1);
        taskDef.setTimeoutSeconds(1000);
        taskDef.setResponseTimeoutSeconds(1);
        taskDef.setOwnerEmail("owner@test.com");

        Set<ConstraintViolation<Object>> result = validator.validate(taskDef);
        assertEquals(0, result.size());
    }
}
