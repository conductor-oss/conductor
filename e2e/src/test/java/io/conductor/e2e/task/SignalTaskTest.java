/*
 * Copyright 2025 Conductor Authors.
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
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;

import io.conductor.e2e.util.ApiUtil;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

public class SignalTaskTest {

    static WorkflowClient workflowClient;
    static MetadataClient metadataClient;
    static OkHttpClient httpClient;

    private static final MediaType JSON = MediaType.get("application/json");

    @BeforeAll
    static void init() {
        workflowClient = ApiUtil.WORKFLOW_CLIENT;
        metadataClient = ApiUtil.METADATA_CLIENT;
        httpClient = new OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build();
    }

    /** Registers and starts a workflow that blocks immediately on an indefinite WAIT task. */
    private String startBlockedWorkflow(String wfName) {
        WorkflowTask waitTask = new WorkflowTask();
        waitTask.setType("WAIT");
        waitTask.setName("wait_for_signal");
        waitTask.setTaskReferenceName("wait_ref");

        WorkflowDef def = new WorkflowDef();
        def.setName(wfName);
        def.setVersion(1);
        def.setOwnerEmail("test@conductor.io");
        def.setTasks(List.of(waitTask));
        metadataClient.updateWorkflowDefs(List.of(def));

        StartWorkflowRequest req = new StartWorkflowRequest();
        req.setName(wfName);
        req.setVersion(1);
        String workflowId = workflowClient.startWorkflow(req);

        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(
                        () -> {
                            Workflow wf = workflowClient.getWorkflow(workflowId, true);
                            assertFalse(wf.getTasks().isEmpty());
                            assertEquals(Task.Status.IN_PROGRESS, wf.getTasks().get(0).getStatus());
                        });

        return workflowId;
    }

    @Test
    @DisplayName("async signal completes the WAIT task and advances the workflow")
    public void testAsyncSignalCompletesWaitTask() throws IOException {
        String wfName = "signal_e2e_async_" + System.nanoTime();
        String workflowId = startBlockedWorkflow(wfName);

        Request req =
                new Request.Builder()
                        .url(ApiUtil.SERVER_ROOT_URI + "/tasks/" + workflowId + "/COMPLETED/signal")
                        .post(RequestBody.create("{}", JSON))
                        .build();
        try (Response response = httpClient.newCall(req).execute()) {
            assertEquals(200, response.code(), "Expected 200 from async signal");
        }

        await().atMost(15, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(
                        () -> {
                            Workflow wf = workflowClient.getWorkflow(workflowId, true);
                            assertEquals(
                                    Task.Status.COMPLETED,
                                    wf.getTasks().get(0).getStatus(),
                                    "WAIT task should be COMPLETED after signal");
                        });
    }

    @Test
    @DisplayName("sync signal returns a SignalResponse containing the target workflow id")
    public void testSyncSignalReturnsWorkflowResponse() throws IOException {
        String wfName = "signal_e2e_sync_" + System.nanoTime();
        String workflowId = startBlockedWorkflow(wfName);

        Request req =
                new Request.Builder()
                        .url(
                                ApiUtil.SERVER_ROOT_URI
                                        + "/tasks/"
                                        + workflowId
                                        + "/COMPLETED/signal/sync")
                        .post(RequestBody.create("{}", JSON))
                        .build();
        try (Response response = httpClient.newCall(req).execute()) {
            assertEquals(200, response.code(), "Expected 200 from sync signal");
            String body = response.body().string();
            assertNotNull(body);
            assertTrue(
                    body.contains("\"targetWorkflowId\""),
                    "Response must contain targetWorkflowId field, got: " + body);
            assertTrue(
                    body.contains(workflowId),
                    "Response must echo back the workflow id, got: " + body);
            assertFalse(
                    body.contains("\"signalTimeout\":true"),
                    "signalTimeout should not be true for a successful signal, got: " + body);
        }
    }

    @Test
    @DisplayName("async signal on non-existent workflow returns 404")
    public void testAsyncSignalWorkflowNotFound() throws IOException {
        Request req =
                new Request.Builder()
                        .url(ApiUtil.SERVER_ROOT_URI + "/tasks/non-existent-wf-id/COMPLETED/signal")
                        .post(RequestBody.create("{}", JSON))
                        .build();
        try (Response response = httpClient.newCall(req).execute()) {
            assertEquals(404, response.code(), "Expected 404 for non-existent workflow");
        }
    }

    @Test
    @DisplayName("sync signal on non-existent workflow returns 404")
    public void testSyncSignalWorkflowNotFound() throws IOException {
        Request req =
                new Request.Builder()
                        .url(
                                ApiUtil.SERVER_ROOT_URI
                                        + "/tasks/non-existent-wf-id/COMPLETED/signal/sync")
                        .post(RequestBody.create("{}", JSON))
                        .build();
        try (Response response = httpClient.newCall(req).execute()) {
            assertEquals(404, response.code(), "Expected 404 for non-existent workflow");
        }
    }
}
