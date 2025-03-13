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
package org.conductoross.ai;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

@Component
public class LLMProvider {

    private final Map<String, LLM> models = new HashMap<>();

    public LLMProvider(List<LLM> llms) {
        for (LLM llm : llms) {
            models.put(llm.getModelProvider(), llm);
        }
    }

    public ChatModel getChatModel(String name) {
        LLM llm = models.get(name);
        if (llm == null) {
            throw new IllegalArgumentException("Unsupported model " + name);
        }
        return llm.getChatModel();
    }
}
