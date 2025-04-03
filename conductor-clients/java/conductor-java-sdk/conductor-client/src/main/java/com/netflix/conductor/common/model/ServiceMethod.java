package com.netflix.conductor.common.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class ServiceMethod {
    private Long id;
    private String operationName;
    private String methodName;
    private String methodType;  //GET, PUT, POST, UNARY, SERVER_STREAMING etc.
    private String inputType;
    private String outputType;

    // Add request parameters
    private List<RequestParam> requestParams = new ArrayList<>();

    // Sample input request -- useful for constructing input payload in the UI
    private Map<String, Object> exampleInput;
}
