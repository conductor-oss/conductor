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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Pod-local fallback. Use only when Redis is unavailable (e.g. local dev). TTL is ignored. */
public class InMemoryAgentExecutionStateStore implements AgentExecutionStateStore {

    private final ConcurrentHashMap<String, Map<String, String>> map = new ConcurrentHashMap<>();

    @Override
    public void save(String executionId, Map<String, String> fields, long ttlSeconds) {
        map.put(executionId, new HashMap<>(fields));
    }

    @Override
    public Map<String, String> load(String executionId) {
        Map<String, String> entry = map.get(executionId);
        return entry != null ? Collections.unmodifiableMap(entry) : Collections.emptyMap();
    }

    @Override
    public void updateField(String executionId, String field, String value) {
        map.computeIfPresent(
                executionId,
                (k, v) -> {
                    Map<String, String> updated = new HashMap<>(v);
                    updated.put(field, value);
                    return updated;
                });
    }

    @Override
    public void delete(String executionId) {
        map.remove(executionId);
    }
}
