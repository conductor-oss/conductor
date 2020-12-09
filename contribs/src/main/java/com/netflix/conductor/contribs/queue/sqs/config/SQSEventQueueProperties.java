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
package com.netflix.conductor.contribs.queue.sqs.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "workflow", name = "sqs.event.queue.enabled", havingValue = "true")
public class SQSEventQueueProperties {

    @Value("${workflow.event.queues.sqs.batchSize:1}")
    private int batchSize;

    @Value("${workflow.event.queues.sqs.pollTimeInMS:100}")
    private int pollTimeMS;

    @Value("${workflow.event.queues.sqs.visibilityTimeoutInSeconds:60}")
    private int visibilityTimeoutSeconds;

    @Value("${workflow.listener.queue.prefix:}")
    private String listenerQueuePrefix;

    @Value("${workflow.listener.queue.authorizedAccounts:}")
    private String authorizedAccounts;

    public int getBatchSize() {
        return batchSize;
    }

    public int getPollTimeMS() {
        return pollTimeMS;
    }

    public int getVisibilityTimeoutSeconds() {
        return visibilityTimeoutSeconds;
    }

    public String getListenerQueuePrefix() {
        return listenerQueuePrefix;
    }

    public String getAuthorizedAccounts() {
        return authorizedAccounts;
    }
}
