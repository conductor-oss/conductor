/*
 * Copyright 2026 Conductor Authors.
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
package org.conductoross.conductor.service;

import java.util.List;
import java.util.Map;

import org.conductoross.conductor.common.JsonSchemaValidator;
import org.conductoross.conductor.core.exception.SchemaValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.SchemaDef;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.metrics.Monitors;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What an operator sees after turning enforcement on. The tag values are the point: a counter whose
 * boundary or schema name is wrong reads as a different event, and nothing else in the build would
 * notice.
 */
class SchemaMetricsTest {

    private SimpleMeterRegistry registry;
    private SchemaService schemaService;
    private SchemaEnforcement enforcement;

    @BeforeEach
    void setUp() {
        // Meters are global, so assertions are made against a registry added here and read by
        // name: a fresh registry sees only what this test records.
        registry = new SimpleMeterRegistry();
        Monitors.addMeterRegistry(registry);

        schemaService =
                new SchemaServiceImpl(
                        new InMemorySchemaDAO(),
                        new SchemaCacheProperties(),
                        new JsonSchemaValidator(new ObjectMapperProvider().getObjectMapper()));
        SchemaValidationProperties properties = new SchemaValidationProperties();
        properties.setEnabled(true);
        enforcement = new SchemaEnforcement(schemaService, properties);
    }

    private static SchemaDef requiresName() {
        SchemaDef def = new SchemaDef();
        def.setName("person");
        def.setVersion(1);
        def.setType(SchemaDef.Type.JSON);
        def.setData(
                Map.of(
                        "$schema", "https://json-schema.org/draft/2020-12/schema",
                        "type", "object",
                        "required", List.of("name")));
        return def;
    }

    @Test
    void aRejectedWorkflowInputIsCountedAgainstItsBoundaryAndSchema() {
        WorkflowDef def = new WorkflowDef();
        def.setName("order");
        def.setVersion(1);
        def.setInputSchema(requiresName());
        def.setEnforceSchema(true);

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(def);
        workflow.setWorkflowId("wf-1");
        workflow.setInput(Map.of());

        assertThrows(
                SchemaValidationException.class, () -> enforcement.validateWorkflowInput(workflow));

        assertEquals(
                1.0,
                registry.get("schema_validation_failure")
                        .tag("boundary", "workflowInput")
                        .tag("schemaName", "person")
                        .tag("workflowName", "order")
                        .counter()
                        .count(),
                0.001);
        assertTrue(
                registry.get("schema_validation").tag("boundary", "workflowInput").timer().count()
                        >= 1,
                "validating a payload is timed even when it is rejected");
    }

    @Test
    void aRejectedTaskInputIsCountedAgainstItsOwnBoundary() {
        TaskDef taskDef = new TaskDef();
        taskDef.setName("charge");
        taskDef.setInputSchema(requiresName());
        taskDef.setEnforceSchema(true);

        TaskModel task = new TaskModel();
        task.setTaskId("task-1");
        task.setReferenceTaskName("charge_ref");
        task.setWorkflowType("order");
        task.setInputData(Map.of());

        assertThrows(
                SchemaValidationException.class,
                () -> enforcement.validateTaskInput(task, () -> taskDef));

        assertEquals(
                1.0,
                registry.get("schema_validation_failure")
                        .tag("boundary", "taskInput")
                        .tag("schemaName", "person")
                        .tag("workflowName", "order")
                        .counter()
                        .count(),
                0.001);
    }

    @Test
    void aReferenceToAnUnregisteredSchemaIsCountedAsAMissRatherThanAFailure() {
        SchemaDef reference = new SchemaDef();
        reference.setName("absent");
        reference.setVersion(3);
        reference.setType(SchemaDef.Type.JSON);

        assertThrows(
                SchemaValidationException.class,
                () -> schemaService.validate(reference, Map.of("name", "ada")));

        assertEquals(
                1.0,
                registry.get("schema_registry_miss")
                        .tag("schemaName", "absent")
                        .tag("schemaVersion", "3")
                        .counter()
                        .count(),
                0.001);
        assertNotNull(registry.find("schema_registry_miss").counter());
    }
}
