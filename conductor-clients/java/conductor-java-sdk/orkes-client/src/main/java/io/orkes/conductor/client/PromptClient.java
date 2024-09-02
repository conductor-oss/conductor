/*
 * Copyright 2024 Orkes, Inc.
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
package io.orkes.conductor.client;

import java.util.List;
import java.util.Map;

import io.orkes.conductor.client.model.TagObject;
import io.orkes.conductor.client.model.integration.ai.PromptTemplate;

public interface PromptClient {

    void savePrompt(String promptName, String description, String promptTemplate);

    PromptTemplate getPrompt(String promptName);

    List<PromptTemplate> getPrompts();

    void deletePrompt(String promptName);

    List<TagObject> getTagsForPromptTemplate(String promptName);

    void updateTagForPromptTemplate(String promptName, List<TagObject> tags);

    void deleteTagForPromptTemplate(String promptName, List<TagObject> tags);

    /**
     * Tests a prompt template by substituting variables and processing through the specified AI model.
     *
     * @param promptText the text of the prompt template
     * @param variables a map containing variables to be replaced in the template
     * @param aiIntegration the AI integration context
     * @param textCompleteModel the AI model used for completing text
     * @param temperature the randomness of the output (optional, default is 0.1)
     * @param topP the probability mass to consider from the output distribution (optional, default is 0.9)
     * @param stopWords a list of words to stop generating further (can be null)
     * @return the processed prompt text
     */
    String testPrompt(String promptText, Map<String, Object> variables, String aiIntegration,
        String textCompleteModel, float temperature, float topP, List<String> stopWords);
}
