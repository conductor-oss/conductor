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
import io.orkes.conductor.client.http.OrkesSchemaClient;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Schema enforcement as a caller sees it: a status, a reason, and whether an execution exists at
 * all. Nothing here asserts on which class ran.
 *
 * <p>Enforcement needs no server-side switch: a definition that sets {@code enforceSchema} and
 * attaches a schema is enforced, so the servers these tests run against need no special
 * configuration.
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
    private final OrkesSchemaClient schemaClient = ApiUtil.SCHEMA_CLIENT;
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

    /** {@link #register} always leaves {@code enforceSchema} at its default; this one sets it. */
    private String registerWorkflow(
            String label,
            TaskDef taskDef,
            Map<String, Object> taskInputParameters,
            SchemaDef workflowInput,
            SchemaDef workflowOutput,
            boolean enforceSchema) {
        String workflowName =
                register(label, taskDef, taskInputParameters, workflowInput, workflowOutput);
        WorkflowDef workflowDef = metadataClient.getWorkflowDef(workflowName, 1);
        workflowDef.setEnforceSchema(enforceSchema);
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
                                    Task.Status.FAILED_WITH_TERMINAL_ERROR,
                                    task.getStatus(),
                                    "the worker returned a shape its definition refuses, so "
                                            + "re-running it would spend the retry budget on the "
                                            + "same outcome");
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

    // ── system task output, in the decider ────────────────────────────────────

    /**
     * An {@code INLINE} task completes inside the decider rather than by a worker reporting through
     * the task-update API, so its output passes through a different point entirely. Its {@code
     * outputSchema} used to be stored and never enforced.
     */
    @Test
    void aSystemTaskWhoseOutputBreaksItsSchemaFailsTerminally() {
        String taskName = "e2e_schema_task_sysout_" + suffix;
        String workflowName = "e2e_schema_sysout_" + suffix;

        // The definition carrying the schema, registered under the name the INLINE task uses.
        TaskDef taskDef = taskDef(taskName, 3, null, requiresName("e2e_sysout_" + suffix));

        WorkflowTask inline = new WorkflowTask();
        inline.setName(taskName);
        inline.setTaskReferenceName("step");
        inline.setWorkflowTaskType(TaskType.INLINE);
        // Evaluates to a number, so the output is {"result": 3} — no `name`, which its schema
        // requires.
        inline.setInputParameters(
                Map.of(
                        "evaluatorType",
                        "graaljs",
                        "expression",
                        "(function () { return $.value1 + $.value2; })();",
                        "value1",
                        1,
                        "value2",
                        2));

        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName(workflowName);
        workflowDef.setVersion(1);
        workflowDef.setOwnerEmail("test@conductor.io");
        workflowDef.setTimeoutSeconds(120);
        workflowDef.setTasks(List.of(inline));

        metadataClient.registerTaskDefs(List.of(taskDef));
        metadataClient.updateWorkflowDefs(List.of(workflowDef));

        String workflowId = start(workflowName, Map.of());

        await().atMost(30, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
                            assertEquals(Workflow.WorkflowStatus.FAILED, workflow.getStatus());

                            Task task = workflow.getTasks().get(0);
                            assertEquals(
                                    Task.Status.FAILED_WITH_TERMINAL_ERROR,
                                    task.getStatus(),
                                    "the server produced this output itself, so re-running the "
                                            + "task produces the same shape");
                            assertEquals(
                                    1,
                                    workflow.getTasks().size(),
                                    "a terminal failure schedules no retry");
                            assertTrue(
                                    task.getReasonForIncompletion().contains("name"),
                                    task.getReasonForIncompletion());
                        });
    }

    // ── registry references ───────────────────────────────────────────────────

    /**
     * Registers the requires-{@code name} document under its own name and returns a reference to
     * it: name and version, and deliberately no {@code data}. Only the registry can resolve it, so
     * a test using one proves the server went and looked.
     */
    private SchemaDef registered(String schemaName, int version) {
        SchemaDef stored = requiresName(schemaName);
        stored.setVersion(version);
        schemaClient.saveSchemas(List.of(stored));
        return reference(schemaName, version);
    }

    private static SchemaDef reference(String schemaName, int version) {
        SchemaDef ref = new SchemaDef();
        ref.setName(schemaName);
        ref.setVersion(version);
        ref.setType(SchemaDef.Type.JSON);
        return ref;
    }

    @Test
    void aRegisteredReferenceIsResolvedAndEnforcedAtTheWorkflowInput() {
        String schemaName = "e2e_ref_wfin_" + suffix;
        String workflowName =
                register(
                        "refwfin",
                        taskDef("e2e_schema_task_refwfin_" + suffix, 0, null, null),
                        Map.of("name", "${workflow.input.name}"),
                        registered(schemaName, 1),
                        null);

        ConductorClientException thrown =
                assertThrows(
                        ConductorClientException.class,
                        () -> start(workflowName, Map.of("nickname", "ada")));

        assertEquals(400, thrown.getStatusCode(), "Expected 400 but got: " + thrown);
        assertTrue(thrown.getMessage().contains("name"), thrown.getMessage());
    }

    @Test
    void aRegisteredReferenceIsResolvedAndEnforcedAtTheTaskInput() {
        String schemaName = "e2e_ref_tin_" + suffix;
        String taskName = "e2e_schema_task_reftin_" + suffix;
        String workflowName =
                register(
                        "reftin",
                        taskDef(taskName, 3, registered(schemaName, 1), null),
                        Map.of("nickname", "${workflow.input.nickname}"),
                        null,
                        null);

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
                                    "a reference resolves to the same document on every attempt, "
                                            + "so retrying cannot help");
                            assertEquals(1, workflow.getTasks().size());
                            assertTrue(
                                    task.getReasonForIncompletion().contains("name"),
                                    task.getReasonForIncompletion());
                        });
    }

    @Test
    void aRegisteredReferenceIsResolvedAndEnforcedAtTheTaskOutput() {
        String schemaName = "e2e_ref_tout_" + suffix;
        String taskName = "e2e_schema_task_reftout_" + suffix;
        String workflowName =
                register(
                        "reftout",
                        taskDef(taskName, 0, null, registered(schemaName, 1)),
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
                            assertEquals(Task.Status.FAILED_WITH_TERMINAL_ERROR, task.getStatus());
                            assertTrue(
                                    task.getReasonForIncompletion().contains("name"),
                                    task.getReasonForIncompletion());
                        });
    }

    /**
     * {@link SchemaDef} defaults its version to 1, so a definition that wants whatever is newest
     * has to say {@code 0} outright. Version 1 here permits anything and version 2 requires {@code
     * name}: resolving version 1 would let this payload through, so the test discriminates.
     */
    @Test
    void aReferenceCarryingNoVersionResolvesTheLatestRegisteredVersion() {
        String schemaName = "e2e_ref_latest_" + suffix;

        SchemaDef permissive = new SchemaDef();
        permissive.setName(schemaName);
        permissive.setVersion(1);
        permissive.setType(SchemaDef.Type.JSON);
        permissive.setData(
                Map.of(
                        "$schema",
                        "https://json-schema.org/draft/2020-12/schema",
                        "type",
                        "object"));
        schemaClient.saveSchemas(List.of(permissive));
        registered(schemaName, 2);

        String taskName = "e2e_schema_task_reflatest_" + suffix;
        String workflowName =
                register(
                        "reflatest",
                        taskDef(taskName, 0, reference(schemaName, 0), null),
                        Map.of("nickname", "${workflow.input.nickname}"),
                        null,
                        null);

        String workflowId = start(workflowName, Map.of("nickname", "ada"));

        await().atMost(30, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
                            Task task = workflow.getTasks().get(0);
                            assertEquals(
                                    Task.Status.FAILED_WITH_TERMINAL_ERROR,
                                    task.getStatus(),
                                    "version 2 must be the one that was applied, not version 1");
                            assertTrue(
                                    task.getReasonForIncompletion().contains("name"),
                                    task.getReasonForIncompletion());
                        });
    }

    /**
     * A conforming payload against a reference still passes, so the tests above are not vacuous.
     */
    @Test
    void aConformingPayloadAgainstAReferencePasses() {
        String schemaName = "e2e_ref_ok_" + suffix;
        String workflowName =
                register(
                        "refok",
                        taskDef("e2e_schema_task_refok_" + suffix, 0, null, null),
                        Map.of("name", "${workflow.input.name}"),
                        registered(schemaName, 1),
                        null);

        assertNotNull(start(workflowName, Map.of("name", "ada")));
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

    @Test
    void aRegisteredReferenceIsResolvedAndEnforcedAtTheWorkflowOutput() {
        String taskName = "e2e_schema_task_refwfout_" + suffix;
        String workflowName =
                register(
                        "refwfout",
                        taskDef(taskName, 0, null, null),
                        Map.of("name", "${workflow.input.name}"),
                        null,
                        registered("e2e_ref_wfout_" + suffix, 1));

        String workflowId = start(workflowName, Map.of("name", "ada"));

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

    /**
     * A workflow that opts out is checked at neither boundary, even with schemas on both and a
     * payload that matches neither.
     */
    @Test
    void aWorkflowThatOptsOutIsNotValidatedOnInputOrOutput() {
        String taskName = "e2e_schema_task_wfoptout_" + suffix;
        String workflowName =
                registerWorkflow(
                        "wfoptout",
                        taskDef(taskName, 0, null, null),
                        Map.of("name", "${workflow.input.name}"),
                        requiresName("e2e_wfoptout_in_" + suffix),
                        requiresName("e2e_wfoptout_out_" + suffix),
                        false);

        String workflowId = start(workflowName, Map.of("nickname", "ada"));

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
                            assertEquals(
                                    Workflow.WorkflowStatus.COMPLETED,
                                    workflow.getStatus(),
                                    "neither boundary was checked, so nothing rejected it");
                        });
    }

    @Test
    void aTaskOutputIsNotValidatedWhenTheDefinitionDoesNotOptIn() {
        String taskName = "e2e_schema_task_toutoptout_" + suffix;
        TaskDef taskDef = taskDef(taskName, 0, null, requiresName("e2e_toutoptout_" + suffix));
        taskDef.setEnforceSchema(false);
        String workflowName =
                register(
                        "toutoptout",
                        taskDef,
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
                            assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
                            assertEquals(
                                    Task.Status.COMPLETED,
                                    workflow.getTasks().get(0).getStatus(),
                                    "an output schema attached without opting in is not applied");
                        });
    }

    /**
     * The two definition types default the flag differently, and it is load-bearing: a workflow
     * carrying a schema is enforced unless it opts out, while a task carrying one does nothing
     * until it opts in. Proved through behaviour rather than by reading the field, because it is
     * the behaviour a caller depends on.
     */
    @Test
    void theTwoDefinitionTypesDefaultTheFlagDifferently() {
        String taskName = "e2e_schema_task_defaults_" + suffix;

        // The task attaches an input schema and says nothing about enforceSchema.
        TaskDef silentTask = new TaskDef(taskName);
        silentTask.setOwnerEmail("test@conductor.io");
        silentTask.setRetryCount(0);
        silentTask.setInputSchema(requiresName("e2e_defaults_task_" + suffix));

        // The workflow attaches an input schema and likewise says nothing.
        String workflowName =
                register(
                        "defaults",
                        silentTask,
                        Map.of("nickname", "${workflow.input.nickname}"),
                        requiresName("e2e_defaults_wf_" + suffix),
                        null);

        ConductorClientException thrown =
                assertThrows(
                        ConductorClientException.class,
                        () -> start(workflowName, Map.of("nickname", "ada")));
        assertEquals(
                400,
                thrown.getStatusCode(),
                "a workflow enforces its schema without being asked: " + thrown);

        // And with an input the workflow accepts, the task's own schema is still not applied —
        // `nickname` does not satisfy it, yet the task is scheduled.
        String workflowId = start(workflowName, Map.of("name", "ada"));
        await().atMost(30, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
                            assertEquals(
                                    Task.Status.SCHEDULED,
                                    workflow.getTasks().get(0).getStatus(),
                                    "a task does not enforce its schema until it opts in");
                        });
    }
}
