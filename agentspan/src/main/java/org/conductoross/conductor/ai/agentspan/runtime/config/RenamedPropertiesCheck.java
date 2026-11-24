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
package org.conductoross.conductor.ai.agentspan.runtime.config;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import static java.util.Map.entry;

/**
 * Stops startup when a deprecated {@code agentspan.*} property is set. The server no longer reads
 * these names, so a deployment that still sets one would silently run with the default instead.
 */
@Component
@ConditionalOnProperty(name = "conductor.integrations.ai.enabled", havingValue = "true")
public class RenamedPropertiesCheck {

    static final Map<String, String> RENAMED =
            Map.ofEntries(
                    entry("agentspan.embedded", "conductor.integrations.ai.enabled"),
                    entry("agentspan.skills.enabled", "conductor.ai.skills.enabled"),
                    entry(
                            "agentspan.skills.max-package-bytes",
                            "conductor.ai.skills.max-package-bytes"),
                    entry(
                            "agentspan.skills.max-preview-bytes",
                            "conductor.ai.skills.max-preview-bytes"),
                    entry(
                            "agentspan.skills.max-uncompressed-bytes",
                            "conductor.ai.skills.max-uncompressed-bytes"),
                    entry("agentspan.skills.max-file-count", "conductor.ai.skills.max-file-count"),
                    entry(
                            "agentspan.context-condensation.recent-exchanges",
                            "conductor.ai.context-condensation.recent-exchanges"),
                    entry(
                            "agentspan.default-context-window",
                            "conductor.ai.default-context-window"),
                    entry("agentspan.model-context-windows", "conductor.ai.model-context-windows"));

    public RenamedPropertiesCheck(Environment environment) {
        Binder binder = Binder.get(environment);
        String found =
                RENAMED.entrySet().stream()
                        .filter(e -> isSet(environment, binder, e.getKey()))
                        .map(e -> e.getKey() + " -> " + e.getValue())
                        .sorted()
                        .collect(Collectors.joining(", "));
        if (!found.isEmpty()) {
            throw new IllegalStateException(
                    "Deprecated agentspan properties are set and no longer read. Rename them: "
                            + found);
        }
    }

    /** A plain key, or a map prefix such as agentspan.model-context-windows.<model>. */
    private static boolean isSet(Environment environment, Binder binder, String name) {
        return environment.containsProperty(name)
                || binder.bind(name, Bindable.mapOf(String.class, String.class)).isBound();
    }
}
