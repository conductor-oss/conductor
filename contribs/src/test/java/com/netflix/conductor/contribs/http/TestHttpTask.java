/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.contribs.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.Task.Status;
import com.netflix.conductor.common.metadata.workflow.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.utils.JsonMapperProvider;
import com.netflix.conductor.contribs.http.HttpTask.Input;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.execution.DeciderService;
import com.netflix.conductor.core.execution.ParametersUtils;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.mapper.TaskMapper;
import com.netflix.conductor.core.utils.ExternalPayloadStorageUtils;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.dao.QueueDAO;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * @author Viren
 *
 */
@SuppressWarnings("unchecked")
public class TestHttpTask {

    private static final String ERROR_RESPONSE = "Something went wrong!";

    private static final String TEXT_RESPONSE = "Text Response";

    private static final double NUM_RESPONSE = 42.42d;

    private static String JSON_RESPONSE;

    private HttpTask httpTask;

    private WorkflowExecutor workflowExecutor;
    private Configuration config;

    private Workflow workflow = new Workflow();

    private static Server server;

    private static ObjectMapper objectMapper = new JsonMapperProvider().get();

    @BeforeClass
    public static void init() throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("key", "value1");
        map.put("num", 42);
        map.put("SomeKey", null);
        JSON_RESPONSE = objectMapper.writeValueAsString(map);

        server = new Server(7009);
        ServletContextHandler servletContextHandler = new ServletContextHandler(server, "/", ServletContextHandler.SESSIONS);
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
        config = mock(Configuration.class);
        RestClientManager rcm = new RestClientManager(Mockito.mock(Configuration.class));
        when(config.getServerId()).thenReturn("test_server_id");
        httpTask = new HttpTask(rcm, config, objectMapper);
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
        assertEquals(task.getReasonForIncompletion(), Task.Status.COMPLETED, task.getStatus());
        Map<String, Object> hr = (Map<String, Object>) task.getOutputData().get("response");
        Object response = hr.get("body");
        assertEquals(Task.Status.COMPLETED, task.getStatus());
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
        assertEquals(task.getReasonForIncompletion(), Task.Status.COMPLETED, task.getStatus());
        Map<String, Object> hr = (Map<String, Object>) task.getOutputData().get("response");
        Object response = hr.get("body");
        assertEquals(Task.Status.COMPLETED, task.getStatus());
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
        assertEquals("Task output: " + task.getOutputData(), Task.Status.FAILED, task.getStatus());
        assertEquals(ERROR_RESPONSE, task.getReasonForIncompletion());

        task.setStatus(Status.SCHEDULED);
        task.getInputData().remove(HttpTask.REQUEST_PARAMETER_NAME);
        httpTask.start(workflow, task, workflowExecutor);
        assertEquals(Task.Status.FAILED, task.getStatus());
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
        assertEquals(Task.Status.COMPLETED, task.getStatus());
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
        assertEquals(Task.Status.COMPLETED, task.getStatus());
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
        assertEquals(Task.Status.COMPLETED, task.getStatus());
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
    public void testHTTPGetConnectionTimeOut() throws Exception{
        Task task = new Task();
        Input input = new Input();
        Instant start  = Instant.now();
        input.setConnectionTimeOut(110);
        input.setMethod("GET");
        input.setUri("http://10.255.14.15");
        task.getInputData().put(HttpTask.REQUEST_PARAMETER_NAME, input);
        task.setStatus(Status.SCHEDULED);
        task.setScheduledTime(0);
        httpTask.start(workflow,task,workflowExecutor);
        Instant end  = Instant.now();
        long diff = end.toEpochMilli()-start.toEpochMilli();
        Assert.assertEquals(task.getStatus(),Status.FAILED);
        Assert.assertTrue(diff >= 110l);
    }

    @Test
    public void testHTTPGETReadTimeOut() throws Exception{
        Task task = new Task();
        Input input = new Input();
        input.setReadTimeOut(-1);
        input.setMethod("GET");
        input.setUri("http://localhost:7009/json");
        task.getInputData().put(HttpTask.REQUEST_PARAMETER_NAME, input);
        task.setStatus(Status.SCHEDULED);
        task.setScheduledTime(0);
        httpTask.start(workflow,task,workflowExecutor);
        Assert.assertEquals(task.getStatus(),Status.FAILED);
    }

