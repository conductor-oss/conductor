/*
 * Copyright 2026 Conductor Authors.
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
package io.orkes.conductor.scheduler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

@Configuration
@ConfigurationProperties("conductor.scheduler")
@Getter
@Setter
public class SchedulerProperties {

    private int pollingThreadCount = 1;
    private int archivalThreadCount = 2;
    private String schedulerTimeZone = "UTC";

    private int pollingInterval = 100;
    private int pollBatchSize = 5;
    private int archivalPollBatchSize = 5;

    private int archivalMaintenanceIntervalRecordCount = 5000;
    private int archivalMaintenanceLockSeconds = 600;
    private int archivalMaintenanceLockTrySeconds = 1;
    private int archivalMaxRecords = 5;
    private int archivalMaxRecordThreshold = 10;
    private int maxScheduleJitterMs = 1000;
    private long initialDelayMs = 15000;

    private int userCacheExpireAfterWriteSeconds = 120;
    private int userCacheMaxSize = 1000;

    /** When true, the scheduler uses an external {@code SchedulerCacheDAO} for hot-path lookups. */
    private boolean cacheEnabled = false;
}
