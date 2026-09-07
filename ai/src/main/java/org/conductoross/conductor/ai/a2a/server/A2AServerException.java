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
package org.conductoross.conductor.ai.a2a.server;

/** An A2A-server error carrying a JSON-RPC error code, mapped to a JSON-RPC error response. */
public class A2AServerException extends RuntimeException {

    private final int code;

    public A2AServerException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    /** -32001: agent/task not found (A2A TaskNotFoundError range). */
    public static A2AServerException notFound(String message) {
        return new A2AServerException(-32001, message);
    }

    /** -32602: invalid params. */
    public static A2AServerException invalidParams(String message) {
        return new A2AServerException(-32602, message);
    }
}
