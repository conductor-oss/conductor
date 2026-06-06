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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.springframework.test.context.TestPropertySource;

import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@TestPropertySource(properties = "conductor.app.workflow.name-validation.enabled=true")
public class WorkflowDefValidatorTest {

    @Before
    public void before() {
        System.setProperty("NETFLIX_STACK", "test");
        System.setProperty("NETFLIX_ENVIRONMENT", "test");
        System.setProperty("TEST_ENV", "test");
    }

    @Test
    public void testWorkflowDefConstraints() {
        WorkflowDef workflowDef = new WorkflowDef(); // name is null
        workflowDef.setSchemaVersion(2);

        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        Validator validator = factory.getValidator();
        Set<ConstraintViolation<Object>> result = validator.validate(workflowDef);
        assertEquals(3, result.size());

        List<String> validationErrors = new ArrayList<>();
        result.forEach(e -> validationErrors.add(e.getMessage()));

        assertTrue(validationErrors.contains("WorkflowDef name cannot be null or empty"));
        assertTrue(validationErrors.contains("WorkflowTask list cannot be empty"));
        assertTrue(validationErrors.contains("ownerEmail cannot be empty"));
        // assertTrue(validationErrors.contains("workflowDef schemaVersion: 1 should be >= 2"));
    }

    @Test
    public void testWorkflowDefConstraintsWithMultipleEnvVariable() {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setSchemaVersion(2);
        workflowDef.setName("test_env");
        workflowDef.setOwnerEmail("owner@test.com");

        WorkflowTask workflowTask_1 = new WorkflowTask();
        workflowTask_1.setName("task_1");
        workflowTask_1.setTaskReferenceName("task_1");
        workflowTask_1.setType(TaskType.TASK_TYPE_SIMPLE);

        Map<String, Object> inputParam = new HashMap<>();
        inputParam.put("taskId", "${CPEWF_TASK_ID}");
        inputParam.put(
                "entryPoint",
                "${NETFLIX_ENVIRONMENT} ${NETFLIX_STACK} ${CPEWF_TASK_ID} ${workflow.input.status}");

        workflowTask_1.setInputParameters(inputParam);

        WorkflowTask workflowTask_2 = new WorkflowTask();
        workflowTask_2.setName("task_2");
        workflowTask_2.setTaskReferenceName("task_2");
        workflowTask_2.setType(TaskType.TASK_TYPE_SIMPLE);

        Map<String, Object> inputParam2 = new HashMap<>();
        inputParam2.put("env", inputParam);

        workflowTask_2.setInputParameters(inputParam2);

        List<WorkflowTask> tasks = new ArrayList<>();
        tasks.add(workflowTask_1);
        tasks.add(workflowTask_2);

        workflowDef.setTasks(tasks);

        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        Validator validator = factory.getValidator();
        Set<ConstraintViolation<Object>> result = validator.validate(workflowDef);
        assertEquals(0, result.size());
    }

    @Test
    public void testWorkflowDefConstraintsSingleEnvVariable() {
        WorkflowDef workflowDef = new WorkflowDef(); // name is null
        workflowDef.setSchemaVersion(2);
        workflowDef.setName("test_env");
        workflowDef.setOwnerEmail("owner@test.com");

        WorkflowTask workflowTask_1 = new WorkflowTask();
        workflowTask_1.setName("task_1");
        workflowTask_1.setTaskReferenceName("task_1");
        workflowTask_1.setType(TaskType.TASK_TYPE_SIMPLE);

        Map<String, Object> inputParam = new HashMap<>();
        inputParam.put("taskId", "${CPEWF_TASK_ID}");

        workflowTask_1.setInputParameters(inputParam);

        List<WorkflowTask> tasks = new ArrayList<>();
        tasks.add(workflowTask_1);

        workflowDef.setTasks(tasks);

        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        Validator validator = factory.getValidator();
        Set<ConstraintViolation<Object>> result = validator.validate(workflowDef);
        assertEquals(0, result.size());
    }

