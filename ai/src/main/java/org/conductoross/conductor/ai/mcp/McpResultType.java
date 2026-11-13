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
 * The {@code resultType} of a result in the MCP {@code 2026-07-28} form: what kind of result a
 * request returned. An absent {@code resultType} means {@link #COMPLETE}.
 *
 * <p>Not to be confused with {@link McpTaskStatus}, the state of a task; both vocabularies have an
 * {@code input_required} value, with different meanings.
 */
public enum McpResultType {
    /** A final result. */
    COMPLETE("complete"),
    /** A {@code CreateTaskResult}: the server created a task to poll. */
    TASK("task"),
    /** The server asks the client for input before it can answer. */
    INPUT_REQUIRED("input_required"),
    /**
     * A value the protocol does not define. Never sent; its {@link #value()} is empty. An absent
     * {@code resultType} is {@link #COMPLETE}, not this.
     */
    UNKNOWN("");

    /** The name of the result member that holds the result type. */
    public static final String FIELD = "resultType";

    private final String value;

    McpResultType(String value) {
        this.value = value;
    }

    /** The value on the wire. */
    public String value() {
        return value;
    }

    /** True if {@code value} is this result type on the wire. */
    public boolean matches(String value) {
        return this.value.equals(value);
    }

    /** The result type with this wire value, or {@link #UNKNOWN} for any other value. */
    public static McpResultType of(String value) {
        return Arrays.stream(values())
                .filter(t -> t.value.equals(value))
                .findFirst()
                .orElse(UNKNOWN);
    }
}
