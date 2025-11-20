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
package com.netflix.conductor.contribs.queue.amqp.util;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.contribs.queue.amqp.config.AMQPEventQueueProperties;

import lombok.Getter;

import static com.netflix.conductor.contribs.queue.amqp.util.AMQPConfigurations.*;

/**
 * @author Ritu Parathody
 */
public class AMQPSettings {

    private static final Pattern URI_PATTERN =
            Pattern.compile(
                    "^(?<type>amqp_(?:queue|exchange))?:?(?<name>[^?]+)\\??(?<params>.*)$",
                    Pattern.CASE_INSENSITIVE);

    private Type type;
    private String queueOrExchangeName;
    private String eventName;
    private String exchangeType;
    private String exchangeBoundQueueName;
    private String queueType;
    private String routingKey;
    private final String contentEncoding;
    private final String contentType;
    private boolean durable;
    private boolean exclusive;
    private boolean autoDelete;
    private boolean sequentialProcessing;
    private int deliveryMode;

    private final Map<String, Object> arguments = new HashMap<>();
    private static final Logger LOGGER = LoggerFactory.getLogger(AMQPSettings.class);

    public AMQPSettings(final AMQPEventQueueProperties properties) {
        // Initialize with a default values
        durable = properties.isDurable();
        exclusive = properties.isExclusive();
        autoDelete = properties.isAutoDelete();
        contentType = properties.getContentType();
        contentEncoding = properties.getContentEncoding();
        exchangeType = properties.getExchangeType();
        routingKey = StringUtils.EMPTY;
        queueType = properties.getQueueType();
        sequentialProcessing = properties.isSequentialMsgProcessing();
        type = Type.QUEUE;
        // Set common settings for publishing and consuming
        setDeliveryMode(properties.getDeliveryMode());
    }

    public AMQPSettings(final AMQPEventQueueProperties properties, final String type) {
        this(properties);
        this.type = Type.fromString(type);
    }

    public final boolean isDurable() {
        return durable;
    }

    public final boolean isExclusive() {
        return exclusive;
    }

    public final boolean autoDelete() {
        return autoDelete;
    }

    public final Map<String, Object> getArguments() {
        return arguments;
    }

    public final String getContentEncoding() {
        return contentEncoding;
    }

    /**
     * Use queue for publishing
     *
     * @param queueName the name of queue
     */
    public void setQueue(String queueName) {
        if (StringUtils.isEmpty(queueName)) {
            throw new IllegalArgumentException("Queue name for publishing is undefined");
        }
        this.queueOrExchangeName = queueName;
    }

    public String getQueueOrExchangeName() {
        return queueOrExchangeName;
    }

    public String getExchangeBoundQueueName() {
        if (StringUtils.isEmpty(exchangeBoundQueueName)) {
            return String.format("bound_to_%s", queueOrExchangeName);
        }
        return exchangeBoundQueueName;
    }

