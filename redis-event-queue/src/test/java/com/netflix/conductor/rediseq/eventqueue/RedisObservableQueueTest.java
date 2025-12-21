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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.rediseq.config.RedisEventQueueProperties;

import io.lettuce.core.Consumer;
import io.lettuce.core.RedisBusyException;
import io.lettuce.core.StreamMessage;
import io.lettuce.core.XGroupCreateArgs;
import io.lettuce.core.XReadArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisStreamCommands;
import rx.Observable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
@RunWith(SpringJUnit4ClassRunner.class)
public class RedisObservableQueueTest {

    private RedisObservableQueue queue;

    @Mock private StatefulRedisConnection<String, String> mockConnection;

    @Mock private RedisStreamCommands<String, String> mockStreamCommands;

    @Mock private RedisEventQueueProperties mockProperties;

    @Before
    public void setUp() {
        mockConnection = mock(StatefulRedisConnection.class);
        mockStreamCommands = mock(RedisStreamCommands.class);
        mockProperties = mock(RedisEventQueueProperties.class);

        when(mockConnection.sync()).thenReturn(mockStreamCommands);
        when(mockProperties.getPollTimeDuration()).thenReturn(Duration.ofMillis(100));
        when(mockProperties.getDlqStream()).thenReturn("test-dlq");
        when(mockProperties.getConsumerGroup()).thenReturn("test-group");
        when(mockProperties.getEffectiveConsumerName()).thenReturn("test-consumer");
        when(mockProperties.getBatchSize()).thenReturn(10);
        when(mockProperties.getBlockTimeout()).thenReturn(Duration.ofSeconds(2));

        queue = new RedisObservableQueue("test-stream", mockConnection, mockProperties);
    }

    @Test
    public void testStart() {
        // Consumer group already exists
        doThrow(new RedisBusyException("BUSYGROUP Consumer Group name already exists"))
                .when(mockStreamCommands)
                .xgroupCreate(any(XReadArgs.StreamOffset.class), anyString(), any());

        queue.start();

        assertTrue(queue.isRunning());
        verify(mockStreamCommands)
                .xgroupCreate(
                        any(XReadArgs.StreamOffset.class),
                        eq("test-group"),
                        any(XGroupCreateArgs.class));
    }

    @Test
    public void testStartCreatesConsumerGroup() {
        // Consumer group doesn't exist yet
        when(mockStreamCommands.xgroupCreate(
                        any(XReadArgs.StreamOffset.class), anyString(), any()))
                .thenReturn("OK");

        queue.start();

        assertTrue(queue.isRunning());
        verify(mockStreamCommands)
                .xgroupCreate(
                        any(XReadArgs.StreamOffset.class),
                        eq("test-group"),
                        any(XGroupCreateArgs.class));
    }

    @Test
    public void testObserve() throws InterruptedException {
        // Mock stream messages
        StreamMessage<String, String> message1 =
                new StreamMessage<>("test-stream", "1234567890123-0", Map.of("payload", "test-payload-1"));
        StreamMessage<String, String> message2 =
                new StreamMessage<>("test-stream", "1234567890124-0", Map.of("payload", "{\"key\":\"value\"}"));

        List<StreamMessage<String, String>> streamMessages = List.of(message1, message2);

        when(mockStreamCommands.xreadgroup(
                        any(Consumer.class), any(XReadArgs.class), any(XReadArgs.StreamOffset.class)))
                .thenReturn(streamMessages)
                .thenReturn(null); // Subsequent calls return empty

        queue.start();

        List<Message> received = new ArrayList<>();
        Observable<Message> observable = queue.observe();
        assertNotNull(observable);
        observable.subscribe(received::add);

        Thread.sleep(500); // Allow time for polling

        assertEquals(2, received.size());
        assertEquals("1234567890123-0", received.get(0).getId());
        assertEquals("1234567890124-0", received.get(1).getId());
    }

    @Test
    public void testObserveWithJsonPayload() throws InterruptedException {
        // Mock stream message with valid JSON payload
        StreamMessage<String, String> message =
                new StreamMessage<>(
                        "test-stream",
                        "1234567890123-0",
                        Map.of("payload", "{\"workflowId\":\"123\",\"taskId\":\"456\"}"));

        when(mockStreamCommands.xreadgroup(
                        any(Consumer.class), any(XReadArgs.class), any(XReadArgs.StreamOffset.class)))
                .thenReturn(List.of(message))
                .thenReturn(null);

        queue.start();

        List<Message> received = new ArrayList<>();
        Observable<Message> observable = queue.observe();
        observable.subscribe(received::add);

        Thread.sleep(500);

        assertEquals(1, received.size());
        assertEquals("{\"workflowId\":\"123\",\"taskId\":\"456\"}", received.get(0).getPayload());
    }

