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
package org.conductoross.conductor.ai.providers.openai;

import java.util.List;

import org.conductoross.conductor.ai.providers.openai.api.OpenAIResponsesApi;
import org.springframework.ai.chat.prompt.ChatOptions;

import lombok.Builder;
import lombok.Data;

/**
 * Chat options for the OpenAI Responses API. Carries both standard ChatOptions fields and
 * Responses-API-specific fields (previousResponseId, reasoningEffort, built-in tools, etc.) through
 * Spring AI's ChatOptions interface.
 */
@Data
@Builder
public class OpenAIResponsesChatOptions implements ChatOptions {

    private String model;
    private Double temperature;
    private Double topP;
    private Integer maxTokens;
    private Double frequencyPenalty;
    private Double presencePenalty;
    private List<String> stopSequences;

    // Responses API specific
    private String previousResponseId;
    private String reasoningEffort;
    private Boolean jsonOutput;
    private List<OpenAIResponsesApi.Tool> responsesApiTools;

    @Override
    public Integer getTopK() {
        return null; // Not supported by OpenAI
    }

    @Override
    public ChatOptions copy() {
        return OpenAIResponsesChatOptions.builder()
                .model(model)
                .temperature(temperature)
                .topP(topP)
                .maxTokens(maxTokens)
                .frequencyPenalty(frequencyPenalty)
                .presencePenalty(presencePenalty)
                .stopSequences(stopSequences)
                .previousResponseId(previousResponseId)
                .reasoningEffort(reasoningEffort)
                .jsonOutput(jsonOutput)
                .responsesApiTools(responsesApiTools)
                .build();
    }
}
