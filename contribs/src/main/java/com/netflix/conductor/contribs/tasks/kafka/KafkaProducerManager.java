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
package com.netflix.conductor.contribs.tasks.kafka;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("rawtypes")
@Component
public class KafkaProducerManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaProducerManager.class);

    private final String requestTimeoutConfig;
    private final Cache<Properties, Producer> kafkaProducerCache;
    private final String maxBlockMsConfig;

    private static final String STRING_SERIALIZER = "org.apache.kafka.common.serialization.StringSerializer";
    private static final RemovalListener<Properties, Producer> LISTENER = notification -> {
        if (notification.getValue() != null) {
            notification.getValue().close();
            LOGGER.info("Closed producer for {}", notification.getKey());
        }
    };

    @Autowired
    public KafkaProducerManager(@Value("${kafka.publish.request.timeout.ms: 100}") String publishRequestTimeoutMs,
        @Value("${kafka.publish.max.block.ms:500}") String maxBlockMs,
        @Value("${kafka.publish.producer.cache.size:10}") int cacheSize,
        @Value("${kafka.publish.producer.cache.time.ms:120000}") int cacheTimeMs) {
        this.requestTimeoutConfig = publishRequestTimeoutMs;
        this.maxBlockMsConfig = maxBlockMs;
        this.kafkaProducerCache = CacheBuilder.newBuilder().removalListener(LISTENER)
            .maximumSize(cacheSize).expireAfterAccess(cacheTimeMs, TimeUnit.MILLISECONDS)
            .build();
    }

    public Producer getProducer(KafkaPublishTask.Input input) {
        Properties configProperties = getProducerProperties(input);
        return getFromCache(configProperties, () -> new KafkaProducer(configProperties));
    }

    @VisibleForTesting
    Producer getFromCache(Properties configProperties, Callable<Producer> createProducerCallable) {
        try {
            return kafkaProducerCache.get(configProperties, createProducerCallable);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @VisibleForTesting
    Properties getProducerProperties(KafkaPublishTask.Input input) {

        Properties configProperties = new Properties();
        configProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, input.getBootStrapServers());

        configProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, input.getKeySerializer());

        String requestTimeoutMs = requestTimeoutConfig;

        if (Objects.nonNull(input.getRequestTimeoutMs())) {
            requestTimeoutMs = String.valueOf(input.getRequestTimeoutMs());
        }

        String maxBlockMs = maxBlockMsConfig;

        if (Objects.nonNull(input.getMaxBlockMs())) {
            maxBlockMs = String.valueOf(input.getMaxBlockMs());
        }

        configProperties.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, requestTimeoutMs);
        configProperties.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, maxBlockMs);
        configProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, STRING_SERIALIZER);
        return configProperties;
    }
}
