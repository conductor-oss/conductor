package com.netflix.conductor.client.exceptions;

public class ConductorClientException extends RuntimeException {

    private Integer status;
    private String message;
    private String instance;
    private String code;

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

    public ConductorClientException(Integer status, String message) {
        super(message);
        this.status = status;
        this.message = message;
    }

    public ConductorClientException(Integer status, String message, String instance) {
        super(message);
        this.status = status;
        this.message = message;
        this.instance = instance;
    }

    public ConductorClientException(Integer status, ErrorResponse errorResponse) {
        super(errorResponse.getMessage());
        this.message = errorResponse.getMessage();
        this.status = status;
        this.code = errorResponse.getCode();
        this.instance = errorResponse.getInstance();
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }


    public void setStatus(Integer status) {
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


    @Override
    public String getMessage() {
        return this.message;
    }

    public Integer getStatus() {
        return this.status;
    }
}
