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

/**
 * Field names of MCP messages, grouped by the object they belong to and shared by the client and
 * the server, so both change together when the protocol does. JSON Schema keywords and Conductor's
 * own output keys are not here.
 */
public final class MCPFields {

    private MCPFields() {}

    /** A JSON-RPC request or response. */
    public static final class JsonRpc {
        public static final String JSONRPC = "jsonrpc";
        public static final String ID = "id";
        public static final String METHOD = "method";
        public static final String PARAMS = "params";
        public static final String RESULT = "result";
        public static final String ERROR = "error";

        private JsonRpc() {}
    }

    /**
     * A JSON-RPC error object, and the fields of its {@code data} that this code reads or sends.
     */
    public static final class Error {
        public static final String CODE = "code";
        public static final String MESSAGE = "message";
        public static final String DATA = "data";
        public static final String SUPPORTED = "supported";
        public static final String REQUESTED = "requested";
        public static final String REQUIRED_CAPABILITIES = "requiredCapabilities";

        private Error() {}
    }

    /** Request metadata, and the {@code server/discover} and {@code initialize} results. */
    public static final class Session {
        public static final String META = "_meta";
        public static final String RESULT_TYPE = "resultType";
        public static final String PROTOCOL_VERSION = "protocolVersion";
        public static final String SUPPORTED_VERSIONS = "supportedVersions";
        public static final String CAPABILITIES = "capabilities";

        /** The tools capability, inside {@code capabilities}. */
        public static final String TOOLS = "tools";

        public static final String EXTENSIONS = "extensions";
        public static final String SERVER_INFO = "serverInfo";

        private Session() {}
    }

    /** A {@code serverInfo} or {@code clientInfo}. */
    public static final class Implementation {
        public static final String NAME = "name";
        public static final String VERSION = "version";

        private Implementation() {}
    }

    /**
     * A tool in {@code tools/list}, and the params of {@code tools/list} and {@code tools/call}.
     */
    public static final class Tool {
        public static final String TOOLS = "tools";
        public static final String NAME = "name";
        public static final String DESCRIPTION = "description";
        public static final String INPUT_SCHEMA = "inputSchema";
        public static final String OUTPUT_SCHEMA = "outputSchema";
        public static final String ARGUMENTS = "arguments";
        public static final String CURSOR = "cursor";
        public static final String NEXT_CURSOR = "nextCursor";

        private Tool() {}
    }

    /** A {@code CallToolResult} and its content blocks. */
    public static final class ToolResult {
        public static final String CONTENT = "content";
        public static final String IS_ERROR = "isError";
        public static final String STRUCTURED_CONTENT = "structuredContent";
        public static final String TYPE = "type";
        public static final String TEXT = "text";
        public static final String DATA = "data";
        public static final String MIME_TYPE = "mimeType";

        private ToolResult() {}
    }

    /** A task, and the params of {@code tasks/get} and {@code tasks/cancel}. */
    public static final class Task {
        public static final String TASK_ID = "taskId";
        public static final String STATUS = "status";
        public static final String STATUS_MESSAGE = "statusMessage";
        public static final String CREATED_AT = "createdAt";
        public static final String LAST_UPDATED_AT = "lastUpdatedAt";
        public static final String TTL_MS = "ttlMs";
        public static final String POLL_INTERVAL_MS = "pollIntervalMs";
        public static final String RESULT = "result";
        public static final String ERROR = "error";

        private Task() {}
    }
}
