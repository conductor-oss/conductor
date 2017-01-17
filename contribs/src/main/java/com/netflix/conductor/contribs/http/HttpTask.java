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


import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.Task.Status;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource.Builder;

/**
 * @author Viren
 * Task that enables calling another http endpoint as part of its execution
 */
@Singleton
public class HttpTask extends WorkflowSystemTask {

	public static final String REQUEST_PARAMETER_NAME = "http_request";
	
	static final String MISSING_REQUEST = "Missing HTTP request. Task input MUST have a '" + REQUEST_PARAMETER_NAME + "' key wiht HttpTask.Input as value. See documentation for HttpTask for required input parameters";

	private static final Logger logger = LoggerFactory.getLogger(HttpTask.class);
	
	public static final String NAME = "HTTP";
	
	private TypeReference<Map<String, Object>> mapOfObj = new TypeReference<Map<String, Object>>(){};
	
	private TypeReference<List<Object>> listOfObj = new TypeReference<List<Object>>(){};
	
	protected ObjectMapper om = objectMapper();

	protected RestClientManager rcm;
	
	protected Configuration config;
	
	private String requestParameter;
	
	@Inject
	public HttpTask(RestClientManager rcm, Configuration config) {
		this(NAME, rcm, config);
	}
	
	public HttpTask(String name, RestClientManager rcm, Configuration config) {
		super(name);
		this.rcm = rcm;
		this.config = config;
		this.requestParameter = REQUEST_PARAMETER_NAME;
		logger.info("HttpTask initialized...");
	}
	
	@Override
	public void start(Workflow workflow, Task task, WorkflowExecutor executor) throws Exception {
		Object request = task.getInputData().get(requestParameter);
		task.setWorkerId(config.getServerId());
		if(request == null) {
			String reason = MISSING_REQUEST;
			task.setReasonForIncompletion(reason);
			task.setStatus(Status.FAILED);
			return;
		}
		
		Input input = om.convertValue(request, Input.class);
		if(input.getUri() == null) {
			String reason = "Missing HTTP URI.  See documentation for HttpTask for required input parameters";
			task.setReasonForIncompletion(reason);
			task.setStatus(Status.FAILED);
			return;
		}
		
		if(input.getMethod() == null) {
			String reason = "No HTTP method specified";
			task.setReasonForIncompletion(reason);
			task.setStatus(Status.FAILED);
			return;
		}
		
		try {
			
			HttpResponse response = httpCall(input);
			if(response.statusCode > 199 && response.statusCode < 300) {
				task.setStatus(Status.COMPLETED);
			} else {
				task.setReasonForIncompletion(response.body.toString());
				task.setStatus(Status.FAILED);
			}
			if(response != null) {
				task.getOutputData().put("response", response);
			}
			
		}catch(Exception e) {
			task.setStatus(Status.FAILED);
			task.setReasonForIncompletion(e.getMessage());
			task.getOutputData().put("response", e.getMessage());
		}
	}

	/**
	 * 
	 * @param input HTTP Request
	 * @return Response of the http call
	 * @throws Exception If there was an error making http call
	 */
	protected HttpResponse httpCall(Input input) throws Exception {
		Client client = rcm.getClient(input);
		Builder builder = client.resource(input.uri).type(MediaType.APPLICATION_JSON);
		if(input.body != null) {
			builder.entity(input.body);
		}
		input.headers.entrySet().forEach(e -> {
			builder.header(e.getKey(), e.getValue());
		});
		
		HttpResponse response = new HttpResponse();
		try {

			ClientResponse cr = builder.accept(input.accept).method(input.method, ClientResponse.class);
			if (cr.hasEntity()) {
				response.body = extractBody(cr);
			}
			response.statusCode = cr.getStatus();
			response.headers = cr.getHeaders();
			return response;

		} catch(UniformInterfaceException ex) {
			
			ClientResponse cr = ex.getResponse();
			
			if(cr.getStatus() > 199 && cr.getStatus() < 300) {
				
				if(cr.hasEntity()) {
					response.body = extractBody(cr);
				}
				response.headers = cr.getHeaders();
				response.statusCode = cr.getStatus();
				return response;
				
			}else {
				String reason = cr.getEntity(String.class);
				logger.error(reason, ex);
				throw new Exception(reason);
			}
		}
	}

	private Object extractBody(ClientResponse cr) {
		
		String json = cr.getEntity(String.class);
		logger.debug(json);
		
		try {
			
			JsonNode node = om.readTree(json);
			if (node.isArray()) {
				return om.convertValue(node, listOfObj);
			} else if (node.isObject()) {
				return om.convertValue(node, mapOfObj);
			} else if (node.isNumber()) {
				return om.convertValue(node, Double.class);
			} else {
				return node.asText();
			}

		} catch (IOException jpe) {
			logger.error(jpe.getMessage(), jpe);
			return json;
		}
	}

	@Override
	public boolean execute(Workflow workflow, Task task, WorkflowExecutor executor) throws Exception {
		if (task.getStatus().equals(Status.SCHEDULED)) {
			long timeSince = System.currentTimeMillis() - task.getScheduledTime();
			if(timeSince > 600_000) {
				start(workflow, task, executor);
				return true;	
			}else {
				return false;
			}				
		}
		return false;
	}
	
	@Override
	public void cancel(Workflow workflow, Task task, WorkflowExecutor executor) throws Exception {
		task.setStatus(Status.CANCELED);
	}
	
	private static ObjectMapper objectMapper() {
	    final ObjectMapper om = new ObjectMapper();
        om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        om.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
        om.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false);
        om.setSerializationInclusion(Include.NON_NULL);
        om.setSerializationInclusion(Include.NON_EMPTY);
	    return om;
	}
	
	public static class HttpResponse {
		
		public Object body;
		
		public MultivaluedMap<String, String> headers;
		
		public int statusCode;

		@Override
		public String toString() {
			return "HttpResponse [body=" + body + ", headers=" + headers + ", statusCode=" + statusCode + "]";
		}
	}
	
	public static class Input {
		
		private String method;	//PUT, POST, GET, DELETE, OPTIONS, HEAD
		
		private String vipAddress;
		
		private Map<String, Object> headers = new HashMap<>();
		
		private String uri;
		
		private Object body;
		
		private String accept = MediaType.APPLICATION_JSON;

		/**
		 * @return the method
		 */
		public String getMethod() {
			return method;
		}

		/**
		 * @param method the method to set
		 */
		public void setMethod(String method) {
			this.method = method;
		}

		/**
		 * @return the headers
		 */
		public Map<String, Object> getHeaders() {
			return headers;
		}

		/**
		 * @param headers the headers to set
		 */
		public void setHeaders(Map<String, Object> headers) {
			this.headers = headers;
		}

		/**
		 * @return the body
		 */
		public Object getBody() {
			return body;
		}

		/**
		 * @param body the body to set
		 */
		public void setBody(Object body) {
			this.body = body;
		}

		/**
		 * @return the uri
		 */
		public String getUri() {
			return uri;
		}

		/**
		 * @param uri the uri to set
		 */
		public void setUri(String uri) {
			this.uri = uri;
		}

		/**
		 * @return the vipAddress
		 */
		public String getVipAddress() {
			return vipAddress;
		}

		/**
		 * @param vipAddress the vipAddress to set
		 * 
		 */
		public void setVipAddress(String vipAddress) {
			this.vipAddress = vipAddress;
		}

		/**
		 * @return the accept
		 */
		public String getAccept() {
			return accept;
		}

		/**
		 * @param accept the accept to set
		 * 
		 */
		public void setAccept(String accept) {
			this.accept = accept;
		}
		
	}
}