    @Test
    public void testOptional() {
        Task task = new Task();
        Input input = new Input();
        input.setUri("http://localhost:7009/failure");
        input.setMethod("GET");
        task.getInputData().put(HttpTask.REQUEST_PARAMETER_NAME, input);

        httpTask.start(workflow, task, workflowExecutor);
        assertEquals("Task output: " + task.getOutputData(), Task.Status.FAILED, task.getStatus());
        assertEquals(ERROR_RESPONSE, task.getReasonForIncompletion());
        assertTrue(!task.getStatus().isSuccessful());

        task.setStatus(Status.SCHEDULED);
        task.getInputData().remove(HttpTask.REQUEST_PARAMETER_NAME);
        task.setReferenceTaskName("t1");
        httpTask.start(workflow, task, workflowExecutor);
        assertEquals(Task.Status.FAILED, task.getStatus());
        assertEquals(HttpTask.MISSING_REQUEST, task.getReasonForIncompletion());
        assertTrue(!task.getStatus().isSuccessful());

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
        Configuration configuration = mock(Configuration.class);

        Map<String, TaskMapper> taskMappers = new HashMap<>();
        new DeciderService(parametersUtils, metadataDAO, externalPayloadStorageUtils, taskMappers, configuration).decide(workflow);

        System.out.println(workflow.getTasks());
        System.out.println(workflow.getStatus());
    }

    @Test
    public void testOAuth() {
        Task task = new Task();
        Input input = new Input();
        input.setUri("http://localhost:7009/oauth");
        input.setMethod("POST");
        input.setOauthConsumerKey("someKey");
        input.setOauthConsumerSecret("someSecret");
        task.getInputData().put(HttpTask.REQUEST_PARAMETER_NAME, input);

        httpTask.start(workflow, task, workflowExecutor);

        Map<String, Object> response = (Map<String, Object>) task.getOutputData().get("response");
        Map<String, String> body = (Map<String, String>) response.get("body");

        assertEquals("someKey", body.get("oauth_consumer_key"));
        assertTrue("Should have OAuth nonce", body.containsKey("oauth_nonce"));
        assertTrue("Should have OAuth signature", body.containsKey("oauth_signature"));
        assertTrue("Should have OAuth signature method", body.containsKey("oauth_signature_method"));
        assertTrue("Should have OAuth oauth_timestamp", body.containsKey("oauth_timestamp"));
        assertTrue("Should have OAuth oauth_version", body.containsKey("oauth_version"));

        assertEquals("Task output: " + task.getOutputData(), Status.COMPLETED, task.getStatus());
    }

    private static class EchoHandler extends AbstractHandler {

        private TypeReference<Map<String, Object>> mapOfObj = new TypeReference<Map<String, Object>>() {
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
                System.out.println(keys);
                response.getWriter().close();

            } else if (request.getMethod().equals("GET") && request.getRequestURI().equals("/numeric")) {
                PrintWriter writer = response.getWriter();
                writer.print(NUM_RESPONSE);
                writer.flush();
                writer.close();
            } else if (request.getMethod().equals("POST") && request.getRequestURI().equals("/oauth")) {
                //echo back oauth parameters generated in the Authorization header in the response
                Map<String, String> params = parseOauthParameters(request);
                response.addHeader("Content-Type", "application/json");
                PrintWriter writer = response.getWriter();
                writer.print(objectMapper.writeValueAsString(params));
                writer.flush();
                writer.close();
            }
        }

        private Map<String, String> parseOauthParameters(HttpServletRequest request) {
            String paramString = request.getHeader("Authorization").replaceAll("^OAuth (.*)", "$1");
            return Arrays.stream(paramString.split("\\s*,\\s*"))
                    .map(pair -> pair.split("="))
                    .collect(Collectors.toMap(o -> o[0], o -> o[1].replaceAll("\"", "")));
        }
    }
}
