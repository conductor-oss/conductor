/*
 * Copyright 2025 Conductor Authors.
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
package org.conductoross.conductor.ai.agentspan.runtime.context;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Per-request principal context stored in a ThreadLocal for the duration of each request.
 *
 * <p>Carries the resolved {@code userId} used for per-user secret scoping. AgentSpan defines no
 * identity model of its own — the host populates this: the standalone server's {@code AuthFilter}
 * sets an anonymous id, while an embedding application (e.g. orkes-conductor) supplies its own
 * principal id.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestContext {
    private String requestId; // UUID per HTTP request
    private String executionId; // populated when request is execution-scoped
    private String userId; // resolved principal id, for per-user secret scoping
    private Instant createdAt;
}
