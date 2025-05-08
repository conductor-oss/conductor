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
package com.netflix.conductor.service;

import java.time.Clock;

/**
 * Centralised time service to get the current time. This is aimed to abstract the time source and
 * allow for interacting with time during testing.
 */
public class TimeService {
    public static boolean useCustomClockForTests = false;
    public static Clock customClockForTests = Clock.systemDefaultZone();

    public static long currentTimeMillis() {
        if (!useCustomClockForTests) {
            return TimeService.currentTimeMillis();
        } else {
            return customClockForTests.millis();
        }
    }
}
