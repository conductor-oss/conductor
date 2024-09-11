/*
 * Copyright 2022 Orkes, Inc.
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
package io.orkes.conductor.client.model.event;

import java.util.Map;

import com.netflix.conductor.common.config.ObjectMapperProvider;

import com.fasterxml.jackson.databind.ObjectMapper;

public abstract class QueueConfiguration {

    private final String queueName;
    private final String queueType;

    private final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();

    private QueueWorkerConfiguration consumer;
    private QueueWorkerConfiguration producer;

    public QueueConfiguration(String queueName, String queueType) {
        this.queueName = queueName;
        this.queueType = queueType;
    }

    public QueueConfiguration withConsumer(QueueWorkerConfiguration consumer) {
        this.consumer = consumer;
        return this;
    }

    public QueueConfiguration withProducer(QueueWorkerConfiguration producer) {
        this.producer = producer;
        return this;
    }

    public String getQueueType() {
        return this.queueType;
    }

    public String getQueueName() {
        return this.queueName;
    }

    //FIXME why? explain me why?
    @Deprecated
    public String getConfiguration() throws Exception {
        if (this.consumer == null) {
            throw new RuntimeException("consumer must be set");
        }
        if (this.producer == null) {
            throw new RuntimeException("producer must be set");
        }
        Map<String, Object> config =
                Map.of(
                        "consumer", this.consumer.getConfiguration(),
                        "producer", this.producer.getConfiguration());
        return objectMapper.writeValueAsString(config);
    }

    public Map<String, Object> getQueueConfiguration() throws Exception {
        if (this.consumer == null) {
            throw new RuntimeException("consumer must be set");
        }
        if (this.producer == null) {
            throw new RuntimeException("producer must be set");
        }
        return Map.of(
                "consumer", this.consumer.getConfiguration(),
                "producer", this.producer.getConfiguration());
    }
}
