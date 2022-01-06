/*
 * Copyright 2021 Netflix, Inc.
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
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.conductor.common.config.TestObjectMapperConfiguration;
import com.netflix.conductor.common.metadata.workflow.SubWorkflowParams;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@ContextConfiguration(classes = {TestObjectMapperConfiguration.class})
@RunWith(SpringRunner.class)
public class SubWorkflowParamsTest {

    @Autowired private ObjectMapper objectMapper;

    @Test
    public void testWorkflowTaskName() {
        SubWorkflowParams subWorkflowParams = new SubWorkflowParams(); // name is null
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
        SubWorkflowParams subWorkflowParams = new SubWorkflowParams();
        Map<String, String> taskToDomain = new HashMap<>();
        taskToDomain.put("unit", "test");
        subWorkflowParams.setTaskToDomain(taskToDomain);
        assertEquals(taskToDomain, subWorkflowParams.getTaskToDomain());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetWorkflowDefinition() {
        SubWorkflowParams subWorkflowParams = new SubWorkflowParams();
        subWorkflowParams.setName("dummy-name");
        subWorkflowParams.setWorkflowDefinition(new Object());
    }

    @Test
    public void testGetWorkflowDef() {
        SubWorkflowParams subWorkflowParams = new SubWorkflowParams();
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
        SubWorkflowParams subWorkflowParams = new SubWorkflowParams();
        subWorkflowParams.setName("dummy-name");
        WorkflowDef def = new WorkflowDef();
        def.setName("test_workflow");
        def.setVersion(1);
        WorkflowTask task = new WorkflowTask();
        task.setName("test_task");
        task.setTaskReferenceName("t1");
        def.getTasks().add(task);
        subWorkflowParams.setWorkflowDefinition(def);

        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        objectMapper.enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY);
        objectMapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

        String serializedParams =
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(subWorkflowParams);
        SubWorkflowParams deserializedParams =
                objectMapper.readValue(serializedParams, SubWorkflowParams.class);
        assertEquals(def, deserializedParams.getWorkflowDefinition());
        assertEquals(def, deserializedParams.getWorkflowDef());
    }
}
