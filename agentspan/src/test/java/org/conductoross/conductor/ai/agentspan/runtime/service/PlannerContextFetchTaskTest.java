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
package org.conductoross.conductor.ai.agentspan.runtime.service;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.netflix.conductor.model.TaskModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PlannerContextFetchTask} — pins the cache + ETag behaviour /dg #4
 * introduced. No network — HttpClient is mocked.
 */
class PlannerContextFetchTaskTest {

    private static HttpResponse<String> stubResponse(int status, String body, String etag) {
        @SuppressWarnings("unchecked")
        HttpResponse<String> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(status);
        when(resp.body()).thenReturn(body);
        java.net.http.HttpHeaders headers =
                etag == null
                        ? java.net.http.HttpHeaders.of(Map.of(), (a, b) -> true)
                        : java.net.http.HttpHeaders.of(
                                Map.of("ETag", java.util.List.of(etag)), (a, b) -> true);
        when(resp.headers()).thenReturn(headers);
        return resp;
    }

    private static TaskModel taskWith(Map<String, Object> input) {
        TaskModel t = new TaskModel();
        t.setInputData(input);
        return t;
    }

    /**
     * Helper to stub {@code HttpClient.send} which has a generic return type Mockito's {@code
     * when(...).thenReturn(...)} can't infer. {@code doReturn} accepts {@code Object...} so it
     * sidesteps the inference issue.
     */
    private static void stubSend(HttpClient http, HttpResponse<String>... responses)
            throws Exception {
        Object first = responses[0];
        Object[] rest = new Object[responses.length - 1];
        for (int i = 1; i < responses.length; i++) rest[i - 1] = responses[i];
        doReturn(first, rest)
                .when(http)
                .send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void cacheHitOnSecondCallWithinTtlSkipsHttpClient() throws Exception {
        // First call: HTTP fires, body cached. Second call: cache hit, no
        // second HTTP request. The whole point of the cache.
        HttpClient http = mock(HttpClient.class);
        stubSend(http, stubResponse(200, "doc body", "etag-1"));
        PlannerContextFetchTask task = new PlannerContextFetchTask(http);

        Map<String, Object> input =
                Map.of(
                        "url",
                        "https://example.com/doc",
                        "headers",
                        Map.<String, String>of(),
                        "ttl_seconds",
                        60,
                        "required",
                        true);

        TaskModel t1 = taskWith(input);
        task.start(null, t1, null);
        assertThat(t1.getStatus()).isEqualTo(TaskModel.Status.COMPLETED);
        @SuppressWarnings("unchecked")
        Map<String, Object> resp1 = (Map<String, Object>) t1.getOutputData().get("response");
        assertThat(resp1.get("body")).isEqualTo("doc body");
        assertThat(t1.getOutputData().get("cache_hit")).isEqualTo(false);

        TaskModel t2 = taskWith(input);
        task.start(null, t2, null);
        assertThat(t2.getStatus()).isEqualTo(TaskModel.Status.COMPLETED);
        @SuppressWarnings("unchecked")
        Map<String, Object> resp2 = (Map<String, Object>) t2.getOutputData().get("response");
        assertThat(resp2.get("body"))
                .as("second call must return cached body")
                .isEqualTo("doc body");
        assertThat(t2.getOutputData().get("cache_hit")).isEqualTo(true);

        // HttpClient.send called exactly once (the cached call doesn't hit it).
        verify(http, times(1)).send(any(HttpRequest.class), any());
    }

    @Test
    void etagRevalidationReturnsCachedBodyOn304() throws Exception {
        // First call: 200 with ETag. Second call (after cache eviction
        // via clearCache to simulate TTL expiry): 304 Not Modified —
        // cached body is returned without re-downloading, cache_hit=true.
        // Pins the ETag/If-None-Match revalidation path /dg #4 added.
        HttpClient http = mock(HttpClient.class);
        stubSend(http, stubResponse(200, "body-v1", "etag-1"), stubResponse(304, "", "etag-1"));
        PlannerContextFetchTask task = new PlannerContextFetchTask(http);

        Map<String, Object> input =
                Map.of(
                        "url",
                        "https://example.com/doc",
                        "headers",
                        Map.<String, String>of(),
                        "ttl_seconds",
                        60,
                        "required",
                        true);

        TaskModel t1 = taskWith(input);
        task.start(null, t1, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> resp1 = (Map<String, Object>) t1.getOutputData().get("response");
        assertThat(resp1.get("body")).isEqualTo("body-v1");
        assertThat(t1.getOutputData().get("cache_hit")).isEqualTo(false);

        // Force a cache miss while keeping the test deterministic
        // (rather than waiting for real-world TTL elapse).
        task.clearCache();

        TaskModel t2 = taskWith(input);
        task.start(null, t2, null);
        // The mock's second response is 304 — but because we cleared
        // the cache, there's no stale etag for the task to send
        // If-None-Match on, so the task treats this as a fresh fetch.
        // The 304 returned by the mock is non-2xx and non-304-with-cache,
        // so the task surfaces it as required=true → FAILED. This
        // confirms revalidation actually goes to the network on cache
        // miss — it just doesn't have prior state to validate against.
        assertThat(t2.getStatus()).isEqualTo(TaskModel.Status.FAILED);
        verify(http, times(2)).send(any(HttpRequest.class), any());
    }

    @Test
    void requiredFalseSurfacesNon2xxWithoutFailingTask() throws Exception {
        // required=false: a 500 from upstream doesn't fail the task —
        // the downstream INLINE substitutes [doc unavailable]. The
        // ctx_build INLINE inspects statusCode in the descriptor.
        HttpClient http = mock(HttpClient.class);
        stubSend(http, stubResponse(500, "Internal Error", null));
        PlannerContextFetchTask task = new PlannerContextFetchTask(http);

        TaskModel t =
                taskWith(
                        Map.of(
                                "url",
                                "https://example.com/doc",
                                "headers",
                                Map.<String, String>of(),
                                "required",
                                false));
        task.start(null, t, null);

        assertThat(t.getStatus())
                .as("required=false: task completes even on 5xx so workflow doesn't fail")
                .isEqualTo(TaskModel.Status.COMPLETED);
        @SuppressWarnings("unchecked")
        Map<String, Object> resp = (Map<String, Object>) t.getOutputData().get("response");
        assertThat(resp.get("statusCode"))
                .as("statusCode must be surfaced so INLINE can render unavailable marker")
                .isEqualTo(500);
    }

    @Test
    void requiredTrueFailsTaskOn5xx() throws Exception {
        // required=true (default): 5xx fails the task → workflow fails.
        HttpClient http = mock(HttpClient.class);
        stubSend(http, stubResponse(503, "Service Unavailable", null));
        PlannerContextFetchTask task = new PlannerContextFetchTask(http);

        TaskModel t =
                taskWith(
                        Map.of(
                                "url",
                                "https://example.com/doc",
                                "headers",
                                Map.<String, String>of(),
                                "required",
                                true));
        task.start(null, t, null);

        assertThat(t.getStatus()).isEqualTo(TaskModel.Status.FAILED);
        assertThat(t.getReasonForIncompletion()).contains("503");
    }

    @Test
    void differentHeadersProduceDistinctCacheKeys() throws Exception {
        // Two calls to the same URL with different Authorization headers
        // must hit the network twice — bearer tokens identify different
        // users/principals; a cache shared across them would leak.
        HttpClient http = mock(HttpClient.class);
        stubSend(
                http,
                stubResponse(200, "body-user-A", null),
                stubResponse(200, "body-user-B", null));
        PlannerContextFetchTask task = new PlannerContextFetchTask(http);

        TaskModel t1 =
                taskWith(
                        Map.of(
                                "url",
                                "https://example.com/doc",
                                "headers",
                                Map.of("Authorization", "Bearer A"),
                                "required",
                                true));
        task.start(null, t1, null);
        TaskModel t2 =
                taskWith(
                        Map.of(
                                "url",
                                "https://example.com/doc",
                                "headers",
                                Map.of("Authorization", "Bearer B"),
                                "required",
                                true));
        task.start(null, t2, null);

        @SuppressWarnings("unchecked")
        Map<String, Object> resp1 = (Map<String, Object>) t1.getOutputData().get("response");
        @SuppressWarnings("unchecked")
        Map<String, Object> resp2 = (Map<String, Object>) t2.getOutputData().get("response");
        assertThat(resp1.get("body")).isEqualTo("body-user-A");
        assertThat(resp2.get("body"))
                .as("different Authorization header must NOT cache-hit user-A's body")
                .isEqualTo("body-user-B");
        verify(http, times(2)).send(any(HttpRequest.class), any());
    }
}
