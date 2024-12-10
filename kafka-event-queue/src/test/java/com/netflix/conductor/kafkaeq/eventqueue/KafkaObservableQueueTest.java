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

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeTopicsResult;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.TopicPartitionInfo;
import org.apache.kafka.common.header.Headers;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.kafkaeq.config.KafkaEventQueueProperties;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import rx.Observable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
@RunWith(SpringJUnit4ClassRunner.class)
public class KafkaObservableQueueTest {

    private KafkaObservableQueue queue;

    @Mock private volatile MockConsumer<String, String> mockKafkaConsumer;

    @Mock private volatile MockProducer<String, String> mockKafkaProducer;

    @Mock private volatile AdminClient mockAdminClient;

    @Mock private volatile KafkaEventQueueProperties mockProperties;

    @Before
    public void setUp() throws Exception {
        System.out.println("Setup called");
        // Create mock instances
        this.mockKafkaConsumer = mock(MockConsumer.class);
        this.mockKafkaProducer = mock(MockProducer.class);
        this.mockAdminClient = mock(AdminClient.class);
        this.mockProperties = mock(KafkaEventQueueProperties.class);

        // Mock KafkaEventQueueProperties behavior
        when(this.mockProperties.getPollTimeDuration()).thenReturn(Duration.ofMillis(100));
        when(this.mockProperties.getDlqTopic()).thenReturn("test-dlq");

        // Create an instance of KafkaObservableQueue with the mocks
        queue =
                new KafkaObservableQueue(
                        "test-topic",
                        this.mockKafkaConsumer,
                        this.mockKafkaProducer,
                        this.mockAdminClient,
                        this.mockProperties);
    }

