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
package org.conductoross.conductor.ai.providers.anthropic;

import org.conductoross.conductor.ai.ModelConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Component
@ConfigurationProperties(prefix = "conductor.ai.anthropic")
@AllArgsConstructor
@NoArgsConstructor
public class AnthropicConfiguration implements ModelConfiguration<Anthropic> {

    private String apiKey;

    private String baseURL;

    private String version;

    private String betaVersion;

    private String completionsPath;

    public String getBaseURL() {
        return baseURL == null ? "https://api.anthropic.com" : baseURL;
    }

    @Override
    public Anthropic get() {
        return new Anthropic(this);
    }
}
