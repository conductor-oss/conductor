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
package org.conductoross.conductor.ai.providers.gemini;

import java.util.List;

import org.conductoross.conductor.ai.models.ToolSpec;
import org.springframework.ai.chat.prompt.ChatOptions;

import lombok.Builder;
import lombok.Data;

/**
 * Chat options for the Gemini API. Carries standard ChatOptions fields plus Gemini-specific fields
 * (tools, google search, code execution, thinking).
 */
@Data
@Builder
public class GeminiChatOptions implements ChatOptions {

    private String model;
    private Double temperature;
    private Double topP;
    private Integer topK;
    private Integer maxTokens;
    private List<String> stopSequences;
    private Double frequencyPenalty;
    private Double presencePenalty;

    // Gemini-specific
    private List<ToolSpec> tools;
    private boolean googleSearchRetrieval;
    private boolean codeExecution;
    private Integer thinkingBudgetTokens;

    @Override
    public ChatOptions copy() {
        return GeminiChatOptions.builder()
                .model(model)
                .temperature(temperature)
                .topP(topP)
                .topK(topK)
                .maxTokens(maxTokens)
                .stopSequences(stopSequences)
                .frequencyPenalty(frequencyPenalty)
                .presencePenalty(presencePenalty)
                .tools(tools)
                .googleSearchRetrieval(googleSearchRetrieval)
                .codeExecution(codeExecution)
                .thinkingBudgetTokens(thinkingBudgetTokens)
                .build();
    }
}
