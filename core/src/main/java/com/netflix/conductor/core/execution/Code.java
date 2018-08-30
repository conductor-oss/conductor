package com.netflix.conductor.core.execution;

import java.util.HashMap;
import java.util.Map;

public enum Code {
    INVALID_INPUT(400), INTERNAL_ERROR(500), NOT_FOUND(404), CONFLICT(409), UNAUTHORIZED(403), BACKEND_ERROR(500);

    private final int statusCode;

    Code(int statusCode){
        this.statusCode = statusCode;
    }

    public int getStatusCode(){
        return statusCode;
    }

    private static final Map<Integer, Code> codesByValue = new HashMap<>();

    static {
        for (Code type : Code.values()) {
            codesByValue.put(type.statusCode, type);
        }
    }

    public static Code forValue(int value) {
        return codesByValue.get(value);
    }
}