/*
 * Copyright 2025 Conductor Authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
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
