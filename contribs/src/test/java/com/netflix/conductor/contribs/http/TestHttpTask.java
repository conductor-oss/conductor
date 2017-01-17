/**
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 * 
 */
package com.netflix.conductor.contribs.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.Task.Status;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.contribs.http.HttpTask.HttpResponse;
import com.netflix.conductor.contribs.http.HttpTask.Input;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.execution.WorkflowExecutor;

/**
 * @author Viren
 *
 */
public class TestHttpTask {

	private static final String ERROR_RESPONSE = "Something went wrong!";
	
	private static final String TEXT_RESPONSE = "Text Response";
	
	private static final double NUM_RESPONSE = 42.42d;
	
	private static String JSON_RESPONSE;
	
	private HttpTask httpTask;
	
	private WorkflowExecutor executor = mock(WorkflowExecutor.class);
	
	private Workflow workflow = new Workflow();
	
	private static Server server;
	
	private static ObjectMapper om = new ObjectMapper();
	
	@BeforeClass
	public static void init() throws Exception {
		Map<String, Object> map = new HashMap<>();
		map.put("key", "value1");
		map.put("num", 42);
		JSON_RESPONSE = om.writeValueAsString(map);
		
		server = new Server(7009);
		ServletContextHandler servletContextHandler = new ServletContextHandler(server, "/", ServletContextHandler.SESSIONS);
		servletContextHandler.setHandler(new EchoHandler());
		server.start();
	}
	
