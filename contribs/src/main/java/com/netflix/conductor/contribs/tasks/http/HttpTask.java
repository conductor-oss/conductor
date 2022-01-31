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

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import com.netflix.conductor.core.utils.Utils;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_HTTP;

/** Task that enables calling another HTTP endpoint as part of its execution */
@Component(TASK_TYPE_HTTP)
public class HttpTask extends WorkflowSystemTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpTask.class);

    public static final String REQUEST_PARAMETER_NAME = "http_request";

    static final String MISSING_REQUEST =
            "Missing HTTP request. Task input MUST have a '"
                    + REQUEST_PARAMETER_NAME
                    + "' key with HttpTask.Input as value. See documentation for HttpTask for required input parameters";

    private final TypeReference<Map<String, Object>> mapOfObj =
            new TypeReference<Map<String, Object>>() {};
    private final TypeReference<List<Object>> listOfObj = new TypeReference<List<Object>>() {};
    protected ObjectMapper objectMapper;
    protected RestTemplateProvider restTemplateProvider;
    private final String requestParameter;

    @Autowired
    public HttpTask(RestTemplateProvider restTemplateProvider, ObjectMapper objectMapper) {
        this(TASK_TYPE_HTTP, restTemplateProvider, objectMapper);
    }

    public HttpTask(
            String name, RestTemplateProvider restTemplateProvider, ObjectMapper objectMapper) {
        super(name);
        this.restTemplateProvider = restTemplateProvider;
        this.objectMapper = objectMapper;
        this.requestParameter = REQUEST_PARAMETER_NAME;
        LOGGER.info("{} initialized...", getTaskType());
    }

    @Override
    public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
        Object request = task.getInputData().get(requestParameter);
        task.setWorkerId(Utils.getServerId());
        if (request == null) {
            task.setReasonForIncompletion(MISSING_REQUEST);
            task.setStatus(TaskModel.Status.FAILED);
            return;
        }

        Input input = objectMapper.convertValue(request, Input.class);
        if (input.getUri() == null) {
            String reason =
                    "Missing HTTP URI.  See documentation for HttpTask for required input parameters";
            task.setReasonForIncompletion(reason);
            task.setStatus(TaskModel.Status.FAILED);
            return;
        }

        if (input.getMethod() == null) {
            String reason = "No HTTP method specified";
            task.setReasonForIncompletion(reason);
            task.setStatus(TaskModel.Status.FAILED);
            return;
        }

        try {
            HttpResponse response = httpCall(input);
            LOGGER.debug(
                    "Response: {}, {}, task:{}",
                    response.statusCode,
                    response.body,
                    task.getTaskId());
            if (response.statusCode > 199 && response.statusCode < 300) {
                if (isAsyncComplete(task)) {
                    task.setStatus(TaskModel.Status.IN_PROGRESS);
                } else {
                    task.setStatus(TaskModel.Status.COMPLETED);
                }
            } else {
                if (response.body != null) {
                    task.setReasonForIncompletion(response.body.toString());
                } else {
                    task.setReasonForIncompletion("No response from the remote service");
                }
                task.setStatus(TaskModel.Status.FAILED);
            }
            //noinspection ConstantConditions
            if (response != null) {
                task.getOutputData().put("response", response.asMap());
            }

        } catch (Exception e) {
            LOGGER.error(
                    "Failed to invoke {} task: {} - uri: {}, vipAddress: {} in workflow: {}",
                    getTaskType(),
                    task.getTaskId(),
                    input.getUri(),
                    input.getVipAddress(),
                    task.getWorkflowInstanceId(),
                    e);
            task.setStatus(TaskModel.Status.FAILED);
            task.setReasonForIncompletion(
                    "Failed to invoke " + getTaskType() + " task due to: " + e);
            task.getOutputData().put("response", e.toString());
        }
    }

    /**
     * @param input HTTP Request
     * @return Response of the http call
     * @throws Exception If there was an error making http call Note: protected access is so that
     *     tasks extended from this task can re-use this to make http calls
     */
    protected HttpResponse httpCall(Input input) throws Exception {
        RestTemplate restTemplate = restTemplateProvider.getRestTemplate(input);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf(input.getContentType()));
        headers.setAccept(Collections.singletonList(MediaType.valueOf(input.getAccept())));

        input.headers.forEach((key, value) -> headers.add(key, value.toString()));

        HttpEntity<Object> request = new HttpEntity<>(input.getBody(), headers);

        HttpResponse response = new HttpResponse();
        try {
            ResponseEntity<String> responseEntity =
                    restTemplate.exchange(input.getUri(), input.getMethod(), request, String.class);
            if (responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.hasBody()) {
                response.body = extractBody(responseEntity.getBody());
            }

            response.statusCode = responseEntity.getStatusCodeValue();
            response.reasonPhrase = responseEntity.getStatusCode().getReasonPhrase();
            response.headers = responseEntity.getHeaders();
            return response;
        } catch (RestClientException ex) {
            LOGGER.error(
                    String.format(
                            "Got unexpected http response - uri: %s, vipAddress: %s",
                            input.getUri(), input.getVipAddress()),
                    ex);
            String reason = ex.getLocalizedMessage();
            LOGGER.error(reason, ex);
            throw new Exception(reason);
        }
    }

    private Object extractBody(String responseBody) {
        try {
            JsonNode node = objectMapper.readTree(responseBody);
            if (node.isArray()) {
                return objectMapper.convertValue(node, listOfObj);
            } else if (node.isObject()) {
                return objectMapper.convertValue(node, mapOfObj);
            } else if (node.isNumber()) {
                return objectMapper.convertValue(node, Double.class);
            } else {
                return node.asText();
            }
        } catch (IOException jpe) {
            LOGGER.error("Error extracting response body", jpe);
            return responseBody;
        }
    }

    @Override
    public boolean execute(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
        return false;
    }

    @Override
    public void cancel(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
        task.setStatus(TaskModel.Status.CANCELED);
    }

    @Override
    public boolean isAsync() {
        return true;
    }

    public static class HttpResponse {

        public Object body;
        public MultiValueMap<String, String> headers;
        public int statusCode;
        public String reasonPhrase;

        @Override
        public String toString() {
            return "HttpResponse [body="
                    + body
                    + ", headers="
                    + headers
                    + ", statusCode="
                    + statusCode
                    + ", reasonPhrase="
                    + reasonPhrase
                    + "]";
        }

        public Map<String, Object> asMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("body", body);
            map.put("headers", headers);
            map.put("statusCode", statusCode);
            map.put("reasonPhrase", reasonPhrase);
            return map;
        }
    }

    public static class Input {

        private HttpMethod method; // PUT, POST, GET, DELETE, OPTIONS, HEAD
        private String vipAddress;
        private String appName;
        private Map<String, Object> headers = new HashMap<>();
        private String uri;
        private Object body;
        private String accept = MediaType.APPLICATION_JSON_VALUE;
        private String contentType = MediaType.APPLICATION_JSON_VALUE;
        private Integer connectionTimeOut;
        private Integer readTimeOut;

        /** @return the method */
        public HttpMethod getMethod() {
            return method;
        }

        /** @param method the method to set */
        public void setMethod(String method) {
            this.method = HttpMethod.valueOf(method);
        }

        /** @return the headers */
        public Map<String, Object> getHeaders() {
            return headers;
        }

        /** @param headers the headers to set */
        public void setHeaders(Map<String, Object> headers) {
            this.headers = headers;
        }

        /** @return the body */
        public Object getBody() {
            return body;
        }

        /** @param body the body to set */
        public void setBody(Object body) {
            this.body = body;
        }

        /** @return the uri */
        public String getUri() {
            return uri;
        }

        /** @param uri the uri to set */
        public void setUri(String uri) {
            this.uri = uri;
        }

        /** @return the vipAddress */
        public String getVipAddress() {
            return vipAddress;
        }

        /** @param vipAddress the vipAddress to set */
        public void setVipAddress(String vipAddress) {
            this.vipAddress = vipAddress;
        }

        /** @return the accept */
        public String getAccept() {
            return accept;
        }

        /** @param accept the accept to set */
        public void setAccept(String accept) {
            this.accept = accept;
        }

        /** @return the MIME content type to use for the request */
        public String getContentType() {
            return contentType;
        }

        /** @param contentType the MIME content type to set */
        public void setContentType(String contentType) {
            this.contentType = contentType;
        }

        public String getAppName() {
            return appName;
        }

        public void setAppName(String appName) {
            this.appName = appName;
        }

        /** @return the connectionTimeOut */
        public Integer getConnectionTimeOut() {
            return connectionTimeOut;
        }

        /** @return the readTimeOut */
        public Integer getReadTimeOut() {
            return readTimeOut;
        }

        public void setConnectionTimeOut(Integer connectionTimeOut) {
            this.connectionTimeOut = connectionTimeOut;
        }

        public void setReadTimeOut(Integer readTimeOut) {
            this.readTimeOut = readTimeOut;
        }
    }
}
