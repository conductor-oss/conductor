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
 * The MCP JSON-RPC methods that Conductor sends as a client or handles as a server. Only the method
 * names; how each request is built or served is decided by the client and the server.
 */
public enum McpMethod {
    // The 2026-07-28 methods.
    SERVER_DISCOVER("server/discover"),
    TOOLS_LIST("tools/list"),
    TOOLS_CALL("tools/call"),
    TASKS_GET("tasks/get"),
    TASKS_UPDATE("tasks/update"),
    TASKS_CANCEL("tasks/cancel"),
    // Methods of the earlier, session-based revisions that the server still answers.
    INITIALIZE("initialize"),
    PING("ping"),
    /** A method this enum does not name. Never sent; its {@link #value()} is empty. */
    UNKNOWN("");

    private final String value;

    McpMethod(String value) {
        this.value = value;
    }

    /** The method name on the wire. */
    public String value() {
        return value;
    }

    /** The method with this name on the wire, or {@link #UNKNOWN} for any other name. */
    public static McpMethod of(String value) {
        return Arrays.stream(values())
                .filter(m -> m.value.equals(value))
                .findFirst()
                .orElse(UNKNOWN);
    }

    /** The method name on the wire, so that messages read {@code tools/call}, not the constant. */
    @Override
    public String toString() {
        return value;
    }
}
