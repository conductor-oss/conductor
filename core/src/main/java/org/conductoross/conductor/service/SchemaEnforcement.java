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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.SchemaDef;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.metrics.Monitors;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(SchemaEnforcement.class);

    /**
     * Which validation point rejected a payload, and the execution it happened on. {@code
     * description} goes in front of the schema's own complaint on the recorded reason; {@code
     * metric} is the stable tag value; {@code executionId} is logged rather than recorded, since
     * the record it would go on is the execution it identifies.
     */
    private record Boundary(
            String description, String metric, String executionId, String workflowType) {}

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
                new Boundary(
                        "Workflow " + def.getName() + " input",
                        "workflowInput",
                        workflow.getWorkflowId(),
                        def.getName()));
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
                new Boundary(
                        "Workflow " + def.getName() + " output",
                        "workflowOutput",
                        workflow.getWorkflowId(),
                        def.getName()));
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
                new Boundary(
                        "Task " + task.getReferenceTaskName() + " input",
                        "taskInput",
                        task.getTaskId(),
                        task.getWorkflowType()));
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
                new Boundary(
                        "Task " + task.getReferenceTaskName() + " output",
                        "taskOutput",
                        task.getTaskId(),
                        task.getWorkflowType()));
    }

    /**
     * The rest of the three-part gate: the definition's own flag and a schema to check against. The
     * server property is checked by each hook before it reads a payload, because the getters that
     * produce one are not free of side effects.
     *
     * <p>{@code boundary} names which payload was rejected and goes in front of the schema's own
     * complaint, so a reason recorded on an execution says more than which rule was broken.
     */
    private void validate(
            SchemaDef schema,
            boolean enforceSchema,
            Map<String, Object> payload,
            Boundary boundary) {
        if (!enforceSchema || schema == null) {
            return;
        }
        long start = System.currentTimeMillis();
        try {
            schemaService.validate(schema, payload);
        } catch (SchemaValidationException e) {
            // The reason recorded on the execution names what broke; this names which execution
            // it was, so a rise in rejections is traceable without querying every failure.
            LOGGER.warn(
                    "Schema enforcement rejected {} [{}]: {}",
                    boundary.description(),
                    boundary.executionId(),
                    e.getMessage());
            Monitors.recordSchemaValidationFailure(
                    boundary.metric(), schema.getName(), boundary.workflowType());
            throw new SchemaValidationException(boundary.description() + ": " + e.getMessage());
        } finally {
            // Recorded for rejections too: a run that rejects every payload is still doing the
            // work.
            Monitors.recordSchemaValidationTime(
                    boundary.metric(), System.currentTimeMillis() - start);
        }
    }
}
