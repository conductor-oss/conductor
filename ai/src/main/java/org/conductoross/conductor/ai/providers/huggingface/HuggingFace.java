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
package org.conductoross.conductor.ai.providers.huggingface;

import java.util.List;

import org.conductoross.conductor.ai.AIModel;
import org.conductoross.conductor.ai.models.ChatCompletion;
import org.conductoross.conductor.ai.models.EmbeddingGenRequest;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.huggingface.HuggingfaceChatModel;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

public class HuggingFace implements AIModel {

    public static final String NAME = "huggingface";
    private final HuggingFaceConfiguration config;

    public HuggingFace(HuggingFaceConfiguration config) {
        this.config = config;
    }

    @Override
    public String getModelProvider() {
        return NAME;
    }

    @Override
    public List<Float> generateEmbeddings(EmbeddingGenRequest embeddingGenRequest) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public ChatOptions getChatOptions(ChatCompletion input) {
        // HuggingFace has limited options support through generic interface
        return ToolCallingChatOptions.builder()
                .model(input.getModel())
                .temperature(input.getTemperature())
                .topP(input.getTopP())
                .maxTokens(input.getMaxTokens())
                .internalToolExecutionEnabled(false)
                .build();
    }

    @Override
    public ChatModel getChatModel() {
        return new HuggingfaceChatModel(config.getApiKey(), config.getBaseURL());
    }

    @Override
    public ImageModel getImageModel() {
        throw new UnsupportedOperationException("Image generation not supported by the model yet");
    }
}
