package com.netflix.conductor.common.workflow;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.netflix.conductor.common.metadata.workflow.SubWorkflowParams;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.utils.JsonMapperProvider;
import org.junit.Test;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SubWorkflowParamsTest {

    @Test
    public void testWorkflowTaskName() {
        SubWorkflowParams subWorkflowParams = new SubWorkflowParams();//name is null
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        Validator validator = factory.getValidator();

        Set<ConstraintViolation<Object>> result = validator.validate(subWorkflowParams);
        assertEquals(2, result.size());

        List<String> validationErrors = new ArrayList<>();
        result.forEach(e -> validationErrors.add(e.getMessage()));

        assertTrue(validationErrors.contains("SubWorkflowParams name cannot be null"));
        assertTrue(validationErrors.contains("SubWorkflowParams name cannot be empty"));
    }

    @Test
    public void testWorkflowSetTaskToDomain() {
        SubWorkflowParams subWorkflowParams = new SubWorkflowParams();//name is null
        Map<String, String> taskToDomain = new HashMap<>();
        taskToDomain.put("unit", "test");
        subWorkflowParams.setTaskToDomain(taskToDomain);
        assertEquals(taskToDomain, subWorkflowParams.getTaskToDomain());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetWorkflowDefinition() {
        SubWorkflowParams subWorkflowParams = new SubWorkflowParams();//name is null
        subWorkflowParams.setName("dummy-name");
        subWorkflowParams.setWorkflowDefinition(new Object());
    }

    @Test
    public void testGetWorkflowDef() {
        SubWorkflowParams subWorkflowParams = new SubWorkflowParams();//name is null
        subWorkflowParams.setName("dummy-name");
        WorkflowDef def = new WorkflowDef();
        def.setName("test_workflow");
        def.setVersion(1);
        WorkflowTask task = new WorkflowTask();
        task.setName("test_task");
        task.setTaskReferenceName("t1");
        def.getTasks().add(task);
        subWorkflowParams.setWorkflowDefinition(def);
        assertEquals(def, subWorkflowParams.getWorkflowDefinition());
        assertEquals(def, subWorkflowParams.getWorkflowDef());
    }

    @Test
    public void testWorkflowDefJson() throws Exception {
        SubWorkflowParams subWorkflowParams = new SubWorkflowParams();//name is null
        subWorkflowParams.setName("dummy-name");
        WorkflowDef def = new WorkflowDef();
        def.setName("test_workflow");
        def.setVersion(1);
        WorkflowTask task = new WorkflowTask();
        task.setName("test_task");
        task.setTaskReferenceName("t1");
        def.getTasks().add(task);
        subWorkflowParams.setWorkflowDefinition(def);

        String expected = "{\n" +
            "  \"name\" : \"test_workflow\",\n" +
            "  \"version\" : 1,\n" +
            "  \"workflowDefinition\" : {\n" +
            "    \"inputParameters\" : [ ],\n" +
            "    \"name\" : \"test_workflow\",\n" +
            "    \"outputParameters\" : { },\n" +
            "    \"restartable\" : true,\n" +
            "    \"schemaVersion\" : 2,\n" +
            "    \"tasks\" : [ {\n" +
            "      \"asyncComplete\" : false,\n" +
            "      \"decisionCases\" : { },\n" +
            "      \"defaultCase\" : [ ],\n" +
            "      \"defaultExclusiveJoinTask\" : [ ],\n" +
            "      \"forkTasks\" : [ ],\n" +
            "      \"inputParameters\" : { },\n" +
            "      \"joinOn\" : [ ],\n" +
            "      \"loopOver\" : [ ],\n" +
            "      \"name\" : \"test_task\",\n" +
            "      \"optional\" : false,\n" +
            "      \"startDelay\" : 0,\n" +
            "      \"taskReferenceName\" : \"t1\",\n" +
            "      \"type\" : \"SIMPLE\"\n" +
            "    } ],\n" +
            "    \"timeoutPolicy\" : \"ALERT_ONLY\",\n" +
            "    \"timeoutSeconds\" : 0,\n" +
            "    \"variables\" : { },\n" +
            "    \"version\" : 1,\n" +
            "    \"workflowStatusListenerEnabled\" : false\n" +
            "  }\n" +
            "}";
        ObjectMapper objectMapper = new JsonMapperProvider().get();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        objectMapper.enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY);
        objectMapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

        assertEquals(expected, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(subWorkflowParams));

        SubWorkflowParams actualSubWorkflowParam = objectMapper.readValue(expected, SubWorkflowParams.class);
        assertEquals(subWorkflowParams, actualSubWorkflowParam);
        assertEquals(def, actualSubWorkflowParam.getWorkflowDefinition());
        assertEquals(def, actualSubWorkflowParam.getWorkflowDef());
    }
}
