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

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import com.netflix.conductor.metrics.Monitors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class A2AObservabilityTest {

    // ---- metrics -----------------------------------------------------------------------------

    @Test
    void clientCall_incrementsCounterForResultTag() {
        double before = Monitors.getCounter("a2a_client_calls", "result", "completed").count();
        A2AMetrics.clientCall("completed");
        double after = Monitors.getCounter("a2a_client_calls", "result", "completed").count();
        assertEquals(before + 1, after, 0.0001);
    }

    @Test
    void rpcError_tagsMethodAndTerminal() {
        double before =
                Monitors.getCounter("a2a_rpc_errors", "method", "tasks/get", "terminal", "true")
                        .count();
        A2AMetrics.rpcError("tasks/get", true);
        double after =
                Monitors.getCounter("a2a_rpc_errors", "method", "tasks/get", "terminal", "true")
                        .count();
        assertEquals(before + 1, after, 0.0001);
    }

    @Test
    void ssrfBlocked_andServerResume_increment() {
        double ssrfBefore = Monitors.getCounter("a2a_ssrf_blocked").count();
        A2AMetrics.ssrfBlocked();
        assertEquals(ssrfBefore + 1, Monitors.getCounter("a2a_ssrf_blocked").count(), 0.0001);

        double resumeBefore = Monitors.getCounter("a2a_server_resumes").count();
        A2AMetrics.serverResume();
        assertEquals(resumeBefore + 1, Monitors.getCounter("a2a_server_resumes").count(), 0.0001);
    }

    // ---- MDC ---------------------------------------------------------------------------------

    @Test
    void mdcScope_setsKeysAndRemovesThemOnClose() {
        assertNull(MDC.get(A2ALogging.WORKFLOW_ID));
        try (A2ALogging.Scope scope =
                A2ALogging.of(
                        A2ALogging.WORKFLOW_ID,
                        "wf-1",
                        A2ALogging.TASK_ID,
                        null)) { // null value is skipped
            assertEquals("wf-1", MDC.get(A2ALogging.WORKFLOW_ID));
            assertNull(MDC.get(A2ALogging.TASK_ID));
            scope.add(A2ALogging.REMOTE_TASK_ID, "remote-1");
            assertEquals("remote-1", MDC.get(A2ALogging.REMOTE_TASK_ID));
        }
        // Every key this scope set is gone — no leak onto the next task on a pooled thread.
        assertNull(MDC.get(A2ALogging.WORKFLOW_ID));
        assertNull(MDC.get(A2ALogging.REMOTE_TASK_ID));
    }
}