    private void injectMockField(Object target, String fieldName, Object mock) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, mock);
    }

    @Test
    public void testObserveWithHeaders() throws Exception {
        // Prepare mock consumer records with diverse headers, keys, and payloads
        List<ConsumerRecord<String, String>> records =
                List.of(
                        new ConsumerRecord<>("test-topic", 0, 0, "key-1", "payload-1"),
                        new ConsumerRecord<>("test-topic", 0, 1, "key-2", "{\"field\":\"value\"}"),
                        new ConsumerRecord<>("test-topic", 0, 2, null, "null-key-payload"),
                        new ConsumerRecord<>("test-topic", 0, 3, "key-3", ""),
                        new ConsumerRecord<>("test-topic", 0, 4, "key-4", "12345"),
                        new ConsumerRecord<>(
                                "test-topic",
                                0,
                                5,
                                "key-5",
                                "{\"complex\":{\"nested\":\"value\"}}"));

        // Add headers to each ConsumerRecord
        for (int i = 0; i < records.size(); i++) {
            ConsumerRecord<String, String> record = records.get(i);
            record.headers().add("header-key-" + i, ("header-value-" + i).getBytes());
        }

        ConsumerRecords<String, String> consumerRecords =
                new ConsumerRecords<>(Map.of(new TopicPartition("test-topic", 0), records));

        // Mock the KafkaConsumer poll behavior
        when(mockKafkaConsumer.poll(any(Duration.class)))
                .thenReturn(consumerRecords)
                .thenReturn(new ConsumerRecords<>(Collections.emptyMap())); // Subsequent polls
        // return empty

        // Start the queue
        queue.start();

        // Collect emitted messages
        List<Message> found = new ArrayList<>();
        Observable<Message> observable = queue.observe();
        assertNotNull(observable);
        observable.subscribe(found::add);

        // Allow polling to run
        try {
            Thread.sleep(1000); // Adjust duration if necessary
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Assert results
        assertNotNull(queue);
        assertEquals(6, found.size()); // Expect all 6 messages to be processed

        assertEquals("0-0", found.get(0).getId());
        assertEquals("0-1", found.get(1).getId());
        assertEquals("0-2", found.get(2).getId());
        assertEquals("0-3", found.get(3).getId());
        assertEquals("0-4", found.get(4).getId());
        assertEquals("0-5", found.get(5).getId());

        // Validate headers
        for (int i = 0; i < records.size(); i++) {
            ConsumerRecord<String, String> record = records.get(i);
            assertNotNull(record.headers());
            assertEquals(1, record.headers().toArray().length);
            assertEquals(
                    "header-value-" + i,
                    new String(record.headers().lastHeader("header-key-" + i).value()));
        }
    }

    @Test
    public void testObserveWithComplexPayload() throws Exception {
        // Prepare mock consumer records with diverse headers, keys, and payloads
        List<ConsumerRecord<String, String>> records =
                List.of(
                        new ConsumerRecord<>(
                                "test-topic", 0, 0, "key-1", "{\"data\":\"payload-1\"}"),
                        new ConsumerRecord<>("test-topic", 0, 1, "key-2", "{\"field\":\"value\"}"),
                        new ConsumerRecord<>("test-topic", 0, 2, null, "null-key-payload"),
                        new ConsumerRecord<>("test-topic", 0, 3, "key-3", ""),
                        new ConsumerRecord<>("test-topic", 0, 4, "key-4", "12345"),
                        new ConsumerRecord<>(
                                "test-topic",
                                0,
                                5,
                                "key-5",
                                "{\"complex\":{\"nested\":\"value\"}}"));

        // Add headers to each ConsumerRecord
        for (int i = 0; i < records.size(); i++) {
            ConsumerRecord<String, String> record = records.get(i);
            record.headers().add("header-key-" + i, ("header-value-" + i).getBytes());
        }

        ConsumerRecords<String, String> consumerRecords =
                new ConsumerRecords<>(Map.of(new TopicPartition("test-topic", 0), records));

        // Mock the KafkaConsumer poll behavior
        when(mockKafkaConsumer.poll(any(Duration.class)))
                .thenReturn(consumerRecords)
                .thenReturn(new ConsumerRecords<>(Collections.emptyMap())); // Subsequent polls
        // return empty

        // Start the queue
        queue.start();

        // Collect emitted messages
        List<Message> found = new ArrayList<>();
        Observable<Message> observable = queue.observe();
        assertNotNull(observable);
        observable.subscribe(found::add);

        // Allow polling to run
        try {
            Thread.sleep(1000); // Adjust duration if necessary
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Assert results
        assertNotNull(queue);
        assertEquals(6, found.size()); // Expect all 6 messages to be processed

        // Validate individual message payloads, keys, and headers in the structured
        ObjectMapper objectMapper = new ObjectMapper();
        // JSON format
        for (int i = 0; i < records.size(); i++) {
            ConsumerRecord<String, String> record = records.get(i);
            Message message = found.get(i);

            String expectedPayload =
                    constructJsonMessage(
                            objectMapper,
                            record.key(),
                            record.headers().toArray().length > 0
                                    ? extractHeaders(record.headers())
                                    : Collections.emptyMap(),
                            record.value());

            assertEquals(expectedPayload, message.getPayload());
        }
    }

    private Map<String, String> extractHeaders(Headers headers) {
        Map<String, String> headerMap = new HashMap<>();
        headers.forEach(header -> headerMap.put(header.key(), new String(header.value())));
        return headerMap;
    }

    private String constructJsonMessage(
            ObjectMapper objectMapper, String key, Map<String, String> headers, String payload) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"key\":\"").append(key != null ? key : "").append("\",");

        // Serialize headers to JSON, handling potential errors
        String headersJson = toJson(objectMapper, headers);
        if (headersJson != null) {
            json.append("\"headers\":").append(headersJson).append(",");
        } else {
            json.append("\"headers\":{}")
                    .append(","); // Default to an empty JSON object if headers are invalid
        }

        json.append("\"payload\":");

        // Detect if the payload is valid JSON
        if (isJsonValid(objectMapper, payload)) {
            json.append(payload); // Embed JSON object directly
        } else {
            json.append(payload != null ? "\"" + payload + "\"" : "null"); // Treat as plain text
        }

        json.append("}");
        return json.toString();
    }

    private boolean isJsonValid(ObjectMapper objectMapper, String json) {
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

    protected String toJson(ObjectMapper objectMapper, Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    @Test
    public void testAck() throws Exception {
        Map<String, Long> unacknowledgedMessages = new ConcurrentHashMap<>();
        unacknowledgedMessages.put("0-1", 1L);
        injectMockField(queue, "unacknowledgedMessages", unacknowledgedMessages);

        Message message = new Message("0-1", "payload", null);
        List<Message> messages = List.of(message);

        doNothing().when(mockKafkaConsumer).commitSync(anyMap());

        List<String> failedAcks = queue.ack(messages);

        assertTrue(failedAcks.isEmpty());
        verify(mockKafkaConsumer, times(1)).commitSync(anyMap());
    }

    @Test
    public void testNack() {
        // Arrange
        Message message = new Message("0-1", "payload", null);
        List<Message> messages = List.of(message);

        // Simulate the Kafka Producer behavior
        doAnswer(
                        invocation -> {
                            ProducerRecord<String, String> record = invocation.getArgument(0);
                            System.out.println("Simulated record sent: " + record);
                            return null; // Simulate success
                        })
                .when(mockKafkaProducer)
                .send(any(ProducerRecord.class));

        // Act
        queue.nack(messages);

        // Assert
        ArgumentCaptor<ProducerRecord<String, String>> captor =
                ArgumentCaptor.forClass(ProducerRecord.class);
        verify(mockKafkaProducer).send(captor.capture());

        ProducerRecord<String, String> actualRecord = captor.getValue();
        System.out.println("Captured Record: " + actualRecord);

        // Verify the captured record matches the expected values
        assertEquals("test-dlq", actualRecord.topic());
        assertEquals("0-1", actualRecord.key());
        assertEquals("payload", actualRecord.value());
    }

    @Test
    public void testPublish() {
        Message message = new Message("key-1", "payload", null);
        List<Message> messages = List.of(message);

        // Mock the behavior of the producer's send() method
        when(mockKafkaProducer.send(any(ProducerRecord.class), any()))
                .thenAnswer(
                        invocation -> {
                            Callback callback = invocation.getArgument(1);
                            // Simulate a successful send with mock metadata
                            callback.onCompletion(
                                    new RecordMetadata(
                                            new TopicPartition("test-topic", 0), // Topic and
                                            // partition
                                            0, // Base offset
                                            0, // Log append time
                                            0, // Create time
                                            10, // Serialized key size
                                            100 // Serialized value size
                                            ),
                                    null);
                            return null;
                        });

        // Invoke the publish method
        queue.publish(messages);

        // Verify that the producer's send() method was called exactly once
        verify(mockKafkaProducer, times(1)).send(any(ProducerRecord.class), any());
    }

    @Test
    public void testSize() throws Exception {
        // Step 1: Mock TopicDescription
        TopicDescription topicDescription =
                new TopicDescription(
                        "test-topic",
                        false,
                        List.of(
                                new TopicPartitionInfo(
                                        0, null, List.of(), List.of())) // One partition
                        );

        // Simulate `describeTopics` returning the TopicDescription
        DescribeTopicsResult mockDescribeTopicsResult = mock(DescribeTopicsResult.class);
        KafkaFuture<TopicDescription> mockFutureTopicDescription =
                KafkaFuture.completedFuture(topicDescription);
        when(mockDescribeTopicsResult.topicNameValues())
                .thenReturn(Map.of("test-topic", mockFutureTopicDescription));
        when(mockAdminClient.describeTopics(anyCollection())).thenReturn(mockDescribeTopicsResult);

        // Step 2: Mock Offsets
        ListOffsetsResult.ListOffsetsResultInfo offsetInfo =
                new ListOffsetsResult.ListOffsetsResultInfo(
                        10, // Mock the offset size
                        0, // Leader epoch
                        null // Timestamp
                        );

        ListOffsetsResult mockListOffsetsResult = mock(ListOffsetsResult.class);
        KafkaFuture<Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo>>
                mockOffsetsFuture =
                        KafkaFuture.completedFuture(
                                Map.of(new TopicPartition("test-topic", 0), offsetInfo));
        when(mockListOffsetsResult.all()).thenReturn(mockOffsetsFuture);
        when(mockAdminClient.listOffsets(anyMap())).thenReturn(mockListOffsetsResult);

        // Step 3: Call the `size` method
        long size = queue.size();

        // Step 4: Verify the size is correctly calculated
        assertEquals(10, size); // As we mocked 10 as the offset in the partition
    }

    @Test
    public void testSizeWhenTopicExists() throws Exception {
        // Mock topic description
        TopicDescription topicDescription =
                new TopicDescription(
                        "test-topic",
                        false,
                        List.of(new TopicPartitionInfo(0, null, List.of(), List.of())));

        // Mock offsets
        Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> offsets =
                Map.of(
                        new TopicPartition("test-topic", 0),
                        new ListOffsetsResult.ListOffsetsResultInfo(
                                10L, // Offset value
                                0L, // Log append time (can be 0 if not available)
                                Optional.empty() // Leader epoch (optional, use a default value like
                                // 100)
                                ));

        // Mock AdminClient behavior for describeTopics
        DescribeTopicsResult mockDescribeTopicsResult = mock(DescribeTopicsResult.class);
        when(mockDescribeTopicsResult.topicNameValues())
                .thenReturn(Map.of("test-topic", KafkaFuture.completedFuture(topicDescription)));
        when(mockAdminClient.describeTopics(anyCollection())).thenReturn(mockDescribeTopicsResult);

        // Mock AdminClient behavior for listOffsets
        ListOffsetsResult mockListOffsetsResult = mock(ListOffsetsResult.class);
        when(mockListOffsetsResult.all()).thenReturn(KafkaFuture.completedFuture(offsets));
        when(mockAdminClient.listOffsets(anyMap())).thenReturn(mockListOffsetsResult);

        // Call size
        long size = queue.size();

        // Verify
        assertEquals(10L, size);
    }

    @Test
    public void testSizeWhenTopicDoesNotExist() throws Exception {
        // Mock KafkaFuture to simulate a topic-not-found exception
        KafkaFuture<TopicDescription> failedFuture = mock(KafkaFuture.class);
        when(failedFuture.get())
                .thenThrow(
                        new org.apache.kafka.common.errors.UnknownTopicOrPartitionException(
                                "Topic not found"));

        // Mock DescribeTopicsResult
        DescribeTopicsResult mockDescribeTopicsResult = mock(DescribeTopicsResult.class);
        when(mockDescribeTopicsResult.topicNameValues())
                .thenReturn(Map.of("test-topic", failedFuture));
        when(mockAdminClient.describeTopics(anyCollection())).thenReturn(mockDescribeTopicsResult);

        // Call size
        long size = queue.size();

        // Verify the result
        assertEquals(-1L, size); // Return -1 for non-existent topics
    }

    @Test
    public void testLifecycle() {
        queue.start();
        assertTrue(queue.isRunning());

        queue.stop();
        assertFalse(queue.isRunning());
    }
}
