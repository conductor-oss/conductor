/*
 * Copyright 2020 Netflix, Inc.
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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;

@ConfigurationProperties("conductor.workflow-status-listener.archival")
public class ArchivingWorkflowListenerProperties {

    private final Environment environment;

    @Autowired
    public ArchivingWorkflowListenerProperties(Environment environment) {
        this.environment = environment;
    }

    /**
     * The time to live in seconds for workflow archiving module. Currently, only RedisExecutionDAO supports this
     */
    private int ttlSeconds = 0;

    /**
     * The number of threads to process the delay queue in workflow archival
     */
    private int delayQueueWorkerThreadCount = 5;

    public int getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(int ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    public int getDelayQueueWorkerThreadCount() {
        return delayQueueWorkerThreadCount;
    }

    public void setDelayQueueWorkerThreadCount(int delayQueueWorkerThreadCount) {
        this.delayQueueWorkerThreadCount = delayQueueWorkerThreadCount;
    }

    /**
     * The time to delay the archival of workflow
     */
    public int getWorkflowArchivalDelay() {
        return environment.getProperty("conductor.workflow-status-listener.archival.delaySeconds", Integer.class,
            environment.getProperty("async.update.delay.seconds", Integer.class, 60));
    }
}
