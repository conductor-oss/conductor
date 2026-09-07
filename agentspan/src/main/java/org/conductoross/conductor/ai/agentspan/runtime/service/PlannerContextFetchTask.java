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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

/**
 * /dg #4: HTTP fetch task for PLAN_EXECUTE {@code plannerContext} URL entries with an in-process
 * TTL cache and {@code If-None-Match} conditional-GET support.
 *
 * <p>Previously the compiler emitted a plain Conductor {@code HTTP} task per URL — every planner
 * invocation made a fresh GET regardless of whether the doc had changed since the last run. On a
 * hot pipeline (dozens of plans/minute) that's dozens of identical GETs/minute against the upstream
 * doc CMS. A doc-host outage stalled every plan for the full read timeout, sequentially per URL.
 *
 * <p>This task:
 *
 * <ul>
 *   <li>Caches responses in-process keyed on {@code (url, sorted-headers)} with a per-entry TTL
 *       (default 60s).
 *   <li>Sends {@code If-None-Match} when a previous {@code ETag} is in cache; a 304 refreshes the
 *       cache TTL without re-downloading the body.
 *   <li>Bounded cache: ~1000 entries with LRU eviction (LinkedHashMap access-order). Sufficient for
 *       typical N≤dozens-of-distinct-URLs workloads — adjust upward if telemetry shows high evict
 *       rate.
 *   <li>Surfaces {@code cache_hit} on the output so observability can distinguish fresh fetches
 *       from cached returns.
 * </ul>
 *
 * <p>The output shape mirrors Conductor's built-in HTTP task — {@code response.body}, {@code
 * response.statusCode} — so the downstream {@code _ctx_build} INLINE (which reads {@code
 * ${fetchRef.output.response.body}}) works without changes.
 *
 * <p>Concurrency: cache is a {@code synchronized} access-order {@code LinkedHashMap}. Lock is held
 * only for the get + size-bound check; HTTP I/O happens outside any lock. Cache reads are O(1).
 */
public class PlannerContextFetchTask extends WorkflowSystemTask {

    public static final String TASK_TYPE = "PLANNER_CONTEXT_FETCH";

    private static final Logger logger = LoggerFactory.getLogger(PlannerContextFetchTask.class);
    private static final int DEFAULT_TTL_SECONDS = 60;
    private static final int MAX_CACHE_ENTRIES = 1024;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Cache entry. {@code expiresAtMillis} is checked on read; entries past it are treated as
     * misses (but the {@code etag} survives so a conditional GET can revalidate).
     */
    private static final class Entry {
        final String body;
        final int statusCode;
        final String etag;
        final long expiresAtMillis;

        Entry(String body, int statusCode, String etag, long expiresAtMillis) {
            this.body = body;
            this.statusCode = statusCode;
            this.etag = etag;
            this.expiresAtMillis = expiresAtMillis;
        }
    }

    /**
     * Access-order LinkedHashMap with size-bounded eviction. The {@code synchronized} wrapper makes
     * it safe to call from concurrent task workers; held only for cache get/put, never across HTTP
     * I/O.
     */
    private final Map<String, Entry> cache;

    private final HttpClient httpClient;

    public PlannerContextFetchTask() {
        this(
                HttpClient.newBuilder()
                        .connectTimeout(CONNECT_TIMEOUT)
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build());
    }

