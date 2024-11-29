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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.core.events.queue.ObservableQueue;
import com.netflix.conductor.kafkaeq.config.KafkaEventQueueProperties;

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
                                                    Message message =
                                                            new Message(
                                                                    record.partition()
                                                                            + "-"
                                                                            + record
                                                                                    .offset(), // Message ID based on partition and
                                                                    // offset
                                                                    record.value(),
                                                                    null);
                                                    String messageId =
                                                            record.partition()
                                                                    + "-"
                                                                    + record.offset();
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

                                            return Observable.from(messages);
                                        } catch (Exception e) {
                                            LOGGER.error(
                                                    "Error while polling Kafka for topic: {}",
                                                    topic,
                                                    e);
                                            return Observable.error(e);
                                        }
                                    })
                            .subscribe(subscriber::onNext, subscriber::onError);

                    subscriber.add(Subscriptions.create(this::stop));
                });
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
        long topicSize = getTopicSizeUsingAdminClient();
        if (topicSize != -1) {
            LOGGER.info("Topic size for 'conductor-event': {}", topicSize);
        } else {
            LOGGER.error("Failed to fetch topic size for 'conductor-event'");
        }

        return topicSize;
    }

    private long getTopicSizeUsingAdminClient() {
        try {
            // Fetch metadata for the topic asynchronously
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
            Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> offsets =
                    adminClient.listOffsets(offsetRequest).all().get();

            // Calculate total size by summing offsets
            return offsets.values().stream()
                    .mapToLong(ListOffsetsResult.ListOffsetsResultInfo::offset)
                    .sum();
        } catch (Exception e) {
            LOGGER.error("Error fetching topic size using AdminClient for topic: {}", topic, e);
            return -1;
        }
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
    public void stop() {
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
        int retries = 3;
        while (retries > 0) {
            try {
                kafkaConsumer.close();
                LOGGER.info("Kafka consumer closed successfully on retry.");
                return;
            } catch (Exception e) {
                retries--;
                LOGGER.warn(
                        "Retry failed to close Kafka consumer. Remaining attempts: {}", retries, e);
                if (retries == 0) {
                    LOGGER.error("Exhausted retries for closing Kafka consumer.");
                }
            }
        }
    }

    private void retryCloseProducer() {
        int retries = 3;
        while (retries > 0) {
            try {
                kafkaProducer.close();
                LOGGER.info("Kafka producer closed successfully on retry.");
                return;
            } catch (Exception e) {
                retries--;
                LOGGER.warn(
                        "Retry failed to close Kafka producer. Remaining attempts: {}", retries, e);
                if (retries == 0) {
                    LOGGER.error("Exhausted retries for closing Kafka producer.");
                }
            }
        }
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
}
