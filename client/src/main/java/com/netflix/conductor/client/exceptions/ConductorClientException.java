package com.netflix.conductor.client.exceptions;

/**
 * Client exception thrown from Conductor api clients.
 */
public class ConductorClientException extends RuntimeException {

    private int status;
    private String message;
    private String instance;
    private String code;
    private boolean retryable;

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
        this.message = errorResponse.getMessage();
        this.code = errorResponse.getCode();
        this.instance = errorResponse.getInstance();
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
