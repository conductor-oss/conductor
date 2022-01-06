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

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;

@ConfigurationProperties("conductor.event-queues.sqs")
public class SQSEventQueueProperties {

    /** The maximum number of messages to be fetched from the queue in a single request */
    private int batchSize = 1;

    /** The polling interval (in milliseconds) */
    private Duration pollTimeDuration = Duration.ofMillis(100);

    /** The visibility timeout (in seconds) for the message on the queue */
    @DurationUnit(ChronoUnit.SECONDS)
    private Duration visibilityTimeout = Duration.ofSeconds(60);

    /** The prefix to be used for the default listener queues */
    private String listenerQueuePrefix = "";

    /** The AWS account Ids authorized to send messages to the queues */
    private String authorizedAccounts = "";

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public Duration getPollTimeDuration() {
        return pollTimeDuration;
    }

    public void setPollTimeDuration(Duration pollTimeDuration) {
        this.pollTimeDuration = pollTimeDuration;
    }

    public Duration getVisibilityTimeout() {
        return visibilityTimeout;
    }

    public void setVisibilityTimeout(Duration visibilityTimeout) {
        this.visibilityTimeout = visibilityTimeout;
    }

    public String getListenerQueuePrefix() {
        return listenerQueuePrefix;
    }

    public void setListenerQueuePrefix(String listenerQueuePrefix) {
        this.listenerQueuePrefix = listenerQueuePrefix;
    }

    public String getAuthorizedAccounts() {
        return authorizedAccounts;
    }

    public void setAuthorizedAccounts(String authorizedAccounts) {
        this.authorizedAccounts = authorizedAccounts;
    }
}
