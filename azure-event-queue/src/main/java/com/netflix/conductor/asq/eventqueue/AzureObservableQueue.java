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
package com.netflix.conductor.asq.eventqueue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.core.events.queue.ObservableQueue;
import com.netflix.conductor.metrics.Monitors;

import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.QueueServiceClient;
import com.azure.storage.queue.models.QueueMessageItem;
import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Scheduler;

public class AzureObservableQueue implements ObservableQueue {

    private static final Logger LOGGER = LoggerFactory.getLogger(AzureObservableQueue.class);
    private static final String QUEUE_TYPE = "azure";

    private final String queueName;
    private final int visibilityTimeoutInSeconds;
    private final int batchSize;
    private final QueueServiceClient serviceClient;
    private final long pollTimeInMS;
    private final String queueURL;
    private final Scheduler scheduler;
    private volatile boolean running;

    public AzureObservableQueue(
            String queueName,
            QueueServiceClient serviceClient,
            int visibilityTimeoutInSeconds,
            int batchSize,
            long pollTimeInMS,
            Scheduler scheduler)
            throws Exception {
        this.queueName = queueName;
        this.serviceClient = serviceClient;
        this.visibilityTimeoutInSeconds = visibilityTimeoutInSeconds;
        this.batchSize = batchSize;
        this.pollTimeInMS = pollTimeInMS;
        this.queueURL = getOrCreateQueue();
        this.scheduler = scheduler;
    }

    @Override
    public Observable<Message> observe() {
        OnSubscribe<Message> subscriber = getOnSubscribe();
        return Observable.create(subscriber);
    }

    @Override
    public List<String> ack(List<Message> messages) {
        return delete(messages);
    }

    @Override
    public void publish(List<Message> messages) {
        publishMessages(messages);
    }

    @Override
    public long size() {
        try {
            return serviceClient
                    .getQueueClient(queueName)
                    .getProperties()
                    .getApproximateMessagesCount();
        } catch (Exception e) {
            return -1;
        }
    }

    @Override
    public void setUnackTimeout(Message message, long unackTimeout) {
        serviceClient
                .getQueueClient(queueName)
                .updateMessage(
                        message.getId(),
                        message.getReceipt(),
                        message.getPayload(),
                        Duration.ofMillis(unackTimeout));
    }

    @Override
    public String getType() {
        return QUEUE_TYPE;
    }

    @Override
    public String getName() {
        return queueName;
    }

    @Override
    public String getURI() {
        return queueURL;
    }

