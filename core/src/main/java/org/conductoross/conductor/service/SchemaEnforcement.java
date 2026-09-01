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

import org.conductoross.conductor.core.exception.SchemaValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.SchemaDef;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.metrics.Monitors;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

/**
 * The engine's validation points — workflow input at start, task input at scheduling, task output
 * at update and in the decider, workflow output at completion — and the gate in front of each.
 *
 * <p>A payload is validated when two things hold at once: the definition's own {@code
 * enforceSchema} flag is set, and a schema is actually attached. Both matter. Without the flag a
 * schema attached for documentation would start rejecting work; without a schema there is nothing
 * to check against. There is no server-wide switch in front of these: a definition that asks for
 * enforcement and carries a schema gets it.
 *
 * <p>The rules for what a schema means — resolving a reference against the registry, refusing a
 * type this server cannot validate, refusing one with no type at all — live on {@link
 * SchemaService#validate}, so they read the same here and in the AI layer.
 */
@Component
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

    public SchemaEnforcement(SchemaService schemaService) {
        this.schemaService = schemaService;
    }

    /**
     * Validates a workflow's input against its definition's input schema. Called before the
     * execution is created, so a failure means it never starts and there is nothing to compensate.
     */
    public void validateWorkflowInput(WorkflowModel workflow) {
        WorkflowDef def = workflow.getWorkflowDefinition();
        if (def == null || !def.isEnforceSchema() || def.getInputSchema() == null) {
            return;
        }
        validate(
                def.getInputSchema(),
                workflow.getInput(),
                new Boundary(
                        "Workflow " + def.getName() + " input",
                        "workflowInput",
                        workflow.getWorkflowId(),
                        def.getName()));
    }

    /** Validates a workflow's output against its definition's output schema, at completion. */
    public void validateWorkflowOutput(WorkflowModel workflow) {
        WorkflowDef def = workflow.getWorkflowDefinition();
        if (def == null || !def.isEnforceSchema() || def.getOutputSchema() == null) {
            return;
        }
        validate(
                def.getOutputSchema(),
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
     * <p>A task with no definition has no schema to enforce, and is passed over.
     */
    public void validateTaskInput(TaskModel task, TaskDef taskDef) {
        if (taskDef == null || !taskDef.isEnforceSchema() || taskDef.getInputSchema() == null) {
            return;
        }
        validate(
                taskDef.getInputSchema(),
                task.getInputData(),
                new Boundary(
                        "Task " + task.getReferenceTaskName() + " input",
                        "taskInput",
                        task.getTaskId(),
                        task.getWorkflowType()));
    }

    /**
     * Validates a task's output against its definition's output schema — as a worker reports it, or
     * as the decider produces it for a system task the server runs itself.
     */
    public void validateTaskOutput(TaskModel task, TaskDef taskDef) {
        if (taskDef == null || !taskDef.isEnforceSchema() || taskDef.getOutputSchema() == null) {
            return;
        }
        validate(
                taskDef.getOutputSchema(),
                task.getOutputData(),
                new Boundary(
                        "Task " + task.getReferenceTaskName() + " output",
                        "taskOutput",
                        task.getTaskId(),
                        task.getWorkflowType()));
    }

    /**
     * Runs the check and reports it. The gate — a definition that opts in, and a schema on the side
     * being checked — is applied by each hook above rather than here, because it has to come before
     * the payload is read. {@link WorkflowModel#getInput()} and {@link WorkflowModel#getOutput()}
     * are not plain getters: when both the inline map and the external-storage map hold entries
     * they merge the two and reset the payload field, so passing one as an argument here would
     * evaluate it for every execution, including the ones that never opted in.
     *
     * <p>{@code boundary} names which payload was rejected and goes in front of the schema's own
     * complaint, so a reason recorded on an execution says more than which rule was broken.
     */
    private void validate(SchemaDef schema, Map<String, Object> payload, Boundary boundary) {
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
