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
package com.netflix.conductor.validations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;

import org.apache.bval.jsr.ApacheValidationProvider;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.dao.MetadataDAO;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class WorkflowDefConstraintTest {

    private static Validator validator;
    private static ValidatorFactory validatorFactory;
    private MetadataDAO mockMetadataDao;

    @BeforeClass
    public static void init() {
        validatorFactory =
                Validation.byProvider(ApacheValidationProvider.class)
                        .configure()
                        .buildValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterClass
    public static void close() {
        validatorFactory.close();
    }

    @Before
    public void setUp() {
        mockMetadataDao = Mockito.mock(MetadataDAO.class);
        when(mockMetadataDao.getTaskDef(anyString())).thenReturn(new TaskDef());
        ValidationContext.initialize(mockMetadataDao);
    }

    @Test
    public void testWorkflowTaskName() {
        TaskDef taskDef = new TaskDef(); // name is null
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        Validator validator = factory.getValidator();
        Set<ConstraintViolation<Object>> result = validator.validate(taskDef);
        assertEquals(2, result.size());
    }

    @Test
    public void testWorkflowTaskSimple() {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("sampleWorkflow");
        workflowDef.setDescription("Sample workflow def");
        workflowDef.setOwnerEmail("sample@test.com");
        workflowDef.setVersion(2);

        WorkflowTask workflowTask_1 = new WorkflowTask();
        workflowTask_1.setName("task_1");
        workflowTask_1.setTaskReferenceName("task_1");
        workflowTask_1.setType(TaskType.TASK_TYPE_SIMPLE);

        Map<String, Object> inputParam = new HashMap<>();
        inputParam.put("fileLocation", "${workflow.input.fileLocation}");

        workflowTask_1.setInputParameters(inputParam);

        List<WorkflowTask> tasks = new ArrayList<>();
        tasks.add(workflowTask_1);

        workflowDef.setTasks(tasks);

        Set<ConstraintViolation<WorkflowDef>> result = validator.validate(workflowDef);
        assertEquals(0, result.size());
    }

    @Test
    /*Testcase to check inputParam is not valid
     */
    public void testWorkflowTaskInvalidInputParam() {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("sampleWorkflow");
        workflowDef.setDescription("Sample workflow def");
        workflowDef.setOwnerEmail("sample@test.com");
        workflowDef.setVersion(2);

        WorkflowTask workflowTask_1 = new WorkflowTask();
        workflowTask_1.setName("task_1");
        workflowTask_1.setTaskReferenceName("task_1");
        workflowTask_1.setType(TaskType.TASK_TYPE_SIMPLE);

        Map<String, Object> inputParam = new HashMap<>();
        inputParam.put("fileLocation", "${work.input.fileLocation}");

        workflowTask_1.setInputParameters(inputParam);

        List<WorkflowTask> tasks = new ArrayList<>();
        tasks.add(workflowTask_1);

        workflowDef.setTasks(tasks);

        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();

        when(mockMetadataDao.getTaskDef("work1")).thenReturn(new TaskDef());
        Set<ConstraintViolation<WorkflowDef>> result = validator.validate(workflowDef);
        assertEquals(1, result.size());
        assertEquals(
                result.iterator().next().getMessage(),
                "taskReferenceName: work for given task: task_1 input value: fileLocation of input parameter: ${work.input.fileLocation} is not defined in workflow definition.");
    }

    @Test
    public void testWorkflowTaskReferenceNameNotUnique() {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("sampleWorkflow");
        workflowDef.setDescription("Sample workflow def");
        workflowDef.setOwnerEmail("sample@test.com");
        workflowDef.setVersion(2);

        WorkflowTask workflowTask_1 = new WorkflowTask();
        workflowTask_1.setName("task_1");
        workflowTask_1.setTaskReferenceName("task_1");
        workflowTask_1.setType(TaskType.TASK_TYPE_SIMPLE);

        Map<String, Object> inputParam = new HashMap<>();
        inputParam.put("fileLocation", "${task_2.input.fileLocation}");

        workflowTask_1.setInputParameters(inputParam);

        WorkflowTask workflowTask_2 = new WorkflowTask();
        workflowTask_2.setName("task_2");
        workflowTask_2.setTaskReferenceName("task_1");
        workflowTask_2.setType(TaskType.TASK_TYPE_SIMPLE);

        workflowTask_2.setInputParameters(inputParam);

        List<WorkflowTask> tasks = new ArrayList<>();
        tasks.add(workflowTask_1);
        tasks.add(workflowTask_2);

        workflowDef.setTasks(tasks);

        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();

        when(mockMetadataDao.getTaskDef(anyString())).thenReturn(new TaskDef());
        Set<ConstraintViolation<WorkflowDef>> result = validator.validate(workflowDef);
        assertEquals(3, result.size());

        List<String> validationErrors = new ArrayList<>();

        result.forEach(e -> validationErrors.add(e.getMessage()));

        assertTrue(
                validationErrors.contains(
                        "taskReferenceName: task_2 for given task: task_2 input value: fileLocation of input parameter: ${task_2.input.fileLocation} is not defined in workflow definition."));
        assertTrue(
                validationErrors.contains(
                        "taskReferenceName: task_2 for given task: task_1 input value: fileLocation of input parameter: ${task_2.input.fileLocation} is not defined in workflow definition."));
        assertTrue(
                validationErrors.contains(
                        "taskReferenceName: task_1 should be unique across tasks for a given workflowDefinition: sampleWorkflow"));
    }
}
