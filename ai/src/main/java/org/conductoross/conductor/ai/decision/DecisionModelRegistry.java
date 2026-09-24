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
package org.conductoross.conductor.ai.decision;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.netflix.conductor.sdk.workflow.executor.task.NonRetryableException;

/** Immutable provider registry; provider-specific configuration stays with each implementation. */
@Component
public class DecisionModelRegistry {
    private final Map<String, DecisionModel> providers;

    public DecisionModelRegistry(List<DecisionModel> models) {
        Map<String, DecisionModel> registered = new LinkedHashMap<>();
        for (DecisionModel model : models) {
            if (registered.putIfAbsent(model.provider(), model) != null)
                throw new IllegalArgumentException("Duplicate decision model provider");
        }
        providers = Map.copyOf(registered);
    }

    public DecisionModel get(String provider) {
        DecisionModel model = providers.get(provider);
        if (model == null)
            throw new NonRetryableException("Decision model provider is not configured");
        return model;
    }
}
