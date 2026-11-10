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
 * A failure that only a call in the MCP {@code 2026-07-28} form can have, and that Conductor
 * detects itself: the tool is not listed, an {@code x-mcp-header} annotation or value is invalid,
 * or the server returned an unsupported {@code resultType} or an invalid task result. Its message
 * is the task's reason as it is. Every other failure of such a call keeps the reason of a legacy
 * call.
 */
public class McpCallException extends RuntimeException {

    public McpCallException(String message) {
        super(message);
    }
}