    @Test
    public void testWorkflowDefConstraintsDualEnvVariable() {
        WorkflowDef workflowDef = new WorkflowDef(); // name is null
        workflowDef.setSchemaVersion(2);
        workflowDef.setName("test_env");
        workflowDef.setOwnerEmail("owner@test.com");

        WorkflowTask workflowTask_1 = new WorkflowTask();
        workflowTask_1.setName("task_1");
        workflowTask_1.setTaskReferenceName("task_1");
        workflowTask_1.setType(TaskType.TASK_TYPE_SIMPLE);

        Map<String, Object> inputParam = new HashMap<>();
        inputParam.put("taskId", "${CPEWF_TASK_ID} ${NETFLIX_STACK}");

        workflowTask_1.setInputParameters(inputParam);

        List<WorkflowTask> tasks = new ArrayList<>();
        tasks.add(workflowTask_1);

        workflowDef.setTasks(tasks);

        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        Validator validator = factory.getValidator();
        Set<ConstraintViolation<Object>> result = validator.validate(workflowDef);
        assertEquals(0, result.size());
    }

    @Test
    public void testWorkflowDefConstraintsWithMapAsInputParam() {
        WorkflowDef workflowDef = new WorkflowDef(); // name is null
        workflowDef.setSchemaVersion(2);
        workflowDef.setName("test_env");
        workflowDef.setOwnerEmail("owner@test.com");

        WorkflowTask workflowTask_1 = new WorkflowTask();
        workflowTask_1.setName("task_1");
        workflowTask_1.setTaskReferenceName("task_1");
        workflowTask_1.setType(TaskType.TASK_TYPE_SIMPLE);

        Map<String, Object> inputParam = new HashMap<>();
        inputParam.put("taskId", "${CPEWF_TASK_ID} ${NETFLIX_STACK}");
        Map<String, Object> envInputParam = new HashMap<>();
        envInputParam.put("packageId", "${workflow.input.packageId}");
        envInputParam.put("taskId", "${CPEWF_TASK_ID}");
        envInputParam.put("NETFLIX_STACK", "${NETFLIX_STACK}");
        envInputParam.put("NETFLIX_ENVIRONMENT", "${NETFLIX_ENVIRONMENT}");

        inputParam.put("env", envInputParam);

        workflowTask_1.setInputParameters(inputParam);

        List<WorkflowTask> tasks = new ArrayList<>();
        tasks.add(workflowTask_1);

        workflowDef.setTasks(tasks);

        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        Validator validator = factory.getValidator();
        Set<ConstraintViolation<Object>> result = validator.validate(workflowDef);
        assertEquals(0, result.size());
    }

    @Test
    public void testWorkflowTaskInputParamInvalid() {
        WorkflowDef workflowDef = new WorkflowDef(); // name is null
        workflowDef.setSchemaVersion(2);
        workflowDef.setName("test_env");
        workflowDef.setOwnerEmail("owner@test.com");

        WorkflowTask workflowTask = new WorkflowTask(); // name is null
        workflowTask.setName("t1");
        workflowTask.setWorkflowTaskType(TaskType.SIMPLE);
        workflowTask.setTaskReferenceName("t1");

        Map<String, Object> map = new HashMap<>();
        map.put("blabla", "${workflow.input.Space Value}");
        workflowTask.setInputParameters(map);

        workflowDef.getTasks().add(workflowTask);

        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        Validator validator = factory.getValidator();
        Set<ConstraintViolation<Object>> result = validator.validate(workflowDef);
        assertEquals(1, result.size());

        List<String> validationErrors = new ArrayList<>();
        result.forEach(e -> validationErrors.add(e.getMessage()));

        assertTrue(
                validationErrors.contains(
                        "key: blabla input parameter value: workflow.input.Space Value is not valid"));
    }

    @Test
    public void testWorkflowTaskEmptyStringInputParamValue() {
        WorkflowDef workflowDef = new WorkflowDef(); // name is null
        workflowDef.setSchemaVersion(2);
        workflowDef.setName("test_env");
        workflowDef.setOwnerEmail("owner@test.com");

        WorkflowTask workflowTask = new WorkflowTask(); // name is null

        workflowTask.setName("t1");
        workflowTask.setWorkflowTaskType(TaskType.SIMPLE);
        workflowTask.setTaskReferenceName("t1");

        Map<String, Object> map = new HashMap<>();
        map.put("blabla", "");
        workflowTask.setInputParameters(map);

        workflowDef.getTasks().add(workflowTask);

        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        Validator validator = factory.getValidator();
        Set<ConstraintViolation<Object>> result = validator.validate(workflowDef);
        assertEquals(0, result.size());
    }

