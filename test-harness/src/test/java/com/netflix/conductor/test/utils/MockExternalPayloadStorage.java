/*
 * Copyright 2021 Netflix, Inc.
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
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.SubWorkflowParams;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.ExternalStorageLocation;
import com.netflix.conductor.common.utils.ExternalPayloadStorage;

import com.fasterxml.jackson.databind.ObjectMapper;

@ConditionalOnProperty(name = "conductor.external-payload-storage.type", havingValue = "mock")
@Component
public class MockExternalPayloadStorage implements ExternalPayloadStorage {

    private static final Logger LOGGER = LoggerFactory.getLogger(MockExternalPayloadStorage.class);

    public static final String INPUT_PAYLOAD_PATH = "payload/input";

    public static final String INITIAL_WORKFLOW_INPUT_PATH = "workflow/input";
    public static final String WORKFLOW_OUTPUT_PATH = "workflow/output";
    public static final String TASK_OUTPUT_PATH = "task/output";
    public static final String DYNAMIC_FORK_LARGE_PAYLOAD_PATH = "dynamic_fork_large_payload";

    public static final String TEMP_FILE_PATH = "/input.json";

    private final ObjectMapper objectMapper;

    @Autowired
    public MockExternalPayloadStorage(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        LOGGER.info("Initialized {}", this.getClass().getCanonicalName());
    }

    @Override
    public ExternalStorageLocation getLocation(
            Operation operation, PayloadType payloadType, String path) {
        ExternalStorageLocation location = new ExternalStorageLocation();
        location.setUri("http://some/uri");
        switch (payloadType) {
            case TASK_INPUT:
            case WORKFLOW_INPUT:
                location.setPath(INPUT_PAYLOAD_PATH);
                break;
            case WORKFLOW_OUTPUT:
                location.setPath(WORKFLOW_OUTPUT_PATH);
                break;
            case TASK_OUTPUT:
                location.setPath(TASK_OUTPUT_PATH);
                break;
        }
        return location;
    }

    @Override
    public void upload(String path, InputStream payload, long payloadSize) {
        try {
            URL filePathURL = MockExternalPayloadStorage.class.getResource(TEMP_FILE_PATH);
            File file = new File(filePathURL.toURI());
            try (FileOutputStream outputStream = new FileOutputStream(file)) {
                int read;
                byte[] bytes = new byte[1024];
                while ((read = payload.read(bytes)) != -1) {
                    outputStream.write(bytes, 0, read);
                }
                outputStream.flush();
            }
        } catch (Exception e) {
            // just handle this exception here and return empty map so that test will fail in case
            // this exception is thrown
        } finally {
            try {
                if (payload != null) {
                    payload.close();
                }
            } catch (IOException e) {
                LOGGER.warn("Unable to close inputstream when writing to file");
            }
        }
    }

    @Override
    public InputStream download(String path) {
        try {
            Map<String, Object> payload = getPayload(path);
            String jsonString = objectMapper.writeValueAsString(payload);
            return new ByteArrayInputStream(jsonString.getBytes());
        } catch (IOException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getPayload(String path) {
        Map<String, Object> stringObjectMap = new HashMap<>();
        try {
            switch (path) {
                case INITIAL_WORKFLOW_INPUT_PATH:
                    stringObjectMap.put("param1", "p1 value");
                    stringObjectMap.put("param2", "p2 value");
                    stringObjectMap.put("case", "two");
                    return stringObjectMap;
                case TASK_OUTPUT_PATH:
                    InputStream opStream =
                            MockExternalPayloadStorage.class.getResourceAsStream("/output.json");
                    return objectMapper.readValue(opStream, Map.class);
                case INPUT_PAYLOAD_PATH:
                case WORKFLOW_OUTPUT_PATH:
                    InputStream ipStream =
                            MockExternalPayloadStorage.class.getResourceAsStream(TEMP_FILE_PATH);
                    return objectMapper.readValue(ipStream, Map.class);
                case DYNAMIC_FORK_LARGE_PAYLOAD_PATH:
                    return curateDynamicForkLargePayload();
                default:
                    return objectMapper.readValue(
                            MockExternalPayloadStorage.class.getResourceAsStream(path), Map.class);
            }
        } catch (IOException e) {
            // just handle this exception here and return empty map so that test will fail in case
            // this exception is thrown
        }
        return stringObjectMap;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> curateDynamicForkLargePayload() {
        Map<String, Object> dynamicForkLargePayload = new HashMap<>();
        try {
            InputStream inputStream =
                    MockExternalPayloadStorage.class.getResourceAsStream("/output.json");
            Map<String, Object> largePayload = objectMapper.readValue(inputStream, Map.class);

            WorkflowTask simpleWorkflowTask = new WorkflowTask();
            simpleWorkflowTask.setName("integration_task_10");
            simpleWorkflowTask.setTaskReferenceName("t10");
            simpleWorkflowTask.setType(TaskType.SIMPLE.name());
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
            subWorkflowTask.setType(TaskType.SUB_WORKFLOW.name());
            subWorkflowTask.setTaskReferenceName("large_payload_subworkflow");
            subWorkflowTask.setInputParameters(largePayload);
            subWorkflowTask.setSubWorkflowParam(subWorkflowParams);

            dynamicForkLargePayload.put("dynamicTasks", Collections.singletonList(subWorkflowTask));
            dynamicForkLargePayload.put(
                    "dynamicTasksInput",
                    Collections.singletonMap("large_payload_subworkflow", largePayload));
        } catch (IOException e) {
            // just handle this exception here and return empty map so that test will fail in case
            // this exception is thrown
        }
        return dynamicForkLargePayload;
    }
}
