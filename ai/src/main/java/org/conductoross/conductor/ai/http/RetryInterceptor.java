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
package org.conductoross.conductor.ai.http;

import java.io.IOException;
import java.util.Random;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * OkHttp interceptor that retries requests on transient failures.
 *
 * <p>Retries on:
 *
 * <ul>
 *   <li>{@link IOException} — network failure, connection reset, timeout
 *   <li>HTTP 429 — rate limited; honours {@code Retry-After} header (seconds), falls back to
 *       exponential backoff
 *   <li>HTTP 5xx except 501 — server error (501 Not Implemented is non-transient)
 * </ul>
 *
 * <p>Backoff formula: {@code min(baseDelayMs * 2^attempt, 30_000ms) + uniform jitter [0, 500ms]}
 *
 * <p>If the request body is one-shot (cannot be replayed), no retry is attempted. On retry
 * exhaustion the last response is returned rather than throwing.
 */
public class RetryInterceptor implements Interceptor {

    private static final long MAX_DELAY_MS = 30_000L;
    private static final long JITTER_MAX_MS = 500L;
    private static final long DEFAULT_BASE_DELAY_MS = 1_000L;

    private final int maxRetries;
    private final long baseDelayMs;
    private final Random random = new Random();

    /**
     * Creates a {@code RetryInterceptor} with the default base delay of 1 second.
     *
     * @param maxRetries maximum number of retry attempts (not counting the initial request)
     */
    public RetryInterceptor(int maxRetries) {
        this(maxRetries, DEFAULT_BASE_DELAY_MS);
    }

    /**
     * Creates a {@code RetryInterceptor} with a custom base delay. Intended for testing so that
     * backoff waits can be reduced to near-zero.
     *
     * @param maxRetries maximum number of retry attempts (not counting the initial request)
     * @param baseDelayMs base delay in milliseconds used for exponential backoff
     */
    RetryInterceptor(int maxRetries, long baseDelayMs) {
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be >= 0, got: " + maxRetries);
        }
        this.maxRetries = maxRetries;
        this.baseDelayMs = baseDelayMs;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();

        // One-shot bodies cannot be replayed — skip retry entirely
        if (request.body() != null && request.body().isOneShot()) {
            return chain.proceed(request);
        }

        Response lastResponse = null;
        IOException lastException = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            // Close any previous non-successful response before retrying
            if (lastResponse != null) {
                lastResponse.close();
            }

            // Apply backoff delay for all attempts after the first
            if (attempt > 0) {
                long delayMs = computeDelay(attempt - 1, lastResponse);
                sleepUninterrupted(delayMs);
            }

            try {
                lastResponse = chain.proceed(request);
                lastException = null;
            } catch (IOException e) {
                lastException = e;
                lastResponse = null;
                // Will retry unless we've exhausted attempts
                continue;
            }

            if (!shouldRetry(lastResponse)) {
                return lastResponse;
            }
            // shouldRetry == true → loop continues
        }

        // Exhausted retries
        if (lastException != null) {
            throw lastException;
        }
        // lastResponse is non-null here (shouldRetry returned true for it)
        return lastResponse;
    }

    /**
     * Returns {@code true} when the response code represents a transient error that warrants a
     * retry.
     */
    private static boolean shouldRetry(Response response) {
        int code = response.code();
        if (code == 429) {
            return true;
        }
        // 5xx except 501 (Not Implemented — non-transient)
        if (code >= 500 && code < 600 && code != 501) {
            return true;
        }
        return false;
    }

    /**
     * Computes the delay before the next retry attempt.
     *
     * @param retryIndex zero-based index of the retry (0 = first retry)
     * @param response the last response received, or {@code null} on {@code IOException}
     * @return delay in milliseconds
     */
    private long computeDelay(int retryIndex, Response response) {
        // For 429, try to use Retry-After header first
        if (response != null && response.code() == 429) {
            String retryAfter = response.header("Retry-After");
            if (retryAfter != null) {
                try {
                    long seconds = Long.parseLong(retryAfter.trim());
                    if (seconds >= 0) {
                        return seconds * 1_000L + jitter();
                    }
                } catch (NumberFormatException ignored) {
                    // Fall through to exponential backoff
                }
            }
        }
        return exponentialBackoff(retryIndex);
    }

    private long exponentialBackoff(int retryIndex) {
        long exponential = baseDelayMs * (1L << retryIndex); // baseDelayMs * 2^retryIndex
        long capped = Math.min(exponential, MAX_DELAY_MS);
        return capped + jitter();
    }

    private long jitter() {
        return (long) (random.nextDouble() * JITTER_MAX_MS);
    }

    private static void sleepUninterrupted(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