    /** Visible-for-testing constructor with an injectable {@link HttpClient}. */
    PlannerContextFetchTask(HttpClient httpClient) {
        super(TASK_TYPE);
        this.httpClient = httpClient;
        this.cache =
                Collections.synchronizedMap(
                        new LinkedHashMap<String, Entry>(64, 0.75f, true) {
                            @Override
                            protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
                                return size() > MAX_CACHE_ENTRIES;
                            }
                        });
        logger.debug("PlannerContextFetchTask registered (task type={})", TASK_TYPE);
    }

    /** Visible-for-testing: clear the in-process cache. */
    void clearCache() {
        cache.clear();
    }

    @Override
    public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
        Map<String, Object> input = task.getInputData() == null ? Map.of() : task.getInputData();
        String url = Objects.toString(input.get("url"), null);
        if (url == null || url.isBlank()) {
            fail(task, "Missing required input parameter 'url'");
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, String> headers =
                input.get("headers") instanceof Map
                        ? (Map<String, String>) input.get("headers")
                        : Map.of();

        int ttlSeconds = parseIntOr(input.get("ttl_seconds"), DEFAULT_TTL_SECONDS);
        boolean required = !Boolean.FALSE.equals(input.get("required"));

        String cacheKey = cacheKey(url, headers);
        long now = System.currentTimeMillis();

        Entry hit;
        synchronized (cache) {
            hit = cache.get(cacheKey);
        }
        if (hit != null && hit.expiresAtMillis > now) {
            // Cache hit within TTL — return without touching network.
            complete(task, hit.body, hit.statusCode, true);
            return;
        }

        // Cache miss or expired — fetch (with If-None-Match if we have
        // a stale entry with an etag).
        try {
            HttpRequest.Builder reqBuilder =
                    HttpRequest.newBuilder().uri(URI.create(url)).timeout(READ_TIMEOUT).GET();
            for (Map.Entry<String, String> h : headers.entrySet()) {
                reqBuilder.header(h.getKey(), h.getValue());
            }
            if (hit != null && hit.etag != null && !hit.etag.isEmpty()) {
                reqBuilder.header("If-None-Match", hit.etag);
            }
            HttpResponse<String> resp =
                    httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());

            String newEtag =
                    resp.headers().firstValue("ETag").orElse(hit != null ? hit.etag : null);
            int status = resp.statusCode();

            // 304: cached body still valid — refresh TTL.
            if (status == 304 && hit != null) {
                long expiresAt = now + Math.max(1, ttlSeconds) * 1000L;
                Entry refreshed = new Entry(hit.body, hit.statusCode, newEtag, expiresAt);
                synchronized (cache) {
                    cache.put(cacheKey, refreshed);
                }
                complete(task, hit.body, hit.statusCode, true);
                return;
            }

            // 2xx: replace cache. 4xx/5xx: do not cache (so transient
            // errors don't poison the cache for the full TTL).
            if (status >= 200 && status < 300) {
                long expiresAt = now + Math.max(1, ttlSeconds) * 1000L;
                Entry fresh = new Entry(resp.body(), status, newEtag, expiresAt);
                synchronized (cache) {
                    cache.put(cacheKey, fresh);
                }
                complete(task, resp.body(), status, false);
                return;
            }

            // Non-2xx, non-304. If required, fail the task. If not
            // required (and we have nothing cached), still surface the
            // status so the downstream INLINE can render
            // ``[doc unavailable]`` cleanly.
            if (required) {
                fail(task, "PLANNER_CONTEXT_FETCH " + url + " returned status " + status);
                return;
            }
            complete(task, "", status, false);
        } catch (Exception e) {
            // /dg #4: a doc-host outage on a required doc fails the
            // workflow loudly. Non-required falls through to a clean
            // ``[doc unavailable]`` marker via the INLINE.
            if (required) {
                fail(task, "PLANNER_CONTEXT_FETCH " + url + " failed: " + e.getMessage());
                return;
            }
            complete(task, "", 0, false);
        }
    }

    /** Stable cache key: URL + headers sorted by name. */
    private static String cacheKey(String url, Map<String, String> headers) {
        if (headers.isEmpty()) return url;
        // Sort to make order-independent — the same URL + headers in a
        // different insertion order is the same logical request.
        TreeMap<String, String> sorted = new TreeMap<>(headers);
        StringBuilder sb = new StringBuilder(url).append(' ');
        for (Map.Entry<String, String> h : sorted.entrySet()) {
            sb.append(h.getKey()).append('=').append(h.getValue()).append('');
        }
        return sb.toString();
    }

    private static int parseIntOr(Object v, int fallback) {
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static void complete(TaskModel task, String body, int statusCode, boolean cacheHit) {
        // Output shape mirrors Conductor's built-in HTTP task so the
        // downstream INLINE template ``${ref.output.response.body}``
        // continues to work without changes.
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("body", body);
        response.put("statusCode", statusCode);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("response", response);
        out.put("cache_hit", cacheHit);
        task.setOutputData(out);
        task.setStatus(TaskModel.Status.COMPLETED);
    }

    private static void fail(TaskModel task, String reason) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("error", reason);
        task.setOutputData(out);
        task.setReasonForIncompletion(reason);
        task.setStatus(TaskModel.Status.FAILED);
    }
}
