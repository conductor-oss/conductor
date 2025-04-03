package com.netflix.conductor.common.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
public class ServiceRegistry {

    private String name;
    private Type type;
    private String serviceURI;
    private final List<ServiceMethod> methods = new ArrayList<>();
    private final List<RequestParam> requestParams = new ArrayList<>();
    private final Config config = new Config();

    public enum Type {
        HTTP, gRPC
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Config {
        private OrkesCircuitBreakerConfig circuitBreakerConfig = new OrkesCircuitBreakerConfig();
    }
}
