/*
 * Copyright 2020 Netflix, Inc.
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.Task.Status;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.contribs.tasks.http.HttpTask.Input;
import com.netflix.conductor.core.execution.DeciderService;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.mapper.TaskMapper;
import com.netflix.conductor.core.utils.ExternalPayloadStorageUtils;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.dao.MetadataDAO;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

@SuppressWarnings("unchecked")
public class HttpTaskTest {

    private static final String ERROR_RESPONSE = "Something went wrong!";
    private static final String TEXT_RESPONSE = "Text Response";
    private static final double NUM_RESPONSE = 42.42d;

    private HttpTask httpTask;
    private WorkflowExecutor workflowExecutor;
    private final Workflow workflow = new Workflow();

    private static Server server;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static String JSON_RESPONSE;

    @BeforeClass
    public static void init() throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("key", "value1");
        map.put("num", 42);
        map.put("SomeKey", null);
        JSON_RESPONSE = objectMapper.writeValueAsString(map);

        server = new Server(7009);
        ServletContextHandler servletContextHandler = new ServletContextHandler(server, "/",
            ServletContextHandler.SESSIONS);
        servletContextHandler.setHandler(new EchoHandler());
        server.start();
    }

    @AfterClass
    public static void cleanup() {
        if (server != null) {
            try {
                server.stop();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Before
    public void setup() {
        workflowExecutor = mock(WorkflowExecutor.class);
        DefaultRestTemplateProvider defaultRestTemplateProvider = new DefaultRestTemplateProvider(150, 100);
        httpTask = new HttpTask(defaultRestTemplateProvider, objectMapper);
    }

    @Test
    public void testPost() {

        Task task = new Task();
        Input input = new Input();
        input.setUri("http://localhost:7009/post");
        Map<String, Object> body = new HashMap<>();
        body.put("input_key1", "value1");
        body.put("input_key2", 45.3d);
        body.put("someKey", null);
        input.setBody(body);
        input.setMethod("POST");
        task.getInputData().put(HttpTask.REQUEST_PARAMETER_NAME, input);

        httpTask.start(workflow, task, workflowExecutor);
        assertEquals(task.getReasonForIncompletion(), Status.COMPLETED, task.getStatus());
        Map<String, Object> hr = (Map<String, Object>) task.getOutputData().get("response");
        Object response = hr.get("body");
        assertEquals(Status.COMPLETED, task.getStatus());
        assertTrue("response is: " + response, response instanceof Map);
        Map<String, Object> map = (Map<String, Object>) response;
        Set<String> inputKeys = body.keySet();
        Set<String> responseKeys = map.keySet();
        inputKeys.containsAll(responseKeys);
        responseKeys.containsAll(inputKeys);
    }

    @Test
    public void testPostNoContent() {

        Task task = new Task();
        Input input = new Input();
        input.setUri("http://localhost:7009/post2");
        Map<String, Object> body = new HashMap<>();
        body.put("input_key1", "value1");
        body.put("input_key2", 45.3d);
        input.setBody(body);
        input.setMethod("POST");
        task.getInputData().put(HttpTask.REQUEST_PARAMETER_NAME, input);

        httpTask.start(workflow, task, workflowExecutor);
        assertEquals(task.getReasonForIncompletion(), Status.COMPLETED, task.getStatus());
        Map<String, Object> hr = (Map<String, Object>) task.getOutputData().get("response");
        Object response = hr.get("body");
        assertEquals(Status.COMPLETED, task.getStatus());
        assertNull("response is: " + response, response);
    }

    @Test
    public void testFailure() {

        Task task = new Task();
        Input input = new Input();
        input.setUri("http://localhost:7009/failure");
        input.setMethod("GET");
        task.getInputData().put(HttpTask.REQUEST_PARAMETER_NAME, input);

        httpTask.start(workflow, task, workflowExecutor);
        assertEquals("Task output: " + task.getOutputData(), Status.FAILED, task.getStatus());
        assertTrue(task.getReasonForIncompletion().contains(ERROR_RESPONSE));

        task.setStatus(Status.SCHEDULED);
        task.getInputData().remove(HttpTask.REQUEST_PARAMETER_NAME);
        httpTask.start(workflow, task, workflowExecutor);
        assertEquals(Status.FAILED, task.getStatus());
        assertEquals(HttpTask.MISSING_REQUEST, task.getReasonForIncompletion());
    }

    @Test
    public void testPostAsyncComplete() {

        Task task = new Task();
        Input input = new Input();
        input.setUri("http://localhost:7009/post");
        Map<String, Object> body = new HashMap<>();
        body.put("input_key1", "value1");
        body.put("input_key2", 45.3d);
        input.setBody(body);
        input.setMethod("POST");
        task.getInputData().put(HttpTask.REQUEST_PARAMETER_NAME, input);
        task.getInputData().put("asyncComplete", true);

        httpTask.start(workflow, task, workflowExecutor);
        assertEquals(task.getReasonForIncompletion(), Status.IN_PROGRESS, task.getStatus());
        Map<String, Object> hr = (Map<String, Object>) task.getOutputData().get("response");
        Object response = hr.get("body");
        assertEquals(Status.IN_PROGRESS, task.getStatus());
        assertTrue("response is: " + response, response instanceof Map);
        Map<String, Object> map = (Map<String, Object>) response;
        Set<String> inputKeys = body.keySet();
        Set<String> responseKeys = map.keySet();
        inputKeys.containsAll(responseKeys);
        responseKeys.containsAll(inputKeys);
    }

    @Test
    public void testTextGET() {

        Task task = new Task();
        Input input = new Input();
        input.setUri("http://localhost:7009/text");
        input.setMethod("GET");
        task.getInputData().put(HttpTask.REQUEST_PARAMETER_NAME, input);

        httpTask.start(workflow, task, workflowExecutor);
        Map<String, Object> hr = (Map<String, Object>) task.getOutputData().get("response");
        Object response = hr.get("body");
        assertEquals(Status.COMPLETED, task.getStatus());
        assertEquals(TEXT_RESPONSE, response);
    }

    @Test
    public void testNumberGET() {

        Task task = new Task();
        Input input = new Input();
        input.setUri("http://localhost:7009/numeric");
        input.setMethod("GET");
        task.getInputData().put(HttpTask.REQUEST_PARAMETER_NAME, input);

        httpTask.start(workflow, task, workflowExecutor);
        Map<String, Object> hr = (Map<String, Object>) task.getOutputData().get("response");
        Object response = hr.get("body");
        assertEquals(Status.COMPLETED, task.getStatus());
        assertEquals(NUM_RESPONSE, response);
        assertTrue(response instanceof Number);
    }

    @Test
    public void testJsonGET() throws JsonProcessingException {

        Task task = new Task();
        Input input = new Input();
        input.setUri("http://localhost:7009/json");
        input.setMethod("GET");
        task.getInputData().put(HttpTask.REQUEST_PARAMETER_NAME, input);

        httpTask.start(workflow, task, workflowExecutor);
        Map<String, Object> hr = (Map<String, Object>) task.getOutputData().get("response");
        Object response = hr.get("body");
        assertEquals(Status.COMPLETED, task.getStatus());
        assertTrue(response instanceof Map);
        Map<String, Object> map = (Map<String, Object>) response;
        assertEquals(JSON_RESPONSE, objectMapper.writeValueAsString(map));
    }

    @Test
    public void testExecute() {

        Task task = new Task();
        Input input = new Input();
        input.setUri("http://localhost:7009/json");
        input.setMethod("GET");
        task.getInputData().put(HttpTask.REQUEST_PARAMETER_NAME, input);
        task.setStatus(Status.SCHEDULED);
        task.setScheduledTime(0);
        boolean executed = httpTask.execute(workflow, task, workflowExecutor);
        assertFalse(executed);
    }

    @Test
    public void testHTTPGetConnectionTimeOut() {
        Task task = new Task();
        Input input = new Input();
        Instant start = Instant.now();
        input.setConnectionTimeOut(110);
        input.setMethod("GET");
        input.setUri("http://10.255.14.15");
        task.getInputData().put(HttpTask.REQUEST_PARAMETER_NAME, input);
        task.setStatus(Status.SCHEDULED);
        task.setScheduledTime(0);
        httpTask.start(workflow, task, workflowExecutor);
        Instant end = Instant.now();
        long diff = end.toEpochMilli() - start.toEpochMilli();
        assertEquals(task.getStatus(), Status.FAILED);
        assertTrue(diff >= 110L);
    }

    @Test
    public void testHTTPGETReadTimeOut() {
        Task task = new Task();
        Input input = new Input();
        input.setReadTimeOut(-1);
        input.setMethod("GET");
        input.setUri("http://localhost:7009/json");
        task.getInputData().put(HttpTask.REQUEST_PARAMETER_NAME, input);
        task.setStatus(Status.SCHEDULED);
        task.setScheduledTime(0);
        httpTask.start(workflow, task, workflowExecutor);
        assertEquals(task.getStatus(), Status.FAILED);
    }

    @Test
    public void testOptional() {
        Task task = new Task();
        Input input = new Input();
        input.setUri("http://localhost:7009/failure");
        input.setMethod("GET");
        task.getInputData().put(HttpTask.REQUEST_PARAMETER_NAME, input);

        httpTask.start(workflow, task, workflowExecutor);
        assertEquals("Task output: " + task.getOutputData(), Status.FAILED, task.getStatus());
        assertTrue(task.getReasonForIncompletion().contains(ERROR_RESPONSE));
        assertFalse(task.getStatus().isSuccessful());

        task.setStatus(Status.SCHEDULED);
        task.getInputData().remove(HttpTask.REQUEST_PARAMETER_NAME);
        task.setReferenceTaskName("t1");
        httpTask.start(workflow, task, workflowExecutor);
        assertEquals(Status.FAILED, task.getStatus());
        assertEquals(HttpTask.MISSING_REQUEST, task.getReasonForIncompletion());
        assertFalse(task.getStatus().isSuccessful());

        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setOptional(true);
        workflowTask.setName("HTTP");
        workflowTask.setWorkflowTaskType(TaskType.USER_DEFINED);
        workflowTask.setTaskReferenceName("t1");

        WorkflowDef def = new WorkflowDef();
        def.getTasks().add(workflowTask);

        Workflow workflow = new Workflow();
        workflow.setWorkflowDefinition(def);
        workflow.getTasks().add(task);

        MetadataDAO metadataDAO = mock(MetadataDAO.class);
        ExternalPayloadStorageUtils externalPayloadStorageUtils = mock(ExternalPayloadStorageUtils.class);
        ParametersUtils parametersUtils = mock(ParametersUtils.class);

        Map<String, TaskMapper> taskMappers = new HashMap<>();
        new DeciderService(parametersUtils, metadataDAO, externalPayloadStorageUtils, taskMappers, 60)
            .decide(workflow);
    }

    private static class EchoHandler extends AbstractHandler {

        private final TypeReference<Map<String, Object>> mapOfObj = new TypeReference<Map<String, Object>>() {
        };

        @Override
        public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
            if (request.getMethod().equals("GET") && request.getRequestURI().equals("/text")) {
                PrintWriter writer = response.getWriter();
                writer.print(TEXT_RESPONSE);
                writer.flush();
                writer.close();
            } else if (request.getMethod().equals("GET") && request.getRequestURI().equals("/json")) {
                response.addHeader("Content-Type", "application/json");
                PrintWriter writer = response.getWriter();
                writer.print(JSON_RESPONSE);
                writer.flush();
                writer.close();
            } else if (request.getMethod().equals("GET") && request.getRequestURI().equals("/failure")) {
                response.addHeader("Content-Type", "text/plain");
                response.setStatus(500);
                PrintWriter writer = response.getWriter();
                writer.print(ERROR_RESPONSE);
                writer.flush();
                writer.close();
            } else if (request.getMethod().equals("POST") && request.getRequestURI().equals("/post")) {
                response.addHeader("Content-Type", "application/json");
                BufferedReader reader = request.getReader();
                Map<String, Object> input = objectMapper.readValue(reader, mapOfObj);
                Set<String> keys = input.keySet();
                for (String key : keys) {
                    input.put(key, key);
                }
                PrintWriter writer = response.getWriter();
                writer.print(objectMapper.writeValueAsString(input));
                writer.flush();
                writer.close();
            } else if (request.getMethod().equals("POST") && request.getRequestURI().equals("/post2")) {
                response.addHeader("Content-Type", "application/json");
                response.setStatus(204);
                BufferedReader reader = request.getReader();
                Map<String, Object> input = objectMapper.readValue(reader, mapOfObj);
                Set<String> keys = input.keySet();
                response.getWriter().close();
            } else if (request.getMethod().equals("GET") && request.getRequestURI().equals("/numeric")) {
                PrintWriter writer = response.getWriter();
                writer.print(NUM_RESPONSE);
                writer.flush();
                writer.close();
            }
        }
    }
}
