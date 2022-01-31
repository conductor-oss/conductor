/*
 * Copyright 2022 Netflix, Inc.
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
package com.netflix.conductor.contribs.listener.archive;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.core.env.Environment;

@ConfigurationProperties("conductor.workflow-status-listener.archival")
public class ArchivingWorkflowListenerProperties {

    private final Environment environment;

    @Autowired
    public ArchivingWorkflowListenerProperties(Environment environment) {
        this.environment = environment;
    }

    /**
     * The time to live in seconds for workflow archiving module. Currently, only RedisExecutionDAO
     * supports this
     */
    @DurationUnit(ChronoUnit.SECONDS)
    private Duration ttlDuration = Duration.ZERO;

    /** The number of threads to process the delay queue in workflow archival */
    private int delayQueueWorkerThreadCount = 5;

    public Duration getTtlDuration() {
        return ttlDuration;
    }

    public void setTtlDuration(Duration ttlDuration) {
        this.ttlDuration = ttlDuration;
    }

    public int getDelayQueueWorkerThreadCount() {
        return delayQueueWorkerThreadCount;
    }

    public void setDelayQueueWorkerThreadCount(int delayQueueWorkerThreadCount) {
        this.delayQueueWorkerThreadCount = delayQueueWorkerThreadCount;
    }

    /** The time to delay the archival of workflow */
    public int getWorkflowArchivalDelay() {
        return environment.getProperty(
                "conductor.workflow-status-listener.archival.delaySeconds",
                Integer.class,
                environment.getProperty(
                        "conductor.app.asyncUpdateDelaySeconds", Integer.class, 60));
    }
}
