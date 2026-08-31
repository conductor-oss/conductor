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
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The three-part gate in front of every validation point: the server property, the definition's own
 * {@code enforceSchema} flag, and a schema actually being attached. All three are load-bearing —
 * dropping any one of them makes an upgrade change behaviour for somebody.
 */
class SchemaEnforcementTest {

    private SchemaValidationProperties properties;
    private SchemaEnforcement enforcement;

    @BeforeEach
    void setUp() {
        properties = new SchemaValidationProperties();
        SchemaService schemaService =
                new SchemaServiceImpl(
                        new InMemorySchemaDAO(),
                        new SchemaCacheProperties(),
                        new JsonSchemaValidator(new ObjectMapperProvider().getObjectMapper()));
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

    private static WorkflowModel workflow(WorkflowDef def, Map<String, Object> input) {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(def);
        workflow.setInput(input);
        workflow.setOutput(input);
        return workflow;
    }

    private static WorkflowDef workflowDef(SchemaDef schema, boolean enforce) {
        WorkflowDef def = new WorkflowDef();
        def.setName("order");
        def.setVersion(1);
        def.setInputSchema(schema);
        def.setOutputSchema(schema);
        def.setEnforceSchema(enforce);
        return def;
    }

    private static TaskModel task(Map<String, Object> payload) {
        TaskModel task = new TaskModel();
        task.setTaskDefName("charge");
        task.setReferenceTaskName("charge_ref");
        task.setInputData(payload);
        task.setOutputData(payload);
        return task;
    }

    private static TaskDef taskDef(SchemaDef schema, boolean enforce) {
        TaskDef def = new TaskDef();
        def.setName("charge");
        def.setInputSchema(schema);
        def.setOutputSchema(schema);
        def.setEnforceSchema(enforce);
        return def;
    }

    @Test
    void nothingIsValidatedWhileThePropertyIsOff() {
        WorkflowModel workflow = workflow(workflowDef(requiresName(), true), Map.of());

        assertDoesNotThrow(() -> enforcement.validateWorkflowInput(workflow));
        assertDoesNotThrow(() -> enforcement.validateWorkflowOutput(workflow));
        assertDoesNotThrow(
                () ->
                        enforcement.validateTaskInput(
                                task(Map.of()), () -> taskDef(requiresName(), true)));
        assertDoesNotThrow(
                () ->
                        enforcement.validateTaskOutput(
                                task(Map.of()), () -> taskDef(requiresName(), true)));
    }

    /**
     * A disabled server must not so much as read the payload. {@link WorkflowModel#getInput()} and
     * {@link WorkflowModel#getOutput()} are not plain getters: when both the inline map and the
     * external-storage map hold entries they merge the two and reset the payload field. Passing
     * either to {@code validate} evaluates it before the gate inside {@code validate} is reached,
     * so the guard has to sit at the top of the hook instead.
     *
     * <p>This asserts on the call rather than on state because the merge is idempotent, so its
     * effect cannot be seen from outside the model. {@code WorkflowModel.equals} calls {@code
     * getInput()} too, which is why comparing two models cannot pin this down either.
     */
    @Test
    void aDisabledServerDoesNotReadAWorkflowPayload() {
        PayloadReadRecordingWorkflow workflow =
                new PayloadReadRecordingWorkflow(workflowDef(requiresName(), true));

        enforcement.validateWorkflowInput(workflow);
        enforcement.validateWorkflowOutput(workflow);

        assertFalse(workflow.inputWasRead, "a disabled server read the workflow input");
        assertFalse(workflow.outputWasRead, "a disabled server read the workflow output");
    }

    /** The control: with enforcement on, the same hooks do read it, so the test above can fail. */
    @Test
    void anEnabledServerDoesReadAWorkflowPayload() {
        properties.setEnabled(true);
        PayloadReadRecordingWorkflow workflow =
                new PayloadReadRecordingWorkflow(workflowDef(requiresName(), true));

        assertThrows(
                SchemaValidationException.class, () -> enforcement.validateWorkflowInput(workflow));
        assertThrows(
                SchemaValidationException.class,
                () -> enforcement.validateWorkflowOutput(workflow));

        assertTrue(workflow.inputWasRead);
        assertTrue(workflow.outputWasRead);
    }

    /** Hand-written rather than mocked: the subject owns both sides of this call. */
    private static final class PayloadReadRecordingWorkflow extends WorkflowModel {

        private boolean inputWasRead;
        private boolean outputWasRead;

        PayloadReadRecordingWorkflow(WorkflowDef def) {
            setWorkflowDefinition(def);
        }

        @Override
        public Map<String, Object> getInput() {
            inputWasRead = true;
            return super.getInput();
        }

        @Override
        public Map<String, Object> getOutput() {
            outputWasRead = true;
            return super.getOutput();
        }
    }

    @Test
    void thePropertyDefaultsToOff() {
        assertTrue(!new SchemaValidationProperties().isEnabled());
    }

    @Test
    void aDefinitionThatOptsOutIsNotValidated() {
        properties.setEnabled(true);
        WorkflowModel workflow = workflow(workflowDef(requiresName(), false), Map.of());

        assertDoesNotThrow(() -> enforcement.validateWorkflowInput(workflow));
        assertDoesNotThrow(
                () ->
                        enforcement.validateTaskInput(
                                task(Map.of()), () -> taskDef(requiresName(), false)));
    }

    @Test
    void aDefinitionWithNoSchemaIsNotValidated() {
        properties.setEnabled(true);
        WorkflowModel workflow = workflow(workflowDef(null, true), Map.of());

        assertDoesNotThrow(() -> enforcement.validateWorkflowInput(workflow));
        assertDoesNotThrow(() -> enforcement.validateWorkflowOutput(workflow));
        assertDoesNotThrow(
                () -> enforcement.validateTaskInput(task(Map.of()), () -> taskDef(null, true)));
        assertDoesNotThrow(
                () -> enforcement.validateTaskOutput(task(Map.of()), () -> taskDef(null, true)));
    }

    @Test
    void aTaskWithNoDefinitionIsNotValidated() {
        properties.setEnabled(true);

        assertDoesNotThrow(() -> enforcement.validateTaskInput(task(Map.of()), () -> null));
        assertDoesNotThrow(() -> enforcement.validateTaskOutput(task(Map.of()), () -> null));
    }

    @Test
    void allThreeGatesOpenMeansTheWorkflowInputIsValidated() {
        properties.setEnabled(true);
        WorkflowDef def = workflowDef(requiresName(), true);

        assertDoesNotThrow(
                () -> enforcement.validateWorkflowInput(workflow(def, Map.of("name", "ada"))));

        SchemaValidationException thrown =
                assertThrows(
                        SchemaValidationException.class,
                        () -> enforcement.validateWorkflowInput(workflow(def, Map.of())));

        assertTrue(thrown.getMessage().contains("input"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("order"), thrown.getMessage());
    }

    @Test
    void allThreeGatesOpenMeansTheWorkflowOutputIsValidated() {
        properties.setEnabled(true);
        WorkflowDef def = workflowDef(requiresName(), true);

        SchemaValidationException thrown =
                assertThrows(
                        SchemaValidationException.class,
                        () -> enforcement.validateWorkflowOutput(workflow(def, Map.of())));

        assertTrue(thrown.getMessage().contains("output"), thrown.getMessage());
    }

    @Test
    void allThreeGatesOpenMeansTheTaskInputIsValidated() {
        properties.setEnabled(true);
        TaskDef def = taskDef(requiresName(), true);

        assertDoesNotThrow(
                () -> enforcement.validateTaskInput(task(Map.of("name", "ada")), () -> def));

        SchemaValidationException thrown =
                assertThrows(
                        SchemaValidationException.class,
                        () -> enforcement.validateTaskInput(task(Map.of()), () -> def));

        assertTrue(thrown.getMessage().contains("input"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("charge_ref"), thrown.getMessage());
    }

    @Test
    void allThreeGatesOpenMeansTheTaskOutputIsValidated() {
        properties.setEnabled(true);
        TaskDef def = taskDef(requiresName(), true);

        SchemaValidationException thrown =
                assertThrows(
                        SchemaValidationException.class,
                        () -> enforcement.validateTaskOutput(task(Map.of()), () -> def));

        assertTrue(thrown.getMessage().contains("output"), thrown.getMessage());
    }

    /**
     * The workflow flag defaults to true, so a workflow definition carrying a schema is enforced as
     * soon as an operator turns the property on. The task flag defaults to false, which is why the
     * task-level signal is the flag and not the presence of a schema.
     */
    @Test
    void theTwoDefinitionFlagsHaveOppositeDefaults() {
        assertTrue(new WorkflowDef().isEnforceSchema());
        assertTrue(!new TaskDef().isEnforceSchema());
    }
}
