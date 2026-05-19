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

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

public class HuggingFaceChatModel implements ChatModel {

    private final HuggingFaceApi api;

    public HuggingFaceChatModel(HuggingFaceApi api) {
        this.api = api;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        String inputs =
                prompt.getInstructions().stream()
                        .map(m -> m.getText())
                        .reduce("", (a, b) -> a.isBlank() ? b : a + "\n" + b);
        try {
            String text = api.generate(inputs);
            AssistantMessage msg = new AssistantMessage(text);
            ChatGenerationMetadata meta =
                    ChatGenerationMetadata.builder().finishReason("stop").build();
            return new ChatResponse(List.of(new Generation(msg, meta)));
        } catch (Exception e) {
            throw new RuntimeException("HuggingFace generation failed", e);
        }
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return ToolCallingChatOptions.builder().build();
    }
}
