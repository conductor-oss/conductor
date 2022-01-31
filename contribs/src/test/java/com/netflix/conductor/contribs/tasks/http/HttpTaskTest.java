/*
 * Copyright 2022 Netflix, Inc.
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
package com.netflix.conductor.contribs.tasks.http;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.*;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.MediaType;
import org.testcontainers.containers.MockServerContainer;
import org.testcontainers.utility.DockerImageName;

import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.execution.DeciderService;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.tasks.SystemTaskRegistry;
import com.netflix.conductor.core.utils.ExternalPayloadStorageUtils;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

@SuppressWarnings("unchecked")
// @Ignore // Test causes "OutOfMemoryError" error during build
public class HttpTaskTest {

    private static final String ERROR_RESPONSE = "Something went wrong!";
    private static final String TEXT_RESPONSE = "Text Response";
    private static final double NUM_RESPONSE = 42.42d;

    private HttpTask httpTask;
    private WorkflowExecutor workflowExecutor;
    private final WorkflowModel workflow = new WorkflowModel();

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static String JSON_RESPONSE;

    @ClassRule
    public static MockServerContainer mockServer =
            new MockServerContainer(DockerImageName.parse("mockserver/mockserver"));

    @BeforeClass
    public static void init() throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("key", "value1");
        map.put("num", 42);
        map.put("SomeKey", null);
        JSON_RESPONSE = objectMapper.writeValueAsString(map);

        final TypeReference<Map<String, Object>> mapOfObj = new TypeReference<>() {};
        MockServerClient client =
                new MockServerClient(mockServer.getHost(), mockServer.getServerPort());
        client.when(request().withPath("/post").withMethod("POST"))
                .respond(
                        request -> {
                            Map<String, Object> reqBody =
                                    objectMapper.readValue(request.getBody().toString(), mapOfObj);
                            Set<String> keys = reqBody.keySet();
                            Map<String, Object> respBody = new HashMap<>();
                            keys.forEach(k -> respBody.put(k, k));
                            return response()
                                    .withContentType(MediaType.APPLICATION_JSON)
                                    .withBody(objectMapper.writeValueAsString(respBody));
                        });
        client.when(request().withPath("/post2").withMethod("POST"))
                .respond(response().withStatusCode(204));
        client.when(request().withPath("/failure").withMethod("GET"))
                .respond(
                        response()
                                .withStatusCode(500)
                                .withContentType(MediaType.TEXT_PLAIN)
                                .withBody(ERROR_RESPONSE));
        client.when(request().withPath("/text").withMethod("GET"))
                .respond(response().withBody(TEXT_RESPONSE));
        client.when(request().withPath("/numeric").withMethod("GET"))
                .respond(response().withBody(String.valueOf(NUM_RESPONSE)));
        client.when(request().withPath("/json").withMethod("GET"))
                .respond(
                        response()
                                .withContentType(MediaType.APPLICATION_JSON)
                                .withBody(JSON_RESPONSE));
    }

    @Before
    public void setup() {
        workflowExecutor = mock(WorkflowExecutor.class);
        DefaultRestTemplateProvider defaultRestTemplateProvider =
                new DefaultRestTemplateProvider(Duration.ofMillis(150), Duration.ofMillis(100));
        httpTask = new HttpTask(defaultRestTemplateProvider, objectMapper);
    }

    @Test
    public void testPost() {

        TaskModel task = new TaskModel();
        HttpTask.Input input = new HttpTask.Input();
        input.setUri("http://" + mockServer.getHost() + ":" + mockServer.getServerPort() + "/post");
        Map<String, Object> body = new HashMap<>();
        body.put("input_key1", "value1");
        body.put("input_key2", 45.3d);
        body.put("someKey", null);
        input.setBody(body);
        input.setMethod("POST");
        input.setReadTimeOut(1000);
        task.getInputData().put(HttpTask.REQUEST_PARAMETER_NAME, input);

        httpTask.start(workflow, task, workflowExecutor);
        assertEquals(task.getReasonForIncompletion(), TaskModel.Status.COMPLETED, task.getStatus());
        Map<String, Object> hr = (Map<String, Object>) task.getOutputData().get("response");
        Object response = hr.get("body");
        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertTrue("response is: " + response, response instanceof Map);
        Map<String, Object> map = (Map<String, Object>) response;
        Set<String> inputKeys = body.keySet();
        Set<String> responseKeys = map.keySet();
        inputKeys.containsAll(responseKeys);
        responseKeys.containsAll(inputKeys);
    }

    @Test
    public void testPostNoContent() {

        TaskModel task = new TaskModel();
        HttpTask.Input input = new HttpTask.Input();
        input.setUri(
                "http://" + mockServer.getHost() + ":" + mockServer.getServerPort() + "/post2");
        Map<String, Object> body = new HashMap<>();
        body.put("input_key1", "value1");
        body.put("input_key2", 45.3d);
        input.setBody(body);
        input.setMethod("POST");
        task.getInputData().put(HttpTask.REQUEST_PARAMETER_NAME, input);

        httpTask.start(workflow, task, workflowExecutor);
        assertEquals(task.getReasonForIncompletion(), TaskModel.Status.COMPLETED, task.getStatus());
        Map<String, Object> hr = (Map<String, Object>) task.getOutputData().get("response");
        Object response = hr.get("body");
        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertNull("response is: " + response, response);
    }

    @Test
    public void testFailure() {

        TaskModel task = new TaskModel();
        HttpTask.Input input = new HttpTask.Input();
        input.setUri(
                "http://" + mockServer.getHost() + ":" + mockServer.getServerPort() + "/failure");
        input.setMethod("GET");
        task.getInputData().put(HttpTask.REQUEST_PARAMETER_NAME, input);

        httpTask.start(workflow, task, workflowExecutor);
        assertEquals(
                "Task output: " + task.getOutputData(), TaskModel.Status.FAILED, task.getStatus());
        assertTrue(task.getReasonForIncompletion().contains(ERROR_RESPONSE));

        task.setStatus(TaskModel.Status.SCHEDULED);
        task.getInputData().remove(HttpTask.REQUEST_PARAMETER_NAME);
        httpTask.start(workflow, task, workflowExecutor);
        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertEquals(HttpTask.MISSING_REQUEST, task.getReasonForIncompletion());
    }

    @Test
    public void testPostAsyncComplete() {

        TaskModel task = new TaskModel();
        HttpTask.Input input = new HttpTask.Input();
        input.setUri("http://" + mockServer.getHost() + ":" + mockServer.getServerPort() + "/post");
        Map<String, Object> body = new HashMap<>();
        body.put("input_key1", "value1");
        body.put("input_key2", 45.3d);
        input.setBody(body);
        input.setMethod("POST");
        task.getInputData().put(HttpTask.REQUEST_PARAMETER_NAME, input);
        task.getInputData().put("asyncComplete", true);

        httpTask.start(workflow, task, workflowExecutor);
        assertEquals(
                task.getReasonForIncompletion(), TaskModel.Status.IN_PROGRESS, task.getStatus());
        Map<String, Object> hr = (Map<String, Object>) task.getOutputData().get("response");
        Object response = hr.get("body");
        assertEquals(TaskModel.Status.IN_PROGRESS, task.getStatus());
        assertTrue("response is: " + response, response instanceof Map);
        Map<String, Object> map = (Map<String, Object>) response;
        Set<String> inputKeys = body.keySet();
        Set<String> responseKeys = map.keySet();
        inputKeys.containsAll(responseKeys);
        responseKeys.containsAll(inputKeys);
    }

    @Test
    public void testTextGET() {
        TaskModel task = new TaskModel();
        HttpTask.Input input = new HttpTask.Input();
        input.setUri("http://" + mockServer.getHost() + ":" + mockServer.getServerPort() + "/text");
        input.setMethod("GET");
        task.getInputData().put(HttpTask.REQUEST_PARAMETER_NAME, input);

        httpTask.start(workflow, task, workflowExecutor);
        Map<String, Object> hr = (Map<String, Object>) task.getOutputData().get("response");
        Object response = hr.get("body");
        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals(TEXT_RESPONSE, response);
    }

    @Test
    public void testNumberGET() {
        TaskModel task = new TaskModel();
        HttpTask.Input input = new HttpTask.Input();
        input.setUri(
                "http://" + mockServer.getHost() + ":" + mockServer.getServerPort() + "/numeric");
        input.setMethod("GET");
        task.getInputData().put(HttpTask.REQUEST_PARAMETER_NAME, input);

        httpTask.start(workflow, task, workflowExecutor);
        Map<String, Object> hr = (Map<String, Object>) task.getOutputData().get("response");
        Object response = hr.get("body");
        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals(NUM_RESPONSE, response);
        assertTrue(response instanceof Number);
    }

    @Test
    public void testJsonGET() throws JsonProcessingException {

        TaskModel task = new TaskModel();
        HttpTask.Input input = new HttpTask.Input();
        input.setUri("http://" + mockServer.getHost() + ":" + mockServer.getServerPort() + "/json");
        input.setMethod("GET");
        task.getInputData().put(HttpTask.REQUEST_PARAMETER_NAME, input);

        httpTask.start(workflow, task, workflowExecutor);
        Map<String, Object> hr = (Map<String, Object>) task.getOutputData().get("response");
        Object response = hr.get("body");
        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertTrue(response instanceof Map);
        Map<String, Object> map = (Map<String, Object>) response;
        assertEquals(JSON_RESPONSE, objectMapper.writeValueAsString(map));
    }

    @Test
    public void testExecute() {

        TaskModel task = new TaskModel();
        HttpTask.Input input = new HttpTask.Input();
        input.setUri("http://" + mockServer.getHost() + ":" + mockServer.getServerPort() + "/json");
        input.setMethod("GET");
        task.getInputData().put(HttpTask.REQUEST_PARAMETER_NAME, input);
        task.setStatus(TaskModel.Status.SCHEDULED);
        task.setScheduledTime(0);

        boolean executed = httpTask.execute(workflow, task, workflowExecutor);
        assertFalse(executed);
    }

    @Test
    public void testHTTPGetConnectionTimeOut() {
        TaskModel task = new TaskModel();
        HttpTask.Input input = new HttpTask.Input();
        Instant start = Instant.now();
        input.setConnectionTimeOut(110);
        input.setMethod("GET");
        input.setUri("http://10.255.14.15");
        task.getInputData().put(HttpTask.REQUEST_PARAMETER_NAME, input);
        task.setStatus(TaskModel.Status.SCHEDULED);
        task.setScheduledTime(0);
        httpTask.start(workflow, task, workflowExecutor);
        Instant end = Instant.now();
        long diff = end.toEpochMilli() - start.toEpochMilli();
        assertEquals(task.getStatus(), TaskModel.Status.FAILED);
        assertTrue(diff >= 110L);
    }

    @Test
    public void testHTTPGETReadTimeOut() {
        TaskModel task = new TaskModel();
        HttpTask.Input input = new HttpTask.Input();
        input.setReadTimeOut(-1);
        input.setMethod("GET");
        input.setUri("http://" + mockServer.getHost() + ":" + mockServer.getServerPort() + "/json");
        task.getInputData().put(HttpTask.REQUEST_PARAMETER_NAME, input);
        task.setStatus(TaskModel.Status.SCHEDULED);
        task.setScheduledTime(0);

        httpTask.start(workflow, task, workflowExecutor);
        assertEquals(task.getStatus(), TaskModel.Status.FAILED);
    }

    @Test
    public void testOptional() {
        TaskModel task = new TaskModel();
        HttpTask.Input input = new HttpTask.Input();
        input.setUri(
                "http://" + mockServer.getHost() + ":" + mockServer.getServerPort() + "/failure");
        input.setMethod("GET");
        task.getInputData().put(HttpTask.REQUEST_PARAMETER_NAME, input);

        httpTask.start(workflow, task, workflowExecutor);
        assertEquals(
                "Task output: " + task.getOutputData(), TaskModel.Status.FAILED, task.getStatus());
        assertTrue(task.getReasonForIncompletion().contains(ERROR_RESPONSE));
        assertFalse(task.getStatus().isSuccessful());

        task.setStatus(TaskModel.Status.SCHEDULED);
        task.getInputData().remove(HttpTask.REQUEST_PARAMETER_NAME);
        task.setReferenceTaskName("t1");
        httpTask.start(workflow, task, workflowExecutor);
        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertEquals(HttpTask.MISSING_REQUEST, task.getReasonForIncompletion());
        assertFalse(task.getStatus().isSuccessful());

        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setOptional(true);
        workflowTask.setName("HTTP");
        workflowTask.setWorkflowTaskType(TaskType.USER_DEFINED);
        workflowTask.setTaskReferenceName("t1");

        WorkflowDef def = new WorkflowDef();
        def.getTasks().add(workflowTask);

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(def);
        workflow.getTasks().add(task);

        MetadataDAO metadataDAO = mock(MetadataDAO.class);
        ExternalPayloadStorageUtils externalPayloadStorageUtils =
                mock(ExternalPayloadStorageUtils.class);
        ParametersUtils parametersUtils = mock(ParametersUtils.class);
        SystemTaskRegistry systemTaskRegistry = mock(SystemTaskRegistry.class);

        new DeciderService(
                        parametersUtils,
                        metadataDAO,
                        externalPayloadStorageUtils,
                        systemTaskRegistry,
                        Collections.emptyMap(),
                        Duration.ofMinutes(60))
                .decide(workflow);
    }
}
