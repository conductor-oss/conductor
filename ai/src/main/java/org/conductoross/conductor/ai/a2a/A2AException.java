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

/**
 * A retryable failure talking to a remote A2A agent (transport error, 5xx, or a transient JSON-RPC
 * error). Terminal/non-retryable failures (4xx, method-not-found, task-not-found, …) are surfaced
 * as {@link com.netflix.conductor.sdk.workflow.executor.task.NonRetryableException} instead, so
 * both the annotated-worker path and {@code AgentTask} map them to a terminal task status.
 */
public class A2AException extends RuntimeException {

    public A2AException(String message) {
        super(message);
    }

    public A2AException(String message, Throwable cause) {
        super(message, cause);
    }
}
