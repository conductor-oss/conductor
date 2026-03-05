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
package org.conductoross.conductor.ai;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.conductoross.conductor.ai.document.DocumentLoader;
import org.conductoross.conductor.ai.models.AudioGenRequest;
import org.conductoross.conductor.ai.models.ChatCompletion;
import org.conductoross.conductor.ai.models.EmbeddingGenRequest;
import org.conductoross.conductor.ai.models.ImageGenRequest;
import org.conductoross.conductor.ai.models.LLMResponse;
import org.conductoross.conductor.ai.models.VideoGenRequest;
import org.conductoross.conductor.common.JsonSchemaValidator;
import org.conductoross.conductor.common.utils.StringTemplate;
import org.conductoross.conductor.config.AIIntegrationEnabledCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.tasks.Task;

import lombok.extern.slf4j.Slf4j;

@Component
@Conditional(AIIntegrationEnabledCondition.class)
@Slf4j
public class LLMs {

    protected AIModelProvider modelProvider;
    protected LLMHelper helper;
    protected String payloadStoreLocation;
    protected Consumer<TokenUsageLog> tokenUsageLogger =
            (tokenUsageLog) -> {
                log.info("{}", tokenUsageLog);
            };

    public LLMs(
            List<DocumentLoader> documentLoaders,
            JsonSchemaValidator jsonSchemaValidator,
            AIModelProvider modelProvider) {
        this.modelProvider = modelProvider;
        this.helper = new LLMHelper(jsonSchemaValidator, documentLoaders);
        this.payloadStoreLocation = modelProvider.getPayloadStoreLocation();
    }

    public LLMResponse chatComplete(Task task, ChatCompletion chatCompletion) {
        AIModel llm = this.modelProvider.getModel(chatCompletion);
        String prompt =
                replacePromptVariables(
                        task,
                        chatCompletion.getInstructions(),
                        chatCompletion.getPromptVariables());
        chatCompletion.setInstructions(prompt);
        return helper.chatComplete(
                task, llm, chatCompletion, getPayloadStoreLocation(task), tokenUsageLogger);
    }

    public LLMResponse generateImage(Task task, ImageGenRequest imageGenRequest) {
        AIModel llm = this.modelProvider.getModel(imageGenRequest);
        String prompt =
                replacePromptVariables(
                        task, imageGenRequest.getPrompt(), imageGenRequest.getPromptVariables());
        imageGenRequest.setPrompt(prompt);
        return helper.generateImage(
                task, llm, imageGenRequest, getPayloadStoreLocation(task), tokenUsageLogger);
    }

    public LLMResponse generateAudio(Task task, AudioGenRequest audioGenRequest) {
        AIModel llm = this.modelProvider.getModel(audioGenRequest);
        String prompt =
                replacePromptVariables(
                        task, audioGenRequest.getPrompt(), audioGenRequest.getPromptVariables());
        audioGenRequest.setPrompt(prompt);
        return helper.generateAudio(
                task, llm, audioGenRequest, getPayloadStoreLocation(task), tokenUsageLogger);
    }

    public List<Float> generateEmbeddings(Task task, EmbeddingGenRequest embeddingGenRequest) {
        AIModel llm = this.modelProvider.getModel(embeddingGenRequest);
        return helper.generateEmbeddings(task, llm, embeddingGenRequest, tokenUsageLogger);
    }

    public LLMResponse generateVideo(Task task, VideoGenRequest videoGenRequest) {
        AIModel llm = this.modelProvider.getModel(videoGenRequest);
        String prompt =
                replacePromptVariables(
                        task, videoGenRequest.getPrompt(), videoGenRequest.getPromptVariables());
        videoGenRequest.setPrompt(prompt);
        return helper.generateVideo(
                task, llm, videoGenRequest, getPayloadStoreLocation(task), tokenUsageLogger);
    }

    public LLMResponse checkVideoStatus(Task task, VideoGenRequest videoGenRequest) {
        AIModel llm = this.modelProvider.getModel(videoGenRequest);
        return helper.checkVideoStatus(task, llm, videoGenRequest, getPayloadStoreLocation(task));
    }

    public String replacePromptVariables(
            Task task, String prompt, Map<String, Object> paramReplacement) {
        if (paramReplacement != null) {
            prompt = StringTemplate.fString(prompt, paramReplacement);
        }
        return prompt;
    }

    public String getPayloadStoreLocation(Task task) {
        return payloadStoreLocation
                + "/"
                + task.getWorkflowInstanceId()
                + "/"
                + task.getTaskId()
                + "/"
                + UUID.randomUUID();
    }
}