    public long getPollTimeInMS() {
        return pollTimeInMS;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public int getVisibilityTimeoutInSeconds() {
        return visibilityTimeoutInSeconds;
    }

    @Override
    public void start() {
        LOGGER.info("Started listening to {}:{}", getClass().getSimpleName(), queueName);
        running = true;
    }

    @Override
    public void stop() {
        LOGGER.info("Stopped listening to {}:{}", getClass().getSimpleName(), queueName);
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    public static class Builder {

        private String queueName;
        private int visibilityTimeout = 30; // seconds
        private int batchSize = 1;
        private long pollTimeInMS = 100;
        private QueueServiceClient client;
        private List<String> accountsToAuthorize = new LinkedList<>();
        private Scheduler scheduler;

        public Builder withQueueName(String queueName) {
            this.queueName = queueName;
            return this;
        }

        /**
         * @param visibilityTimeout Visibility timeout for the message in SECONDS
         * @return builder instance
         */
        public Builder withVisibilityTimeout(int visibilityTimeout) {
            this.visibilityTimeout = visibilityTimeout;
            return this;
        }

        public Builder withBatchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        public Builder withClient(QueueServiceClient client) {
            this.client = client;
            return this;
        }

        public Builder withPollTimeInMS(long pollTimeInMS) {
            this.pollTimeInMS = pollTimeInMS;
            return this;
        }

        public Builder withAccountsToAuthorize(List<String> accountsToAuthorize) {
            this.accountsToAuthorize = accountsToAuthorize;
            return this;
        }

        public Builder withScheduler(Scheduler scheduler) {
            this.scheduler = scheduler;
            return this;
        }

        public AzureObservableQueue build() throws Exception {
            return new AzureObservableQueue(
                    queueName, client, visibilityTimeout, batchSize, pollTimeInMS, scheduler);
        }
    }

    // Private methods
    String getOrCreateQueue() throws Exception {
        QueueClient queueClient;
        try {
            queueClient = serviceClient.getQueueClient(queueName);
            queueClient.createIfNotExists();
            return queueClient.getQueueUrl();
        } catch (Exception e) {
            throw new Exception("Unable to get or create queue " + queueName + e.getMessage());
        }
    }

    private void publishMessages(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        LOGGER.debug("Sending {} messages to the Azure queue: {}", messages.size(), queueName);
        List<String> failures = new ArrayList<>();
        messages.forEach(
                m -> {
                    try {
                        serviceClient.getQueueClient(queueName).sendMessage(m.getPayload());
                    } catch (Exception e) {
                        failures.add(
                                "Failed to send message id "
                                        + m.getId()
                                        + " from queue "
                                        + queueName);
                    }
                });

        if (!failures.isEmpty()) {
            LOGGER.debug("Failed to send messages from queue: {}: {}", queueName, failures);
        } else {
            LOGGER.debug(
                    "Successfully sent " + messages.size() + " messages from queue " + queueName);
        }
    }

    List<Message> receiveMessages() {
        try {
            QueueClient client = serviceClient.getQueueClient(queueName);
            List<Message> messages = new ArrayList<>();
            // Retrieves up to the maximum number of messages from the queue and hides them from
            // other operations for 30 seconds.
            for (QueueMessageItem message : client.receiveMessages(batchSize)) {
                messages.add(
                        new Message(
                                message.getMessageId(),
                                message.getBody().toString(),
                                message.getPopReceipt()));
            }

            if (!messages.isEmpty()) {
                String message = messages.get(0).getPayload();
                if (message.contains("\n")) {
                    message = message.replace("\n", "");
                }
                LOGGER.info(
                        "Successfully received "
                                + messages.size()
                                + " message from queue "
                                + queueName
                                + ". The message is: "
                                + message);
            }
            Monitors.recordEventQueueMessagesProcessed(QUEUE_TYPE, this.queueName, messages.size());
            return messages;
        } catch (Exception e) {
            LOGGER.error("Exception while getting messages from Azure Queue Storage", e);
            Monitors.recordObservableQMessageReceivedErrors(QUEUE_TYPE);
        }
        return new ArrayList<>();
    }

    OnSubscribe<Message> getOnSubscribe() {
        return subscriber -> {
            Observable<Long> interval = Observable.interval(pollTimeInMS, TimeUnit.MILLISECONDS);
            interval.flatMap(
                            (Long x) -> {
                                if (!isRunning()) {
                                    LOGGER.debug(
                                            "Component stopped, skip listening for messages from Azure Queue Storage");
                                    return Observable.from(Collections.emptyList());
                                }
                                List<Message> messages = receiveMessages();
                                return Observable.from(messages);
                            })
                    .subscribe(subscriber::onNext, subscriber::onError);
        };
    }

    private List<String> delete(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        LOGGER.info("Deleting {} messages to the Azure queue: {}", messages.size(), queueName);
        List<String> failures = new ArrayList<>();
        messages.forEach(
                m -> {
                    try {
                        serviceClient
                                .getQueueClient(queueName)
                                .deleteMessage(m.getId(), m.getReceipt());
                    } catch (Exception e) {
                        failures.add(
                                "Failed to delete message id "
                                        + m.getId()
                                        + "message payload "
                                        + m.getPayload()
                                        + " from queue "
                                        + queueName);
                    }
                });

        if (!failures.isEmpty()) {
            LOGGER.info("Failed to delete messages from queue: {}: {}", queueName, failures);
        } else {
            String message = messages.get(0).getPayload();
            if (message.contains("\n")) {
                message = message.replace("\n", "");
            }
            LOGGER.info(
                    "Successfully deleted "
                            + messages.size()
                            + " messages from queue "
                            + queueName
                            + ". The message is: "
                            + message);
        }
        return failures;
    }
}
