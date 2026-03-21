/*
 * Copyright 2022 Conductor Authors.
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
package com.netflix.conductor.tasks.http;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.springframework.http.MediaType;

import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;
import com.netflix.conductor.tasks.http.providers.DefaultRestTemplateProvider;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for HttpTask that do not require Docker/Testcontainers. Tests input resolution (with
 * and without http_request key) and accept parameter handling (single string vs list).
 */
public class HttpTaskUnitTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private WorkflowExecutor workflowExecutor;
    private HttpTask httpTask;
    private final WorkflowModel workflow = new WorkflowModel();

    @Before
    public void setup() {
        workflowExecutor = mock(WorkflowExecutor.class);
        DefaultRestTemplateProvider restTemplateProvider =
                new DefaultRestTemplateProvider(
                        java.time.Duration.ofMillis(150), java.time.Duration.ofMillis(100));
        httpTask = new HttpTask(restTemplateProvider, objectMapper);
    }

    // ---- Accept parameter tests (Input deserialization) ----

    @Test
    public void testAcceptDefaultValue() {
        HttpTask.Input input = new HttpTask.Input();
        assertEquals("application/json", input.getAccept());
        assertEquals(1, input.getAcceptList().size());
        assertEquals("application/json", input.getAcceptList().get(0));
    }

    @Test
    public void testAcceptSingleString() {
        HttpTask.Input input = new HttpTask.Input();
        input.setAccept("text/plain");
        assertEquals("text/plain", input.getAccept());
        assertEquals(1, input.getAcceptList().size());
        assertEquals("text/plain", input.getAcceptList().get(0));
    }

    @Test
    public void testAcceptMultipleValues() {
        HttpTask.Input input = new HttpTask.Input();
        input.setAccept(Arrays.asList("application/json", "text/plain", "application/xml"));
        assertEquals("application/json", input.getAccept());
        List<String> acceptList = input.getAcceptList();
        assertEquals(3, acceptList.size());
        assertEquals("application/json", acceptList.get(0));
        assertEquals("text/plain", acceptList.get(1));
        assertEquals("application/xml", acceptList.get(2));
    }

    @Test
    public void testAcceptSingleStringDeserialization() {
        Map<String, Object> inputMap = new HashMap<>();
        inputMap.put("uri", "http://example.com");
        inputMap.put("method", "GET");
        inputMap.put("accept", "text/html");

        HttpTask.Input input = objectMapper.convertValue(inputMap, HttpTask.Input.class);
        assertEquals("text/html", input.getAccept());
        assertEquals(1, input.getAcceptList().size());
        assertEquals("text/html", input.getAcceptList().get(0));
    }

    @Test
    public void testAcceptListDeserialization() {
        Map<String, Object> inputMap = new HashMap<>();
        inputMap.put("uri", "http://example.com");
        inputMap.put("method", "GET");
        inputMap.put("accept", Arrays.asList("application/json", "text/plain"));

        HttpTask.Input input = objectMapper.convertValue(inputMap, HttpTask.Input.class);
        assertEquals("application/json", input.getAccept());
        List<String> acceptList = input.getAcceptList();
        assertEquals(2, acceptList.size());
        assertEquals("application/json", acceptList.get(0));
        assertEquals("text/plain", acceptList.get(1));
    }

    @Test
    public void testAcceptMediaTypeParsing() {
        HttpTask.Input input = new HttpTask.Input();
        input.setAccept(Arrays.asList("application/json", "text/plain"));
        List<String> acceptList = input.getAcceptList();
        // Verify all values are valid MediaType strings
        for (String accept : acceptList) {
            MediaType mediaType = MediaType.valueOf(accept);
            assertNotNull(mediaType);
        }
    }

    // ---- Input resolution tests (http_request key vs direct input) ----

    @Test
    public void testInputWithHttpRequestKey() {
        TaskModel task = new TaskModel();
        Map<String, Object> httpRequest = new HashMap<>();
        httpRequest.put("uri", "http://example.com");
        httpRequest.put("method", "GET");
        httpRequest.put("accept", "text/html");
        task.getInputData().put(HttpTask.REQUEST_PARAMETER_NAME, httpRequest);

        httpTask.start(workflow, task, workflowExecutor);
        assertTrue(
                "Should complete successfully with http_request key",
                task.getStatus() == TaskModel.Status.COMPLETED
                        || task.getStatus() == TaskModel.Status.IN_PROGRESS);
        assertNotNull(task.getOutputData().get("response"));
    }

    @Test
    public void testInputWithoutHttpRequestKey() {
        TaskModel task = new TaskModel();
        task.getInputData().put("uri", "http://example.com");
        task.getInputData().put("method", "GET");
        task.getInputData().put("accept", "text/html");

        httpTask.start(workflow, task, workflowExecutor);
        assertTrue(
                "Should complete successfully without http_request key",
                task.getStatus() == TaskModel.Status.COMPLETED
                        || task.getStatus() == TaskModel.Status.IN_PROGRESS);
        assertNotNull(task.getOutputData().get("response"));
    }

    @Test
    public void testInputWithoutHttpRequestKeyAndMissingUri() {
        TaskModel task = new TaskModel();
        task.getInputData().put("method", "GET");

        httpTask.start(workflow, task, workflowExecutor);
        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertTrue(
                "Should fail with missing URI",
                task.getReasonForIncompletion().contains("Missing HTTP URI"));
    }

    @Test
    public void testInputWithoutHttpRequestKeyAndMissingMethod() {
        TaskModel task = new TaskModel();
        task.getInputData().put("uri", "http://example.com");

        httpTask.start(workflow, task, workflowExecutor);
        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertTrue(
                "Should fail with missing method",
                task.getReasonForIncompletion().contains("No HTTP method specified"));
    }

    @Test
    public void testInputDirectWithListAccept() {
        TaskModel task = new TaskModel();
        task.getInputData().put("uri", "http://example.com");
        task.getInputData().put("method", "GET");
        task.getInputData().put("accept", Arrays.asList("application/json", "text/plain"));

        httpTask.start(workflow, task, workflowExecutor);
        assertTrue(
                "Should complete successfully with list accept and direct input",
                task.getStatus() == TaskModel.Status.COMPLETED
                        || task.getStatus() == TaskModel.Status.IN_PROGRESS);
        assertNotNull(task.getOutputData().get("response"));
    }

    @Test
    public void testInputHttpRequestKeyWithListAccept() {
        TaskModel task = new TaskModel();
        Map<String, Object> httpRequest = new HashMap<>();
        httpRequest.put("uri", "http://example.com");
        httpRequest.put("method", "GET");
        httpRequest.put("accept", Arrays.asList("application/json", "application/xml"));
        task.getInputData().put(HttpTask.REQUEST_PARAMETER_NAME, httpRequest);

        httpTask.start(workflow, task, workflowExecutor);
        assertTrue(
                "Should complete successfully with list accept and http_request key",
                task.getStatus() == TaskModel.Status.COMPLETED
                        || task.getStatus() == TaskModel.Status.IN_PROGRESS);
        assertNotNull(task.getOutputData().get("response"));
    }

    @Test
    public void testEmptyInputFails() {
        TaskModel task = new TaskModel();
        // No input at all — falls back to empty inputData map
        httpTask.start(workflow, task, workflowExecutor);
        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertTrue(
                "Should fail with missing URI",
                task.getReasonForIncompletion().contains("Missing HTTP URI"));
    }
}
