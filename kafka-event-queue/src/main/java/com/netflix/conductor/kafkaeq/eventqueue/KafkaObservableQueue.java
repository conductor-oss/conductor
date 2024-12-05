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
package com.netflix.conductor.kafkaeq.eventqueue;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.TopicPartitionInfo;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.core.events.queue.ObservableQueue;
import com.netflix.conductor.kafkaeq.config.KafkaEventQueueProperties;
import com.netflix.conductor.metrics.Monitors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import rx.Observable;
import rx.subscriptions.Subscriptions;

public class KafkaObservableQueue implements ObservableQueue {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaObservableQueue.class);
    private static final String QUEUE_TYPE = "kafka";

    private final String topic;
    private volatile AdminClient adminClient;
    private final Consumer<String, String> kafkaConsumer;
    private final Producer<String, String> kafkaProducer;
    private final long pollTimeInMS;
    private final String dlqTopic;
    private final boolean autoCommitEnabled;
    private final Map<String, Long> unacknowledgedMessages = new ConcurrentHashMap<>();
    private volatile boolean running = false;
    private final KafkaEventQueueProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public KafkaObservableQueue(
            String topic,
            Properties consumerConfig,
            Properties producerConfig,
            Properties adminConfig,
            KafkaEventQueueProperties properties) {
        this.topic = topic;
        this.kafkaConsumer = new KafkaConsumer<>(consumerConfig);
        this.kafkaProducer = new KafkaProducer<>(producerConfig);
        this.properties = properties;
        this.pollTimeInMS = properties.getPollTimeDuration().toMillis();
        this.dlqTopic = properties.getDlqTopic();
        this.autoCommitEnabled =
                Boolean.parseBoolean(
                        consumerConfig
                                .getOrDefault(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
                                .toString());

        this.adminClient = AdminClient.create(adminConfig);
    }

    public KafkaObservableQueue(
            String topic,
            Consumer<String, String> kafkaConsumer,
            Producer<String, String> kafkaProducer,
            AdminClient adminClient,
            KafkaEventQueueProperties properties) {
        this.topic = topic;
        this.kafkaConsumer = kafkaConsumer;
        this.kafkaProducer = kafkaProducer;
        this.adminClient = adminClient;
        this.properties = properties;
        this.pollTimeInMS = properties.getPollTimeDuration().toMillis();
        this.dlqTopic = properties.getDlqTopic();
        this.autoCommitEnabled =
                Boolean.parseBoolean(
                        properties
                                .toConsumerConfig()
                                .getOrDefault(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
                                .toString());
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
                                            ConsumerRecords<String, String> records =
                                                    kafkaConsumer.poll(
                                                            this.properties.getPollTimeDuration());
                                            List<Message> messages = new ArrayList<>();

                                            for (ConsumerRecord<String, String> record : records) {
                                                try {
                                                    String messageId =
                                                            record.partition()
                                                                    + "-"
                                                                    + record.offset();
                                                    String key = record.key();
                                                    String value = record.value();
                                                    Map<String, String> headers = new HashMap<>();

                                                    // Extract headers
                                                    if (record.headers() != null) {
                                                        for (Header header : record.headers()) {
                                                            headers.put(
                                                                    header.key(),
                                                                    new String(header.value()));
                                                        }
                                                    }

                                                    // Log the details
                                                    LOGGER.debug(
                                                            "Input values MessageId: {} Key: {} Headers: {} Value: {}",
                                                            messageId,
                                                            key,
                                                            headers,
                                                            value);

                                                    // Construct message
                                                    String jsonMessage =
                                                            constructJsonMessage(
                                                                    key, headers, value);
                                                    LOGGER.debug("Payload: {}", jsonMessage);

                                                    Message message =
                                                            new Message(
                                                                    messageId, jsonMessage, null);

                                                    unacknowledgedMessages.put(
                                                            messageId, record.offset());
                                                    messages.add(message);
                                                } catch (Exception e) {
                                                    LOGGER.error(
                                                            "Failed to process record from Kafka: {}",
                                                            record,
                                                            e);
                                                }
                                            }

                                            Monitors.recordEventQueueMessagesProcessed(
                                                    QUEUE_TYPE, this.topic, messages.size());
                                            return Observable.from(messages);
                                        } catch (Exception e) {
                                            LOGGER.error(
                                                    "Error while polling Kafka for topic: {}",
                                                    topic,
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

    private String constructJsonMessage(String key, Map<String, String> headers, String payload) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"key\":\"").append(key != null ? key : "").append("\",");

        // Serialize headers to JSON, handling potential errors
        String headersJson = toJson(headers);
        if (headersJson != null) {
            json.append("\"headers\":").append(headersJson).append(",");
        } else {
            json.append("\"headers\":{}")
                    .append(","); // Default to an empty JSON object if headers are invalid
        }

        json.append("\"payload\":");

        // Detect if the payload is valid JSON
        if (isJsonValid(payload)) {
            json.append(payload); // Embed JSON object directly
        } else {
            json.append(payload != null ? "\"" + payload + "\"" : "null"); // Treat as plain text
        }

        json.append("}");
        return json.toString();
    }

    private boolean isJsonValid(String json) {
        if (json == null || json.isEmpty()) {
            return false;
        }
        try {
            objectMapper.readTree(json); // Parses the JSON to check validity
            return true;
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    protected String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            // Log the error and return a placeholder or null
            LOGGER.error("Failed to convert object to JSON: {}", value, ex);
            return null;
        }
    }

    @Override
    public List<String> ack(List<Message> messages) {
        // If autocommit is enabled we do not run this code.
        if (autoCommitEnabled == true) {
            LOGGER.info("Auto commit is enabled. Skipping manual acknowledgment.");
            return List.of();
        }

        Map<TopicPartition, OffsetAndMetadata> offsetsToCommit = new HashMap<>();
        List<String> failedAcks = new ArrayList<>(); // Collect IDs of failed messages

        for (Message message : messages) {
            String messageId = message.getId();
            if (unacknowledgedMessages.containsKey(messageId)) {
                try {
                    String[] parts = messageId.split("-");

                    if (parts.length != 2) {
                        throw new IllegalArgumentException(
                                "Invalid message ID format: " + messageId);
                    }

                    // Extract partition and offset from messageId
                    int partition = Integer.parseInt(parts[0]);
                    long offset = Long.parseLong(parts[1]);

                    // Remove message
                    unacknowledgedMessages.remove(messageId);

                    TopicPartition tp = new TopicPartition(topic, partition);

                    LOGGER.debug(
                            "Parsed messageId: {}, topic: {}, partition: {}, offset: {}",
                            messageId,
                            topic,
                            partition,
                            offset);
                    offsetsToCommit.put(tp, new OffsetAndMetadata(offset + 1));
                } catch (Exception e) {
                    LOGGER.error("Failed to prepare acknowledgment for message: {}", messageId, e);
                    failedAcks.add(messageId); // Add to failed list if exception occurs
                }
            } else {
                LOGGER.warn("Message ID not found in unacknowledged messages: {}", messageId);
                failedAcks.add(messageId); // Add to failed list if not found
            }
        }

        try {
            LOGGER.debug("Committing offsets: {}", offsetsToCommit);

            kafkaConsumer.commitSync(offsetsToCommit); // Commit all collected offsets
        } catch (CommitFailedException e) {
            LOGGER.warn("Offset commit failed: {}", e.getMessage());
        } catch (OffsetOutOfRangeException e) {
            LOGGER.error(
                    "OffsetOutOfRangeException encountered for topic {}: {}",
                    e.partitions(),
                    e.getMessage());

            // Reset offsets for the out-of-range partition
            Map<TopicPartition, OffsetAndMetadata> offsetsToReset = new HashMap<>();
            for (TopicPartition partition : e.partitions()) {
                long newOffset =
                        kafkaConsumer.position(partition); // Default to the current position
                offsetsToReset.put(partition, new OffsetAndMetadata(newOffset));
                LOGGER.warn("Resetting offset for partition {} to {}", partition, newOffset);
            }

            // Commit the new offsets
            kafkaConsumer.commitSync(offsetsToReset);
        } catch (Exception e) {
            LOGGER.error("Failed to commit offsets to Kafka: {}", offsetsToCommit, e);
            // Add all message IDs from the current batch to the failed list
            failedAcks.addAll(messages.stream().map(Message::getId).toList());
        }

        return failedAcks; // Return IDs of messages that were not successfully acknowledged
    }

    @Override
    public void nack(List<Message> messages) {
        for (Message message : messages) {
            try {
                kafkaProducer.send(
                        new ProducerRecord<>(dlqTopic, message.getId(), message.getPayload()));
            } catch (Exception e) {
                LOGGER.error("Failed to send message to DLQ. Message ID: {}", message.getId(), e);
            }
        }
    }

    @Override
    public void publish(List<Message> messages) {
        for (Message message : messages) {
            try {
                kafkaProducer.send(
                        new ProducerRecord<>(topic, message.getId(), message.getPayload()),
                        (metadata, exception) -> {
                            if (exception != null) {
                                LOGGER.error(
                                        "Failed to publish message to Kafka. Message ID: {}",
                                        message.getId(),
                                        exception);
                            } else {
                                LOGGER.info(
                                        "Message published to Kafka. Topic: {}, Partition: {}, Offset: {}",
                                        metadata.topic(),
                                        metadata.partition(),
                                        metadata.offset());
                            }
                        });
            } catch (Exception e) {
                LOGGER.error(
                        "Error publishing message to Kafka. Message ID: {}", message.getId(), e);
            }
        }
    }

    @Override
    public boolean rePublishIfNoAck() {
        return false;
    }

    @Override
    public void setUnackTimeout(Message message, long unackTimeout) {
        // Kafka does not support visibility timeout; this can be managed externally if
        // needed.
    }

    @Override
    public long size() {
        if (topicExists(this.topic) == false) {
            LOGGER.info("Topic '{}' not available, will refresh metadata.", this.topic);
            refreshMetadata(this.topic);
        }

        long topicSize = getTopicSizeUsingAdminClient();
        if (topicSize != -1) {
            LOGGER.info("Topic size for '{}': {}", this.topic, topicSize);
        } else {
            LOGGER.error("Failed to fetch topic size for '{}'", this.topic);
        }

        return topicSize;
    }

    private long getTopicSizeUsingAdminClient() {
        try {
            KafkaFuture<TopicDescription> topicDescriptionFuture =
                    adminClient
                            .describeTopics(Collections.singletonList(topic))
                            .topicNameValues()
                            .get(topic);

            TopicDescription topicDescription = topicDescriptionFuture.get();

            // Prepare request for latest offsets
            Map<TopicPartition, OffsetSpec> offsetRequest = new HashMap<>();
            for (TopicPartitionInfo partition : topicDescription.partitions()) {
                TopicPartition tp = new TopicPartition(topic, partition.partition());
                offsetRequest.put(tp, OffsetSpec.latest());
            }

            // Fetch offsets asynchronously
            KafkaFuture<Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo>>
                    offsetsFuture = adminClient.listOffsets(offsetRequest).all();

            Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> offsets =
                    offsetsFuture.get();

            // Calculate total size by summing offsets
            return offsets.values().stream()
                    .mapToLong(ListOffsetsResult.ListOffsetsResultInfo::offset)
                    .sum();
        } catch (ExecutionException e) {
            if (e.getCause()
                    instanceof org.apache.kafka.common.errors.UnknownTopicOrPartitionException) {
                LOGGER.warn("Topic '{}' does not exist or partitions unavailable.", topic);
            } else {
                LOGGER.error("Error fetching offsets for topic '{}': {}", topic, e.getMessage());
            }
        } catch (Exception e) {
            LOGGER.error(
                    "General error fetching offsets for topic '{}': {}", topic, e.getMessage());
        }
        return -1;
    }

    @Override
    public void close() {
        try {
            stop();
            LOGGER.info("KafkaObservableQueue fully stopped and resources closed.");
        } catch (Exception e) {
            LOGGER.error("Error during close(): {}", e.getMessage(), e);
        }
    }

    @Override
    public void start() {
        LOGGER.info("KafkaObservableQueue starting for topic: {}", topic);
        if (running) {
            LOGGER.warn("KafkaObservableQueue is already running for topic: {}", topic);
            return;
        }

        try {
            running = true;
            kafkaConsumer.subscribe(
                    Collections.singletonList(topic)); // Subscribe to a single topic
            LOGGER.info("KafkaObservableQueue started for topic: {}", topic);
        } catch (Exception e) {
            running = false;
            LOGGER.error("Error starting KafkaObservableQueue for topic: {}", topic, e);
        }
    }

    @Override
    public synchronized void stop() {
        LOGGER.info("Kafka consumer stopping for topic: {}", topic);
        if (!running) {
            LOGGER.warn("KafkaObservableQueue is already stopped for topic: {}", topic);
            return;
        }

        try {
            running = false;

            try {
                kafkaConsumer.unsubscribe();
                kafkaConsumer.close();
                LOGGER.info("Kafka consumer stopped for topic: {}", topic);
            } catch (Exception e) {
                LOGGER.error("Error stopping Kafka consumer for topic: {}", topic, e);
                retryCloseConsumer();
            }

            try {
                kafkaProducer.close();
                LOGGER.info("Kafka producer stopped for topic: {}", topic);
            } catch (Exception e) {
                LOGGER.error("Error stopping Kafka producer for topic: {}", topic, e);
                retryCloseProducer();
            }
        } catch (Exception e) {
            LOGGER.error("Critical error stopping KafkaObservableQueue for topic: {}", topic, e);
        }
    }

    private void retryCloseConsumer() {
        int attempts = 3;
        while (attempts > 0) {
            try {
                kafkaConsumer.unsubscribe();
                kafkaConsumer.close();
                LOGGER.info("Kafka consumer stopped for topic: {}", topic);
                return; // Exit if successful
            } catch (Exception e) {
                LOGGER.warn(
                        "Error stopping Kafka consumer for topic: {}, attempts remaining: {}",
                        topic,
                        attempts - 1,
                        e);
                attempts--;
                try {
                    Thread.sleep(1000); // Wait before retrying
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    LOGGER.error("Thread interrupted during Kafka consumer shutdown retries");
                    break;
                }
            }
        }
        LOGGER.error("Failed to stop Kafka consumer for topic: {} after retries", topic);
    }

    private void retryCloseProducer() {
        int attempts = 3;
        while (attempts > 0) {
            try {
                kafkaProducer.close();
                LOGGER.info("Kafka producer stopped for topic: {}", topic);
                return; // Exit if successful
            } catch (Exception e) {
                LOGGER.warn(
                        "Error stopping Kafka producer for topic: {}, attempts remaining: {}",
                        topic,
                        attempts - 1,
                        e);
                attempts--;
                try {
                    Thread.sleep(1000); // Wait before retrying
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    LOGGER.error("Thread interrupted during Kafka producer shutdown retries");
                    break;
                }
            }
        }
        LOGGER.error("Failed to stop Kafka producer for topic: {} after retries", topic);
    }

    @Override
    public String getType() {
        return QUEUE_TYPE;
    }

    @Override
    public String getName() {
        return topic;
    }

    @Override
    public String getURI() {
        return "kafka://" + topic;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    public static class Builder {
        private final KafkaEventQueueProperties properties;

        public Builder(KafkaEventQueueProperties properties) {
            this.properties = properties;
        }

        public KafkaObservableQueue build(final String topic) {
            Properties consumerConfig = new Properties();
            consumerConfig.putAll(properties.toConsumerConfig());

            LOGGER.debug("Kafka Consumer Config: {}", consumerConfig);

            Properties producerConfig = new Properties();
            producerConfig.putAll(properties.toProducerConfig());

            LOGGER.debug("Kafka Producer Config: {}", producerConfig);

            Properties adminConfig = new Properties();
            adminConfig.putAll(properties.toAdminConfig());

            LOGGER.debug("Kafka Admin Config: {}", adminConfig);

            try {
                return new KafkaObservableQueue(
                        topic, consumerConfig, producerConfig, adminConfig, properties);
            } catch (Exception e) {
                LOGGER.error("Failed to initialize KafkaObservableQueue for topic: {}", topic, e);
                throw new RuntimeException(
                        "Failed to initialize KafkaObservableQueue for topic: " + topic, e);
            }
        }
    }

    private boolean topicExists(String topic) {
        try {
            KafkaFuture<TopicDescription> future =
                    adminClient
                            .describeTopics(Collections.singletonList(topic))
                            .topicNameValues()
                            .get(topic);

            future.get(); // Attempt to fetch metadata
            return true;
        } catch (ExecutionException e) {
            if (e.getCause()
                    instanceof org.apache.kafka.common.errors.UnknownTopicOrPartitionException) {
                LOGGER.warn("Topic '{}' does not exist.", topic);
                return false;
            }
            LOGGER.error("Error checking if topic '{}' exists: {}", topic, e.getMessage());
            return false;
        } catch (Exception e) {
            LOGGER.error("General error checking if topic '{}' exists: {}", topic, e.getMessage());
            return false;
        }
    }

    private void refreshMetadata(String topic) {
        adminClient
                .describeTopics(Collections.singletonList(topic))
                .topicNameValues()
                .get(topic)
                .whenComplete(
                        (topicDescription, exception) -> {
                            if (exception != null) {
                                if (exception.getCause()
                                        instanceof
                                        org.apache.kafka.common.errors
                                                .UnknownTopicOrPartitionException) {
                                    LOGGER.warn("Topic '{}' still does not exist.", topic);
                                } else {
                                    LOGGER.error(
                                            "Error refreshing metadata for topic '{}': {}",
                                            topic,
                                            exception.getMessage());
                                }
                            } else {
                                LOGGER.info(
                                        "Metadata refreshed for topic '{}': Partitions = {}",
                                        topic,
                                        topicDescription.partitions());
                            }
                        });
    }
}
