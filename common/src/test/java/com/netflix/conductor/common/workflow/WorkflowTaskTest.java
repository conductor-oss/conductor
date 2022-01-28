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
package com.netflix.conductor.common.workflow;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;

import org.junit.Test;

import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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
}
