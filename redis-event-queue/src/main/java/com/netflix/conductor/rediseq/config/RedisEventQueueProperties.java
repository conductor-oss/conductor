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
package com.netflix.conductor.rediseq.config;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.UUID;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("conductor.event-queues.redis")
@Validated
public class RedisEventQueueProperties {

    /** Redis host. */
    private String host = "localhost";

    /** Redis port. */
    private int port = 6379;

    /** Redis password (optional). */
    private String password;

    /** Redis database index. */
    private int database = 0;

    /** Enable SSL/TLS connection. */
    private boolean ssl = false;

    /** Consumer group name for Redis Streams. */
    private String consumerGroup = "conductor-consumer-group";

    /** Unique consumer name (auto-generated if null). */
    private String consumerName;

    /** Dead Letter Queue stream name. */
    private String dlqStream = "conductor-dlq";

    /** Prefix for stream names. */
    private String streamPrefix = "";

    /** Polling interval for consuming messages. */
    private Duration pollTimeDuration = Duration.ofMillis(100);

    /** Number of messages to read per poll. */
    private int batchSize = 10;

    /** Block timeout for XREADGROUP (0 = non-blocking). */
    private Duration blockTimeout = Duration.ofSeconds(2);

    /** Connection timeout. */
    private Duration connectionTimeout = Duration.ofSeconds(10);

    /** Command timeout. */
    private Duration commandTimeout = Duration.ofSeconds(5);

    // Getters and setters

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getDatabase() {
        return database;
    }

    public void setDatabase(int database) {
        this.database = database;
    }

    public boolean isSsl() {
        return ssl;
    }

    public void setSsl(boolean ssl) {
        this.ssl = ssl;
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public void setConsumerGroup(String consumerGroup) {
        this.consumerGroup = consumerGroup;
    }

    public String getConsumerName() {
        return consumerName;
    }

    public void setConsumerName(String consumerName) {
        this.consumerName = consumerName;
    }

    public String getDlqStream() {
        return dlqStream;
    }

    public void setDlqStream(String dlqStream) {
        this.dlqStream = dlqStream;
    }

    public String getStreamPrefix() {
        return streamPrefix;
    }

    public void setStreamPrefix(String streamPrefix) {
        this.streamPrefix = streamPrefix;
    }

    public Duration getPollTimeDuration() {
        return pollTimeDuration;
    }

    public void setPollTimeDuration(Duration pollTimeDuration) {
        this.pollTimeDuration = pollTimeDuration;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public Duration getBlockTimeout() {
        return blockTimeout;
    }

    public void setBlockTimeout(Duration blockTimeout) {
        this.blockTimeout = blockTimeout;
    }

    public Duration getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(Duration connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public Duration getCommandTimeout() {
        return commandTimeout;
    }

    public void setCommandTimeout(Duration commandTimeout) {
        this.commandTimeout = commandTimeout;
    }

    /**
     * Get the effective consumer name, generating one if not explicitly set.
     *
     * @return the consumer name
     */
    public String getEffectiveConsumerName() {
        if (consumerName == null || consumerName.isBlank()) {
            try {
                return InetAddress.getLocalHost().getHostName()
                        + "-"
                        + UUID.randomUUID().toString().substring(0, 8);
            } catch (UnknownHostException e) {
                return "consumer-" + UUID.randomUUID().toString().substring(0, 8);
            }
        }
        return consumerName;
    }
}