    @Test
    public void testAck() {
        when(mockStreamCommands.xack(eq("test-stream"), eq("test-group"), eq("1234567890123-0")))
                .thenReturn(1L);

        Message message = new Message("1234567890123-0", "payload", "1234567890123-0");
        List<String> failedAcks = queue.ack(List.of(message));

        assertTrue(failedAcks.isEmpty());
        verify(mockStreamCommands).xack("test-stream", "test-group", "1234567890123-0");
    }

    @Test
    public void testAckFailure() {
        when(mockStreamCommands.xack(eq("test-stream"), eq("test-group"), eq("1234567890123-0")))
                .thenReturn(0L); // Message not acknowledged

        Message message = new Message("1234567890123-0", "payload", "1234567890123-0");
        List<String> failedAcks = queue.ack(List.of(message));

        assertEquals(1, failedAcks.size());
        assertEquals("1234567890123-0", failedAcks.get(0));
    }

    @Test
    public void testNack() {
        when(mockStreamCommands.xadd(eq("test-dlq"), anyMap())).thenReturn("dlq-message-id");
        when(mockStreamCommands.xack(eq("test-stream"), eq("test-group"), eq("1234567890123-0")))
                .thenReturn(1L);

        Message message = new Message("1234567890123-0", "payload", "1234567890123-0");
        queue.nack(List.of(message));

        // Verify message was added to DLQ
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mockStreamCommands).xadd(eq("test-dlq"), captor.capture());

        Map<String, String> dlqFields = captor.getValue();
        assertEquals("payload", dlqFields.get("payload"));
        assertEquals("test-stream", dlqFields.get("original_stream"));
        assertEquals("1234567890123-0", dlqFields.get("original_id"));
        assertNotNull(dlqFields.get("nacked_at"));

        // Verify original message was acknowledged
        verify(mockStreamCommands).xack("test-stream", "test-group", "1234567890123-0");
    }

    @Test
    public void testPublish() {
        when(mockStreamCommands.xadd(eq("test-stream"), anyMap())).thenReturn("new-message-id");

        Message message = new Message("msg-1", "test-payload", null);
        queue.publish(List.of(message));

        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mockStreamCommands).xadd(eq("test-stream"), captor.capture());

        Map<String, String> fields = captor.getValue();
        assertEquals("test-payload", fields.get("payload"));
        assertEquals("msg-1", fields.get("id"));
    }

    @Test
    public void testSize() {
        when(mockStreamCommands.xlen("test-stream")).thenReturn(42L);

        long size = queue.size();

        assertEquals(42, size);
        verify(mockStreamCommands).xlen("test-stream");
    }

    @Test
    public void testSizeOnError() {
        when(mockStreamCommands.xlen("test-stream")).thenThrow(new RuntimeException("Redis error"));

        long size = queue.size();

        assertEquals(-1, size);
    }

    @Test
    public void testLifecycle() {
        // Consumer group already exists
        doThrow(new RedisBusyException("BUSYGROUP Consumer Group name already exists"))
                .when(mockStreamCommands)
                .xgroupCreate(any(XReadArgs.StreamOffset.class), anyString(), any());

        assertFalse(queue.isRunning());

        queue.start();
        assertTrue(queue.isRunning());

        queue.stop();
        assertFalse(queue.isRunning());
    }

    @Test
    public void testGetters() {
        assertEquals("redis", queue.getType());
        assertEquals("test-stream", queue.getName());
        assertEquals("redis://test-stream", queue.getURI());
        assertFalse(queue.rePublishIfNoAck());
    }

    @Test
    public void testClose() {
        // Consumer group already exists
        doThrow(new RedisBusyException("BUSYGROUP Consumer Group name already exists"))
                .when(mockStreamCommands)
                .xgroupCreate(any(XReadArgs.StreamOffset.class), anyString(), any());

        queue.start();
        assertTrue(queue.isRunning());

        queue.close();
        assertFalse(queue.isRunning());
    }
}
