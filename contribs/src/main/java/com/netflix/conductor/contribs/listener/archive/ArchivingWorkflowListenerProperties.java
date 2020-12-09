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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "workflow", name = "status.listener.type", havingValue = "archive")
public class ArchivingWorkflowListenerProperties {

    private final Environment environment;

    public ArchivingWorkflowListenerProperties(Environment environment) {
        this.environment = environment;
    }

    /**
     * The time to live in seconds for workflow archiving module. Currently, only RedisExecutionDAO supports this
     */
    @Value("${workflow.archival.ttl.seconds:0}")
    private int workflowArchivalTTL;

    /**
     * the number of threads to process the delay queue in workflow archival
     */
    @Value("${workflow.archival.delay.queue.worker.thread.count:5}")
    private int workflowArchivalDelayQueueWorkerThreadCount;

    public int getWorkflowArchivalTTL() {
        return workflowArchivalTTL;
    }

    /**
     * the time to delay the archival of workflow
     */
    public int getWorkflowArchivalDelay() {
        return environment.getProperty("workflow.archival.delay.seconds", Integer.class,
            environment.getProperty("async.update.delay.seconds", Integer.class, 60));
    }

    public int getWorkflowArchivalDelayQueueWorkerThreadCount() {
        return workflowArchivalDelayQueueWorkerThreadCount;
    }
}
