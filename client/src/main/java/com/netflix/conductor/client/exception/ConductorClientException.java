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
package com.netflix.conductor.client.exception;

import java.util.List;

import com.netflix.conductor.common.validation.ErrorResponse;
import com.netflix.conductor.common.validation.ValidationError;

/** Client exception thrown from Conductor api clients. */
public class ConductorClientException extends RuntimeException {

    private int status;
    private String message;
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

    public ConductorClientException() {
        super();
    }

    public ConductorClientException(String message) {
        super(message);
        this.message = message;
    }

    public ConductorClientException(String message, Throwable cause) {
        super(message, cause);
        this.message = message;
    }

    public ConductorClientException(int status, String message) {
        super(message);
        this.status = status;
        this.message = message;
    }

    public ConductorClientException(int status, ErrorResponse errorResponse) {
        super(errorResponse.getMessage());
        this.status = status;
        this.retryable = errorResponse.isRetryable();
        this.message = errorResponse.getMessage();
        this.code = errorResponse.getCode();
        this.instance = errorResponse.getInstance();
        this.validationErrors = errorResponse.getValidationErrors();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();

        builder.append(getClass().getName()).append(": ");

        if (this.message != null) {
            builder.append(message);
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
            builder.append(", validationErrors: ").append(validationErrors.toString());
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

    public void setMessage(String message) {
        this.message = message;
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

    @Override
    public String getMessage() {
        return this.message;
    }

    public int getStatus() {
        return this.status;
    }
}
