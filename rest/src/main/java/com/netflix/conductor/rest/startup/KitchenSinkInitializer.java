package com.netflix.conductor.rest.startup;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Component
public class KitchenSinkInitializer {

    private static final Logger log = LoggerFactory.getLogger(KitchenSinkInitializer.class);

    private final RestTemplate restTemplate;

    @Value("${loadSample:false}")
    private boolean loadSamples;

    @Value("${server.port:8080}")
    private int port;

    @Value("classpath:./kitchensink/kitchensink.json")
    private Resource kitchenSink;

    @Value("classpath:./kitchensink/sub_flow_1.json")
    private Resource subFlow;

    @Value("classpath:./kitchensink/kitchenSink-ephemeralWorkflowWithStoredTasks.json")
    private Resource ephemeralWorkflowWithStoredTasks;

    @Value("classpath:./kitchensink/kitchenSink-ephemeralWorkflowWithEphemeralTasks.json")
    private Resource ephemeralWorkflowWithEphemeralTasks;

    public KitchenSinkInitializer(RestTemplateBuilder restTemplateBuilder) {
        MappingJackson2HttpMessageConverter messageConverter = new MappingJackson2HttpMessageConverter();
        messageConverter.setObjectMapper(new ObjectMapperProvider().getObjectMapper());
        this.restTemplate = restTemplateBuilder.additionalMessageConverters(messageConverter).build();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void setupKitchenSink() {
        try {
            if (loadSamples) {
                log.info("Loading Kitchen Sink examples");
                createKitchenSink();
            }
        } catch (Exception e) {
            log.error("Error initializing kitchen sink", e);
        }
    }

    private void createKitchenSink() throws Exception {
        List<TaskDef> taskDefs = new LinkedList<>();
        TaskDef taskDef;
        for (int i = 0; i < 40; i++) {
            taskDef = new TaskDef("task_" + i, "task_" + i, 1, 0);
            taskDef.setOwnerEmail("example@email.com");
            taskDefs.add(taskDef);
        }

        taskDef = new TaskDef("search_elasticsearch", "search_elasticsearch", 1, 0);
        taskDef.setOwnerEmail("example@email.com");
        taskDefs.add(taskDef);

        restTemplate.postForEntity(url("/api/metadata/taskdefs"), taskDefs, Object.class);

        /*
         * Kitchensink example (stored workflow with stored tasks)
         */
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        headers.add(CONTENT_TYPE, APPLICATION_JSON_VALUE);
        HttpEntity<String> request = new HttpEntity<>(readToString(kitchenSink), headers);
        restTemplate.postForEntity(url("/api/metadata/workflow/"), request, Map.class);

        request = new HttpEntity<>(readToString(subFlow), headers);
        restTemplate.postForEntity(url("/api/metadata/workflow/"), request, Map.class);

        restTemplate.postForEntity(url("/api/workflow/kitchensink"), Collections.singletonMap("task2Name", "task_5"), String.class);
        log.info("Kitchen sink workflow is created!");

        /*
         * Kitchensink example with ephemeral workflow and stored tasks
         */
        request = new HttpEntity<>(readToString(ephemeralWorkflowWithStoredTasks), headers);
        restTemplate.postForEntity(url("/api/workflow/"), request, String.class);
        log.info("Ephemeral Kitchen sink workflow with stored tasks is created!");

        /*
         * Kitchensink example with ephemeral workflow and ephemeral tasks
         */
        request = new HttpEntity<>(readToString(ephemeralWorkflowWithEphemeralTasks), headers);
        restTemplate.postForEntity(url("/api/workflow/"), request, String.class);
        log.info("Ephemeral Kitchen sink workflow with ephemeral tasks is created!");
    }

    private String readToString(Resource resource) throws IOException {
        return FileCopyUtils.copyToString(new InputStreamReader(resource.getInputStream()));
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
