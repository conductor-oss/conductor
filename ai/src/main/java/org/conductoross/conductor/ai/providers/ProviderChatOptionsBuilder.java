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
package org.conductoross.conductor.ai.providers;

import java.util.List;
import java.util.function.Function;

import org.springframework.ai.chat.prompt.ChatOptions;

/**
 * Builder used by the provider-specific {@link ChatOptions} implementations to satisfy {@link
 * ChatOptions#mutate()}.
 *
 * <p>Spring AI 2.0 replaced the old {@code copy()} method with {@code mutate()}, which hands back a
 * builder over the portable chat options. Provider options carry extra fields on top of those (tool
 * specs, reasoning budgets, and so on), so this builder collects the portable values and then defers
 * to a finisher supplied by the provider class, which rebuilds its own type with the extras intact.
 */
public final class ProviderChatOptionsBuilder
        implements ChatOptions.Builder<ProviderChatOptionsBuilder> {

    private final Function<ProviderChatOptionsBuilder, ChatOptions> finisher;

    private String model;
    private Double temperature;
    private Double topP;
    private Integer topK;
    private Integer maxTokens;
    private List<String> stopSequences;
    private Double frequencyPenalty;
    private Double presencePenalty;

    private ProviderChatOptionsBuilder(
            ChatOptions source, Function<ProviderChatOptionsBuilder, ChatOptions> finisher) {
        this.finisher = finisher;
        this.model = source.getModel();
        this.temperature = source.getTemperature();
        this.topP = source.getTopP();
        this.topK = source.getTopK();
        this.maxTokens = source.getMaxTokens();
        this.stopSequences = source.getStopSequences();
        this.frequencyPenalty = source.getFrequencyPenalty();
        this.presencePenalty = source.getPresencePenalty();
    }

    /**
     * @param source options to seed the portable values from
     * @param finisher rebuilds the provider type once the portable values have been set
     */
    public static ProviderChatOptionsBuilder from(
            ChatOptions source, Function<ProviderChatOptionsBuilder, ChatOptions> finisher) {
        return new ProviderChatOptionsBuilder(source, finisher);
    }

    public String model() {
        return model;
    }

    public Double temperature() {
        return temperature;
    }

    public Double topP() {
        return topP;
    }

    public Integer topK() {
        return topK;
    }

    public Integer maxTokens() {
        return maxTokens;
    }

    public List<String> stopSequences() {
        return stopSequences;
    }

    public Double frequencyPenalty() {
        return frequencyPenalty;
    }

    public Double presencePenalty() {
        return presencePenalty;
    }

    @Override
    public ProviderChatOptionsBuilder model(String model) {
        this.model = model;
        return this;
    }

    @Override
    public ProviderChatOptionsBuilder temperature(Double temperature) {
        this.temperature = temperature;
        return this;
    }

    @Override
    public ProviderChatOptionsBuilder topP(Double topP) {
        this.topP = topP;
        return this;
    }

    @Override
    public ProviderChatOptionsBuilder topK(Integer topK) {
        this.topK = topK;
        return this;
    }

    @Override
    public ProviderChatOptionsBuilder maxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
        return this;
    }

    @Override
    public ProviderChatOptionsBuilder stopSequences(List<String> stopSequences) {
        this.stopSequences = stopSequences;
        return this;
    }

    @Override
    public ProviderChatOptionsBuilder frequencyPenalty(Double frequencyPenalty) {
        this.frequencyPenalty = frequencyPenalty;
        return this;
    }

    @Override
    public ProviderChatOptionsBuilder presencePenalty(Double presencePenalty) {
        this.presencePenalty = presencePenalty;
        return this;
    }

    @Override
    public ProviderChatOptionsBuilder combineWith(ChatOptions.Builder<?> other) {
        ChatOptions merged = other.build();
        if (merged.getModel() != null) {
            this.model = merged.getModel();
        }
        if (merged.getTemperature() != null) {
            this.temperature = merged.getTemperature();
        }
        if (merged.getTopP() != null) {
            this.topP = merged.getTopP();
        }
        if (merged.getTopK() != null) {
            this.topK = merged.getTopK();
        }
        if (merged.getMaxTokens() != null) {
            this.maxTokens = merged.getMaxTokens();
        }
        if (merged.getStopSequences() != null) {
            this.stopSequences = merged.getStopSequences();
        }
        if (merged.getFrequencyPenalty() != null) {
            this.frequencyPenalty = merged.getFrequencyPenalty();
        }
        if (merged.getPresencePenalty() != null) {
            this.presencePenalty = merged.getPresencePenalty();
        }
        return this;
    }

    @Override
    public ProviderChatOptionsBuilder clone() {
        return finisher.apply(this).mutate() instanceof ProviderChatOptionsBuilder cloned
                ? cloned
                : this;
    }

    @Override
    public ChatOptions build() {
        return finisher.apply(this);
    }
}
