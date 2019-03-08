package com.netflix.conductor.client.exceptions;

import java.util.List;
import com.netflix.conductor.common.validation.ValidationError;
import java.util.StringJoiner;


//TODO: Use one from common
public class ErrorResponse {

    private String code;
    private String message;
    private String instance;
    private boolean retryable;

    public List<ValidationError> getValidationErrors() {
        return validationErrors;
    }

    public void setValidationErrors(List<ValidationError> validationErrors) {
        this.validationErrors = validationErrors;
    }

    private List<ValidationError> validationErrors;

    public boolean isRetryable() {
        return retryable;
    }

    public void setRetryable(boolean retryable) {
        this.retryable = retryable;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
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

    @Override
    public String toString() {
        return new StringJoiner(", ", ErrorResponse.class.getSimpleName() + "[", "]")
            .add("code='" + code + "'")
            .add("message='" + message + "'")
            .add("instance='" + instance + "'")
            .add("retryable=" + retryable)
            .add("validationErrors=" + validationErrors)
            .toString();
    }
}
