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
package com.netflix.conductor.contribs.tasks.kafka;

import java.time.Duration;
import java.util.Properties;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.serialization.LongSerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class KafkaProducerManagerTest {

    @Test
    public void testRequestTimeoutSetFromDefault() {
        KafkaProducerManager manager =
                new KafkaProducerManager(
                        Duration.ofMillis(100),
                        Duration.ofMillis(500),
                        10,
                        Duration.ofMillis(120000),
                        new MockEnvironment());
        KafkaPublishTask.Input input = getInput();
        Properties props = manager.getProducerProperties(input);
        assertEquals(props.getProperty(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG), "100");
    }

    @Test
    public void testRequestTimeoutSetFromInput() {
        KafkaProducerManager manager =
                new KafkaProducerManager(
                        Duration.ofMillis(100),
                        Duration.ofMillis(500),
                        10,
                        Duration.ofMillis(120000),
                        new MockEnvironment());
        KafkaPublishTask.Input input = getInput();
        input.setRequestTimeoutMs(200);
        Properties props = manager.getProducerProperties(input);
        assertEquals(props.getProperty(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG), "200");
    }

    @Test
    public void testRequestTimeoutSetFromConfig() {
        KafkaProducerManager manager =
                new KafkaProducerManager(
                        Duration.ofMillis(150),
                        Duration.ofMillis(500),
                        10,
                        Duration.ofMillis(120000),
                        new MockEnvironment());
        KafkaPublishTask.Input input = getInput();
        Properties props = manager.getProducerProperties(input);
        assertEquals(props.getProperty(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG), "150");
    }

    @SuppressWarnings("rawtypes")
    @Test(expected = RuntimeException.class)
    public void testExecutionException() {
        KafkaProducerManager manager =
                new KafkaProducerManager(
                        Duration.ofMillis(150),
                        Duration.ofMillis(500),
                        10,
                        Duration.ofMillis(120000),
                        new MockEnvironment());
        KafkaPublishTask.Input input = getInput();
        Producer producer = manager.getProducer(input);
        assertNotNull(producer);
    }

    @SuppressWarnings("rawtypes")
    @Test
    public void testCacheInvalidation() {
        KafkaProducerManager manager =
                new KafkaProducerManager(
                        Duration.ofMillis(150),
                        Duration.ofMillis(500),
                        0,
                        Duration.ofMillis(0),
                        new MockEnvironment());
        KafkaPublishTask.Input input = getInput();
        input.setBootStrapServers("");
        Properties props = manager.getProducerProperties(input);
        Producer producerMock = mock(Producer.class);
        Producer producer = manager.getFromCache(props, () -> producerMock);
        assertNotNull(producer);
        verify(producerMock, times(1)).close();
    }

    @Test
    public void testMaxBlockMsFromConfig() {
        KafkaProducerManager manager =
                new KafkaProducerManager(
                        Duration.ofMillis(150),
                        Duration.ofMillis(500),
                        10,
                        Duration.ofMillis(120000),
                        new MockEnvironment());
        KafkaPublishTask.Input input = getInput();
        Properties props = manager.getProducerProperties(input);
        assertEquals(props.getProperty(ProducerConfig.MAX_BLOCK_MS_CONFIG), "500");
    }

    @Test
    public void testMaxBlockMsFromInput() {
        KafkaProducerManager manager =
                new KafkaProducerManager(
                        Duration.ofMillis(150),
                        Duration.ofMillis(500),
                        10,
                        Duration.ofMillis(120000),
                        new MockEnvironment());
        KafkaPublishTask.Input input = getInput();
        input.setMaxBlockMs(600);
        Properties props = manager.getProducerProperties(input);
        assertEquals(props.getProperty(ProducerConfig.MAX_BLOCK_MS_CONFIG), "600");
    }

    @Test
    public void testProducerConfigFromServerProperties() {
        String jaasConfig =
                "org.apache.kafka.common.security.plain.PlainLoginModule required"
                        + " username=\"user\" password=\"secret\";";
        MockEnvironment environment =
                new MockEnvironment()
                        .withProperty(
                                "conductor.tasks.kafka-publish.producer.security.protocol",
                                "SASL_PLAINTEXT")
                        .withProperty(
                                "conductor.tasks.kafka-publish.producer.sasl.mechanism", "PLAIN")
                        .withProperty(
                                "conductor.tasks.kafka-publish.producer.sasl.jaas.config",
                                jaasConfig);
        KafkaProducerManager manager =
                new KafkaProducerManager(
                        Duration.ofMillis(150),
                        Duration.ofMillis(500),
                        10,
                        Duration.ofMillis(120000),
                        environment);
        Properties props = manager.getProducerProperties(getInput());
        assertEquals(
                "SASL_PLAINTEXT", props.getProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG));
        assertEquals("PLAIN", props.getProperty(SaslConfigs.SASL_MECHANISM));
        assertEquals(jaasConfig, props.getProperty(SaslConfigs.SASL_JAAS_CONFIG));
    }

    @Test
    public void testServerProducerConfigDoesNotOverrideTaskSettings() {
        MockEnvironment environment =
                new MockEnvironment()
                        .withProperty(
                                "conductor.tasks.kafka-publish.producer.bootstrap.servers",
                                "other:9092")
                        .withProperty(
                                "conductor.tasks.kafka-publish.producer.value.serializer",
                                LongSerializer.class.getCanonicalName())
                        .withProperty(
                                "conductor.tasks.kafka-publish.producer.request.timeout.ms", "999");
        KafkaProducerManager manager =
                new KafkaProducerManager(
                        Duration.ofMillis(150),
                        Duration.ofMillis(500),
                        10,
                        Duration.ofMillis(120000),
                        environment);
        Properties props = manager.getProducerProperties(getInput());
        assertEquals("servers", props.getProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG));
        assertEquals(
                StringSerializer.class.getCanonicalName(),
                props.getProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG));
        assertEquals("150", props.getProperty(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG));
    }

    private KafkaPublishTask.Input getInput() {
        KafkaPublishTask.Input input = new KafkaPublishTask.Input();
        input.setTopic("testTopic");
        input.setValue("TestMessage");
        input.setKeySerializer(LongSerializer.class.getCanonicalName());
        input.setBootStrapServers("servers");
        return input;
    }
}
