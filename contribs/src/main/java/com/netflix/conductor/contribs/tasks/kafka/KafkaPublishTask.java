/*
 * Copyright 2022 Netflix, Inc.
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
package com.netflix.conductor.contribs.tasks.kafka;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.IntegerSerializer;
import org.apache.kafka.common.serialization.LongSerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import com.netflix.conductor.core.utils.Utils;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_KAFKA_PUBLISH;

@Component(TASK_TYPE_KAFKA_PUBLISH)
public class KafkaPublishTask extends WorkflowSystemTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaPublishTask.class);

    static final String REQUEST_PARAMETER_NAME = "kafka_request";
    private static final String MISSING_REQUEST =
            "Missing Kafka request. Task input MUST have a '"
                    + REQUEST_PARAMETER_NAME
                    + "' key with KafkaTask.Input as value. See documentation for KafkaTask for required input parameters";
    private static final String MISSING_BOOT_STRAP_SERVERS = "No boot strap servers specified";
    private static final String MISSING_KAFKA_TOPIC =
            "Missing Kafka topic. See documentation for KafkaTask for required input parameters";
    private static final String MISSING_KAFKA_VALUE =
            "Missing Kafka value.  See documentation for KafkaTask for required input parameters";
    private static final String FAILED_TO_INVOKE = "Failed to invoke kafka task due to: ";

    private final ObjectMapper objectMapper;
    private final String requestParameter;
    private final KafkaProducerManager producerManager;

    @Autowired
    public KafkaPublishTask(KafkaProducerManager clientManager, ObjectMapper objectMapper) {
        super(TASK_TYPE_KAFKA_PUBLISH);
        this.requestParameter = REQUEST_PARAMETER_NAME;
        this.producerManager = clientManager;
        this.objectMapper = objectMapper;
        LOGGER.info("KafkaTask initialized.");
    }

    @Override
    public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {

        long taskStartMillis = Instant.now().toEpochMilli();
        task.setWorkerId(Utils.getServerId());
        Object request = task.getInputData().get(requestParameter);

        if (Objects.isNull(request)) {
            markTaskAsFailed(task, MISSING_REQUEST);
            return;
        }

        Input input = objectMapper.convertValue(request, Input.class);

        if (StringUtils.isBlank(input.getBootStrapServers())) {
            markTaskAsFailed(task, MISSING_BOOT_STRAP_SERVERS);
            return;
        }

        if (StringUtils.isBlank(input.getTopic())) {
            markTaskAsFailed(task, MISSING_KAFKA_TOPIC);
            return;
        }

        if (Objects.isNull(input.getValue())) {
            markTaskAsFailed(task, MISSING_KAFKA_VALUE);
            return;
        }

        try {
            Future<RecordMetadata> recordMetaDataFuture = kafkaPublish(input);
            try {
                recordMetaDataFuture.get();
                if (isAsyncComplete(task)) {
                    task.setStatus(TaskModel.Status.IN_PROGRESS);
                } else {
                    task.setStatus(TaskModel.Status.COMPLETED);
                }
                long timeTakenToCompleteTask = Instant.now().toEpochMilli() - taskStartMillis;
                LOGGER.debug("Published message {}, Time taken {}", input, timeTakenToCompleteTask);

            } catch (ExecutionException ec) {
                LOGGER.error(
                        "Failed to invoke kafka task: {} - execution exception ",
                        task.getTaskId(),
                        ec);
                markTaskAsFailed(task, FAILED_TO_INVOKE + ec.getMessage());
            }
        } catch (Exception e) {
            LOGGER.error(
                    "Failed to invoke kafka task:{} for input {} - unknown exception",
                    task.getTaskId(),
                    input,
                    e);
            markTaskAsFailed(task, FAILED_TO_INVOKE + e.getMessage());
        }
    }

    private void markTaskAsFailed(TaskModel task, String reasonForIncompletion) {
        task.setReasonForIncompletion(reasonForIncompletion);
        task.setStatus(TaskModel.Status.FAILED);
    }

    /**
     * @param input Kafka Request
     * @return Future for execution.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Future<RecordMetadata> kafkaPublish(Input input) throws Exception {

        long startPublishingEpochMillis = Instant.now().toEpochMilli();

        Producer producer = producerManager.getProducer(input);

        long timeTakenToCreateProducer = Instant.now().toEpochMilli() - startPublishingEpochMillis;

        LOGGER.debug("Time taken getting producer {}", timeTakenToCreateProducer);

        Object key = getKey(input);

        Iterable<Header> headers =
                input.getHeaders().entrySet().stream()
                        .map(
                                header ->
                                        new RecordHeader(
                                                header.getKey(),
                                                String.valueOf(header.getValue()).getBytes()))
                        .collect(Collectors.toList());
        ProducerRecord rec =
                new ProducerRecord(
                        input.getTopic(),
                        null,
                        null,
                        key,
                        objectMapper.writeValueAsString(input.getValue()),
                        headers);

        Future send = producer.send(rec);

        long timeTakenToPublish = Instant.now().toEpochMilli() - startPublishingEpochMillis;

        LOGGER.debug("Time taken publishing {}", timeTakenToPublish);

        return send;
    }

    @VisibleForTesting
    Object getKey(Input input) {
        String keySerializer = input.getKeySerializer();

        if (LongSerializer.class.getCanonicalName().equals(keySerializer)) {
            return Long.parseLong(String.valueOf(input.getKey()));
        } else if (IntegerSerializer.class.getCanonicalName().equals(keySerializer)) {
            return Integer.parseInt(String.valueOf(input.getKey()));
        } else {
            return String.valueOf(input.getKey());
        }
    }

    @Override
    public boolean execute(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
        return false;
    }

    @Override
    public void cancel(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
        task.setStatus(TaskModel.Status.CANCELED);
    }

    @Override
    public boolean isAsync() {
        return true;
    }

    public static class Input {

        public static final String STRING_SERIALIZER = StringSerializer.class.getCanonicalName();
        private Map<String, Object> headers = new HashMap<>();
        private String bootStrapServers;
        private Object key;
        private Object value;
        private Integer requestTimeoutMs;
        private Integer maxBlockMs;
        private String topic;
        private String keySerializer = STRING_SERIALIZER;

        public Map<String, Object> getHeaders() {
            return headers;
        }

        public void setHeaders(Map<String, Object> headers) {
            this.headers = headers;
        }

        public String getBootStrapServers() {
            return bootStrapServers;
        }

        public void setBootStrapServers(String bootStrapServers) {
            this.bootStrapServers = bootStrapServers;
        }

        public Object getKey() {
            return key;
        }

        public void setKey(Object key) {
            this.key = key;
        }

        public Object getValue() {
            return value;
        }

        public void setValue(Object value) {
            this.value = value;
        }

        public Integer getRequestTimeoutMs() {
            return requestTimeoutMs;
        }

        public void setRequestTimeoutMs(Integer requestTimeoutMs) {
            this.requestTimeoutMs = requestTimeoutMs;
        }

        public String getTopic() {
            return topic;
        }

        public void setTopic(String topic) {
            this.topic = topic;
        }

        public String getKeySerializer() {
            return keySerializer;
        }

        public void setKeySerializer(String keySerializer) {
            this.keySerializer = keySerializer;
        }

        public Integer getMaxBlockMs() {
            return maxBlockMs;
        }

        public void setMaxBlockMs(Integer maxBlockMs) {
            this.maxBlockMs = maxBlockMs;
        }

        @Override
        public String toString() {
            return "Input{"
                    + "headers="
                    + headers
                    + ", bootStrapServers='"
                    + bootStrapServers
                    + '\''
                    + ", key="
                    + key
                    + ", value="
                    + value
                    + ", requestTimeoutMs="
                    + requestTimeoutMs
                    + ", maxBlockMs="
                    + maxBlockMs
                    + ", topic='"
                    + topic
                    + '\''
                    + ", keySerializer='"
                    + keySerializer
                    + '\''
                    + '}';
        }
    }
}