    @Test
    public void testWorkflowTasklistInputParamWithEmptyString() {
        WorkflowDef workflowDef = new WorkflowDef(); // name is null
        workflowDef.setSchemaVersion(2);
        workflowDef.setName("test_env");
        workflowDef.setOwnerEmail("owner@test.com");

        WorkflowTask workflowTask = new WorkflowTask(); // name is null

        workflowTask.setName("t1");
        workflowTask.setWorkflowTaskType(TaskType.SIMPLE);
        workflowTask.setTaskReferenceName("t1");

        Map<String, Object> map = new HashMap<>();
        map.put("blabla", "");
        map.put("foo", new String[] {""});
        workflowTask.setInputParameters(map);

        workflowDef.getTasks().add(workflowTask);

        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        Validator validator = factory.getValidator();
        Set<ConstraintViolation<Object>> result = validator.validate(workflowDef);
        assertEquals(0, result.size());
    }

    @Test
    public void testWorkflowSchemaVersion1() {
        WorkflowDef workflowDef = new WorkflowDef(); // name is null
        workflowDef.setSchemaVersion(3);
        workflowDef.setName("test_env");
        workflowDef.setOwnerEmail("owner@test.com");

        WorkflowTask workflowTask = new WorkflowTask();

        workflowTask.setName("t1");
        workflowTask.setWorkflowTaskType(TaskType.SIMPLE);
        workflowTask.setTaskReferenceName("t1");

        Map<String, Object> map = new HashMap<>();
        map.put("blabla", "");
        workflowTask.setInputParameters(map);

        workflowDef.getTasks().add(workflowTask);

        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        Validator validator = factory.getValidator();
        Set<ConstraintViolation<Object>> result = validator.validate(workflowDef);
        assertEquals(1, result.size());

        List<String> validationErrors = new ArrayList<>();
        result.forEach(e -> validationErrors.add(e.getMessage()));

        assertTrue(validationErrors.contains("workflowDef schemaVersion: 2 is only supported"));
    }

    @Test
    public void testWorkflowOwnerInvalidEmail() {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("test_env");
        workflowDef.setOwnerEmail("owner");

        WorkflowTask workflowTask = new WorkflowTask();

        workflowTask.setName("t1");
        workflowTask.setWorkflowTaskType(TaskType.SIMPLE);
        workflowTask.setTaskReferenceName("t1");

        Map<String, Object> map = new HashMap<>();
        map.put("blabla", "");
        workflowTask.setInputParameters(map);

        workflowDef.getTasks().add(workflowTask);

        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        Validator validator = factory.getValidator();
        Set<ConstraintViolation<Object>> result = validator.validate(workflowDef);
        assertEquals(0, result.size());
    }

    @Test
    public void testWorkflowOwnerValidEmail() {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("test_env");
        workflowDef.setOwnerEmail("owner@test.com");

        WorkflowTask workflowTask = new WorkflowTask();

        workflowTask.setName("t1");
        workflowTask.setWorkflowTaskType(TaskType.SIMPLE);
        workflowTask.setTaskReferenceName("t1");

        Map<String, Object> map = new HashMap<>();
        map.put("blabla", "");
        workflowTask.setInputParameters(map);

        workflowDef.getTasks().add(workflowTask);

        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        Validator validator = factory.getValidator();
        Set<ConstraintViolation<Object>> result = validator.validate(workflowDef);
        assertEquals(0, result.size());
    }

    // -------------------------------------------------------------------------
    // Default values
    // -------------------------------------------------------------------------

    @Test
    public void testDefaultValues() {
        WorkflowDef def = new WorkflowDef();
        assertEquals(1, def.getVersion());
        assertEquals(2, def.getSchemaVersion());
        assertTrue(def.isRestartable());
        assertFalse(def.isWorkflowStatusListenerEnabled());
        assertEquals(WorkflowDef.TimeoutPolicy.ALERT_ONLY, def.getTimeoutPolicy());
        assertNotNull(def.getTasks());
        assertTrue(def.getTasks().isEmpty());
        assertNotNull(def.getInputParameters());
        assertTrue(def.getInputParameters().isEmpty());
        assertNotNull(def.getOutputParameters());
        assertTrue(def.getOutputParameters().isEmpty());
        assertNotNull(def.getVariables());
        assertTrue(def.getVariables().isEmpty());
        assertNotNull(def.getInputTemplate());
        assertTrue(def.getInputTemplate().isEmpty());
        assertNotNull(def.getMetadata());
        assertTrue(def.getMetadata().isEmpty());
        assertNotNull(def.getMaskedFields());
        assertTrue(def.getMaskedFields().isEmpty());
    }

    // -------------------------------------------------------------------------
    // equals / hashCode — PK is (name, version)
    // -------------------------------------------------------------------------

