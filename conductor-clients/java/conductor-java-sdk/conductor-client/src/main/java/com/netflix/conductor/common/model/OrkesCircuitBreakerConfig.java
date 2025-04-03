package com.netflix.conductor.common.model;

import lombok.Data;

@Data
public class OrkesCircuitBreakerConfig {
    private float failureRateThreshold; // Percentage (e.g., 50.0 for 50%)
    private int slidingWindowSize;
    private int minimumNumberOfCalls;
    private long waitDurationInOpenState; // In millisec
    private int permittedNumberOfCallsInHalfOpenState;

    private float slowCallRateThreshold; // Percentage of slow calls
    private long slowCallDurationThreshold; // Defines "slow" call duration in milliSec
    private boolean automaticTransitionFromOpenToHalfOpenEnabled; // Auto transition
    private long maxWaitDurationInHalfOpenState; // Max time in HALF-OPEN state

}
