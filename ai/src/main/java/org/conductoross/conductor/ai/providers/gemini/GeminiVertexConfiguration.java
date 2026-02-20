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
package org.conductoross.conductor.ai.providers.gemini;

import org.conductoross.conductor.ai.ModelConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.vertexai.api.PredictionServiceClient;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Component
@ConfigurationProperties(prefix = "conductor.ai.gemini")
@AllArgsConstructor
@NoArgsConstructor
public class GeminiVertexConfiguration implements ModelConfiguration<GeminiVertex> {

    private String projectId;
    private String location;
    private String baseURL;
    private String publisher;
    GoogleCredentials googleCredentials;
    PredictionServiceClient predictionServiceClient;

    public String getBaseURL() {
        return baseURL == null
                ? String.format("%s-aiplatform.googleapis.com:443", location)
                : baseURL;
    }

    @Override
    public GeminiVertex get() {
        return new GeminiVertex(this);
    }
}
