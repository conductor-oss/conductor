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
package com.netflix.conductor.rediseq.eventqueue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.core.events.queue.ObservableQueue;
import com.netflix.conductor.metrics.Monitors;
import com.netflix.conductor.rediseq.config.RedisEventQueueProperties;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.Consumer;
import io.lettuce.core.RedisBusyException;
import io.lettuce.core.RedisClient;
import io.lettuce.core.StreamMessage;
import io.lettuce.core.XAddArgs;
import io.lettuce.core.XGroupCreateArgs;
import io.lettuce.core.XReadArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisStreamCommands;
import rx.Observable;
import rx.subscriptions.Subscriptions;

public class RedisObservableQueue implements ObservableQueue {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisObservableQueue.class);
    private static final String QUEUE_TYPE = "redis";

    private final String streamName;
    private final RedisClient redisClient;
    private final RedisEventQueueProperties properties;
    private final String consumerGroup;
    private final String consumerName;
    private final String dlqStream;
    private final long pollTimeInMS;
    private final int batchSize;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private StatefulRedisConnection<String, String> connection;
    private RedisStreamCommands<String, String> streamCommands;
    private volatile boolean running = false;

    public RedisObservableQueue(
            String streamName, RedisClient redisClient, RedisEventQueueProperties properties) {
        this.streamName = streamName;
        this.redisClient = redisClient;
        this.properties = properties;
        this.consumerGroup = properties.getConsumerGroup();
        this.consumerName = properties.getEffectiveConsumerName();
        this.dlqStream = properties.getDlqStream();
        this.pollTimeInMS = properties.getPollTimeDuration().toMillis();
        this.batchSize = properties.getBatchSize();
    }

    // Constructor for testing with injected connection
    RedisObservableQueue(
            String streamName,
            StatefulRedisConnection<String, String> connection,
            RedisEventQueueProperties properties) {
        this.streamName = streamName;
        this.redisClient = null;
        this.connection = connection;
        this.streamCommands = connection.sync();
        this.properties = properties;
        this.consumerGroup = properties.getConsumerGroup();
        this.consumerName = properties.getEffectiveConsumerName();
        this.dlqStream = properties.getDlqStream();
        this.pollTimeInMS = properties.getPollTimeDuration().toMillis();
        this.batchSize = properties.getBatchSize();
    }

    @Override
    public void start() {
        LOGGER.info("Starting RedisObservableQueue for stream: {}", streamName);
        if (running) {
            LOGGER.warn("RedisObservableQueue is already running for stream: {}", streamName);
            return;
        }

        try {
            if (connection == null) {
                connection = redisClient.connect();
                streamCommands = connection.sync();
            }

            // Create consumer group if it doesn't exist
            ensureConsumerGroupExists();

            running = true;
            LOGGER.info("RedisObservableQueue started for stream: {}", streamName);
        } catch (Exception e) {
            LOGGER.error("Error starting RedisObservableQueue for stream: {}", streamName, e);
            throw new RuntimeException("Failed to start RedisObservableQueue", e);
        }
    }

    private void ensureConsumerGroupExists() {
        try {
            // XGROUP CREATE <stream> <group> $ MKSTREAM
            streamCommands.xgroupCreate(
                    XReadArgs.StreamOffset.from(streamName, "0"),
                    consumerGroup,
                    XGroupCreateArgs.Builder.mkstream());
            LOGGER.info(
                    "Created consumer group '{}' for stream '{}'", consumerGroup, streamName);
        } catch (RedisBusyException e) {
            // Consumer group already exists, this is fine
            LOGGER.debug(
                    "Consumer group '{}' already exists for stream '{}'",
                    consumerGroup,
                    streamName);
        } catch (Exception e) {
            LOGGER.warn("Error creating consumer group: {}", e.getMessage());
        }
    }

    @Override
    public Observable<Message> observe() {
        return Observable.create(
                subscriber -> {
                    Observable<Long> interval =
                            Observable.interval(pollTimeInMS, TimeUnit.MILLISECONDS);

                    interval.flatMap(
                                    (Long x) -> {
                                        if (!isRunning()) {
                                            return Observable.from(Collections.emptyList());
                                        }

                                        try {
                                            List<Message> messages = readMessages();
                                            Monitors.recordEventQueueMessagesProcessed(
                                                    QUEUE_TYPE, streamName, messages.size());
                                            return Observable.from(messages);
                                        } catch (Exception e) {
                                            LOGGER.error(
                                                    "Error while reading from Redis stream: {}",
                                                    streamName,
                                                    e);
                                            Monitors.recordObservableQMessageReceivedErrors(
                                                    QUEUE_TYPE);
                                            return Observable.error(e);
                                        }
                                    })
                            .subscribe(subscriber::onNext, subscriber::onError);

                    subscriber.add(Subscriptions.create(this::stop));
                });
    }

    private List<Message> readMessages() {
        List<Message> messages = new ArrayList<>();

        try {
            // XREADGROUP GROUP <group> <consumer> COUNT <count> BLOCK <ms> STREAMS <stream> >
            List<StreamMessage<String, String>> streamMessages =
                    streamCommands.xreadgroup(
                            Consumer.from(consumerGroup, consumerName),
                            XReadArgs.Builder.count(batchSize)
                                    .block(properties.getBlockTimeout().toMillis()),
                            XReadArgs.StreamOffset.lastConsumed(streamName));

            if (streamMessages != null) {
                for (StreamMessage<String, String> streamMessage : streamMessages) {
                    try {
                        String messageId = streamMessage.getId();
                        Map<String, String> body = streamMessage.getBody();

                        // Extract payload from the message body
                        String payload = constructJsonPayload(body);

                        Message message = new Message(messageId, payload, messageId);
                        messages.add(message);

                        LOGGER.debug(
                                "Received message: id={}, stream={}", messageId, streamName);
                    } catch (Exception e) {
                        LOGGER.error(
                                "Failed to process message from Redis stream: {}",
                                streamMessage,
                                e);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error reading messages from stream {}: {}", streamName, e.getMessage());
            throw e;
        }

        return messages;
    }

    private String constructJsonPayload(Map<String, String> body) {
        try {
            // If body contains a single "payload" key, return its value if it's valid JSON
            if (body.containsKey("payload")) {
                String payload = body.get("payload");
                if (isJsonValid(payload)) {
                    return payload;
                }
                return objectMapper.writeValueAsString(Map.of("payload", payload));
            }
            // Otherwise serialize the entire body as JSON
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to serialize message body to JSON", e);
            return "{}";
        }
    }

    private boolean isJsonValid(String json) {
        if (json == null || json.isEmpty()) {
            return false;
        }
        try {
            objectMapper.readTree(json);
            return true;
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    @Override
    public List<String> ack(List<Message> messages) {
        List<String> failedAcks = new ArrayList<>();

        for (Message message : messages) {
            try {
                // XACK <stream> <group> <messageId>
                long acknowledged =
                        streamCommands.xack(streamName, consumerGroup, message.getId());

                if (acknowledged == 0) {
                    LOGGER.warn(
                            "Message {} was not acknowledged (may already be acked)",
                            message.getId());
                    failedAcks.add(message.getId());
                } else {
                    LOGGER.debug(
                            "Acknowledged message: {} on stream: {}",
                            message.getId(),
                            streamName);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to acknowledge message: {}", message.getId(), e);
                failedAcks.add(message.getId());
            }
        }

        return failedAcks;
    }

    @Override
    public void nack(List<Message> messages) {
        // Move messages to DLQ stream
        for (Message message : messages) {
            try {
                // XADD <dlq-stream> * payload <payload> original_stream <stream> original_id <id>
                Map<String, String> dlqFields = new HashMap<>();
                dlqFields.put("payload", message.getPayload());
                dlqFields.put("original_stream", streamName);
                dlqFields.put("original_id", message.getId());
                dlqFields.put("nacked_at", String.valueOf(System.currentTimeMillis()));

                streamCommands.xadd(dlqStream, dlqFields);

                // Acknowledge the original message to remove it from pending
                streamCommands.xack(streamName, consumerGroup, message.getId());

                LOGGER.info("Moved message {} to DLQ: {}", message.getId(), dlqStream);
            } catch (Exception e) {
                LOGGER.error("Failed to nack message {} to DLQ", message.getId(), e);
            }
        }
    }

    @Override
    public void publish(List<Message> messages) {
        for (Message message : messages) {
            try {
                Map<String, String> fields = new HashMap<>();
                fields.put("payload", message.getPayload());
                if (message.getId() != null) {
                    fields.put("id", message.getId());
                }

                // XADD <stream> * <field> <value> ...
                String messageId = streamCommands.xadd(streamName, fields);

                LOGGER.debug(
                        "Published message to stream {}: messageId={}", streamName, messageId);
            } catch (Exception e) {
                LOGGER.error(
                        "Error publishing message to stream {}: {}",
                        streamName,
                        e.getMessage(),
                        e);
            }
        }
    }

    @Override
    public boolean rePublishIfNoAck() {
        // Redis Streams with consumer groups automatically makes unacknowledged
        // messages available for re-claiming via XPENDING + XCLAIM
        return false;
    }

    @Override
    public void setUnackTimeout(Message message, long unackTimeout) {
        // Redis Streams doesn't have a direct visibility timeout mechanism
        // Messages stay in pending list until explicitly acknowledged
        // Could implement via XCLAIM with IDLE parameter for reprocessing
        LOGGER.debug("setUnackTimeout called but not implemented for Redis Streams");
    }

    @Override
    public long size() {
        try {
            // XLEN <stream>
            Long length = streamCommands.xlen(streamName);
            return length != null ? length : 0;
        } catch (Exception e) {
            LOGGER.error("Error getting stream length for {}: {}", streamName, e.getMessage());
            return -1;
        }
    }

    @Override
    public void stop() {
        LOGGER.info("Stopping RedisObservableQueue for stream: {}", streamName);
        if (!running) {
            LOGGER.warn("RedisObservableQueue is already stopped for stream: {}", streamName);
            return;
        }

        running = false;

        try {
            if (connection != null && redisClient != null) {
                connection.close();
                connection = null;
                streamCommands = null;
            }
            LOGGER.info("RedisObservableQueue stopped for stream: {}", streamName);
        } catch (Exception e) {
            LOGGER.error("Error stopping RedisObservableQueue for stream: {}", streamName, e);
        }
    }

    @Override
    public void close() {
        stop();
    }

    @Override
    public String getType() {
        return QUEUE_TYPE;
    }

    @Override
    public String getName() {
        return streamName;
    }

    @Override
    public String getURI() {
        return "redis://" + streamName;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
