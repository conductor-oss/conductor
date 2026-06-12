/*
 * Copyright 2021 Conductor Authors.
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
package com.netflix.conductor.test.utils;

import java.io.*;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.workflow.SubWorkflowParams;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.ExternalStorageLocation;
import com.netflix.conductor.common.utils.ExternalPayloadStorage;

import com.fasterxml.jackson.databind.ObjectMapper;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_SIMPLE;
import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_SUB_WORKFLOW;

/** A {@link ExternalPayloadStorage} implementation that stores payload in file. */
@ConditionalOnProperty(name = "conductor.external-payload-storage.type", havingValue = "mock")
@Component
public class MockExternalPayloadStorage implements ExternalPayloadStorage {

    private static final Logger LOGGER = LoggerFactory.getLogger(MockExternalPayloadStorage.class);

    private final ObjectMapper objectMapper;
    private final File payloadDir;

    public MockExternalPayloadStorage(ObjectMapper objectMapper) throws IOException {
        this.objectMapper = objectMapper;
        this.payloadDir = Files.createTempDirectory("payloads").toFile();
        LOGGER.info(
                "{} initialized in directory: {}",
                this.getClass().getSimpleName(),
                payloadDir.getAbsolutePath());
    }

    @Override
    public ExternalStorageLocation getLocation(
            Operation operation, PayloadType payloadType, String path) {
        ExternalStorageLocation location = new ExternalStorageLocation();
        location.setPath(UUID.randomUUID() + ".json");
        return location;
    }

    @Override
    public void upload(String path, InputStream payload, long payloadSize) {
        File file = new File(payloadDir, path);
        String filePath = file.getAbsolutePath();
        try {
            if (!file.exists() && file.createNewFile()) {
                LOGGER.debug("Created file: {}", filePath);
            }
            IOUtils.copy(payload, new FileOutputStream(file));
            LOGGER.debug("Written to {}", filePath);
        } catch (IOException e) {
            // just handle this exception here and return empty map so that test will fail in case
            // this exception is thrown
            LOGGER.error("Error writing to {}", filePath);
        } finally {
            try {
                if (payload != null) {
                    payload.close();
                }
            } catch (IOException e) {
                LOGGER.warn("Unable to close input stream when writing to file");
            }
        }
    }

    @Override
    public InputStream download(String path) {
        try {
            LOGGER.debug("Reading from {}", path);
            return new FileInputStream(new File(payloadDir, path));
        } catch (IOException e) {
            LOGGER.error("Error reading {}", path, e);
            return null;
        }
    }

    public void upload(String path, Map<String, Object> payload) {
        try {
            InputStream bais = new ByteArrayInputStream(objectMapper.writeValueAsBytes(payload));
            upload(path, bais, 0);
        } catch (IOException e) {
            LOGGER.error("Error serializing map to json", e);
        }
    }

    public InputStream readOutputDotJson() {
        return MockExternalPayloadStorage.class.getResourceAsStream("/output.json");
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> curateDynamicForkLargePayload() {
        Map<String, Object> dynamicForkLargePayload = new HashMap<>();
        try {
            InputStream inputStream = readOutputDotJson();
            Map<String, Object> largePayload = objectMapper.readValue(inputStream, Map.class);

            WorkflowTask simpleWorkflowTask = new WorkflowTask();
            simpleWorkflowTask.setName("integration_task_10");
            simpleWorkflowTask.setTaskReferenceName("t10");
            simpleWorkflowTask.setType(TASK_TYPE_SIMPLE);
            simpleWorkflowTask.setInputParameters(
                    Collections.singletonMap("p1", "${workflow.input.imageType}"));

            WorkflowDef subWorkflowDef = new WorkflowDef();
            subWorkflowDef.setName("one_task_workflow");
            subWorkflowDef.setVersion(1);
            subWorkflowDef.setTasks(Collections.singletonList(simpleWorkflowTask));

            SubWorkflowParams subWorkflowParams = new SubWorkflowParams();
            subWorkflowParams.setName("one_task_workflow");
            subWorkflowParams.setVersion(1);
            subWorkflowParams.setWorkflowDef(subWorkflowDef);

            WorkflowTask subWorkflowTask = new WorkflowTask();
            subWorkflowTask.setName("large_payload_subworkflow");
            subWorkflowTask.setType(TASK_TYPE_SUB_WORKFLOW);
            subWorkflowTask.setTaskReferenceName("large_payload_subworkflow");
            subWorkflowTask.setInputParameters(largePayload);
            subWorkflowTask.setSubWorkflowParam(subWorkflowParams);

            dynamicForkLargePayload.put("dynamicTasks", List.of(subWorkflowTask));
            dynamicForkLargePayload.put(
                    "dynamicTasksInput", Map.of("large_payload_subworkflow", largePayload));
        } catch (IOException e) {
            // just handle this exception here and return empty map so that test will fail in case
            // this exception is thrown
        }
        return dynamicForkLargePayload;
    }

    public Map<String, Object> downloadPayload(String path) {
        InputStream inputStream = download(path);
        if (inputStream != null) {
            try {
                Map<String, Object> largePayload = objectMapper.readValue(inputStream, Map.class);
                return largePayload;
            } catch (IOException e) {
                LOGGER.error("Error in downloading payload for path {}", path, e);
            }
        }
        return new HashMap<>();
    }

    public Map<String, Object> createLargePayload(int repeat) {
        Map<String, Object> largePayload = new HashMap<>();
        try {
            InputStream inputStream = readOutputDotJson();
            Map<String, Object> payload = objectMapper.readValue(inputStream, Map.class);
            for (int i = 0; i < repeat; i++) {
                largePayload.put(String.valueOf(i), payload);
            }
        } catch (IOException e) {
            // just handle this exception here and return empty map so that test will fail in case
            // this exception is thrown
        }
        return largePayload;
    }
}
