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
package org.conductoross.conductor.ai.providers.mock;

import java.io.IOException;

import org.conductoross.conductor.ai.ModelConfiguration;
import org.conductoross.conductor.ai.testing.LlmRecordingProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import okhttp3.OkHttpClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LlmRecordingProperties.class)
@ConditionalOnProperty(
        prefix = LlmRecordingProperties.PREFIX,
        name = LlmRecordingProperties.ENABLE_LLM_MOCKS,
        havingValue = LlmRecordingProperties.ENABLED)
public class MockLLMConfiguration implements ModelConfiguration<MockLLM> {
    private final MockLLM model;

    public MockLLMConfiguration(LlmRecordingProperties properties) throws IOException {
        // Validate during bean creation, before the provider registry's catch-and-log loop.
        this.model = new MockLLM(properties.getRecordingsDirectory());
    }

    @Bean
    @Override
    public MockLLM get() {
        return model;
    }

    @Override
    public void setHttpClient(OkHttpClient httpClient) {
        // Playback never uses an HTTP client.
    }
}