    public String getExchangeType() {
        return exchangeType;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    public int getDeliveryMode() {
        return deliveryMode;
    }

    public AMQPSettings setDeliveryMode(int deliveryMode) {
        if (deliveryMode != 1 && deliveryMode != 2) {
            throw new IllegalArgumentException("Delivery mode must be 1 or 2");
        }
        this.deliveryMode = deliveryMode;
        return this;
    }

    public String getContentType() {
        return contentType;
    }

    /**
     * Complete settings from the queue URI.
     *
     * <p><u>Example for queue:</u>
     *
     * <pre>
     * amqp_queue:myQueue?deliveryMode=1&autoDelete=true&exclusive=true
     * </pre>
     *
     * <u>Example for exchange:</u>
     *
     * <pre>
     * amqp_exchange:myExchange?bindQueueName=myQueue&exchangeType=topic&routingKey=myRoutingKey&exclusive=true
     * </pre>
     *
     * @param queueURI
     * @return
     */
    public final AMQPSettings fromURI(final String queueURI) {
        final Matcher matcher = URI_PATTERN.matcher(queueURI);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Queue URI doesn't matches the expected regexp");
        }

        // Set name of queue or exchange from group "name"
        LOGGER.info("Queue URI:{}", queueURI);
        if (Objects.nonNull(matcher.group("type"))) {
            type = Type.fromString(matcher.group("type"));
        }
        queueOrExchangeName = matcher.group("name");
        eventName = queueURI;
        if (matcher.groupCount() > 1) {
            final String queryParams = matcher.group("params");
            if (StringUtils.isNotEmpty(queryParams)) {
                // Handle parameters
                Arrays.stream(queryParams.split("\\s*\\&\\s*"))
                        .forEach(
                                param -> {
                                    final String[] kv = param.split("\\s*=\\s*");
                                    if (kv.length == 2) {
                                        if (kv[0].equalsIgnoreCase(
                                                String.valueOf(PARAM_EXCHANGE_TYPE))) {
                                            String value = kv[1];
                                            if (StringUtils.isEmpty(value)) {
                                                throw new IllegalArgumentException(
                                                        "The provided exchange type is empty");
                                            }
                                            exchangeType = value;
                                        }
                                        if (kv[0].equalsIgnoreCase(
                                                (String.valueOf(PARAM_QUEUE_NAME)))) {
                                            exchangeBoundQueueName = kv[1];
                                        }
                                        if (kv[0].equalsIgnoreCase(
                                                (String.valueOf(PARAM_ROUTING_KEY)))) {
                                            String value = kv[1];
                                            if (StringUtils.isEmpty(value)) {
                                                throw new IllegalArgumentException(
                                                        "The provided routing key is empty");
                                            }
                                            routingKey = value;
                                        }
                                        if (kv[0].equalsIgnoreCase(
                                                (String.valueOf(PARAM_DURABLE)))) {
                                            durable = Boolean.parseBoolean(kv[1]);
                                        }
                                        if (kv[0].equalsIgnoreCase(
                                                (String.valueOf(PARAM_EXCLUSIVE)))) {
                                            exclusive = Boolean.parseBoolean(kv[1]);
                                        }
                                        if (kv[0].equalsIgnoreCase(
                                                (String.valueOf(PARAM_AUTO_DELETE)))) {
                                            autoDelete = Boolean.parseBoolean(kv[1]);
                                        }
                                        if (kv[0].equalsIgnoreCase(
                                                (String.valueOf(PARAM_DELIVERY_MODE)))) {
                                            setDeliveryMode(Integer.parseInt(kv[1]));
                                        }
                                        if (kv[0].equalsIgnoreCase(
                                                (String.valueOf(PARAM_MAX_PRIORITY)))) {
                                            arguments.put("x-max-priority", Integer.valueOf(kv[1]));
                                        }
                                    }
                                });
            }
        }
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof AMQPSettings)) return false;
        AMQPSettings other = (AMQPSettings) obj;
        return Objects.equals(arguments, other.arguments)
                && autoDelete == other.autoDelete
                && Objects.equals(contentEncoding, other.contentEncoding)
                && Objects.equals(contentType, other.contentType)
                && deliveryMode == other.deliveryMode
                && durable == other.durable
                && Objects.equals(eventName, other.eventName)
                && Objects.equals(exchangeType, other.exchangeType)
                && exclusive == other.exclusive
                && Objects.equals(queueOrExchangeName, other.queueOrExchangeName)
                && Objects.equals(exchangeBoundQueueName, other.exchangeBoundQueueName)
                && Objects.equals(queueType, other.queueType)
                && Objects.equals(routingKey, other.routingKey)
                && sequentialProcessing == other.sequentialProcessing;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                arguments,
                autoDelete,
                contentEncoding,
                contentType,
                deliveryMode,
                durable,
                eventName,
                exchangeType,
                exclusive,
                queueOrExchangeName,
                exchangeBoundQueueName,
                queueType,
                routingKey,
                sequentialProcessing);
    }

    @Override
    public String toString() {
        return "AMQPSettings [queueOrExchangeName="
                + queueOrExchangeName
                + ", eventName="
                + eventName
                + ", exchangeType="
                + exchangeType
                + ", exchangeQueueName="
                + exchangeBoundQueueName
                + ", queueType="
                + queueType
                + ", routingKey="
                + routingKey
                + ", contentEncoding="
                + contentEncoding
                + ", contentType="
                + contentType
                + ", durable="
                + durable
                + ", exclusive="
                + exclusive
                + ", autoDelete="
                + autoDelete
                + ", sequentialProcessing="
                + sequentialProcessing
                + ", deliveryMode="
                + deliveryMode
                + ", arguments="
                + arguments
                + "]";
    }

    public String getEventName() {
        return eventName;
    }

    /**
     * @return the queueType
     */
    public String getQueueType() {
        return queueType;
    }

    /**
     * @return the sequentialProcessing
     */
    public boolean isSequentialProcessing() {
        return sequentialProcessing;
    }

    /**
     * Determine observer type - exchange or queue
     *
     * @return the observer type
     */
    public Type getType() {
        return type;
    }

    public enum Type {
        QUEUE("amqp_queue"),
        EXCHANGE("amqp_exchange");

        @Getter private String value;

        Type(String value) {
            this.value = value;
        }

        public static Type fromString(String value) {
            for (Type type : Type.values()) {
                if (type.value.equalsIgnoreCase(value)) {
                    return type;
                }
            }

            return QUEUE;
        }
    }
}
