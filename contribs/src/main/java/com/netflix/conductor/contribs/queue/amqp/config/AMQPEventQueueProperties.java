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
package com.netflix.conductor.contribs.queue.amqp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "workflow", name = "amqp.event.queue.enabled", havingValue = "true")
public class AMQPEventQueueProperties {

    @Value("${workflow.event.queues.amqp.batchSize:1}")
    private int batchSize;

    @Value("${workflow.event.queues.amqp.pollTimeInMs:100}")
    private int pollTimeMS;

    @Value("${workflow.event.queues.amqp.hosts:#{T(com.rabbitmq.client.ConnectionFactory).DEFAULT_HOST}}")
    private String hosts;

    @Value("${workflow.event.queues.amqp.username:#{T(com.rabbitmq.client.ConnectionFactory).DEFAULT_USER}}")
    private String username;

    @Value("${workflow.event.queues.amqp.password:#{T(com.rabbitmq.client.ConnectionFactory).DEFAULT_PASS}}")
    private String password;

    @Value("${workflow.event.queues.amqp.virtualHost:#{T(com.rabbitmq.client.ConnectionFactory).DEFAULT_VHOST}}")
    private String virtualHost;

    @Value("${workflow.event.queues.amqp.port:#{T(com.rabbitmq.client.AMQP.PROTOCOL).PORT}}")
    private int port;

    @Value("${workflow.event.queues.amqp.connectionTimeout:#{T(com.rabbitmq.client.ConnectionFactory).DEFAULT_CONNECTION_TIMEOUT}}")
    private int connectionTimeout;

    @Value("${workflow.event.queues.amqp.useNio:false}")
    private boolean useNio;

    @Value("${workflow.event.queues.amqp.durable:true}")
    private boolean durable;

    @Value("${workflow.event.queues.amqp.exclusive:false}")
    private boolean exclusive;

    @Value("${workflow.event.queues.amqp.autoDelete:false}")
    private boolean autoDelete;

    @Value("${workflow.event.queues.amqp.contentType:application/json}")
    private String contentType;

    @Value("${workflow.event.queues.amqp.contentEncoding:UTF-8}")
    private String contentEncoding;

    @Value("${workflow.event.queues.amqp.amqp_exchange:topic}")
    private String exchangeType;

    @Value("${workflow.event.queues.amqp.deliveryMode:2}")
    private int deliveryMode;

    @Value("${workflow.listener.queue.useExchange:true}")
    private boolean useExchange;

    @Value("${workflow.listener.queue.prefix:}")
    private String listenerQueuePrefix;

    public int getBatchSize() {
        return batchSize;
    }

    public int getPollTimeMS() {
        return pollTimeMS;
    }

    public String getHosts() {
        return hosts;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getVirtualHost() {
        return virtualHost;
    }

    public int getPort() {
        return port;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public boolean isUseNio() {
        return useNio;
    }

    public boolean isDurable() {
        return durable;
    }

    public boolean isExclusive() {
        return exclusive;
    }

    public boolean isAutoDelete() {
        return autoDelete;
    }

    public String getContentType() {
        return contentType;
    }

    public String getContentEncoding() {
        return contentEncoding;
    }

    public String getExchangeType() {
        return exchangeType;
    }

    public int getDeliveryMode() {
        return deliveryMode;
    }

    public boolean isUseExchange() {
        return useExchange;
    }

    public String getListenerQueuePrefix() {
        return listenerQueuePrefix;
    }
}
