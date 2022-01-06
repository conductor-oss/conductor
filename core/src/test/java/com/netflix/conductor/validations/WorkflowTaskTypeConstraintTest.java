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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import javax.validation.executable.ExecutableValidator;

import org.apache.bval.jsr.ApacheValidationProvider;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.SubWorkflowParams;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.execution.tasks.Terminate;
import com.netflix.conductor.dao.MetadataDAO;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class WorkflowTaskTypeConstraintTest {

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
        ValidationContext.initialize(mockMetadataDao);
    }

    @Test
    public void testWorkflowTaskMissingReferenceName() {
        WorkflowTask workflowTask = createSampleWorkflowTask();
        workflowTask.setDynamicForkTasksParam("taskList");
        workflowTask.setDynamicForkTasksInputParamName("ForkTaskInputParam");
        workflowTask.setTaskReferenceName(null);

        Set<ConstraintViolation<Object>> result = validator.validate(workflowTask);
        assertEquals(1, result.size());

        assertEquals(
                result.iterator().next().getMessage(),
                "WorkflowTask taskReferenceName name cannot be empty or null");
    }

    @Test
    public void testWorkflowTaskTestSetType() throws NoSuchMethodException {
        WorkflowTask workflowTask = createSampleWorkflowTask();

        Method method = WorkflowTask.class.getMethod("setType", String.class);
        Object[] parameterValues = {""};

        ExecutableValidator executableValidator = validator.forExecutables();

        Set<ConstraintViolation<Object>> result =
                executableValidator.validateParameters(workflowTask, method, parameterValues);

        assertEquals(1, result.size());
        assertEquals(
                result.iterator().next().getMessage(), "WorkTask type cannot be null or empty");
    }

    @Test
    public void testWorkflowTaskTypeEvent() {
        WorkflowTask workflowTask = createSampleWorkflowTask();
        workflowTask.setType("EVENT");

        when(mockMetadataDao.getTaskDef(anyString())).thenReturn(new TaskDef());

        Set<ConstraintViolation<WorkflowTask>> result = validator.validate(workflowTask);
        assertEquals(1, result.size());
        assertEquals(
                result.iterator().next().getMessage(),
                "sink field is required for taskType: EVENT taskName: encode");
    }

    @Test
    public void testWorkflowTaskTypeDynamic() {
        WorkflowTask workflowTask = createSampleWorkflowTask();
        workflowTask.setType("DYNAMIC");

        when(mockMetadataDao.getTaskDef(anyString())).thenReturn(new TaskDef());

        Set<ConstraintViolation<WorkflowTask>> result = validator.validate(workflowTask);
        assertEquals(1, result.size());
        assertEquals(
                result.iterator().next().getMessage(),
                "dynamicTaskNameParam field is required for taskType: DYNAMIC taskName: encode");
    }

    @Test
    public void testWorkflowTaskTypeDecision() {
        WorkflowTask workflowTask = createSampleWorkflowTask();
        workflowTask.setType("DECISION");

        when(mockMetadataDao.getTaskDef(anyString())).thenReturn(new TaskDef());

        Set<ConstraintViolation<WorkflowTask>> result = validator.validate(workflowTask);
        assertEquals(2, result.size());

        List<String> validationErrors = new ArrayList<>();

        result.forEach(e -> validationErrors.add(e.getMessage()));

        assertTrue(
                validationErrors.contains(
                        "decisionCases should have atleast one task for taskType: DECISION taskName: encode"));
        assertTrue(
                validationErrors.contains(
                        "caseValueParam or caseExpression field is required for taskType: DECISION taskName: encode"));
    }

    @Test
    public void testWorkflowTaskTypeDoWhile() {
        WorkflowTask workflowTask = createSampleWorkflowTask();
        workflowTask.setType("DO_WHILE");

        when(mockMetadataDao.getTaskDef(anyString())).thenReturn(new TaskDef());

        Set<ConstraintViolation<WorkflowTask>> result = validator.validate(workflowTask);
        assertEquals(2, result.size());

        List<String> validationErrors = new ArrayList<>();

        result.forEach(e -> validationErrors.add(e.getMessage()));

        assertTrue(
                validationErrors.contains(
                        "loopExpression field is required for taskType: DO_WHILE taskName: encode"));
        assertTrue(
                validationErrors.contains(
                        "loopover field is required for taskType: DO_WHILE taskName: encode"));
    }

    @Test
    public void testWorkflowTaskTypeDoWhileWithSubWorkflow() {
        WorkflowTask workflowTask = createSampleWorkflowTask();
        workflowTask.setType("DO_WHILE");
        workflowTask.setLoopCondition("Test condition");
        WorkflowTask workflowTask2 = createSampleWorkflowTask();
        workflowTask2.setType("SUB_WORKFLOW");
        workflowTask.setLoopOver(Collections.singletonList(workflowTask2));

        when(mockMetadataDao.getTaskDef(anyString())).thenReturn(new TaskDef());

        Set<ConstraintViolation<WorkflowTask>> result = validator.validate(workflowTask);
        assertEquals(1, result.size());

        List<String> validationErrors = new ArrayList<>();

        result.forEach(e -> validationErrors.add(e.getMessage()));

        assertTrue(
                validationErrors.contains(
                        "SUB_WORKFLOW task inside loopover task: encode is not supported."));
    }

    @Test
    public void testWorkflowTaskTypeDecisionWithCaseParam() {
        WorkflowTask workflowTask = createSampleWorkflowTask();
        workflowTask.setType("DECISION");
        workflowTask.setCaseExpression("$.valueCheck == null ? 'true': 'false'");

        when(mockMetadataDao.getTaskDef(anyString())).thenReturn(new TaskDef());

        Set<ConstraintViolation<WorkflowTask>> result = validator.validate(workflowTask);
        assertEquals(1, result.size());

        List<String> validationErrors = new ArrayList<>();

        result.forEach(e -> validationErrors.add(e.getMessage()));

        assertTrue(
                validationErrors.contains(
                        "decisionCases should have atleast one task for taskType: DECISION taskName: encode"));
    }

    @Test
    public void testWorkflowTaskTypeForJoinDynamic() {
        WorkflowTask workflowTask = createSampleWorkflowTask();
        workflowTask.setType("FORK_JOIN_DYNAMIC");

        when(mockMetadataDao.getTaskDef(anyString())).thenReturn(new TaskDef());

        Set<ConstraintViolation<WorkflowTask>> result = validator.validate(workflowTask);
        assertEquals(2, result.size());

        List<String> validationErrors = new ArrayList<>();

        result.forEach(e -> validationErrors.add(e.getMessage()));

        assertTrue(
                validationErrors.contains(
                        "dynamicForkTasksInputParamName field is required for taskType: FORK_JOIN_DYNAMIC taskName: encode"));
        assertTrue(
                validationErrors.contains(
                        "dynamicForkTasksParam field is required for taskType: FORK_JOIN_DYNAMIC taskName: encode"));
    }

    @Test
    public void testWorkflowTaskTypeForJoinDynamicLegacy() {
        WorkflowTask workflowTask = createSampleWorkflowTask();
        workflowTask.setType("FORK_JOIN_DYNAMIC");
        workflowTask.setDynamicForkJoinTasksParam("taskList");

        when(mockMetadataDao.getTaskDef(anyString())).thenReturn(new TaskDef());

        Set<ConstraintViolation<WorkflowTask>> result = validator.validate(workflowTask);
        assertEquals(0, result.size());
    }

    @Test
    public void testWorkflowTaskTypeForJoinDynamicWithForJoinTaskParam() {
        WorkflowTask workflowTask = createSampleWorkflowTask();
        workflowTask.setType("FORK_JOIN_DYNAMIC");
        workflowTask.setDynamicForkJoinTasksParam("taskList");
        workflowTask.setDynamicForkTasksInputParamName("ForkTaskInputParam");

        when(mockMetadataDao.getTaskDef(anyString())).thenReturn(new TaskDef());

        Set<ConstraintViolation<WorkflowTask>> result = validator.validate(workflowTask);
        assertEquals(1, result.size());

        List<String> validationErrors = new ArrayList<>();

        result.forEach(e -> validationErrors.add(e.getMessage()));

        assertTrue(
                validationErrors.contains(
                        "dynamicForkJoinTasksParam or combination of dynamicForkTasksInputParamName and dynamicForkTasksParam cam be used for taskType: FORK_JOIN_DYNAMIC taskName: encode"));
    }

    @Test
    public void testWorkflowTaskTypeForJoinDynamicValid() {
        WorkflowTask workflowTask = createSampleWorkflowTask();
        workflowTask.setType("FORK_JOIN_DYNAMIC");
        workflowTask.setDynamicForkTasksParam("ForkTasksParam");
        workflowTask.setDynamicForkTasksInputParamName("ForkTaskInputParam");

        when(mockMetadataDao.getTaskDef(anyString())).thenReturn(new TaskDef());

        Set<ConstraintViolation<WorkflowTask>> result = validator.validate(workflowTask);
        assertEquals(0, result.size());
    }

    @Test
    public void testWorkflowTaskTypeForJoinDynamicWithForJoinTaskParamAndInputTaskParam() {
        WorkflowTask workflowTask = createSampleWorkflowTask();
        workflowTask.setType("FORK_JOIN_DYNAMIC");
        workflowTask.setDynamicForkJoinTasksParam("taskList");
        workflowTask.setDynamicForkTasksInputParamName("ForkTaskInputParam");
        workflowTask.setDynamicForkTasksParam("ForkTasksParam");

        when(mockMetadataDao.getTaskDef(anyString())).thenReturn(new TaskDef());

        Set<ConstraintViolation<WorkflowTask>> result = validator.validate(workflowTask);
        assertEquals(1, result.size());

        List<String> validationErrors = new ArrayList<>();

        result.forEach(e -> validationErrors.add(e.getMessage()));

        assertTrue(
                validationErrors.contains(
                        "dynamicForkJoinTasksParam or combination of dynamicForkTasksInputParamName and dynamicForkTasksParam cam be used for taskType: FORK_JOIN_DYNAMIC taskName: encode"));
    }

    @Test
    public void testWorkflowTaskTypeHTTP() {
        WorkflowTask workflowTask = createSampleWorkflowTask();
        workflowTask.setType("HTTP");
        workflowTask.getInputParameters().put("http_request", "http://www.netflix.com");

        when(mockMetadataDao.getTaskDef(anyString())).thenReturn(new TaskDef());

        Set<ConstraintViolation<WorkflowTask>> result = validator.validate(workflowTask);
        assertEquals(0, result.size());
    }

    @Test
    public void testWorkflowTaskTypeHTTPWithHttpParamMissing() {
        WorkflowTask workflowTask = createSampleWorkflowTask();
        workflowTask.setType("HTTP");

        when(mockMetadataDao.getTaskDef(anyString())).thenReturn(new TaskDef());

        Set<ConstraintViolation<WorkflowTask>> result = validator.validate(workflowTask);
        assertEquals(1, result.size());

        List<String> validationErrors = new ArrayList<>();

        result.forEach(e -> validationErrors.add(e.getMessage()));

        assertTrue(
                validationErrors.contains(
                        "inputParameters.http_request field is required for taskType: HTTP taskName: encode"));
    }

    @Test
    public void testWorkflowTaskTypeHTTPWithHttpParamInTaskDef() {
        WorkflowTask workflowTask = createSampleWorkflowTask();
        workflowTask.setType("HTTP");

        TaskDef taskDef = new TaskDef();
        taskDef.setName("encode");
        taskDef.getInputTemplate().put("http_request", "http://www.netflix.com");

        when(mockMetadataDao.getTaskDef(anyString())).thenReturn(taskDef);

        Set<ConstraintViolation<WorkflowTask>> result = validator.validate(workflowTask);
        assertEquals(0, result.size());
    }

    @Test
    public void testWorkflowTaskTypeHTTPWithHttpParamInTaskDefAndWorkflowTask() {
        WorkflowTask workflowTask = createSampleWorkflowTask();
        workflowTask.setType("HTTP");
        workflowTask.getInputParameters().put("http_request", "http://www.netflix.com");

        TaskDef taskDef = new TaskDef();
        taskDef.setName("encode");
        taskDef.getInputTemplate().put("http_request", "http://www.netflix.com");

        when(mockMetadataDao.getTaskDef(anyString())).thenReturn(taskDef);

        Set<ConstraintViolation<WorkflowTask>> result = validator.validate(workflowTask);
        assertEquals(0, result.size());
    }

    @Test
    public void testWorkflowTaskTypeFork() {
        WorkflowTask workflowTask = createSampleWorkflowTask();
        workflowTask.setType("FORK_JOIN");

        when(mockMetadataDao.getTaskDef(anyString())).thenReturn(new TaskDef());

        Set<ConstraintViolation<WorkflowTask>> result = validator.validate(workflowTask);
        assertEquals(1, result.size());

        List<String> validationErrors = new ArrayList<>();

        result.forEach(e -> validationErrors.add(e.getMessage()));

        assertTrue(
                validationErrors.contains(
                        "forkTasks should have atleast one task for taskType: FORK_JOIN taskName: encode"));
    }

    @Test
    public void testWorkflowTaskTypeSubworkflowMissingSubworkflowParam() {
        WorkflowTask workflowTask = createSampleWorkflowTask();
        workflowTask.setType("SUB_WORKFLOW");

        Set<ConstraintViolation<WorkflowTask>> result = validator.validate(workflowTask);
        assertEquals(1, result.size());

        List<String> validationErrors = new ArrayList<>();

        result.forEach(e -> validationErrors.add(e.getMessage()));

        assertTrue(
                validationErrors.contains(
                        "subWorkflowParam field is required for taskType: SUB_WORKFLOW taskName: encode"));
    }

    @Test
    public void testWorkflowTaskTypeSubworkflow() {
        WorkflowTask workflowTask = createSampleWorkflowTask();
        workflowTask.setType("SUB_WORKFLOW");

        SubWorkflowParams subWorkflowTask = new SubWorkflowParams();
        workflowTask.setSubWorkflowParam(subWorkflowTask);

        Set<ConstraintViolation<WorkflowTask>> result = validator.validate(workflowTask);
        assertEquals(2, result.size());

        List<String> validationErrors = new ArrayList<>();

        result.forEach(e -> validationErrors.add(e.getMessage()));

        assertTrue(validationErrors.contains("SubWorkflowParams name cannot be null"));
        assertTrue(validationErrors.contains("SubWorkflowParams name cannot be empty"));
    }

    @Test
    public void testWorkflowTaskTypeTerminateWithoutTerminationStatus() {
        WorkflowTask workflowTask = createSampleWorkflowTask();
        workflowTask.setType(TaskType.TASK_TYPE_TERMINATE);
        workflowTask.setName("terminate_task");

        workflowTask.setInputParameters(
                Collections.singletonMap(
                        Terminate.getTerminationWorkflowOutputParameter(), "blah"));
        List<String> validationErrors = getErrorMessages(workflowTask);

        Assert.assertEquals(1, validationErrors.size());
        Assert.assertEquals(
                "terminate task must have an terminationStatus parameter and must be set to COMPLETED or FAILED, taskName: terminate_task",
                validationErrors.get(0));
    }

    @Test
    public void testWorkflowTaskTypeTerminateWithInvalidStatus() {
        WorkflowTask workflowTask = createSampleWorkflowTask();
        workflowTask.setType(TaskType.TASK_TYPE_TERMINATE);
        workflowTask.setName("terminate_task");

        workflowTask.setInputParameters(
                Collections.singletonMap(Terminate.getTerminationStatusParameter(), "blah"));

        List<String> validationErrors = getErrorMessages(workflowTask);

        Assert.assertEquals(1, validationErrors.size());
        Assert.assertEquals(
                "terminate task must have an terminationStatus parameter and must be set to COMPLETED or FAILED, taskName: terminate_task",
                validationErrors.get(0));
    }

    @Test
    public void testWorkflowTaskTypeTerminateOptional() {
        WorkflowTask workflowTask = createSampleWorkflowTask();
        workflowTask.setType(TaskType.TASK_TYPE_TERMINATE);
        workflowTask.setName("terminate_task");

        workflowTask.setInputParameters(
                Collections.singletonMap(Terminate.getTerminationStatusParameter(), "COMPLETED"));
        workflowTask.setOptional(true);

        List<String> validationErrors = getErrorMessages(workflowTask);

        Assert.assertEquals(1, validationErrors.size());
        Assert.assertEquals(
                "terminate task cannot be optional, taskName: terminate_task",
                validationErrors.get(0));
    }

    @Test
    public void testWorkflowTaskTypeTerminateValid() {
        WorkflowTask workflowTask = createSampleWorkflowTask();
        workflowTask.setType(TaskType.TASK_TYPE_TERMINATE);
        workflowTask.setName("terminate_task");

        workflowTask.setInputParameters(
                Collections.singletonMap(Terminate.getTerminationStatusParameter(), "COMPLETED"));

        List<String> validationErrors = getErrorMessages(workflowTask);

        Assert.assertEquals(0, validationErrors.size());
    }

    @Test
    public void testWorkflowTaskTypeKafkaPublish() {
        WorkflowTask workflowTask = createSampleWorkflowTask();
        workflowTask.setType("KAFKA_PUBLISH");
        workflowTask.getInputParameters().put("kafka_request", "testInput");

        when(mockMetadataDao.getTaskDef(anyString())).thenReturn(new TaskDef());

        Set<ConstraintViolation<WorkflowTask>> result = validator.validate(workflowTask);
        assertEquals(0, result.size());
    }

    @Test
    public void testWorkflowTaskTypeKafkaPublishWithRequestParamMissing() {
        WorkflowTask workflowTask = createSampleWorkflowTask();
        workflowTask.setType("KAFKA_PUBLISH");

        when(mockMetadataDao.getTaskDef(anyString())).thenReturn(new TaskDef());

        Set<ConstraintViolation<WorkflowTask>> result = validator.validate(workflowTask);
        assertEquals(1, result.size());

        List<String> validationErrors = new ArrayList<>();

        result.forEach(e -> validationErrors.add(e.getMessage()));

        assertTrue(
                validationErrors.contains(
                        "inputParameters.kafka_request field is required for taskType: KAFKA_PUBLISH taskName: encode"));
    }

    @Test
    public void testWorkflowTaskTypeKafkaPublishWithKafkaParamInTaskDef() {
        WorkflowTask workflowTask = createSampleWorkflowTask();
        workflowTask.setType("KAFKA_PUBLISH");

        TaskDef taskDef = new TaskDef();
        taskDef.setName("encode");
        taskDef.getInputTemplate().put("kafka_request", "test_kafka_request");

        when(mockMetadataDao.getTaskDef(anyString())).thenReturn(taskDef);

        Set<ConstraintViolation<WorkflowTask>> result = validator.validate(workflowTask);
        assertEquals(0, result.size());
    }

    @Test
    public void testWorkflowTaskTypeKafkaPublishWithRequestParamInTaskDefAndWorkflowTask() {
        WorkflowTask workflowTask = createSampleWorkflowTask();
        workflowTask.setType("KAFKA_PUBLISH");
        workflowTask.getInputParameters().put("kafka_request", "http://www.netflix.com");

        TaskDef taskDef = new TaskDef();
        taskDef.setName("encode");
        taskDef.getInputTemplate().put("kafka_request", "test Kafka Request");

        when(mockMetadataDao.getTaskDef(anyString())).thenReturn(taskDef);

        Set<ConstraintViolation<WorkflowTask>> result = validator.validate(workflowTask);
        assertEquals(0, result.size());
    }

    @Test
    public void testWorkflowTaskTypeJSONJQTransform() {
        WorkflowTask workflowTask = createSampleWorkflowTask();
        workflowTask.setType("JSON_JQ_TRANSFORM");
        workflowTask.getInputParameters().put("queryExpression", ".");

        when(mockMetadataDao.getTaskDef(anyString())).thenReturn(new TaskDef());

        Set<ConstraintViolation<WorkflowTask>> result = validator.validate(workflowTask);
        assertEquals(0, result.size());
    }

    @Test
    public void testWorkflowTaskTypeJSONJQTransformWithQueryParamMissing() {
        WorkflowTask workflowTask = createSampleWorkflowTask();
        workflowTask.setType("JSON_JQ_TRANSFORM");

        when(mockMetadataDao.getTaskDef(anyString())).thenReturn(new TaskDef());

        Set<ConstraintViolation<WorkflowTask>> result = validator.validate(workflowTask);
        assertEquals(1, result.size());

        List<String> validationErrors = new ArrayList<>();

        result.forEach(e -> validationErrors.add(e.getMessage()));

        assertTrue(
                validationErrors.contains(
                        "inputParameters.queryExpression field is required for taskType: JSON_JQ_TRANSFORM taskName: encode"));
    }

    @Test
    public void testWorkflowTaskTypeJSONJQTransformWithQueryParamInTaskDef() {
        WorkflowTask workflowTask = createSampleWorkflowTask();
        workflowTask.setType("JSON_JQ_TRANSFORM");

        TaskDef taskDef = new TaskDef();
        taskDef.setName("encode");
        taskDef.getInputTemplate().put("queryExpression", ".");

        when(mockMetadataDao.getTaskDef(anyString())).thenReturn(taskDef);

        Set<ConstraintViolation<WorkflowTask>> result = validator.validate(workflowTask);
        assertEquals(0, result.size());
    }

    private List<String> getErrorMessages(WorkflowTask workflowTask) {
        Set<ConstraintViolation<WorkflowTask>> result = validator.validate(workflowTask);
        List<String> validationErrors = new ArrayList<>();
        result.forEach(e -> validationErrors.add(e.getMessage()));

        return validationErrors;
    }

    private WorkflowTask createSampleWorkflowTask() {
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setName("encode");
        workflowTask.setTaskReferenceName("encode");
        workflowTask.setType("FORK_JOIN_DYNAMIC");
        Map<String, Object> inputParam = new HashMap<>();
        inputParam.put("fileLocation", "${workflow.input.fileLocation}");
        workflowTask.setInputParameters(inputParam);
        return workflowTask;
    }
}