	@AfterClass
	public static void cleanup() {
		if(server != null) {
			try {
				server.stop();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	@Before
	public void setup() {
		RestClientManager rcm = new RestClientManager();
		Configuration config = mock(Configuration.class);
		when(config.getServerId()).thenReturn("test_server_id");
		httpTask = new HttpTask(rcm, config);
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testPost() throws Exception {

		Task task = new Task();
		Input input = new Input();
		input.setUri("http://localhost:7009/post");
		Map<String, Object> body = new HashMap<>();
		body.put("input_key1", "value1");
		body.put("input_key2", 45.3d);
		input.setBody(body);
		input.setMethod("POST");
		task.getInputData().put(HttpTask.REQUEST_PARAMETER_NAME, input);
		
		httpTask.start(workflow, task, executor);
		assertEquals(task.getReasonForIncompletion(), Task.Status.COMPLETED, task.getStatus());
		HttpResponse hr = (HttpResponse) task.getOutputData().get("response");
		Object response = hr.body;
		assertEquals(Task.Status.COMPLETED, task.getStatus());
		assertTrue("response is: " + response, response instanceof Map);
		Map<String, Object> map = (Map<String, Object>) response;
		Set<String> inputKeys = body.keySet();
		Set<String> responseKeys = map.keySet();
		inputKeys.containsAll(responseKeys);
		responseKeys.containsAll(inputKeys);
	}
	
	@Test
	public void testFailure() throws Exception {

		Task task = new Task();
		Input input = new Input();
		input.setUri("http://localhost:7009/failure");
		input.setMethod("GET");
		task.getInputData().put(HttpTask.REQUEST_PARAMETER_NAME, input);
		
		httpTask.start(workflow, task, executor);
		assertEquals("Task output: " + task.getOutputData(), Task.Status.FAILED, task.getStatus());
		assertEquals(ERROR_RESPONSE, task.getReasonForIncompletion());
		
		task.setStatus(Status.SCHEDULED);
		task.getInputData().remove(HttpTask.REQUEST_PARAMETER_NAME);
		httpTask.start(workflow, task, executor);
		assertEquals(Task.Status.FAILED, task.getStatus());
		assertEquals(HttpTask.MISSING_REQUEST, task.getReasonForIncompletion());
	}
	
	@Test
	public void testTextGET() throws Exception {

		Task task = new Task();
		Input input = new Input();
		input.setUri("http://localhost:7009/text");
		input.setMethod("GET");
		task.getInputData().put(HttpTask.REQUEST_PARAMETER_NAME, input);
		
		httpTask.start(workflow, task, executor);
		HttpResponse hr = (HttpResponse) task.getOutputData().get("response");
		Object response = hr.body;
		assertEquals(Task.Status.COMPLETED, task.getStatus());
		assertEquals(TEXT_RESPONSE, response);	
	}
	
	@Test
	public void testNumberGET() throws Exception {

		Task task = new Task();
		Input input = new Input();
		input.setUri("http://localhost:7009/numeric");
		input.setMethod("GET");
		task.getInputData().put(HttpTask.REQUEST_PARAMETER_NAME, input);
		
		httpTask.start(workflow, task, executor);
		HttpResponse hr = (HttpResponse) task.getOutputData().get("response");
		Object response = hr.body;
		assertEquals(Task.Status.COMPLETED, task.getStatus());
		assertEquals(NUM_RESPONSE, response);
		assertTrue(response instanceof Number);
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testJsonGET() throws Exception {

		Task task = new Task();
		Input input = new Input();
		input.setUri("http://localhost:7009/json");
		input.setMethod("GET");
		task.getInputData().put(HttpTask.REQUEST_PARAMETER_NAME, input);
		
		httpTask.start(workflow, task, executor);
		HttpResponse hr = (HttpResponse) task.getOutputData().get("response");
		Object response = hr.body;
		assertEquals(Task.Status.COMPLETED, task.getStatus());
		assertTrue(response instanceof Map);
		Map<String, Object> map = (Map<String, Object>) response;
		assertEquals(JSON_RESPONSE, om.writeValueAsString(map));
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testExecute() throws Exception {

		Task task = new Task();
		Input input = new Input();
		input.setUri("http://localhost:7009/json");
		input.setMethod("GET");
		task.getInputData().put(HttpTask.REQUEST_PARAMETER_NAME, input);
		task.setStatus(Status.SCHEDULED);
		task.setScheduledTime(0);
		httpTask.execute(workflow, task, executor);

		HttpResponse hr = (HttpResponse) task.getOutputData().get("response");
		Object response = hr.body;
		assertEquals(Task.Status.COMPLETED, task.getStatus());
		assertTrue(response instanceof Map);
		Map<String, Object> map = (Map<String, Object>) response;
		assertEquals(JSON_RESPONSE, om.writeValueAsString(map));
		
		task.setStatus(Status.SCHEDULED);
		task.setScheduledTime(System.currentTimeMillis()-100);
		//For a recently scheduled task, execute does NOP
		httpTask.execute(workflow, task, executor);
		assertEquals(Task.Status.SCHEDULED, task.getStatus());

	}
	
	private static class EchoHandler extends AbstractHandler {

		private TypeReference<Map<String, Object>> mapOfObj = new TypeReference<Map<String,Object>>() {};
		
		@Override
		public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
				throws IOException, ServletException {
			if(request.getMethod().equals("GET") && request.getRequestURI().equals("/text")) {
				PrintWriter writer = response.getWriter();
				writer.print(TEXT_RESPONSE);
				writer.flush();
				writer.close();
			} else if(request.getMethod().equals("GET") && request.getRequestURI().equals("/json")) {
				response.addHeader("Content-Type", "application/json");
				PrintWriter writer = response.getWriter();
				writer.print(JSON_RESPONSE);
				writer.flush();
				writer.close();
			} else if(request.getMethod().equals("GET") && request.getRequestURI().equals("/failure")) {
				response.addHeader("Content-Type", "text/plain");
				response.setStatus(500);
				PrintWriter writer = response.getWriter();
				writer.print(ERROR_RESPONSE);
				writer.flush();
				writer.close();
			} else if(request.getMethod().equals("POST") && request.getRequestURI().equals("/post")) {
				response.addHeader("Content-Type", "application/json");
				
				BufferedReader reader = request.getReader();
				Map<String, Object> input = om.readValue(reader, mapOfObj);
				Set<String> keys = input.keySet();
				for(String key : keys) {
					input.put(key, key);					
				}
				PrintWriter writer = response.getWriter();
				writer.print(om.writeValueAsString(input));
				writer.flush();
				writer.close();
			} else if(request.getMethod().equals("GET") && request.getRequestURI().equals("/numeric")) {
				PrintWriter writer = response.getWriter();
				writer.print(NUM_RESPONSE);
				writer.flush();
				writer.close();
			} 
		}
		
	}
}
