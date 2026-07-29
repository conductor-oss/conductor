/*
 * Copyright 2022 Conductor Authors.
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
package io.conductor.e2e.task;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.compress.utils.IOUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.client.exception.ConductorClientException;
import com.netflix.conductor.client.http.ConductorClientRequest;
import com.netflix.conductor.client.http.ConductorClientRequest.Method;
import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.WorkflowSummary;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.conductor.e2e.util.ApiUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Slf4j
public class ConcurrentExecLimitTests {

    static WorkflowClient workflowClient;
    static TaskClient taskClient;
    static MetadataClient metadataClient;

    static ObjectMapper objectMapper;

    /** How long the wakeup test waits for the released task after the slot frees. */
    private static final long POLL_WINDOW_SECONDS = 5;

    /**
     * Server default for {@code conductor.app.taskExecutionPostponeDuration}, applied when the
     * property was never overridden and so does not appear in {@code /admin/config}.
     */
    private static final long DEFAULT_POSTPONE_SECONDS = 60;

    /**
     * The wakeup test can only tell the fix from the bug when the postpone window is well past
     * {@link #POLL_WINDOW_SECONDS} — otherwise the postponed task becomes pollable by natural
     * expiry and the test passes either way.
     */
    private static final long MIN_POSTPONE_SECONDS = POLL_WINDOW_SECONDS * 4;

    private static final Pattern DURATION_PATTERN =
            Pattern.compile("(\\d+)\\s*(ms|s|m|h|d)?", Pattern.CASE_INSENSITIVE);

    @BeforeAll
    public static void init() {
        workflowClient = ApiUtil.WORKFLOW_CLIENT;
        metadataClient = ApiUtil.METADATA_CLIENT;
        taskClient = ApiUtil.TASK_CLIENT;
        objectMapper = new ObjectMapperProvider().getObjectMapper();
    }

    private void terminateExistingRunningWorkflows(String workflowName) {
        // clean up first
        SearchResult<WorkflowSummary> found =
                workflowClient.search(
                        "workflowType IN (" + workflowName + ") AND status IN (RUNNING)");
        System.out.println(
                "Found " + found.getResults().size() + " running workflows to be cleaned up");
        found.getResults()
                .forEach(
                        workflowSummary -> {
                            try {
                                System.out.println(
                                        "[ConcurrentExecLimit] Going to terminate "
                                                + workflowSummary.getWorkflowId()
                                                + " with status "
                                                + workflowSummary.getStatus());
                                workflowClient.terminateWorkflow(
                                        workflowSummary.getWorkflowId(),
                                        "terminate - ConcurrentExecLimit test");
                            } catch (Exception e) {
                                if (!(e instanceof ConductorClientException)) {
                                    throw e;
                                }
                            }
                        });
    }

    @SneakyThrows
    protected static String getResourceAsString(String classpathResource) {
        InputStream stream =
                ConcurrentExecLimitTests.class
                        .getClassLoader()
                        .getResourceAsStream(classpathResource);
        byte[] data = IOUtils.toByteArray(stream);
        return new String(data);
    }

    @Test
    @DisplayName("Check concurrent exec limit test")
    public void testTerminateForDataInconsistency() {
        String workflowName = "exec_limit_check";
        String taskName = "exec_limit";

        terminateExistingRunningWorkflows(workflowName);

        try {
            WorkflowDef workflowDef =
                    objectMapper.readValue(
                            getResourceAsString("exec_limit_workflow.json"), WorkflowDef.class);
            metadataClient.updateWorkflowDefs(java.util.List.of(workflowDef));
            TaskDef tasKDef = new TaskDef(taskName);
            tasKDef.setOwnerEmail("test@conductor.io");
            tasKDef.setConcurrentExecLimit(2);
            metadataClient.registerTaskDefs(List.of(tasKDef));
        } catch (IOException e) {
            log.error("Failed to read and register workflow", e);
        }

        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName);
        startWorkflowRequest.setVersion(1);

        String workflowId = workflowClient.startWorkflow(startWorkflowRequest);
        workflowClient.getWorkflow(workflowId, true);

        // With default taskExecutionPostponeDuration=60s, the 6-task fork needs up to ~180s to
        // complete.
        // To speed up, set conductor.app.taskExecutionPostponeDuration=10s in server config.
        await().atMost(180, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            try {
                                List<Task> task =
                                        taskClient.batchPollTasksByTaskType(
                                                taskName, "worker", 10, 1000);
                                if (task != null) {
                                    System.out.println("Got " + task.size() + " tasks...");
                                    assertTrue(task.size() <= 2);
                                    task.forEach(
                                            task1 -> {
                                                taskClient.updateTaskSync(
                                                        workflowId,
                                                        task1.getReferenceTaskName(),
                                                        TaskResult.Status.COMPLETED,
                                                        Map.of("updatedBy", "e2e"));
                                            });
                                }
                                assertEquals(
                                        Workflow.WorkflowStatus.COMPLETED,
                                        workflowClient.getWorkflow(workflowId, false).getStatus());
                            } catch (Exception e) {
                                if (!(e instanceof ConductorClientException)) {
                                    throw e;
                                }
                            }
                        });
    }

    /**
     * Reproduces the bug fixed by PR conductor-oss/conductor#1105.
     *
     * <p>Sequence:
     *
     * <ol>
     *   <li>Register a task with {@code concurrentExecLimit=1}; workflow forks two tasks of that
     *       type.
     *   <li>Poll once → get task A. A goes IN_PROGRESS and occupies the only slot.
     *   <li>Poll again → empty (B fails {@code exceedsInProgressLimit} and gets postponed for
     *       {@code conductor.app.taskExecutionPostponeDuration} seconds — 60s by default).
     *   <li>Complete A; the slot is now free.
     *   <li>Poll for B and measure the wait.
     * </ol>
     *
     * <p>Post-fix: B is pollable within a couple seconds because terminal completion of A peeks the
     * queue and resets B's offset to {@code now}. Pre-fix: B remains postponed for ~the postpone
     * duration.
     */
    @Test
    @DisplayName("Postponed task should be released when concurrencyLimit slot frees")
    public void testPostponedTaskReleasedOnSlotFree() throws Exception {
        String workflowName = "concurrency_wakeup_check";
        String taskName = "concurrency_wakeup";

        long postponeSeconds = configuredPostponeSeconds();
        assumeTrue(
                postponeSeconds >= MIN_POSTPONE_SECONDS,
                "conductor.app.taskExecutionPostponeDuration is "
                        + postponeSeconds
                        + "s, which is not comfortably longer than the "
                        + POLL_WINDOW_SECONDS
                        + "s poll window: the postponed task would become pollable by natural"
                        + " expiry, so this test cannot distinguish the fix from the bug. Configure"
                        + " at least "
                        + MIN_POSTPONE_SECONDS
                        + "s to run it.");

        terminateExistingRunningWorkflows(workflowName);

        TaskDef taskDef = new TaskDef(taskName);
        taskDef.setOwnerEmail("test@conductor.io");
        taskDef.setRetryCount(0);
        taskDef.setConcurrentExecLimit(1);
        metadataClient.registerTaskDefs(List.of(taskDef));

        WorkflowTask branchA = new WorkflowTask();
        branchA.setName(taskName);
        branchA.setTaskReferenceName(taskName + "_a");
        branchA.setWorkflowTaskType(TaskType.SIMPLE);

        WorkflowTask branchB = new WorkflowTask();
        branchB.setName(taskName);
        branchB.setTaskReferenceName(taskName + "_b");
        branchB.setWorkflowTaskType(TaskType.SIMPLE);

        WorkflowTask forkTask = new WorkflowTask();
        forkTask.setName("fork");
        forkTask.setTaskReferenceName("fork_ref");
        forkTask.setWorkflowTaskType(TaskType.FORK_JOIN);
        List<List<WorkflowTask>> forkBranches = new ArrayList<>();
        forkBranches.add(List.of(branchA));
        forkBranches.add(List.of(branchB));
        forkTask.setForkTasks(forkBranches);

        WorkflowTask joinTask = new WorkflowTask();
        joinTask.setName("join");
        joinTask.setTaskReferenceName("join_ref");
        joinTask.setWorkflowTaskType(TaskType.JOIN);
        joinTask.setJoinOn(List.of(taskName + "_a", taskName + "_b"));

        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName(workflowName);
        workflowDef.setOwnerEmail("test@conductor.io");
        workflowDef.setVersion(1);
        workflowDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.ALERT_ONLY);
        workflowDef.setTimeoutSeconds(0);
        workflowDef.setTasks(List.of(forkTask, joinTask));
        // updateWorkflowDefs overwrites; registerWorkflowDef would 409 on re-runs.
        metadataClient.updateWorkflowDefs(List.of(workflowDef));

        StartWorkflowRequest start = new StartWorkflowRequest();
        start.setName(workflowName);
        start.setVersion(1);
        String workflowId = workflowClient.startWorkflow(start);
        log.info("Started workflow {} for concurrency wakeup test", workflowId);

        // Step 1: poll first task - should get exactly one because concurrentExecLimit=1.
        List<Task> first = pollUntilNonEmpty(taskName, 10);
        assertEquals(
                1,
                first.size(),
                "Expected exactly one task on first poll due to concurrentExecLimit=1");
        Task taskA = first.get(0);

        // Step 2: poll again - should be empty because B fails exceedsInProgressLimit and gets
        // postponed. Drain stragglers for a short window to ensure the postpone is firmly placed.
        long postponePlacedDeadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < postponePlacedDeadline) {
            List<Task> drain = taskClient.batchPollTasksByTaskType(taskName, "worker", 10, 500);
            if (drain != null && !drain.isEmpty()) {
                fail("Did not expect a second task while A is in progress: " + drain);
            }
        }

        // Step 3: complete A. This frees the slot and (post-fix) releases B's postpone.
        long completedAt = System.currentTimeMillis();
        taskClient.updateTaskSync(
                workflowId,
                taskA.getReferenceTaskName(),
                TaskResult.Status.COMPLETED,
                Map.of("updatedBy", "e2e"));
        log.info("Completed task A at t={}, now polling for B...", completedAt);

        // Step 4: B should now be pollable quickly. Allow POLL_WINDOW_SECONDS; pre-fix the
        // bug forces the full postpone duration.
        Task taskB = null;
        long deadline = completedAt + TimeUnit.SECONDS.toMillis(POLL_WINDOW_SECONDS);
        while (System.currentTimeMillis() < deadline) {
            List<Task> polled = taskClient.batchPollTasksByTaskType(taskName, "worker", 1, 500);
            if (polled != null && !polled.isEmpty()) {
                taskB = polled.get(0);
                break;
            }
        }
        long waitedMs = System.currentTimeMillis() - completedAt;
        log.info(
                "Polled for B for {} ms; got task={}",
                waitedMs,
                taskB != null ? taskB.getTaskId() : "<none>");
        assertNotNull(
                taskB,
                "Task B should be pollable within "
                        + POLL_WINDOW_SECONDS
                        + "s of A completing, but no task was returned (waited "
                        + waitedMs
                        + " ms). This indicates the postpone offset was not reset when the "
                        + "concurrencyLimit slot was freed.");

        // Cleanup: complete B and let the workflow finish.
        taskClient.updateTaskSync(
                workflowId,
                taskB.getReferenceTaskName(),
                TaskResult.Status.COMPLETED,
                Map.of("updatedBy", "e2e"));
        await().atMost(20, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(
                        () ->
                                assertEquals(
                                        Workflow.WorkflowStatus.COMPLETED,
                                        workflowClient.getWorkflow(workflowId, false).getStatus()));
    }

    /**
     * Reads {@code conductor.app.taskExecutionPostponeDuration} from the running server. The
     * endpoint reports only overridden properties, so an absent value means the server default is
     * in effect.
     */
    private static long configuredPostponeSeconds() {
        Map<String, Object> config =
                ApiUtil.CLIENT
                        .execute(
                                ConductorClientRequest.builder()
                                        .method(Method.GET)
                                        .path("/admin/config")
                                        .build(),
                                new TypeReference<Map<String, Object>>() {})
                        .getData();
        Object raw =
                config == null ? null : config.get("conductor.app.taskExecutionPostponeDuration");
        return raw == null ? DEFAULT_POSTPONE_SECONDS : parseDurationSeconds(raw.toString());
    }

    /** Accepts the ISO-8601 and the {@code @DurationUnit(SECONDS)} bare-number/suffix forms. */
    private static long parseDurationSeconds(String value) {
        String trimmed = value.trim();
        try {
            if (trimmed.regionMatches(true, 0, "PT", 0, 2)) {
                return Duration.parse(trimmed).getSeconds();
            }
            Matcher matcher = DURATION_PATTERN.matcher(trimmed);
            if (!matcher.matches()) {
                return DEFAULT_POSTPONE_SECONDS;
            }
            long amount = Long.parseLong(matcher.group(1));
            String unit = matcher.group(2);
            if (unit == null) {
                return amount; // @DurationUnit(SECONDS) on the server property
            }
            return switch (unit.toLowerCase()) {
                case "ms" -> amount / 1000;
                case "m" -> amount * 60;
                case "h" -> amount * 3600;
                case "d" -> amount * 86400;
                default -> amount;
            };
        } catch (RuntimeException e) {
            log.warn("Could not parse postpone duration '{}'; assuming server default", value, e);
            return DEFAULT_POSTPONE_SECONDS;
        }
    }

    private List<Task> pollUntilNonEmpty(String taskName, int timeoutSecs) {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeoutSecs);
        while (System.currentTimeMillis() < deadline) {
            List<Task> polled = taskClient.batchPollTasksByTaskType(taskName, "worker", 1, 500);
            if (polled != null && !polled.isEmpty()) {
                return polled;
            }
        }
        return List.of();
    }
}
