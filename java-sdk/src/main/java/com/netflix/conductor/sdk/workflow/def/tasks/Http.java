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
package com.netflix.conductor.sdk.workflow.def.tasks;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.sdk.workflow.utils.ObjectMapperProvider;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Wait task */
public class Http extends Task<Http> {

    private static final Logger LOGGER = LoggerFactory.getLogger(Http.class);

    private static final String INPUT_PARAM = "http_request";

    private ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();

    private Input httpRequest;

    public Http(String taskReferenceName) {
        super(taskReferenceName, TaskType.HTTP);
        this.httpRequest = new Input();
        this.httpRequest.method = Input.HttpMethod.GET;
        super.input(INPUT_PARAM, httpRequest);
    }

    Http(WorkflowTask workflowTask) {
        super(workflowTask);

        Object inputRequest = workflowTask.getInputParameters().get(INPUT_PARAM);
        if (inputRequest != null) {
            try {
                this.httpRequest = objectMapper.convertValue(inputRequest, Input.class);
            } catch (Exception e) {
                LOGGER.error("Error while trying to convert input request " + e.getMessage(), e);
            }
        }
    }

    public Http input(Input httpRequest) {
        this.httpRequest = httpRequest;
        return this;
    }

    public Http url(String url) {
        this.httpRequest.setUri(url);
        return this;
    }

    public Http method(Input.HttpMethod method) {
        this.httpRequest.setMethod(method);
        return this;
    }

    public Http headers(Map<String, Object> headers) {
        this.httpRequest.setHeaders(headers);
        return this;
    }

    public Http body(Object body) {
        this.httpRequest.setBody(body);
        return this;
    }

    public Http readTimeout(int readTimeout) {
        this.httpRequest.setReadTimeOut(readTimeout);
        return this;
    }

    public Input getHttpRequest() {
        return httpRequest;
    }

    @Override
    protected void updateWorkflowTask(WorkflowTask workflowTask) {
        workflowTask.getInputParameters().put(INPUT_PARAM, httpRequest);
    }

    public static class Input {
        public enum HttpMethod {
            PUT,
            POST,
            GET,
            DELETE,
            OPTIONS,
            HEAD
        }

        private HttpMethod method; // PUT, POST, GET, DELETE, OPTIONS, HEAD
        private String vipAddress;
        private String appName;
        private Map<String, Object> headers = new HashMap<>();
        private String uri;
        private Object body;
        private String accept = "application/json";
        private String contentType = "application/json";
        private Integer connectionTimeOut;
        private Integer readTimeOut;

        /**
         * @return the method
         */
        public HttpMethod getMethod() {
            return method;
        }

        /**
         * @param method the method to set
         */
        public void setMethod(HttpMethod method) {
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
         */
        public void setAccept(String accept) {
            this.accept = accept;
        }

        /**
         * @return the MIME content type to use for the request
         */
        public String getContentType() {
            return contentType;
        }

        /**
         * @param contentType the MIME content type to set
         */
        public void setContentType(String contentType) {
            this.contentType = contentType;
        }

        public String getAppName() {
            return appName;
        }

        public void setAppName(String appName) {
            this.appName = appName;
        }

        /**
         * @return the connectionTimeOut
         */
        public Integer getConnectionTimeOut() {
            return connectionTimeOut;
        }

        /**
         * @return the readTimeOut
         */
        public Integer getReadTimeOut() {
            return readTimeOut;
        }

        public void setConnectionTimeOut(Integer connectionTimeOut) {
            this.connectionTimeOut = connectionTimeOut;
        }

        public void setReadTimeOut(Integer readTimeOut) {
            this.readTimeOut = readTimeOut;
        }

        @Override
        public String toString() {
            return "Input{"
                    + "method="
                    + method
                    + ", vipAddress='"
                    + vipAddress
                    + '\''
                    + ", appName='"
                    + appName
                    + '\''
                    + ", headers="
                    + headers
                    + ", uri='"
                    + uri
                    + '\''
                    + ", body="
                    + body
                    + ", accept='"
                    + accept
                    + '\''
                    + ", contentType='"
                    + contentType
                    + '\''
                    + ", connectionTimeOut="
                    + connectionTimeOut
                    + ", readTimeOut="
                    + readTimeOut
                    + '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Input input = (Input) o;
            return method == input.method
                    && Objects.equals(vipAddress, input.vipAddress)
                    && Objects.equals(appName, input.appName)
                    && Objects.equals(headers, input.headers)
                    && Objects.equals(uri, input.uri)
                    && Objects.equals(body, input.body)
                    && Objects.equals(accept, input.accept)
                    && Objects.equals(contentType, input.contentType)
                    && Objects.equals(connectionTimeOut, input.connectionTimeOut)
                    && Objects.equals(readTimeOut, input.readTimeOut);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    method,
                    vipAddress,
                    appName,
                    headers,
                    uri,
                    body,
                    accept,
                    contentType,
                    connectionTimeOut,
                    readTimeOut);
        }
    }
}
