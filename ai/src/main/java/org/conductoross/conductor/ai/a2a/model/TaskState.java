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
package org.conductoross.conductor.ai.a2a.model;

import java.util.Locale;

/**
 * A2A task lifecycle states and helpers.
 *
 * <p>Constants use the A2A v0.3.x wire spelling (lowercase, e.g. {@code "input-required"}). {@link
 * #normalize(String)} additionally tolerates the v1.0 ProtoJSON spelling ({@code
 * "TASK_STATE_INPUT_REQUIRED"}) so the client interoperates with agents on either generation.
 */
public final class TaskState {

    private TaskState() {}

    public static final String SUBMITTED = "submitted";
    public static final String WORKING = "working";
    public static final String INPUT_REQUIRED = "input-required";
    public static final String AUTH_REQUIRED = "auth-required";
    public static final String COMPLETED = "completed";
    public static final String CANCELED = "canceled";
    public static final String FAILED = "failed";
    public static final String REJECTED = "rejected";
    public static final String UNKNOWN = "unknown";

    /** Terminal states — no further work happens on the task. */
    public static boolean isTerminal(String state) {
        switch (normalize(state)) {
            case COMPLETED:
            case CANCELED:
            case FAILED:
            case REJECTED:
                return true;
            default:
                return false;
        }
    }

    /** Interrupted (paused, resumable) states — the client may continue with the same taskId. */
    public static boolean isInterrupted(String state) {
        String s = normalize(state);
        return INPUT_REQUIRED.equals(s) || AUTH_REQUIRED.equals(s);
    }

    /**
     * Normalizes a state string to the v0.3.x lowercase form, also accepting the v1.0 {@code
     * TASK_STATE_*} spelling. Returns {@link #UNKNOWN} for a null/blank value.
     */
    public static String normalize(String state) {
        if (state == null || state.isBlank()) {
            return UNKNOWN;
        }
        String s = state.trim().toLowerCase(Locale.ROOT);
        if (s.startsWith("task_state_")) {
            s = s.substring("task_state_".length()).replace('_', '-');
        }
        return s;
    }
}
