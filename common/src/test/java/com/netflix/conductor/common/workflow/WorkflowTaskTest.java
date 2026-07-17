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
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

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

    @Test
    public void testAgentMetadataJsonRoundTrip() throws Exception {
        WorkflowTask task = new WorkflowTask();
        task.setName("invoke_agent");
        task.setTaskReferenceName("invoke_agent_ref");
        task.setType(TaskType.AGENT.name());
        task.setMetadata(
                Map.of(
                        "agent",
                        Map.of(
                                "schemaVersion",
                                1,
                                "agentType",
                                "conductor",
                                "resolved",
                                true,
                                "conductor",
                                Map.of(
                                        "name",
                                        "researcher",
                                        "resolvedVersion",
                                        7,
                                        "agentConfig",
                                        Map.of(
                                                "model",
                                                "openai/gpt-5",
                                                "tools",
                                                List.of(Map.of("name", "search")))))));

        ObjectMapperProvider provider = new ObjectMapperProvider();
        String json = provider.getObjectMapper().writeValueAsString(task);
        WorkflowTask decoded = provider.getObjectMapper().readValue(json, WorkflowTask.class);

        assertEquals(task.getMetadata(), decoded.getMetadata());
    }

    @Test
    public void testWorkflowTaskWithoutMetadataRemainsValid() throws Exception {
        String json =
                "{\"name\":\"legacy\",\"taskReferenceName\":\"legacy_ref\",\"type\":\"SIMPLE\"}";

        WorkflowTask decoded =
                new ObjectMapperProvider().getObjectMapper().readValue(json, WorkflowTask.class);

        assertNotNull(decoded.getMetadata());
        assertTrue(decoded.getMetadata().isEmpty());

        WorkflowTask explicitlyNull =
                new ObjectMapperProvider()
                        .getObjectMapper()
                        .readValue(
                                "{\"name\":\"legacy\",\"taskReferenceName\":\"legacy_ref\",\"type\":\"SIMPLE\",\"metadata\":null}",
                                WorkflowTask.class);
        assertNotNull(explicitlyNull.getMetadata());
        assertTrue(explicitlyNull.getMetadata().isEmpty());
    }
}