    @Test
    public void testEqualsReflexive() {
        WorkflowDef def = new WorkflowDef();
        def.setName("wf");
        assertEquals(def, def);
    }

    @Test
    public void testEqualsSymmetric() {
        WorkflowDef a = new WorkflowDef();
        a.setName("wf");
        WorkflowDef b = new WorkflowDef();
        b.setName("wf");
        assertEquals(a, b);
        assertEquals(b, a);
    }

    @Test
    public void testEqualsNull() {
        WorkflowDef def = new WorkflowDef();
        def.setName("wf");
        assertNotEquals(null, def);
    }

    @Test
    public void testEqualsDifferentClass() {
        WorkflowDef def = new WorkflowDef();
        def.setName("wf");
        assertFalse(def.equals("wf"));
    }

    @Test
    public void testEqualsSameNameAndVersion() {
        WorkflowDef a = new WorkflowDef();
        a.setName("wf");
        a.setVersion(2);
        WorkflowDef b = new WorkflowDef();
        b.setName("wf");
        b.setVersion(2);
        // unrelated fields differ — must still be equal
        b.setDescription("something else");
        b.setOwnerEmail("owner@test.com");
        assertEquals(a, b);
    }

    @Test
    public void testEqualsDifferentName() {
        WorkflowDef a = new WorkflowDef();
        a.setName("wf-a");
        WorkflowDef b = new WorkflowDef();
        b.setName("wf-b");
        assertNotEquals(a, b);
    }

    @Test
    public void testEqualsDifferentVersion() {
        WorkflowDef a = new WorkflowDef();
        a.setName("wf");
        a.setVersion(1);
        WorkflowDef b = new WorkflowDef();
        b.setName("wf");
        b.setVersion(2);
        assertNotEquals(a, b);
    }

    @Test
    public void testEqualsBothDefaultVersion() {
        WorkflowDef a = new WorkflowDef();
        a.setName("wf");
        WorkflowDef b = new WorkflowDef();
        b.setName("wf");
        assertEquals(a, b);
    }

    @Test
    public void testHashCodeConsistency() {
        WorkflowDef def = new WorkflowDef();
        def.setName("wf");
        def.setVersion(3);
        assertEquals(def.hashCode(), def.hashCode());
    }

    @Test
    public void testHashCodeEqualObjects() {
        WorkflowDef a = new WorkflowDef();
        a.setName("wf");
        a.setVersion(2);
        WorkflowDef b = new WorkflowDef();
        b.setName("wf");
        b.setVersion(2);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void testHashSetDeduplication() {
        WorkflowDef a = new WorkflowDef();
        a.setName("wf");
        WorkflowDef b = new WorkflowDef();
        b.setName("wf");
        b.setDescription("different description");

        Set<WorkflowDef> set = new HashSet<>();
        set.add(a);
        set.add(b);
        assertEquals(1, set.size());
    }

    @Test
    public void testHashSetDistinctVersions() {
        WorkflowDef v1 = new WorkflowDef();
        v1.setName("wf");
        v1.setVersion(1);
        WorkflowDef v2 = new WorkflowDef();
        v2.setName("wf");
        v2.setVersion(2);

        Set<WorkflowDef> set = new HashSet<>();
        set.add(v1);
        set.add(v2);
        assertEquals(2, set.size());
    }

    // -------------------------------------------------------------------------
    // key() / getKey()
    // -------------------------------------------------------------------------

    @Test
    public void testKeyMethod() {
        WorkflowDef def = new WorkflowDef();
        def.setName("my-workflow");
        def.setVersion(3);
        assertEquals("my-workflow.3", def.key());
    }

    @Test
    public void testGetKeyStatic() {
        assertEquals("my-workflow.1", WorkflowDef.getKey("my-workflow", 1));
        assertEquals("my-workflow.5", WorkflowDef.getKey("my-workflow", 5));
    }

    @Test
    public void testKeyUsesDefaultVersion() {
        WorkflowDef def = new WorkflowDef();
        def.setName("my-workflow");
        assertEquals("my-workflow.1", def.key());
    }

    // -------------------------------------------------------------------------
    // toString
    // -------------------------------------------------------------------------

    @Test
    public void testToStringContainsNameAndVersion() {
        WorkflowDef def = new WorkflowDef();
        def.setName("my-workflow");
        def.setVersion(2);
        String s = def.toString();
        assertTrue(s.contains("my-workflow"));
        assertTrue(s.contains("2"));
    }
}
