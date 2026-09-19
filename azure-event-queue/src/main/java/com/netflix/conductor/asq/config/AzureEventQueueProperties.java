/*
 * Copyright 2024 Conductor Authors.
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
package com.netflix.conductor.asq.config;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;

@ConfigurationProperties("conductor.event-queues.azure")
public class AzureEventQueueProperties {

    /** The maximum number of messages to be fetched from the queue in a single request */
    private int batchSize = 1;

    /** The polling interval (in milliseconds) */
    private Duration pollTimeDuration = Duration.ofMillis(100);

    /** The visibility timeout (in seconds) for the message on the queue */
    @DurationUnit(ChronoUnit.SECONDS)
    private Duration visibilityTimeout = Duration.ofSeconds(60);

    /** The endpoint to use to connect to azure server for local testing */
    // e.g. https://storage-account-name.queue.core.windows.net
    private String endpoint = "https://storage-account-name.queue.core.windows.net";

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

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }
}
