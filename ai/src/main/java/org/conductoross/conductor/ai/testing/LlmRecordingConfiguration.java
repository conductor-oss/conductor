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
package org.conductoross.conductor.ai.testing;

import java.io.IOException;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LlmRecordingProperties.class)
public class LlmRecordingConfiguration {
    @Bean
    @ConditionalOnProperty(
            prefix = LlmRecordingProperties.PREFIX,
            name = LlmRecordingProperties.RECORD_MODE,
            havingValue = LlmRecordingProperties.ENABLED)
    public LlmCallRecorder llmCallRecorder(
            LlmRecordingProperties properties, ObjectMapper objectMapper) throws IOException {
        return new JsonFileLlmCallRecorder(properties.getRecordingsDirectory(), objectMapper);
    }
}
