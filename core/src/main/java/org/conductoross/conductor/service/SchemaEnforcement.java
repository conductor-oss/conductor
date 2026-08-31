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

import java.util.Map;
import java.util.function.Supplier;

import org.conductoross.conductor.core.exception.SchemaValidationException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.SchemaDef;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

/**
 * The engine's four validation points — workflow input at start, task input at scheduling, task
 * output at update, workflow output at completion — and the gate in front of each.
 *
 * <p>A payload is validated only when three things hold at once: the server property is on, the
 * definition's own {@code enforceSchema} flag is set, and a schema is actually attached. All three
 * matter. Without the property an upgrade would change how running deployments behave; without the
 * per-definition flag a schema attached for documentation would start rejecting work; without the
 * schema there is nothing to check against.
 *
 * <p>The rules for what a schema means — resolving a reference against the registry, refusing a
 * type this server cannot validate, refusing one with no type at all — live on {@link
 * SchemaService#validate}, so they read the same here and in the AI layer.
 */
@Component
@EnableConfigurationProperties(SchemaValidationProperties.class)
public class SchemaEnforcement {

    private final SchemaService schemaService;
    private final SchemaValidationProperties properties;

    public SchemaEnforcement(SchemaService schemaService, SchemaValidationProperties properties) {
        this.schemaService = schemaService;
        this.properties = properties;
    }

    /**
     * Validates a workflow's input against its definition's input schema. Called before the
     * execution is created, so a failure means it never starts and there is nothing to compensate.
     */
    public void validateWorkflowInput(WorkflowModel workflow) {
        if (!properties.isEnabled()) {
            return;
        }
        WorkflowDef def = workflow.getWorkflowDefinition();
        if (def == null) {
            return;
        }
        validate(
                def.getInputSchema(),
                def.isEnforceSchema(),
                workflow.getInput(),
                "Workflow " + def.getName() + " input");
    }

    /** Validates a workflow's output against its definition's output schema, at completion. */
    public void validateWorkflowOutput(WorkflowModel workflow) {
        if (!properties.isEnabled()) {
            return;
        }
        WorkflowDef def = workflow.getWorkflowDefinition();
        if (def == null) {
            return;
        }
        validate(
                def.getOutputSchema(),
                def.isEnforceSchema(),
                workflow.getOutput(),
                "Workflow " + def.getName() + " output");
    }

    /**
     * Validates a task's input against its definition's input schema, before the task is queued and
     * so before any worker sees it.
     *
     * <p>The definition is supplied lazily because resolving it can cost a metadata read, and this
     * runs for every task the engine schedules whether or not enforcement is on.
     */
    public void validateTaskInput(TaskModel task, Supplier<TaskDef> taskDefinition) {
        if (!properties.isEnabled()) {
            return;
        }
        TaskDef taskDef = taskDefinition.get();
        if (taskDef == null) {
            return;
        }
        validate(
                taskDef.getInputSchema(),
                taskDef.isEnforceSchema(),
                task.getInputData(),
                "Task " + task.getReferenceTaskName() + " input");
    }

    /**
     * Validates a task's output against its definition's output schema, as the worker reports it.
     * The definition is supplied lazily, for the reason given on {@link #validateTaskInput}.
     */
    public void validateTaskOutput(TaskModel task, Supplier<TaskDef> taskDefinition) {
        if (!properties.isEnabled()) {
            return;
        }
        TaskDef taskDef = taskDefinition.get();
        if (taskDef == null) {
            return;
        }
        validate(
                taskDef.getOutputSchema(),
                taskDef.isEnforceSchema(),
                task.getOutputData(),
                "Task " + task.getReferenceTaskName() + " output");
    }

    /**
     * The three-part gate, and the one place it is written: the server property, the definition's
     * own flag, and a schema to check against.
     *
     * <p>{@code boundary} names which payload was rejected and goes in front of the schema's own
     * complaint, so a reason recorded on an execution says more than which rule was broken.
     */
    private void validate(
            SchemaDef schema, boolean enforceSchema, Map<String, Object> payload, String boundary) {
        if (!properties.isEnabled() || !enforceSchema || schema == null) {
            return;
        }
        try {
            schemaService.validate(schema, payload);
        } catch (SchemaValidationException e) {
            throw new SchemaValidationException(boundary + ": " + e.getMessage());
        }
    }
}
