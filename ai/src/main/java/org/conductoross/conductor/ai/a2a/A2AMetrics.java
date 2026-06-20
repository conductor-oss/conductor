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

import com.netflix.conductor.metrics.Monitors;

/**
 * A2A metric emission, centralized so names and tags stay consistent and low-cardinality.
 *
 * <p>Counters are published through the shared {@link Monitors} registry (the same one the rest of
 * the engine uses), so they show up alongside core Conductor metrics in whatever backend is wired
 * in (Prometheus, Datadog, …). Tag values are always drawn from bounded sets — never agent URLs,
 * workflow ids, or message ids — to avoid cardinality blow-ups.
 *
 * <table>
 *   <caption>Metrics</caption>
 *   <tr><th>Name</th><th>Tags</th><th>Meaning</th></tr>
 *   <tr><td>{@code a2a_client_calls}</td><td>{@code result}</td>
 *       <td>A CALL_AGENT task reached a terminal status (result = the Conductor status).</td></tr>
 *   <tr><td>{@code a2a_client_poll_failures}</td><td>—</td>
 *       <td>A single transient {@code tasks/get} poll failed (before exhausting retries).</td></tr>
 *   <tr><td>{@code a2a_rpc_errors}</td><td>{@code method}, {@code terminal}</td>
 *       <td>A JSON-RPC/HTTP error from a remote agent, by method and whether it was fatal.</td></tr>
 *   <tr><td>{@code a2a_ssrf_blocked}</td><td>—</td>
 *       <td>An outbound agent URL was rejected by the SSRF guard.</td></tr>
 *   <tr><td>{@code a2a_server_requests}</td><td>{@code method}</td>
 *       <td>An inbound A2A JSON-RPC request to a workflow-backed agent, by method.</td></tr>
 *   <tr><td>{@code a2a_server_resumes}</td><td>—</td>
 *       <td>A follow-up message/send resumed a paused workflow (multi-turn).</td></tr>
 * </table>
 */
public final class A2AMetrics {

    private A2AMetrics() {}

    // ---- client (Conductor calling a remote agent) ------------------------------------------

    /** A CALL_AGENT task settled into a terminal Conductor status (e.g. completed/failed). */
    public static void clientCall(String result) {
        Monitors.getCounter("a2a_client_calls", "result", result).increment();
    }

    /** One transient poll failure during a {@code tasks/get} loop (retry not yet exhausted). */
    public static void clientPollFailure() {
        Monitors.getCounter("a2a_client_poll_failures").increment();
    }

    /** A remote agent returned a JSON-RPC or HTTP error for the given method. */
    public static void rpcError(String method, boolean terminal) {
        Monitors.getCounter(
                        "a2a_rpc_errors", "method", method, "terminal", String.valueOf(terminal))
                .increment();
    }

    /** An outbound agent URL was rejected by the SSRF guard. */
    public static void ssrfBlocked() {
        Monitors.getCounter("a2a_ssrf_blocked").increment();
    }

    // ---- server (Conductor exposed as an agent) ---------------------------------------------

    /** An inbound A2A JSON-RPC request was dispatched for the given method. */
    public static void serverRequest(String method) {
        Monitors.getCounter("a2a_server_requests", "method", method).increment();
    }

    /** A follow-up message/send resumed a paused (input-required) workflow. */
    public static void serverResume() {
        Monitors.getCounter("a2a_server_resumes").increment();
    }
}
