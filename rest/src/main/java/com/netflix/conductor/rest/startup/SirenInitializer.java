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

    private static final Logger LOGGER = LoggerFactory.getLogger(SirenInitializer.class);

    private final RestTemplate restTemplate;

    @Value("${server.port:8080}")
    private int port;

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
        createWorkflow(sirenFinalizeExecutionWorkflow, headers, "sirenFinalizeExecutionWorkflow");
        createWorkflow(sirenWebhookWorkflow, headers, "sirenWebhookWorkflow");
        LOGGER.info("Siren workflows are created");

        createTask(sendNotificationTask, headers, "sendNotificationTask");
        createTask(sendWebhookTask, headers, "sendWebhookTask");
        createTask(finalizeWorkflowExecutionTask, headers, "finalizeWorkflowExecutionTask");
        LOGGER.info("Siren tasks are created");

        createEventHandler(
                finalizeWorkflowExecutionEventHandler,
                headers,
                "finalizeWorkflowExecutionEventHandler");
        LOGGER.info("Siren event handlers are created");
    }

    private void createWorkflow(
            Resource resource, MultiValueMap<String, String> headers, String workflowName) {
        try {
            HttpEntity<String> request = new HttpEntity<>(readToString(resource), headers);
            restTemplate.postForEntity(url("/api/metadata/workflow"), request, Map.class);
        } catch (RestClientException e) {
            LOGGER.info("Skipping create {} ", workflowName);
            e.printStackTrace();
        }
    }

    private void createTask(
            Resource resource, MultiValueMap<String, String> headers, String taskName) {
        try {
            HttpEntity<String> request = new HttpEntity<>(readToString(resource), headers);
            restTemplate.postForEntity(url("/api/metadata/taskdefs"), request, Map.class);
        } catch (RestClientException e) {
            LOGGER.info("Skipping create {} ", taskName);
            e.printStackTrace();
        }
    }

    private void createEventHandler(
            Resource resource, MultiValueMap<String, String> headers, String eventHandlerName) {
        try {
            HttpEntity<String> request = new HttpEntity<>(readToString(resource), headers);
            restTemplate.postForEntity(url("/api/event"), request, Map.class);
        } catch (RestClientException e) {
            LOGGER.info("Skipping create {} ", eventHandlerName);
            e.printStackTrace();
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

    private String url(String path) {
        // TODO replace with url
        return "http://localhost:" + port + path;
    }
}
