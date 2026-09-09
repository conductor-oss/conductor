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
package org.conductoross.conductor.ai.model;

import java.util.Locale;

/** Common finish reasons shared by live responses and fixture replay. */
public enum FinishReason {
    STOP,
    TOOL_CALLS,
    MAX_TOKENS,
    CONTENT_FILTER;
    private static final String MISSING_FINISH_REASON = "Missing model finish reason";
    private static final String END_TURN_ALIAS = "END_TURN";
    private static final String STOP_SEQUENCE_ALIAS = "STOP_SEQUENCE";
    private static final String TOOL_USE_ALIAS = "TOOL_USE";
    private static final String LENGTH_ALIAS = "LENGTH";
    private static final String REFUSAL_ALIAS = "REFUSAL";

    public static FinishReason fromProvider(String reason) {
        return valueOf(normalize(reason));
    }

    /** Normalize known provider aliases while preserving other provider-specific reasons. */
    public static String normalize(String reason) {
        if (reason == null) throw new IllegalArgumentException(MISSING_FINISH_REASON);
        return switch (reason.toUpperCase(Locale.ROOT)) {
            case END_TURN_ALIAS, STOP_SEQUENCE_ALIAS -> STOP.name();
            case TOOL_USE_ALIAS -> TOOL_CALLS.name();
            case LENGTH_ALIAS -> MAX_TOKENS.name();
            case REFUSAL_ALIAS -> CONTENT_FILTER.name();
            default -> reason.toUpperCase(Locale.ROOT);
        };
    }
}
