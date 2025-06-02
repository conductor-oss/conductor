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
package com.netflix.conductor.client.exception;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.netflix.conductor.common.validation.ValidationError;

public class ConductorClientException extends RuntimeException {

    private int status;
    private String instance;
    private String code;
    private boolean retryable;

    public List<ValidationError> getValidationErrors() {
        return validationErrors;
    }

    public void setValidationErrors(List<ValidationError> validationErrors) {
        this.validationErrors = validationErrors;
    }

    private List<ValidationError> validationErrors;

    private Map<String, List<String>> responseHeaders;
    private String responseBody;

    public ConductorClientException() {
    }

    public ConductorClientException(Throwable throwable) {
        super(throwable.getMessage(), throwable);
    }

    public ConductorClientException(String message) {
        super(message);
    }

    public ConductorClientException(String message,
                                    Throwable throwable,
                                    int code,
                                    Map<String, List<String>> responseHeaders,
                                    String responseBody) {
        super(message, throwable);
        setCode(String.valueOf(code));
        setStatus(code);
        this.responseHeaders = responseHeaders;
        this.responseBody = responseBody;
    }

    public ConductorClientException(String message,
                                    int code,
                                    Map<String, List<String>> responseHeaders,
                                    String responseBody) {
        this(message, null, code, responseHeaders, responseBody);
        setCode(String.valueOf(code));
        setStatus(code);
    }

    public ConductorClientException(String message,
                                    Throwable throwable,
                                    int code,
                                    Map<String, List<String>> responseHeaders) {
        this(message, throwable, code, responseHeaders, null);
        setCode(String.valueOf(code));
        setStatus(code);
    }

    public ConductorClientException(int code, Map<String, List<String>> responseHeaders, String responseBody) {
        this(null, null, code, responseHeaders, responseBody);
        setCode(String.valueOf(code));
        setStatus(code);
    }

    public ConductorClientException(int code, String message) {
        super(message);
        setCode(String.valueOf(code));
        setStatus(code);
    }

    public ConductorClientException(int code,
                                    String message,
                                    Map<String, List<String>> responseHeaders,
                                    String responseBody) {
        this(code, message);
        this.responseHeaders = responseHeaders;
        this.responseBody = responseBody;
        setCode(String.valueOf(code));
        setStatus(code);
    }

    public boolean isClientError() {
        return getStatus() > 399 && getStatus() < 499;
    }

    /**
     * @return HTTP status code
     */
    public int getStatusCode() {
        return getStatus();
    }

    /**
     * Get the HTTP response headers.
     *
     * @return A map of list of string
     */
    public Map<String, List<String>> getResponseHeaders() {
        return responseHeaders;
    }

    /**
     * Get the HTTP response body.
     *
     * @return Response body in the form of string
     */
    public String getResponseBody() {
        return responseBody;
    }

    @Override
    public String getMessage() {
        return getStatusCode()
                + ": "
                + (StringUtils.isBlank(responseBody) ? super.getMessage() : responseBody);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(getClass().getName()).append(": ");

        if (getMessage() != null) {
            builder.append(getMessage());
        }

        if (status > 0) {
            builder.append(" {status=").append(status);
            if (this.code != null) {
                builder.append(", code='").append(code).append("'");
            }

            builder.append(", retryable: ").append(retryable);
        }

        if (this.instance != null) {
            builder.append(", instance: ").append(instance);
        }

        if (this.validationErrors != null) {
            builder.append(", validationErrors: ").append(validationErrors);
        }

        builder.append("}");
        return builder.toString();
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public void setStatus(int status) {
        this.status = status;
    }


    public String getInstance() {
        return instance;
    }

    public void setInstance(String instance) {
        this.instance = instance;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public void setRetryable(boolean retryable) {
        this.retryable = retryable;
    }

    public int getStatus() {
        return this.status;
    }
}
