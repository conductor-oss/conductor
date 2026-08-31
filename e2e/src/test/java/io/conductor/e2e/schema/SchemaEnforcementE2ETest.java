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
package io.conductor.e2e.schema;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.netflix.conductor.client.exception.ConductorClientException;
import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.metadata.SchemaDef;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;

import io.conductor.e2e.util.ApiUtil;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Schema enforcement as a caller sees it: a status, a reason, and whether an execution exists at
 * all. Nothing here asserts on which class ran.
 *
 * <p>The server under test has {@code conductor.app.schema-validation.enabled=true}, set by {@code
 * e2e/docker/docker-compose-e2e-overrides.yaml}. The property is off by default, so without that
 * override this suite would pass while testing nothing.
 *
 * <p>Run via any of the {@code e2e/run_tests-*.sh} flavors.
 */
class SchemaEnforcementE2ETest {

    private static final Map<String, Object> REQUIRES_NAME =
            Map.of(
                    "$schema",
                    "https://json-schema.org/draft/2020-12/schema",
                    "type",
                    "object",
                    "properties",
                    Map.of("name", Map.of("type", "string")),
                    "required",
                    List.of("name"));

    private final MetadataClient metadataClient = ApiUtil.METADATA_CLIENT;
    private final WorkflowClient workflowClient = ApiUtil.WORKFLOW_CLIENT;
    private final TaskClient taskClient = ApiUtil.TASK_CLIENT;

    /** Fresh names throughout: the suite runs four forks against one server. */
    private final String suffix = UUID.randomUUID().toString().replace("-", "");

    private static SchemaDef requiresName(String name) {
        SchemaDef schema = new SchemaDef();
        schema.setName(name);
        schema.setVersion(1);
        schema.setType(SchemaDef.Type.JSON);
        schema.setData(REQUIRES_NAME);
        return schema;
    }

    private TaskDef taskDef(String taskName, int retryCount, SchemaDef input, SchemaDef output) {
        TaskDef taskDef = new TaskDef(taskName);
        taskDef.setOwnerEmail("test@conductor.io");
        taskDef.setRetryCount(retryCount);
        taskDef.setInputSchema(input);
        taskDef.setOutputSchema(output);
        taskDef.setEnforceSchema(true);
        return taskDef;
    }

    /** Registers a one-task workflow and returns its name. */
    private String register(
            String label,
            TaskDef taskDef,
            Map<String, Object> taskInputParameters,
            SchemaDef workflowInput,
            SchemaDef workflowOutput) {
        String workflowName = "e2e_schema_" + label + "_" + suffix;

        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setName(taskDef.getName());
        workflowTask.setTaskReferenceName("step");
        workflowTask.setWorkflowTaskType(TaskType.SIMPLE);
        workflowTask.setInputParameters(taskInputParameters);

        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName(workflowName);
        workflowDef.setVersion(1);
        workflowDef.setOwnerEmail("test@conductor.io");
        workflowDef.setTimeoutSeconds(120);
        workflowDef.setInputSchema(workflowInput);
        workflowDef.setOutputSchema(workflowOutput);
        workflowDef.setTasks(List.of(workflowTask));
        if (workflowOutput != null) {
            workflowDef.setOutputParameters(Map.of("name", "${step.output.name}"));
        }

        metadataClient.registerTaskDefs(List.of(taskDef));
        metadataClient.updateWorkflowDefs(List.of(workflowDef));
        return workflowName;
    }

    private String start(String workflowName, Map<String, Object> input) {
        StartWorkflowRequest request = new StartWorkflowRequest();
        request.setName(workflowName);
        request.setVersion(1);
        request.setInput(input);
        return workflowClient.startWorkflow(request);
    }

    // ── workflow input, at start ──────────────────────────────────────────────

    @Test
    void aWorkflowWhoseInputBreaksItsSchemaNeverStarts() {
        String workflowName =
                register(
                        "wfin",
                        taskDef("e2e_schema_task_wfin_" + suffix, 0, null, null),
                        Map.of("name", "${workflow.input.name}"),
                        requiresName("e2e_wfin_" + suffix),
                        null);

        ConductorClientException thrown =
                assertThrows(
                        ConductorClientException.class,
                        () -> start(workflowName, Map.of("nickname", "ada")));

        assertEquals(400, thrown.getStatusCode(), "Expected 400 but got: " + thrown);
        assertTrue(thrown.getMessage().contains("name"), thrown.getMessage());
    }

    @Test
    void aConformingWorkflowInputStarts() {
        String workflowName =
                register(
                        "wfok",
                        taskDef("e2e_schema_task_wfok_" + suffix, 0, null, null),
                        Map.of("name", "${workflow.input.name}"),
                        requiresName("e2e_wfok_" + suffix),
                        null);

        assertNotNull(start(workflowName, Map.of("name", "ada")));
    }

