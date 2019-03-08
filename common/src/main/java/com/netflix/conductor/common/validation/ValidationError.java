package com.netflix.conductor.common.validation;

import com.netflix.conductor.common.validation.ErrorResponse;
import java.util.StringJoiner;

/**
 * Captures a validation error that can be returned in {@link ErrorResponse}.
 */
public class ValidationError {
    private String path;
    private String message;
    private String invalidValue;

    public ValidationError() {
    }

    public ValidationError(String path, String message, String invalidValue) {
        this.path = path;
        this.message = message;
        this.invalidValue = invalidValue;
    }

    public String getPath() {
        return path;
    }

    public String getMessage() {
        return message;
    }

    public String getInvalidValue() {
        return invalidValue;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setInvalidValue(String invalidValue) {
        this.invalidValue = invalidValue;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", ValidationError.class.getSimpleName() + "[", "]")
            .add("path='" + path + "'")
            .add("message='" + message + "'")
            .add("invalidValue='" + invalidValue + "'")
            .toString();
    }
}

