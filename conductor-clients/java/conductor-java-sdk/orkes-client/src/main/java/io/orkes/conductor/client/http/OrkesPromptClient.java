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
package io.orkes.conductor.client.http;

import java.util.List;
import java.util.Map;

import com.netflix.conductor.client.http.ConductorClient;
import com.netflix.conductor.client.http.ConductorClientRequest;
import com.netflix.conductor.client.http.ConductorClientRequest.Method;
import com.netflix.conductor.client.http.ConductorClientResponse;

import io.orkes.conductor.client.PromptClient;
import io.orkes.conductor.client.model.TagObject;
import io.orkes.conductor.client.model.integration.PromptTemplateTestRequest;
import io.orkes.conductor.client.model.integration.ai.PromptTemplate;

import com.fasterxml.jackson.core.type.TypeReference;


public class OrkesPromptClient implements PromptClient {

    private final ConductorClient client;

    public OrkesPromptClient(ConductorClient client) {
        this.client = client;
    }

    @Override
    public void savePrompt(String promptName, String description, String promptTemplate) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.POST)
                .path("/prompts/{name}")
                .addPathParam("name", promptName)
                .addQueryParam("description", description)
                .build();
        client.execute(request);
    }

    @Override
    public PromptTemplate getPrompt(String promptName) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/prompts/{name}")
                .addPathParam("name", promptName)
                .build();

        ConductorClientResponse<PromptTemplate> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }

    @Override
    public List<PromptTemplate> getPrompts() {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/prompts")
                .build();

        ConductorClientResponse<List<PromptTemplate>> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }

    @Override
    public void deletePrompt(String promptName) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.DELETE)
                .path("/prompts/{name}")
                .addPathParam("name", promptName)
                .build();

        client.execute(request);
    }

    @Override
    public List<TagObject> getTagsForPromptTemplate(String promptName) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/prompts/{name}/tags")
                .addPathParam("name", promptName)
                .build();

        ConductorClientResponse<List<TagObject>> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }

    @Override
    public void updateTagForPromptTemplate(String promptName, List<TagObject> tags) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.PUT)
                .path("/prompts/{name}/tags")
                .addPathParam("name", promptName)
                .body(tags)
                .build();

        client.execute(request);
    }

    @Override
    public void deleteTagForPromptTemplate(String promptName, List<TagObject> tags) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.DELETE)
                .path("/prompts/{name}/tags")
                .addPathParam("name", promptName)
                .body(tags)
                .build();

        client.execute(request);
    }

    @Override
    public String testPrompt(String promptText, Map<String, Object> variables, String aiIntegration, String textCompleteModel, float temperature, float topP,
                             List<String> stopWords) {
        PromptTemplateTestRequest body = new PromptTemplateTestRequest();
        body.setPrompt(promptText);
        body.setLlmProvider(aiIntegration);
        body.setModel(textCompleteModel);
        body.setTemperature((double) temperature);
        body.setTopP((double) topP);
        body.setStopWords(stopWords == null ? List.of() : stopWords);
        body.setPromptVariables(variables);

        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.POST)
                .path("/prompts/test")
                .body(body)
                .build();

        ConductorClientResponse<String> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }
}
