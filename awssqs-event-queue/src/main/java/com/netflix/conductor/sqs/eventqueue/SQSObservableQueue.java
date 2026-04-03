/*
 * Copyright 2022 Conductor Authors.
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
package com.netflix.conductor.sqs.eventqueue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.core.events.queue.ObservableQueue;
import com.netflix.conductor.metrics.Monitors;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Scheduler;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.BatchResultErrorEntry;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.CreateQueueResponse;
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchResponse;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesResponse;
import software.amazon.awssdk.services.sqs.model.ListQueuesRequest;
import software.amazon.awssdk.services.sqs.model.ListQueuesResponse;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResponse;
import software.amazon.awssdk.services.sqs.model.SetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.SetQueueAttributesResponse;

public class SQSObservableQueue implements ObservableQueue {

    private static final Logger LOGGER = LoggerFactory.getLogger(SQSObservableQueue.class);
    private static final String QUEUE_TYPE = "sqs";

    private final String queueName;
    private final int visibilityTimeoutInSeconds;
    private final int batchSize;
    private final SqsClient client;
    private final long pollTimeInMS;
    private final String queueURL;
    private final Scheduler scheduler;
    private volatile boolean running;

    private SQSObservableQueue(
            String queueName,
            SqsClient client,
            int visibilityTimeoutInSeconds,
            int batchSize,
            long pollTimeInMS,
            List<String> accountsToAuthorize,
            Scheduler scheduler) {
        this.queueName = queueName;
        this.client = client;
        this.visibilityTimeoutInSeconds = visibilityTimeoutInSeconds;
        this.batchSize = batchSize;
        this.pollTimeInMS = pollTimeInMS;
        this.queueURL = getOrCreateQueue();
        this.scheduler = scheduler;
        addPolicy(accountsToAuthorize);
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
            GetQueueAttributesRequest request =
                    GetQueueAttributesRequest.builder()
                            .queueUrl(queueURL)
                            .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)
                            .build();

            GetQueueAttributesResponse response = client.getQueueAttributes(request);
            String sizeAsStr =
                    response.attributes().get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES);

            return Long.parseLong(sizeAsStr);
        } catch (Exception e) {
            return -1;
        }
    }

    @Override
    public void setUnackTimeout(Message message, long unackTimeout) {
        int unackTimeoutInSeconds = (int) (unackTimeout / 1000);
        ChangeMessageVisibilityRequest request =
                ChangeMessageVisibilityRequest.builder()
                        .queueUrl(queueURL)
                        .receiptHandle(message.getReceipt())
                        .visibilityTimeout(unackTimeoutInSeconds)
                        .build();
        client.changeMessageVisibility(request);
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
        private int batchSize = 5;
        private long pollTimeInMS = 100;
        private SqsClient client;
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

        public Builder withClient(SqsClient client) {
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

        public Builder addAccountToAuthorize(String accountToAuthorize) {
            this.accountsToAuthorize.add(accountToAuthorize);
            return this;
        }

        public Builder withScheduler(Scheduler scheduler) {
            this.scheduler = scheduler;
            return this;
        }

        public SQSObservableQueue build() {
            return new SQSObservableQueue(
                    queueName,
                    client,
                    visibilityTimeout,
                    batchSize,
                    pollTimeInMS,
                    accountsToAuthorize,
                    scheduler);
        }
    }

    // Private methods
    String getOrCreateQueue() {
        List<String> queueUrls = listQueues(queueName);
        if (queueUrls == null || queueUrls.isEmpty()) {
            CreateQueueRequest createQueueRequest =
                    CreateQueueRequest.builder().queueName(queueName).build();
            CreateQueueResponse result = client.createQueue(createQueueRequest);
            return result.queueUrl();
        } else {
            return queueUrls.get(0);
        }
    }

    private String getQueueARN() {
        GetQueueAttributesRequest request =
                GetQueueAttributesRequest.builder()
                        .queueUrl(queueURL)
                        .attributeNames(QueueAttributeName.QUEUE_ARN)
                        .build();
        GetQueueAttributesResponse response = client.getQueueAttributes(request);
        return response.attributes().get(QueueAttributeName.QUEUE_ARN);
    }

    private void addPolicy(List<String> accountsToAuthorize) {
        if (accountsToAuthorize == null || accountsToAuthorize.isEmpty()) {
            LOGGER.info("No additional security policies attached for the queue " + queueName);
            return;
        }
        LOGGER.info("Authorizing " + accountsToAuthorize + " to the queue " + queueName);
        Map<QueueAttributeName, String> attributes = new HashMap<>();
        attributes.put(QueueAttributeName.POLICY, getPolicy(accountsToAuthorize));

        SetQueueAttributesRequest request =
                SetQueueAttributesRequest.builder()
                        .queueUrl(queueURL)
                        .attributes(attributes)
                        .build();
        SetQueueAttributesResponse result = client.setQueueAttributes(request);
        LOGGER.info("policy attachment result: " + result);
        LOGGER.info("policy attachment result: status=" + result.sdkHttpResponse().statusCode());
    }

    private String getPolicy(List<String> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            return null;
        }

        try {
            SqsPolicy policy = new SqsPolicy();
            policy.setVersion("2012-10-17");

            SqsStatement statement = new SqsStatement();
            statement.setEffect("Allow");
            statement.setAction("sqs:SendMessage");
            statement.setResource(getQueueARN());

            SqsPrincipal principal = new SqsPrincipal();
            principal.setAws(new ArrayList<>(accountIds));
            statement.setPrincipal(principal);

            policy.setStatement(List.of(statement));

            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.writeValueAsString(policy);
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to generate SQS policy for accounts: {}", accountIds, e);
            throw new RuntimeException("Failed to generate SQS policy", e);
        }
    }

    private List<String> listQueues(String queueName) {
        ListQueuesRequest listQueuesRequest =
                ListQueuesRequest.builder().queueNamePrefix(queueName).build();
        ListQueuesResponse resultList = client.listQueues(listQueuesRequest);
        return resultList.queueUrls().stream()
                .filter(queueUrl -> queueUrl.contains(queueName))
                .collect(Collectors.toList());
    }

    private void publishMessages(List<Message> messages) {
        LOGGER.debug("Sending {} messages to the SQS queue: {}", messages.size(), queueName);

        List<SendMessageBatchRequestEntry> entries =
                messages.stream()
                        .map(
                                msg ->
                                        SendMessageBatchRequestEntry.builder()
                                                .id(msg.getId())
                                                .messageBody(msg.getPayload())
                                                .build())
                        .collect(Collectors.toList());

        SendMessageBatchRequest batch =
                SendMessageBatchRequest.builder().queueUrl(queueURL).entries(entries).build();

        LOGGER.debug("sending {} messages in batch", entries.size());
        SendMessageBatchResponse result = client.sendMessageBatch(batch);
        LOGGER.debug("send result: {} for SQS queue: {}", result.failed().toString(), queueName);
    }

    List<Message> receiveMessages() {
        try {
            ReceiveMessageRequest receiveMessageRequest =
                    ReceiveMessageRequest.builder()
                            .queueUrl(queueURL)
                            .visibilityTimeout(visibilityTimeoutInSeconds)
                            .maxNumberOfMessages(batchSize)
                            .build();

            ReceiveMessageResponse result = client.receiveMessage(receiveMessageRequest);

            List<Message> messages =
                    result.messages().stream()
                            .map(
                                    msg ->
                                            new Message(
                                                    msg.messageId(),
                                                    msg.body(),
                                                    msg.receiptHandle()))
                            .collect(Collectors.toList());
            Monitors.recordEventQueueMessagesProcessed(QUEUE_TYPE, this.queueName, messages.size());
            return messages;
        } catch (Exception e) {
            LOGGER.error("Exception while getting messages from SQS", e);
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
                                            "Component stopped, skip listening for messages from SQS");
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

        List<DeleteMessageBatchRequestEntry> entries =
                messages.stream()
                        .map(
                                m ->
                                        DeleteMessageBatchRequestEntry.builder()
                                                .id(m.getId())
                                                .receiptHandle(m.getReceipt())
                                                .build())
                        .collect(Collectors.toList());

        DeleteMessageBatchRequest batch =
                DeleteMessageBatchRequest.builder().queueUrl(queueURL).entries(entries).build();

        DeleteMessageBatchResponse result = client.deleteMessageBatch(batch);
        List<String> failures =
                result.failed().stream()
                        .map(BatchResultErrorEntry::id)
                        .collect(Collectors.toList());
        LOGGER.debug("Failed to delete messages from queue: {}: {}", queueName, failures);
        return failures;
    }

    private static class SqsPolicy {
        @JsonProperty("Version")
        private String version;

        @JsonProperty("Statement")
        private List<SqsStatement> statement;

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public List<SqsStatement> getStatement() {
            return statement;
        }

        public void setStatement(List<SqsStatement> statement) {
            this.statement = statement;
        }
    }

    private static class SqsStatement {
        @JsonProperty("Effect")
        private String effect;

        @JsonProperty("Principal")
        private SqsPrincipal principal;

        @JsonProperty("Action")
        private String action;

        @JsonProperty("Resource")
        private String resource;

        public String getEffect() {
            return effect;
        }

        public void setEffect(String effect) {
            this.effect = effect;
        }

        public SqsPrincipal getPrincipal() {
            return principal;
        }

        public void setPrincipal(SqsPrincipal principal) {
            this.principal = principal;
        }

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public String getResource() {
            return resource;
        }

        public void setResource(String resource) {
            this.resource = resource;
        }
    }

    private static class SqsPrincipal {
        @JsonProperty("AWS")
        private List<String> aws;

        public List<String> getAws() {
            return aws;
        }

        public void setAws(List<String> aws) {
            this.aws = aws;
        }
    }
}
