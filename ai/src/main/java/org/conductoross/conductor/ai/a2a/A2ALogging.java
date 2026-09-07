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

import java.util.ArrayList;
import java.util.List;

import org.slf4j.MDC;

/**
 * MDC correlation keys for A2A code paths. A {@link Scope} sets a batch of keys and removes exactly
 * those it set on close, so log lines emitted while calling (or being called by) a remote agent can
 * be grepped by workflow, task, remote task, or context id — and no key leaks onto the next task to
 * run on a pooled worker thread.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * try (A2ALogging.Scope scope = A2ALogging.of(A2ALogging.WORKFLOW_ID, wfId, A2ALogging.TASK_ID, id)) {
 *     scope.add(A2ALogging.REMOTE_TASK_ID, agentTaskId); // once it's known
 *     ...
 * }
 * }</pre>
 */
public final class A2ALogging {

    public static final String WORKFLOW_ID = "a2aWorkflowId";
    public static final String TASK_ID = "a2aTaskId";
    public static final String REF = "a2aRef";
    public static final String REMOTE_TASK_ID = "a2aRemoteTaskId";
    public static final String CONTEXT_ID = "a2aContextId";
    public static final String MESSAGE_ID = "a2aMessageId";
    public static final String AGENT = "a2aAgent";
    public static final String METHOD = "a2aMethod";

    private A2ALogging() {}

    /** Open a scope pre-loaded with alternating key/value pairs (null keys/values are skipped). */
    public static Scope of(String... kv) {
        Scope scope = new Scope();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            scope.add(kv[i], kv[i + 1]);
        }
        return scope;
    }

    /** An auto-closeable set of MDC keys; {@link #close()} removes only the keys this scope set. */
    public static final class Scope implements AutoCloseable {
        private final List<String> keys = new ArrayList<>();

        /** Set an MDC key (no-op if key or value is null); removed on {@link #close()}. */
        public Scope add(String key, String value) {
            if (key != null && value != null) {
                MDC.put(key, value);
                keys.add(key);
            }
            return this;
        }

        @Override
        public void close() {
            keys.forEach(MDC::remove);
        }
    }
}
