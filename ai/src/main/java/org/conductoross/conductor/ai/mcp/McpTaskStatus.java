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
package org.conductoross.conductor.ai.mcp;

import java.util.Arrays;

/**
 * The {@code status} of a task in the MCP Tasks extension ({@code 2026-07-28}), as reported by
 * {@code CreateTaskResult} and {@code tasks/get}.
 *
 * <p>Not to be confused with {@link McpResultType}, which says what kind of result a request
 * returned; both vocabularies have an {@code input_required} value, with different meanings.
 */
public enum McpTaskStatus {
    WORKING("working"),
    COMPLETED("completed"),
    FAILED("failed"),
    CANCELLED("cancelled"),
    INPUT_REQUIRED("input_required"),
    /**
     * A value the extension does not define, or no value. Never sent; its {@link #value()} is
     * empty.
     */
    UNKNOWN("");

    private final String value;

    McpTaskStatus(String value) {
        this.value = value;
    }

    /** The value on the wire. */
    public String value() {
        return value;
    }

    /** True if {@code value} is this status on the wire. */
    public boolean matches(String value) {
        return this.value.equals(value);
    }

    /** The status with this wire value, or {@link #UNKNOWN} for any other value. */
    public static McpTaskStatus of(String value) {
        return Arrays.stream(values())
                .filter(s -> s.value.equals(value))
                .findFirst()
                .orElse(UNKNOWN);
    }
}
