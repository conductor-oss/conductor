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
package org.conductoross.conductor.ai.providers.cohere;

import java.util.List;

import org.conductoross.conductor.ai.providers.cohere.api.CohereApi;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;

/**
 * Cohere Embedding Model implementation following Spring AI patterns. Potential contribution to
 * spring-ai project.
 */
public class CohereEmbeddingModel implements EmbeddingModel {

    private final CohereApi cohereApi;
    private final String defaultModel;
    private final Integer defaultDimensions;

    public CohereEmbeddingModel(CohereApi cohereApi) {
        this(cohereApi, "embed-english-v3.0", null);
    }

    public CohereEmbeddingModel(
            CohereApi cohereApi, String defaultModel, Integer defaultDimensions) {
        Assert.notNull(cohereApi, "CohereApi must not be null");
        this.cohereApi = cohereApi;
        this.defaultModel = defaultModel;
        this.defaultDimensions = defaultDimensions;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        // Extract text from the request
        List<String> texts = request.getInstructions();

        // Get model and dimensions from options or use defaults
        String model = this.defaultModel;
        Integer dimensions = this.defaultDimensions;

        if (request.getOptions() != null) {
            if (request.getOptions().getModel() != null) {
                model = request.getOptions().getModel();
            }
            if (request.getOptions().getDimensions() != null) {
                dimensions = request.getOptions().getDimensions();
            }
        }

        // Create Cohere request with 'texts' field (not 'input')
        CohereApi.EmbeddingRequest cohereRequest =
                CohereApi.EmbeddingRequest.builder()
                        .model(model)
                        .texts(texts)
                        .inputType("search_document")
                        .outputDimensions(dimensions)
                        .build();

        ResponseEntity<CohereApi.EmbeddingResponse> response = this.cohereApi.embed(cohereRequest);
        return toEmbeddingResponse(response.getBody());
    }

    @Override
    public float[] embed(Document document) {
        EmbeddingResponse response = call(new EmbeddingRequest(List.of(document.getText()), null));
        if (response.getResults() != null && !response.getResults().isEmpty()) {
            return response.getResults().get(0).getOutput();
        }
        return new float[0];
    }

    private EmbeddingResponse toEmbeddingResponse(CohereApi.EmbeddingResponse response) {
        if (response == null
                || response.embeddings() == null
                || response.embeddings().floatEmbeddings() == null) {
            return new EmbeddingResponse(List.of());
        }

        List<List<Float>> floatEmbeddings = response.embeddings().floatEmbeddings();
        List<Embedding> embeddings = new java.util.ArrayList<>();

        for (int i = 0; i < floatEmbeddings.size(); i++) {
            List<Float> embedding = floatEmbeddings.get(i);
            float[] output = new float[embedding.size()];
            for (int j = 0; j < embedding.size(); j++) {
                output[j] = embedding.get(j);
            }
            embeddings.add(new Embedding(output, i));
        }

        EmbeddingResponseMetadata metadata = new EmbeddingResponseMetadata();
        return new EmbeddingResponse(embeddings, metadata);
    }

    public static class Builder {
        private CohereApi cohereApi;
        private String defaultModel = "embed-english-v3.0";
        private Integer defaultDimensions;

        public Builder cohereApi(CohereApi cohereApi) {
            this.cohereApi = cohereApi;
            return this;
        }

        public Builder defaultModel(String defaultModel) {
            this.defaultModel = defaultModel;
            return this;
        }

        public Builder defaultDimensions(Integer defaultDimensions) {
            this.defaultDimensions = defaultDimensions;
            return this;
        }

        public CohereEmbeddingModel build() {
            Assert.notNull(this.cohereApi, "CohereApi must not be null");
            return new CohereEmbeddingModel(
                    this.cohereApi, this.defaultModel, this.defaultDimensions);
        }
    }
}
