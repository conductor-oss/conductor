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
package org.conductoross.ai.providers;

import org.conductoross.ai.LLM;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;

@Component(OpenAI.NAME)
public class OpenAI implements LLM {

    public static final String NAME = "openai";
    private static final String OPENAI_API_KEY = "OPENAI_API_KEY";

    private ChatModel chatModel;

    public OpenAI() {
        String apiKey = System.getProperty(OPENAI_API_KEY, System.getenv(OPENAI_API_KEY));
        OpenAiApi openAiApi = OpenAiApi.builder().apiKey(apiKey).build();
        this.chatModel = OpenAiChatModel.builder().openAiApi(openAiApi).build();
    }

    @Override
    public String getModelProvider() {
        return NAME;
    }

    @Override
    public ChatModel getChatModel() {
        return chatModel;
    }
}
