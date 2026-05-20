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
package org.conductoross.conductor.ai.providers.azureopenai;

import java.time.Duration;

import org.conductoross.conductor.ai.ModelConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@ConfigurationProperties(prefix = "conductor.ai.azureopenai")
@Component(value = AzureOpenAI.NAME)
public class AzureOpenAIConfiguration implements ModelConfiguration<AzureOpenAI> {

    private String apiKey;
    private String baseURL;
    private String user;
    private String deploymentName;
    private Duration timeout = Duration.ofSeconds(600);

    public AzureOpenAIConfiguration(
            String apiKey, String baseURL, String user, String deploymentName) {
        this.apiKey = apiKey;
        this.baseURL = baseURL;
        this.user = user;
        this.deploymentName = deploymentName;
    }

    @Override
    public AzureOpenAI get() {
        return new AzureOpenAI(this);
    }
}
