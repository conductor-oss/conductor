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
package org.conductoross.conductor.ai.a2a;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.conductoross.conductor.ai.a2a.EmbeddedA2AAgent.SendMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.model.TaskModel;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Durability test-harness — validates the proof obligations from {@code
 * design/a2a/09-durable-a2a.md} by injecting failures against a real embedded A2A agent and the
 * real {@link CallAgentTask} / {@link A2AService} logic.
 *
 * <table>
 *   <tr><td>T1</td><td>{@link #t1_crashRecovery_resumesOnAFreshInstance()}</td><td>P1 crash-safe resume</td></tr>
 *   <tr><td>T2/T7</td><td>{@link #t2_messageId_isStableAcrossRetries()}</td><td>P3 idempotency key stable across retries/restart</td></tr>
 *   <tr><td>T3</td><td>{@link #t3_messageId_distinctPerIteration()}</td><td>P3 distinct per loop iteration</td></tr>
 *   <tr><td>T4a</td><td>{@link #t4_deadAgent_failsWithinFailureCap()}</td><td>P2 liveness — failure cap</td></tr>
 *   <tr><td>T4b</td><td>{@link #t4_deadline_failsTerminally()}</td><td>P2 liveness — absolute deadline</td></tr>
 *   <tr><td>T5</td><td>{@link #t5_pushBackstop_completesWithoutWebhook()}</td><td>P2 push backstop poll</td></tr>
 * </table>
 *
 * <p>SSRF validation is bypassed because the embedded agent uses loopback — intentional for tests.
 */
class A2ADurabilityTest {

    private EmbeddedA2AAgent agent;
    private OkHttpClient client;
    private Environment environment;

    @BeforeEach
    void setUp() throws Exception {
        agent = new EmbeddedA2AAgent();
        client =
                new OkHttpClient.Builder()
                        .connectTimeout(2, TimeUnit.SECONDS)
                        .readTimeout(5, TimeUnit.SECONDS)
                        .build();
        environment = mock(Environment.class);
    }

    @AfterEach
    void tearDown() {
        agent.close();
    }

    private A2AService newService() {
        // Fresh service instance — also models "a different server picking the task up".
        A2AService service = spy(new A2AService(client));
        doNothing().when(service).validateAgentUrl(anyString());
        return service;
    }

    private CallAgentTask newTask(A2AService service) {
        return new CallAgentTask(service, environment);
    }

    private TaskModel taskModel(
            String taskId, String wf, String ref, int iteration, Map<String, Object> extra) {
        TaskModel model = new TaskModel();
        model.setTaskId(taskId);
        model.setWorkflowInstanceId(wf);
        model.setReferenceTaskName(ref);
        model.setIteration(iteration);
        Map<String, Object> input = new HashMap<>();
        input.put("agentUrl", agent.url());
        input.put("text", "convert 100 USD");
        if (extra != null) {
            input.putAll(extra);
        }
        model.setInputData(input);
        return model;
    }

    // ---- T1: crash recovery -------------------------------------------------------------------

    @Test
    void t1_crashRecovery_resumesOnAFreshInstance() {
        agent.sendMode(SendMode.TASK_WORKING).completeAfterPolls(2).text("done");

        // Instance #1 starts the call; the remote task is created and we go IN_PROGRESS.
        TaskModel model = taskModel("t1", "wf-1", "agent", 0, null);
        newTask(newService()).start(null, model, null);
        assertEquals(TaskModel.Status.IN_PROGRESS, model.getStatus());
        assertEquals(EmbeddedA2AAgent.AGENT_TASK_ID, model.getOutputData().get("taskId"));

        // "Restart": a brand-new service + task instance resumes purely from the persisted
        // TaskModel state (its output carries the remote taskId).
        CallAgentTask afterRestart = newTask(newService());
        int guard = 0;
        while (model.getStatus() == TaskModel.Status.IN_PROGRESS && guard++ < 20) {
            afterRestart.execute(null, model, null);
        }
        assertEquals(TaskModel.Status.COMPLETED, model.getStatus());
        assertEquals("done", model.getOutputData().get("text"));
    }

    @Test
    void t1b_crashRecovery_survivesPersistenceRoundTrip() throws Exception {
        agent.sendMode(SendMode.TASK_WORKING).completeAfterPolls(2).text("recovered");

        // Instance #1 starts the call → IN_PROGRESS, remote taskId recorded in the task output.
        TaskModel before = taskModel("t1b", "wf-1", "agent", 0, null);
        newTask(newService()).start(null, before, null);
        assertEquals(TaskModel.Status.IN_PROGRESS, before.getStatus());

        // Cross the exact persistence boundary the engine crosses on restart: the durable task
        // state (input + output maps) is serialized to JSON — as the execution DAO stores it —
        // and a COLD TaskModel is reconstructed from that JSON alone (no in-memory carry-over).
        ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();
        String inputJson = objectMapper.writeValueAsString(before.getInputData());
        String outputJson = objectMapper.writeValueAsString(before.getOutputData());

        TaskModel restored = new TaskModel();
        restored.setTaskId(before.getTaskId());
        restored.setInputData(objectMapper.readValue(inputJson, Map.class));
        restored.addOutput(objectMapper.readValue(outputJson, Map.class));
        restored.setStatus(TaskModel.Status.IN_PROGRESS);

        // A fresh service + task instance (a "restarted" worker) resumes from the restored state.
        CallAgentTask afterRestart = newTask(newService());
        int guard = 0;
        while (restored.getStatus() == TaskModel.Status.IN_PROGRESS && guard++ < 20) {
            afterRestart.execute(null, restored, null);
        }

        assertEquals(TaskModel.Status.COMPLETED, restored.getStatus());
        assertEquals("recovered", restored.getOutputData().get("text"));
    }

    // ---- T2 / T7: deterministic, retry-stable messageId ---------------------------------------

    @Test
    void t2_messageId_isStableAcrossRetries() {
        agent.sendMode(SendMode.TASK_COMPLETED);

        // Attempt 1 (taskId t-a) and a "retry" (taskId t-b) — Conductor mints a new taskId per
        // retry but keeps the same workflowId + referenceTaskName + iteration.
        TaskModel attempt1 = taskModel("t-a", "wf-9", "callAgent", 0, null);
        newTask(newService()).start(null, attempt1, null);
        String id1 = agent.lastMessageId();

        TaskModel attempt2 = taskModel("t-b", "wf-9", "callAgent", 0, null);
        newTask(newService()).start(null, attempt2, null);
        String id2 = agent.lastMessageId();

        assertNotNull(id1);
        assertEquals(id1, id2, "retry must re-send the same messageId (idempotency key)");
    }

    @Test
    void t3_messageId_distinctPerIteration() {
        agent.sendMode(SendMode.TASK_COMPLETED);

        TaskModel iter0 = taskModel("t-0", "wf-9", "callAgent", 0, null);
        newTask(newService()).start(null, iter0, null);
        String id0 = agent.lastMessageId();

        TaskModel iter1 = taskModel("t-1", "wf-9", "callAgent", 1, null);
        newTask(newService()).start(null, iter1, null);
        String id1 = agent.lastMessageId();

        assertNotEquals(id0, id1, "different DO_WHILE iterations must use distinct messageIds");
    }

    @Test
    void t2_callerCanOverrideMessageId() {
        agent.sendMode(SendMode.TASK_COMPLETED);
        Map<String, Object> message = new HashMap<>();
        message.put("messageId", "caller-supplied-id");
        message.put("parts", java.util.List.of(Map.of("kind", "text", "text", "hi")));
        TaskModel model = taskModel("t-x", "wf-9", "callAgent", 0, Map.of("message", message));

        newTask(newService()).start(null, model, null);

        assertEquals("caller-supplied-id", agent.lastMessageId());
    }

    // ---- T4: liveness — must not hang forever -------------------------------------------------

    @Test
    void t4_deadAgent_failsWithinFailureCap() {
        // Send succeeds (task working), then the agent goes dark on every poll.
        agent.sendMode(SendMode.TASK_WORKING).failGetTask(true);

        TaskModel model = taskModel("t4", "wf-1", "agent", 0, Map.of("maxPollFailures", 3));
        CallAgentTask task = newTask(newService());
        task.start(null, model, null);
        assertEquals(TaskModel.Status.IN_PROGRESS, model.getStatus());

        int guard = 0;
        while (model.getStatus() == TaskModel.Status.IN_PROGRESS && guard++ < 50) {
            task.execute(null, model, null);
        }

        assertEquals(
                TaskModel.Status.FAILED_WITH_TERMINAL_ERROR,
                model.getStatus(),
                "a dead agent must drive the task terminal, not poll forever");
        assertTrue(guard <= 5, "should give up near the failure cap, not loop the guard");
    }

    @Test
    void t4_deadline_failsTerminally() {
        agent.sendMode(SendMode.TASK_WORKING).completeAfterPolls(1000); // never completes in time

        TaskModel model = taskModel("t4b", "wf-1", "agent", 0, Map.of("maxDurationSeconds", 1));
        CallAgentTask task = newTask(newService());
        task.start(null, model, null);
        // Force the deadline to be in the past (simulate elapsed time deterministically).
        model.addOutput(A2AResults.KEY_STARTED_AT, System.currentTimeMillis() - 5000);

        task.execute(null, model, null);

        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, model.getStatus());
        assertTrue(
                model.getReasonForIncompletion().contains("max duration"),
                model.getReasonForIncompletion());
    }

    // ---- T5: durable push — backstop poll completes even with no webhook ----------------------

    @Test
    void t5_pushBackstop_completesWithoutWebhook() {
        when(environment.getProperty(CallAgentTask.CALLBACK_URL_PROPERTY))
                .thenReturn("https://conductor.example.com");
        // Remote returns "working" on send, "completed" on the first poll. No webhook ever fires.
        agent.sendMode(SendMode.TASK_WORKING).completeAfterPolls(0).text("via-backstop");

        TaskModel model =
                taskModel(
                        "t5",
                        "wf-1",
                        "agent",
                        0,
                        Map.of("pushNotification", true, "pushBackstopPollSeconds", 7));
        CallAgentTask task = newTask(newService());

        task.start(null, model, null);
        assertEquals(TaskModel.Status.IN_PROGRESS, model.getStatus());

        // Push mode must poll at the slow backstop cadence, not the fast default.
        assertEquals(7L, task.getEvaluationOffset(model, 30).orElseThrow());

        // The backstop poll completes the task even though no webhook was delivered.
        task.execute(null, model, null);
        assertEquals(TaskModel.Status.COMPLETED, model.getStatus());
        assertEquals("via-backstop", model.getOutputData().get("text"));
    }
}
