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
package org.conductoross.conductor.ai.agentspan.runtime.service;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.conductoross.conductor.ai.agentspan.runtime.spi.AzureAgentRunContext;
import org.conductoross.conductor.ai.agentspan.runtime.spi.AzureAgentRunStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

// Default AzureAgentRunStore backed by an in-process ConcurrentHashMap. Works correctly on a
// single server instance. Multi-replica deployments should replace this with a durable
// implementation (Redis, DB) via @ConditionalOnMissingBean — identical to how orkes-conductor
// overrides SkillMetadataDAO and SkillPackageStore.
@Component
@ConditionalOnMissingBean(AzureAgentRunStore.class)
public class InMemoryAzureAgentRunStore implements AzureAgentRunStore {

    private final ConcurrentHashMap<String, AzureAgentRunContext> store = new ConcurrentHashMap<>();

    @Override
    public void save(String executionId, AzureAgentRunContext context) {
        store.put(executionId, context);
    }

    @Override
    public Optional<AzureAgentRunContext> find(String executionId) {
        return Optional.ofNullable(store.get(executionId));
    }

    @Override
    public void delete(String executionId) {
        store.remove(executionId);
    }
}
