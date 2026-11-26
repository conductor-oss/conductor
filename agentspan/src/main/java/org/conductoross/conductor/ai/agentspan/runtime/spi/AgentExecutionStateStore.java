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
package org.conductoross.conductor.ai.agentspan.runtime.spi;

import java.util.Map;

/**
 * SPI for storing per-execution context needed to poll and resume an external agent run.
 *
 * <p>Each entry is a flat {@code Map<String, String>} so the interface stays client-agnostic. The
 * default implementation is in-memory (pod-local). A Redis-backed implementation should be provided
 * in production so that status polls landing on a different pod than startAgent still find the
 * execution context.
 */
public interface AgentExecutionStateStore {

    /**
     * Persist all fields for an execution. TTL is a hint to the implementation — in-memory stores
     * may ignore it.
     */
    void save(String executionId, Map<String, String> fields, long ttlSeconds);

    /**
     * Load stored fields. Returns an empty map (never null) if no entry exists. Callers check
     * {@code map.isEmpty()} instead of null.
     */
    Map<String, String> load(String executionId);

    /**
     * Update a single field within an existing entry. No-op if the entry has expired or was
     * deleted.
     */
    void updateField(String executionId, String field, String value);

    void delete(String executionId);
}
