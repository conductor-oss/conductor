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

import org.springframework.ai.chat.prompt.ChatOptions;

/** Cohere-specific chat options following Spring AI patterns. */
public class CohereChatOptions implements ChatOptions {

    private String model;
    private Double temperature;
    private Integer maxTokens;
    private Double topP;
    private Integer topK;
    private Double frequencyPenalty;
    private Double presencePenalty;
    private List<String> stopSequences;

    private CohereChatOptions(Builder builder) {
        this.model = builder.model;
        this.temperature = builder.temperature;
        this.maxTokens = builder.maxTokens;
        this.topP = builder.topP;
        this.topK = builder.topK;
        this.frequencyPenalty = builder.frequencyPenalty;
        this.presencePenalty = builder.presencePenalty;
        this.stopSequences = builder.stopSequences;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String getModel() {
        return this.model;
    }

    @Override
    public Double getTemperature() {
        return this.temperature;
    }

    @Override
    public Integer getMaxTokens() {
        return this.maxTokens;
    }

    @Override
    public Double getTopP() {
        return this.topP;
    }

    @Override
    public Integer getTopK() {
        return this.topK;
    }

    @Override
    public Double getFrequencyPenalty() {
        return this.frequencyPenalty;
    }

    @Override
    public Double getPresencePenalty() {
        return this.presencePenalty;
    }

    public List<String> getStopSequences() {
        return this.stopSequences;
    }

    @Override
    public ChatOptions copy() {
        return builder()
                .model(this.model)
                .temperature(this.temperature)
                .maxTokens(this.maxTokens)
                .topP(this.topP)
                .topK(this.topK)
                .frequencyPenalty(this.frequencyPenalty)
                .presencePenalty(this.presencePenalty)
                .stopSequences(this.stopSequences)
                .build();
    }

    public static class Builder {
        private String model;
        private Double temperature;
        private Integer maxTokens;
        private Double topP;
        private Integer topK;
        private Double frequencyPenalty;
        private Double presencePenalty;
        private List<String> stopSequences;

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public Builder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        public Builder frequencyPenalty(Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            return this;
        }

        public Builder presencePenalty(Double presencePenalty) {
            this.presencePenalty = presencePenalty;
            return this;
        }

        public Builder stopSequences(List<String> stopSequences) {
            this.stopSequences = stopSequences;
            return this;
        }

        public CohereChatOptions build() {
            return new CohereChatOptions(this);
        }
    }
}