    // ── task input, at scheduling ─────────────────────────────────────────────

    @Test
    void aTaskWhoseInputBreaksItsSchemaFailsTerminally() {
        String taskName = "e2e_schema_task_tin_" + suffix;
        // Three retries, so a terminal failure is visibly different from an ordinary one: an
        // ordinary FAILED here would reschedule and the workflow would carry four attempts.
        String workflowName =
                register(
                        "tin",
                        taskDef(taskName, 3, requiresName("e2e_tin_" + suffix), null),
                        Map.of("nickname", "${workflow.input.nickname}"),
                        null,
                        null);

        // The task's input carries no `name`, which is what its schema requires. The workflow
        // has no schema of its own, so this is the task boundary failing and nothing else.
        String workflowId = start(workflowName, Map.of("nickname", "ada"));

        await().atMost(30, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
                            assertEquals(Workflow.WorkflowStatus.FAILED, workflow.getStatus());

                            Task task = workflow.getTasks().get(0);
                            assertEquals(
                                    Task.Status.FAILED_WITH_TERMINAL_ERROR,
                                    task.getStatus(),
                                    "an invalid payload is invalid on every retry, so it must not "
                                            + "spend the retry budget");
                            assertEquals(
                                    1,
                                    workflow.getTasks().size(),
                                    "a terminal failure schedules no retry");
                            assertTrue(
                                    task.getReasonForIncompletion().contains("name"),
                                    task.getReasonForIncompletion());
                        });
    }

    // ── task output, at update ────────────────────────────────────────────────

    @Test
    void aTaskWhoseOutputBreaksItsSchemaFails() {
        String taskName = "e2e_schema_task_tout_" + suffix;
        String workflowName =
                register(
                        "tout",
                        taskDef(taskName, 0, null, requiresName("e2e_tout_" + suffix)),
                        Map.of("name", "${workflow.input.name}"),
                        null,
                        null);

        String workflowId = start(workflowName, Map.of("name", "ada"));

        await().atMost(30, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Task task = taskClient.pollTask(taskName, "e2e-schema-worker", null);
                            assertNotNull(task);
                            TaskResult result = new TaskResult(task);
                            result.setStatus(TaskResult.Status.COMPLETED);
                            result.getOutputData().put("nickname", "ada");
                            taskClient.updateTask(result);
                        });

        await().atMost(30, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
                            assertEquals(Workflow.WorkflowStatus.FAILED, workflow.getStatus());
                            Task task = workflow.getTasks().get(0);
                            assertEquals(
                                    Task.Status.FAILED,
                                    task.getStatus(),
                                    "an output failure is retriable, unlike an input failure");
                            assertTrue(
                                    task.getReasonForIncompletion().contains("name"),
                                    task.getReasonForIncompletion());
                        });
    }

    // ── workflow output, at completion ────────────────────────────────────────

    @Test
    void aWorkflowWhoseOutputBreaksItsSchemaFailsAtCompletion() {
        String taskName = "e2e_schema_task_wfout_" + suffix;
        String workflowName =
                register(
                        "wfout",
                        taskDef(taskName, 0, null, null),
                        Map.of("name", "${workflow.input.name}"),
                        null,
                        requiresName("e2e_wfout_" + suffix));

        String workflowId = start(workflowName, Map.of("name", "ada"));

        // The workflow's output parameter maps `step.output.name`, which the worker never sets.
        await().atMost(30, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Task task = taskClient.pollTask(taskName, "e2e-schema-worker", null);
                            assertNotNull(task);
                            TaskResult result = new TaskResult(task);
                            result.setStatus(TaskResult.Status.COMPLETED);
                            taskClient.updateTask(result);
                        });

        await().atMost(30, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow = workflowClient.getWorkflow(workflowId, false);
                            assertEquals(Workflow.WorkflowStatus.FAILED, workflow.getStatus());
                            assertTrue(
                                    workflow.getReasonForIncompletion().contains("name"),
                                    workflow.getReasonForIncompletion());
                        });
    }

    // ── the per-definition gate ───────────────────────────────────────────────

    @Test
    void aDefinitionThatDoesNotOptInIsNotValidated() {
        String taskName = "e2e_schema_task_optout_" + suffix;
        TaskDef taskDef = taskDef(taskName, 0, requiresName("e2e_optout_" + suffix), null);
        taskDef.setEnforceSchema(false);
        String workflowName =
                register(
                        "optout",
                        taskDef,
                        Map.of("nickname", "${workflow.input.nickname}"),
                        null,
                        null);

        String workflowId = start(workflowName, Map.of("nickname", "ada"));

        await().atMost(30, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
                            assertEquals(1, workflow.getTasks().size());
                            assertEquals(
                                    Task.Status.SCHEDULED,
                                    workflow.getTasks().get(0).getStatus(),
                                    "a schema attached without opting in must not reject work");
                        });
    }
}
