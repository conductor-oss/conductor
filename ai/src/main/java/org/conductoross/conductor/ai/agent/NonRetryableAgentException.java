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
package org.conductoross.conductor.ai.agent;

/**
 * Retryability contract for {@link ConductorAgentRuntime}: implementations throw this to signal a
 * <em>permanent</em> failure that retrying cannot fix — an unknown agent, a malformed request, or a
 * resume against an execution with nothing pending. The {@code AGENT} (conductor) delegate maps it
 * to {@code FAILED_WITH_TERMINAL_ERROR} rather than a retryable {@code FAILED}.
 *
 * <p>Any other exception a runtime method throws is treated as transient (retryable): a runtime
 * that is momentarily unreachable should be re-polled, not failed terminally.
 */
public class NonRetryableAgentException extends RuntimeException {

    public NonRetryableAgentException(String message) {
        super(message);
    }

    public NonRetryableAgentException(String message, Throwable cause) {
        super(message, cause);
    }
}
