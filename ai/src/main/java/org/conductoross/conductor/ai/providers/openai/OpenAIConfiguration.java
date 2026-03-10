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
package org.conductoross.conductor.ai.providers.openai;

import java.time.Duration;

import org.conductoross.conductor.ai.ModelConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Component
@ConfigurationProperties(prefix = "conductor.ai.openai")
@NoArgsConstructor
public class OpenAIConfiguration implements ModelConfiguration<OpenAI> {

    private String apiKey;

    private String baseURL;

    private String organizationId;

    private Duration timeout = Duration.ofSeconds(600);

    public OpenAIConfiguration(String apiKey, String baseURL, String organizationId) {
        this.apiKey = apiKey;
        this.baseURL = baseURL;
        this.organizationId = organizationId;
    }

    public String getBaseURL() {
        return baseURL == null || baseURL.isBlank() ? "https://api.openai.com/v1" : baseURL;
    }

    @Override
    public OpenAI get() {
        return new OpenAI(this);
    }
}
