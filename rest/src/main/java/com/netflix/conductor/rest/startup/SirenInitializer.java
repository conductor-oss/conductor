/*
 * Copyright 2024 Conductor Authors.
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
package com.netflix.conductor.rest.startup;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Component
public class SirenInitializer {
    private static final String ALREADY_EXISTS_KEYWORD = "already exists";
    private static final Logger LOGGER = LoggerFactory.getLogger(SirenInitializer.class);

    private final RestTemplate restTemplate;

    @Value("${server.url:http://localhost:8080}")
    private String url;

    @Value("classpath:./siren/eventhandlers/finalizeWorkflowExecutionEventHandler.json")
    private Resource finalizeWorkflowExecutionEventHandler;

    @Value("classpath:./siren/tasks/finalizeWorkflowExecutionTask.json")
    private Resource finalizeWorkflowExecutionTask;

    @Value("classpath:./siren/tasks/sendNotificationTask.json")
    private Resource sendNotificationTask;

    @Value("classpath:./siren/tasks/sendWebhookTask.json")
    private Resource sendWebhookTask;

    @Value("classpath:./siren/workflows/sirenFinalizeExecutionWorkflow.json")
    private Resource sirenFinalizeExecutionWorkflow;

    @Value("classpath:./siren/workflows/sirenWebhookWorkflow.json")
    private Resource sirenWebhookWorkflow;

    public SirenInitializer(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder.build();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void setupSirenResources() {
        LOGGER.info("Loading siren resources");
        createSirenResources();
    }

    private void createSirenResources() {
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        headers.add(CONTENT_TYPE, APPLICATION_JSON_VALUE);
        createWorkflow(sirenFinalizeExecutionWorkflow, headers);
        createWorkflow(sirenWebhookWorkflow, headers);
        LOGGER.info("Siren workflows are created");

        createTask(sendNotificationTask, headers);
        createTask(sendWebhookTask, headers);
        createTask(finalizeWorkflowExecutionTask, headers);
        LOGGER.info("Siren tasks are created");

        createEventHandler(finalizeWorkflowExecutionEventHandler, headers);
        LOGGER.info("Siren event handlers are created");
    }

    private void createWorkflow(Resource resource, MultiValueMap<String, String> headers) {
        try {
            HttpEntity<String> request = new HttpEntity<>(readToString(resource), headers);
            restTemplate.postForEntity(url + "/api/metadata/workflow", request, Map.class);
        } catch (RestClientException e) {
            handleException(e);
        }
    }

    private void createTask(Resource resource, MultiValueMap<String, String> headers) {
        try {
            HttpEntity<String> request = new HttpEntity<>(readToString(resource), headers);
            restTemplate.postForEntity(url + "/api/metadata/taskdefs", request, Map.class);
        } catch (RestClientException e) {
            handleException(e);
        }
    }

    private void createEventHandler(Resource resource, MultiValueMap<String, String> headers) {
        try {
            HttpEntity<String> request = new HttpEntity<>(readToString(resource), headers);
            restTemplate.postForEntity(url + "/api/event", request, Map.class);
        } catch (RestClientException e) {
            handleException(e);
        }
    }

    private void handleException(RestClientException e) {
        if (e.getMessage().contains(ALREADY_EXISTS_KEYWORD)) {
            LOGGER.info("Skipping creation: {}", e.getMessage());
        } else {
            LOGGER.error("Error while creation ", e);
            throw e;
        }
    }

    private String readToString(Resource resource) {
        try {
            return FileCopyUtils.copyToString(new InputStreamReader(resource.getInputStream()));
        } catch (IOException e) {
            LOGGER.error("Error while loading siren resources", e);
            throw new RuntimeException("Error reading resources", e);
        }
    }
}
