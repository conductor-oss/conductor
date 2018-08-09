package com.netflix.conductor.client.exceptions;

public class ErrorResponse {

    private String statusCode;
    private String message;
    private String instance;

    public String getCode() {
        return statusCode;
    }

    public void setCode(String code) {
        this.statusCode = code;
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
}
