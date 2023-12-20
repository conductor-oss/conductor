/*
 * Copyright 2023 Conductor Authors.
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
package com.netflix.conductor.contribs.queue.amqp.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.netflix.conductor.contribs.queue.amqp.util.RetryType;

import com.rabbitmq.client.AMQP.PROTOCOL;
import com.rabbitmq.client.ConnectionFactory;

@ConfigurationProperties("conductor.event-queues.amqp")
public class AMQPEventQueueProperties {

    private int batchSize = 1;

    private Duration pollTimeDuration = Duration.ofMillis(100);

    private String hosts = ConnectionFactory.DEFAULT_HOST;

    private String username = ConnectionFactory.DEFAULT_USER;

    private String password = ConnectionFactory.DEFAULT_PASS;

    private String virtualHost = ConnectionFactory.DEFAULT_VHOST;

    private int port = PROTOCOL.PORT;

    private int connectionTimeoutInMilliSecs = 180000;
    private int networkRecoveryIntervalInMilliSecs = 5000;
    private int requestHeartbeatTimeoutInSecs = 30;
    private int handshakeTimeoutInMilliSecs = 180000;
    private int maxChannelCount = 5000;
    private int limit = 50;
    private int duration = 1000;
    private RetryType retryType = RetryType.REGULARINTERVALS;

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public RetryType getType() {
        return retryType;
    }

    public void setType(RetryType type) {
        this.retryType = type;
    }

    public int getConnectionTimeoutInMilliSecs() {
        return connectionTimeoutInMilliSecs;
    }

    public void setConnectionTimeoutInMilliSecs(int connectionTimeoutInMilliSecs) {
        this.connectionTimeoutInMilliSecs = connectionTimeoutInMilliSecs;
    }

    public int getHandshakeTimeoutInMilliSecs() {
        return handshakeTimeoutInMilliSecs;
    }

    public void setHandshakeTimeoutInMilliSecs(int handshakeTimeoutInMilliSecs) {
        this.handshakeTimeoutInMilliSecs = handshakeTimeoutInMilliSecs;
    }

    public int getMaxChannelCount() {
        return maxChannelCount;
    }

    public void setMaxChannelCount(int maxChannelCount) {
        this.maxChannelCount = maxChannelCount;
    }

    private boolean useNio = false;

    private boolean durable = true;

    private boolean exclusive = false;

    private boolean autoDelete = false;

    private String contentType = "application/json";

    private String contentEncoding = "UTF-8";

    private String exchangeType = "topic";

    private String queueType = "classic";

    private boolean sequentialMsgProcessing = true;

    private int deliveryMode = 2;

    private boolean useExchange = true;

    private String listenerQueuePrefix = "";

    private boolean useSslProtocol = false;

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

    public String getHosts() {
        return hosts;
    }

    public void setHosts(String hosts) {
        this.hosts = hosts;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getVirtualHost() {
        return virtualHost;
    }

    public void setVirtualHost(String virtualHost) {
        this.virtualHost = virtualHost;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isUseNio() {
        return useNio;
    }

    public void setUseNio(boolean useNio) {
        this.useNio = useNio;
    }

    public boolean isDurable() {
        return durable;
    }

    public void setDurable(boolean durable) {
        this.durable = durable;
    }

    public boolean isExclusive() {
        return exclusive;
    }

    public void setExclusive(boolean exclusive) {
        this.exclusive = exclusive;
    }

    public boolean isAutoDelete() {
        return autoDelete;
    }

    public void setAutoDelete(boolean autoDelete) {
        this.autoDelete = autoDelete;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getContentEncoding() {
        return contentEncoding;
    }

    public void setContentEncoding(String contentEncoding) {
        this.contentEncoding = contentEncoding;
    }

    public String getExchangeType() {
        return exchangeType;
    }

    public void setExchangeType(String exchangeType) {
        this.exchangeType = exchangeType;
    }

    public int getDeliveryMode() {
        return deliveryMode;
    }

    public void setDeliveryMode(int deliveryMode) {
        this.deliveryMode = deliveryMode;
    }

    public boolean isUseExchange() {
        return useExchange;
    }

    public void setUseExchange(boolean useExchange) {
        this.useExchange = useExchange;
    }

    public String getListenerQueuePrefix() {
        return listenerQueuePrefix;
    }

    public void setListenerQueuePrefix(String listenerQueuePrefix) {
        this.listenerQueuePrefix = listenerQueuePrefix;
    }

    public String getQueueType() {
        return queueType;
    }

    public boolean isUseSslProtocol() {
        return useSslProtocol;
    }

    public void setUseSslProtocol(boolean useSslProtocol) {
        this.useSslProtocol = useSslProtocol;
    }

    /**
     * @param queueType Supports two queue types, 'classic' and 'quorum'. Classic will be be
     *     deprecated in 2022 and its usage discouraged from RabbitMQ community. So not using enum
     *     type here to hold different values.
     */
    public void setQueueType(String queueType) {
        this.queueType = queueType;
    }

    /**
     * @return the sequentialMsgProcessing
     */
    public boolean isSequentialMsgProcessing() {
        return sequentialMsgProcessing;
    }

    /**
     * @param sequentialMsgProcessing the sequentialMsgProcessing to set Supports sequential and
     *     parallel message processing capabilities. In parallel message processing, number of
     *     threads are controlled by batch size. No thread control or execution framework required
     *     here as threads are limited and short-lived.
     */
    public void setSequentialMsgProcessing(boolean sequentialMsgProcessing) {
        this.sequentialMsgProcessing = sequentialMsgProcessing;
    }

    public int getNetworkRecoveryIntervalInMilliSecs() {
        return networkRecoveryIntervalInMilliSecs;
    }

    public void setNetworkRecoveryIntervalInMilliSecs(int networkRecoveryIntervalInMilliSecs) {
        this.networkRecoveryIntervalInMilliSecs = networkRecoveryIntervalInMilliSecs;
    }

    public int getRequestHeartbeatTimeoutInSecs() {
        return requestHeartbeatTimeoutInSecs;
    }

    public void setRequestHeartbeatTimeoutInSecs(int requestHeartbeatTimeoutInSecs) {
        this.requestHeartbeatTimeoutInSecs = requestHeartbeatTimeoutInSecs;
    }
}
