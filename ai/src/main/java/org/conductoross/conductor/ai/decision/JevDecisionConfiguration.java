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

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/** Bound server settings. Deliberately has no generated toString containing the API key. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "conductor.ai.jev")
public class JevDecisionConfiguration {
    private String apiKey;
    private String route = "openrouter";
    private String endpoint;
    private Duration timeout = Duration.ofSeconds(20);

    public String endpoint() {
        if (endpoint != null && !endpoint.isBlank()) return endpoint;
        return switch (route) {
            case "openrouter" -> "https://openrouter.ai/api/v1/systemone";
            case "typesafe" -> "https://api.typesafe.ai/v1/systemone";
            default -> throw new IllegalArgumentException("Unknown Jev route");
        };
    }
}
