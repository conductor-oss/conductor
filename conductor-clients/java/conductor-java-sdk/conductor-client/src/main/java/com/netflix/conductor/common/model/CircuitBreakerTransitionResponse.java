package com.netflix.conductor.common.model;

import lombok.Data;

@Data
public class CircuitBreakerTransitionResponse {
    private String service;
    private String previousState;
    private String currentState;
    private long transitionTimestamp;
    private String message;
}