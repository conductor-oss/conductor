/*
 * Copyright 2026 Conductor Authors.
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
package org.conductoross.conductor.ai.document;

import java.net.ConnectException;
import java.net.SocketException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.conductoross.conductor.config.AIIntegrationEnabledCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import com.netflix.conductor.sdk.workflow.executor.task.TaskContext;

import com.google.common.util.concurrent.Uninterruptibles;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Slf4j
@Component
@Conditional(AIIntegrationEnabledCondition.class)
public class HttpDocumentLoader implements DocumentLoader {

    private static final int MAX_DEPTH = 1; // Specify the depth limit
    private final OkHttpClient httpClient;

    public HttpDocumentLoader() {
        this.httpClient =
                new OkHttpClient.Builder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .writeTimeout(30, TimeUnit.SECONDS)
                        .build();
    }

    @SuppressWarnings("unchecked")
    @Override
    public byte[] download(String location) {
        try {
            Map<String, Object> headers =
                    (Map<String, Object>) TaskContext.get().getTask().getInputData().get("headers");
            Input input = new Input();
            if (headers != null) {
                input.getHeaders().putAll(headers);
            }
            input.setMethod("GET");
            input.setUri(location);
            input.setAccept("*/*");
            HttpResponse response = retryOperation(o -> httpCall(o), 3, input);
            return (byte[]) response.body;

        } catch (Throwable t) {
            log.error(t.getMessage(), t);
            throw new RuntimeException(t);
        }
    }

    @Override
    public String upload(
            Map<String, String> headers, String contentType, byte[] data, String fileURI) {
        try {
            if (fileURI == null) {
                return null;
            }
            Input input = new Input();
            input.getHeaders().putAll(headers);
            input.setMethod("POST");
            input.setUri(fileURI);
            input.setBody(data);
            HttpResponse response = retryOperation(this::httpCall, 3, input);
            if (response.isError()) {
                throw new RuntimeException(
                        "error uploading file %s - %s"
                                .formatted(response.statusCode, response.reasonPhrase));
            }
            return fileURI;
        } catch (Throwable t) {
            log.error(t.getMessage(), t);
            throw new RuntimeException(t);
        }
    }

    @Override
    public List<String> listFiles(String location) {
        return List.of();
    }

    @Override
    public boolean supports(String location) {
        return location.startsWith("http://") || location.startsWith("https://");
    }

    private static boolean isValidUrl(String url) {
        return url.startsWith("http") && !url.contains("#"); // Basic URL validation
    }

    protected HttpResponse httpCall(Input input) throws Exception {
        // Build headers
        Headers.Builder headersBuilder = new Headers.Builder();

        // Add content type
        String contentType = input.getContentType();
        if (contentType != null && !contentType.isEmpty()) {
            headersBuilder.add("Content-Type", contentType);
        }

        // Add accept header
        String accept = input.getAccept();
        if (accept != null && !accept.isEmpty()) {
            headersBuilder.add("Accept", accept);
        }

        // Add custom headers
        input.getHeaders()
                .forEach(
                        (key, value) -> {
                            if (value != null) {
                                headersBuilder.add(key, value.toString());
                            }
                        });

        Headers headers = headersBuilder.build();

        // Build request based on HTTP method
        Request.Builder requestBuilder = new Request.Builder().url(input.getUri()).headers(headers);

        String method = input.getMethod();
        Object body = input.getBody();

        switch (method) {
            case "GET":
                requestBuilder.get();
                break;
            case "POST":
                RequestBody postBody = createRequestBody(body, contentType);
                requestBuilder.post(postBody);
                break;
            case "PUT":
                RequestBody putBody = createRequestBody(body, contentType);
                requestBuilder.put(putBody);
                break;
            case "DELETE":
                if (body != null) {
                    RequestBody deleteBody = createRequestBody(body, contentType);
                    requestBuilder.delete(deleteBody);
                } else {
                    requestBuilder.delete();
                }
                break;
            case "PATCH":
                RequestBody patchBody = createRequestBody(body, contentType);
                requestBuilder.patch(patchBody);
                break;
            default:
                throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        }

        // Execute request
        try (Response response = httpClient.newCall(requestBuilder.build()).execute()) {
            HttpResponse httpResponse = new HttpResponse();
            httpResponse.statusCode = response.code();
            httpResponse.reasonPhrase = response.message();

            // Convert OkHttp headers to Spring HttpHeaders for compatibility
            org.springframework.http.HttpHeaders springHeaders =
                    new org.springframework.http.HttpHeaders();
            response.headers().toMultimap().forEach(springHeaders::addAll);
            httpResponse.headers = springHeaders;

            // Read response body
            if (response.body() != null) {
                httpResponse.body = response.body().bytes();
            }

            // Check for errors
            if (!response.isSuccessful()) {
                throw new RuntimeException(
                        "Error making an HTTP call "
                                + httpResponse.reasonPhrase
                                + ", status: "
                                + httpResponse.statusCode);
            }

            return httpResponse;
        }
    }

    /** Create RequestBody from the input body object. */
    private RequestBody createRequestBody(Object body, String contentType) {
        if (body == null) {
            return RequestBody.create(new byte[0], null);
        }

        MediaType mediaType =
                contentType != null
                        ? MediaType.parse(contentType)
                        : MediaType.parse("application/octet-stream");

        if (body instanceof byte[]) {
            return RequestBody.create((byte[]) body, mediaType);
        } else if (body instanceof String) {
            return RequestBody.create((String) body, mediaType);
        } else {
            // For other types, convert to string
            return RequestBody.create(body.toString(), mediaType);
        }
    }

    /** Functional interface for operations that can throw exceptions. */
    @FunctionalInterface
    private interface FunctionWithException<T, R> {
        R apply(T input) throws Exception;
    }

    private <T, R> R retryOperation(FunctionWithException<T, R> operation, int count, T input)
            throws Throwable {
        int index = 0;
        Throwable lastException = null;
        while (index < count) {
            try {
                return operation.apply(input);
            } catch (Throwable t) {
                lastException = t;
                if (t instanceof ConnectException
                        || (t.getCause() != null && t.getCause() instanceof SocketException)) {
                    index++;
                    Uninterruptibles.sleepUninterruptibly(
                            100L * (count + 1), TimeUnit.MILLISECONDS);
                } else {
                    break;
                }
            }
        }
        if (lastException != null) {
            throw lastException;
        }
        throw new RuntimeException();
    }

    /** Input model for HTTP requests. */
    static class Input {
        private String method;
        private Map<String, Object> headers = new java.util.HashMap<>();
        private String uri;
        private Object body;
        private String accept = "application/json";
        private String contentType = "application/json";

        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }

        public Map<String, Object> getHeaders() {
            return headers;
        }

        public void setHeaders(Map<String, Object> headers) {
            this.headers = headers;
        }

        public Object getBody() {
            return body;
        }

        public void setBody(Object body) {
            this.body = body;
        }

        public String getUri() {
            return uri;
        }

        public void setUri(String uri) {
            this.uri = uri;
        }

        public String getAccept() {
            return accept;
        }

        public void setAccept(String accept) {
            this.accept = accept;
        }

        public String getContentType() {
            return contentType;
        }

        public void setContentType(String contentType) {
            this.contentType = contentType;
        }
    }

    /** HTTP Response model. */
    static class HttpResponse {
        public Object body;
        public org.springframework.http.HttpHeaders headers;
        public int statusCode;
        public String reasonPhrase;

        /**
         * Checks if the HTTP response indicates an error.
         *
         * @return true if status code is not in the 2xx range (200-299), false otherwise
         */
        public boolean isError() {
            return statusCode < 200 || statusCode >= 300;
        }
    }
}
