/*
 * Copyright 2026 Conductor Authors.
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
package com.netflix.conductor.contribs.queue.nats;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import com.netflix.conductor.contribs.queue.nats.config.JetStreamProperties;
import com.netflix.conductor.core.config.ConductorProperties;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;
import io.nats.client.MessageHandler;
import io.nats.client.PushSubscribeOptions;
import io.nats.client.api.AckPolicy;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.ConsumerInfo;
import io.nats.client.api.DeliverPolicy;
import io.nats.client.api.RetentionPolicy;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.api.StreamInfo;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class JetStreamObservableQueueTest {

    @Mock private JetStreamManagement jsm;

    @Mock private ApplicationEventPublisher eventPublisher;

    @Mock private StreamInfo streamInfo;

    @Mock private ConductorProperties conductorProperties;

    @Mock private JetStreamProperties jetStreamProperties;

    @Mock private Connection natsConnection;

    @Mock private JetStream jetStream;

    @Mock private Dispatcher dispatcher;

    @Mock private JetStreamSubscription subscription;

    private JetStreamObservableQueue queue;
    private Method createStreamMethod;

    @BeforeEach
    public void setUp() throws Exception {
        when(conductorProperties.getStack()).thenReturn("test-stack");
        when(conductorProperties.getAppId()).thenReturn("test-app");

        when(jetStreamProperties.getReplicas()).thenReturn(1);
        when(jetStreamProperties.getStreamMaxBytes()).thenReturn(1024L * 1024 * 100); // 100MB
        when(jetStreamProperties.getStreamStorageType()).thenReturn("Memory");

        queue =
                new JetStreamObservableQueue(
                        conductorProperties,
                        jetStreamProperties,
                        "test-queue-type",
                        "test-subject",
                        null,
                        eventPublisher);

        createStreamMethod =
                JetStreamObservableQueue.class.getDeclaredMethod(
                        "createStream", JetStreamManagement.class);
        createStreamMethod.setAccessible(true);
    }

    @Test
    public void testCreateStreamUsesSanitizedStreamName() throws Exception {
        when(conductorProperties.getStack()).thenReturn(null);
        when(conductorProperties.getAppId()).thenReturn("testapp");
        when(jetStreamProperties.getReplicas()).thenReturn(1);
        when(jetStreamProperties.getStreamMaxBytes()).thenReturn(1024L * 1024 * 100);
        when(jetStreamProperties.getStreamStorageType()).thenReturn("Memory");

        JetStreamObservableQueue testQueue =
                new JetStreamObservableQueue(
                        conductorProperties,
                        jetStreamProperties,
                        "test",
                        "workflow.task.*.status:workers",
                        null,
                        eventPublisher);

        when(jsm.addStream(any(StreamConfiguration.class))).thenReturn(streamInfo);

        Method createStreamMethod =
                JetStreamObservableQueue.class.getDeclaredMethod(
                        "createStream", JetStreamManagement.class);
        createStreamMethod.setAccessible(true);
        createStreamMethod.invoke(testQueue, jsm);

        verify(jsm)
                .addStream(
                        argThat(
                                config -> {
                                    String expectedStreamName =
                                            "TESTAPP_JSM_NOTIFY_WORKFLOW_TASK_ANY_STATUS";
                                    assertEquals(
                                            expectedStreamName,
                                            config.getName(),
                                            "Stream name should be sanitized");

                                    assertTrue(
                                            config.getSubjects()
                                                    .contains(
                                                            "testapp_jsm_notify_workflow.task.*.status"),
                                            "Subjects should contain original subject");

                                    assertEquals(
                                            1,
                                            config.getReplicas(),
                                            "Replicas should match properties");

                                    assertEquals(
                                            RetentionPolicy.Limits,
                                            config.getRetentionPolicy(),
                                            "Retention policy should be Limits");

                                    assertEquals(
                                            1024L * 1024 * 100,
                                            config.getMaxBytes(),
                                            "Max bytes should match properties");

                                    assertEquals(
                                            StorageType.Memory,
                                            config.getStorageType(),
                                            "Storage type should be Memory");

                                    return true;
                                }));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    public void testCreateStreamIOException() throws Exception {
        IOException ioException = new IOException("Network error");
        when(jsm.addStream(any(StreamConfiguration.class))).thenThrow(ioException);

        createStreamMethod.invoke(queue, jsm);

        verify(jsm).addStream(any(StreamConfiguration.class));
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    public void testCreateStreamJetStreamApiException() throws Exception {
        io.nats.client.api.Error mockError = mock(io.nats.client.api.Error.class);
        when(mockError.toString()).thenReturn("API error");
        JetStreamApiException apiException = new JetStreamApiException(mockError);
        when(jsm.addStream(any(StreamConfiguration.class))).thenThrow(apiException);

        createStreamMethod.invoke(queue, jsm);

        verify(jsm).addStream(any(StreamConfiguration.class));
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    public void testStreamNameFromSubjectMethod() throws Exception {
        Method method =
                JetStreamObservableQueue.class.getDeclaredMethod(
                        "streamNameFromSubject", String.class);
        method.setAccessible(true);

        assertEquals(
                "CONDUCTOR_WORKFLOW_TASK_STATUS",
                method.invoke(null, "conductor.workflow.task.status"));
        assertEquals("EVENTS_ANY", method.invoke(null, "events.*"));
        assertEquals("EVENTS_ALL", method.invoke(null, "events.>"));
        assertEquals("TEST_ANY_STATUS_ALL", method.invoke(null, "test.*.status.>"));
        assertEquals("SIMPLE_NAME", method.invoke(null, "simple_name"));
        assertEquals("", method.invoke(null, ""));
    }

    @Test
    public void testStreamNameFromSubjectComprehensive() throws Exception {
        Method method =
                JetStreamObservableQueue.class.getDeclaredMethod(
                        "streamNameFromSubject", String.class);
        method.setAccessible(true);

        String[][] testCases = {
            {"conductor.workflow.task.status", "CONDUCTOR_WORKFLOW_TASK_STATUS"},
            {"events.user.login", "EVENTS_USER_LOGIN"},
            {"orders.*", "ORDERS_ANY"},
            {"logs.>", "LOGS_ALL"},
            {"metrics.*.count.>", "METRICS_ANY_COUNT_ALL"},
            {"simple", "SIMPLE"},
            {"a.b.c.d.e.f", "A_B_C_D_E_F"},
            {"test.*.middle.>", "TEST_ANY_MIDDLE_ALL"},
            {"*.wildcard.*.>", "ANY_WILDCARD_ANY_ALL"}
        };

        for (String[] testCase : testCases) {
            String input = testCase[0];
            String expected = testCase[1];
            String actual = (String) method.invoke(null, input);
            assertEquals(expected, actual, "Failed for input: " + input);
        }
    }

    @Test
    public void testStreamNameFromSubjectNullInput() throws Exception {
        Method method =
                JetStreamObservableQueue.class.getDeclaredMethod(
                        "streamNameFromSubject", String.class);
        method.setAccessible(true);

        Exception ex = assertThrows(Exception.class, () -> method.invoke(null, (String) null));
        assertTrue(
                ex.getCause() instanceof NullPointerException
                        || ex.getCause() instanceof IllegalArgumentException);
    }

    @Test
    public void testStreamNameFromSubjectEdgeCases() throws Exception {
        Method method =
                JetStreamObservableQueue.class.getDeclaredMethod(
                        "streamNameFromSubject", String.class);
        method.setAccessible(true);

        assertEquals("TEST__ANY__ALL", method.invoke(null, "test..*..>"));
        assertEquals("TEST_SUBJECT_ANY_ALL", method.invoke(null, "Test.Subject.*.>"));
        assertEquals("APP_1_V2_ANY_LOGS_ALL", method.invoke(null, "app_1.v2.*.logs.>"));
    }

    @Test
    public void testSubscribeMethodWorksCorrectly() throws Exception {
        when(conductorProperties.getStack()).thenReturn(null);
        when(conductorProperties.getAppId()).thenReturn("testapp");

        JetStreamObservableQueue queue =
                new JetStreamObservableQueue(
                        conductorProperties,
                        jetStreamProperties,
                        "test",
                        "order.workflow.*.completed:processors",
                        null,
                        eventPublisher);

        Field streamNameField = JetStreamObservableQueue.class.getDeclaredField("streamName");
        streamNameField.setAccessible(true);
        String expectedStreamName = (String) streamNameField.get(queue);

        when(natsConnection.jetStream()).thenReturn(jetStream);
        when(natsConnection.createDispatcher()).thenReturn(dispatcher);
        when(jetStream.subscribe(
                        any(String.class),
                        any(String.class),
                        any(Dispatcher.class),
                        any(MessageHandler.class),
                        eq(false),
                        any(PushSubscribeOptions.class)))
                .thenReturn(subscription);

        ConsumerConfiguration consumerConfig =
                ConsumerConfiguration.builder()
                        .name("test-consumer")
                        .durable("test-consumer")
                        .build();

        Method subscribeMethod =
                JetStreamObservableQueue.class.getDeclaredMethod(
                        "subscribe", Connection.class, ConsumerConfiguration.class);
        subscribeMethod.setAccessible(true);
        subscribeMethod.invoke(queue, natsConnection, consumerConfig);

        verify(natsConnection).jetStream();
        verify(natsConnection).createDispatcher();

        verify(jetStream)
                .subscribe(
                        eq("testapp_jsm_notify_order.workflow.*.completed"),
                        eq("processors"),
                        eq(dispatcher),
                        any(MessageHandler.class),
                        eq(false),
                        any(PushSubscribeOptions.class));

        Field subField = JetStreamObservableQueue.class.getDeclaredField("sub");
        subField.setAccessible(true);
        assertEquals(
                subscription, subField.get(queue), "Subscription should be stored in sub field");

        Field runningField = JetStreamObservableQueue.class.getDeclaredField("running");
        runningField.setAccessible(true);
        AtomicBoolean running = (AtomicBoolean) runningField.get(queue);
        assertTrue(running.get(), "Running flag should be set to true");

        assertEquals(
                "TESTAPP_JSM_NOTIFY_ORDER_WORKFLOW_ANY_COMPLETED",
                expectedStreamName,
                "Stream name should be properly sanitized");
    }

    @Test
    public void testSubscribeMethodMessageHandler() throws Exception {
        when(conductorProperties.getStack()).thenReturn(null);
        when(conductorProperties.getAppId()).thenReturn("testapp");

        JetStreamObservableQueue queue =
                new JetStreamObservableQueue(
                        conductorProperties,
                        jetStreamProperties,
                        "test",
                        "test.subject:workers",
                        null,
                        eventPublisher);

        Field streamNameField = JetStreamObservableQueue.class.getDeclaredField("streamName");
        Field subjectField = JetStreamObservableQueue.class.getDeclaredField("subject");
        Field queueGroupField = JetStreamObservableQueue.class.getDeclaredField("queueGroup");

        streamNameField.setAccessible(true);
        subjectField.setAccessible(true);
        queueGroupField.setAccessible(true);

        String actualStreamName = (String) streamNameField.get(queue);
        String actualSubject = (String) subjectField.get(queue);
        String actualQueueGroup = (String) queueGroupField.get(queue);

        assertEquals(
                "testapp_jsm_notify_test.subject", actualSubject, "Subject should include prefix");
        assertEquals(
                "workers",
                actualQueueGroup,
                "Queue group should be extracted from subject:queueGroup");
        assertEquals(
                "TESTAPP_JSM_NOTIFY_TEST_SUBJECT",
                actualStreamName,
                "Stream name should be sanitized");

        when(natsConnection.jetStream()).thenReturn(jetStream);
        when(natsConnection.createDispatcher()).thenReturn(dispatcher);

        ArgumentCaptor<MessageHandler> messageHandlerCaptor =
                ArgumentCaptor.forClass(MessageHandler.class);

        when(jetStream.subscribe(
                        any(String.class),
                        any(String.class),
                        any(Dispatcher.class),
                        messageHandlerCaptor.capture(),
                        eq(false),
                        any(PushSubscribeOptions.class)))
                .thenReturn(subscription);

        ConsumerConfiguration consumerConfig =
                ConsumerConfiguration.builder()
                        .name("test-consumer")
                        .durable("test-consumer")
                        .build();

        Method subscribeMethod =
                JetStreamObservableQueue.class.getDeclaredMethod(
                        "subscribe", Connection.class, ConsumerConfiguration.class);
        subscribeMethod.setAccessible(true);
        subscribeMethod.invoke(queue, natsConnection, consumerConfig);

        verify(jetStream)
                .subscribe(
                        eq(actualSubject),
                        eq(actualQueueGroup),
                        eq(dispatcher),
                        messageHandlerCaptor.capture(),
                        eq(false),
                        any(PushSubscribeOptions.class));

        Field subField = JetStreamObservableQueue.class.getDeclaredField("sub");
        Field runningField = JetStreamObservableQueue.class.getDeclaredField("running");
        subField.setAccessible(true);
        runningField.setAccessible(true);

        assertEquals(
                subscription, subField.get(queue), "Subscription should be stored in sub field");
        assertTrue(
                ((AtomicBoolean) runningField.get(queue)).get(),
                "Running flag should be set to true");

        Message mockMessage = mock(Message.class);
        String testPayload = "test message data";
        when(mockMessage.getData()).thenReturn(testPayload.getBytes());

        Field messagesField = JetStreamObservableQueue.class.getDeclaredField("messages");
        messagesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        BlockingQueue<Message> messages = (BlockingQueue<Message>) messagesField.get(queue);

        MessageHandler capturedHandler = messageHandlerCaptor.getValue();
        capturedHandler.onMessage(mockMessage);

        assertEquals(1, messages.size(), "One message should be in the queue");

        JsmMessage processedMessage = (JsmMessage) messages.poll();
        assertNotNull(processedMessage, "Message should be processed");
        assertEquals(testPayload, processedMessage.getPayload(), "Message payload should match");
        assertNotNull(processedMessage.getJsmMsg(), "Message should have JSM message set");
        assertNotNull(processedMessage.getId(), "Message should have ID generated");
    }

    @Test
    public void testCreateConsumerUsesSanitizedStreamName() throws Exception {
        when(conductorProperties.getStack()).thenReturn(null);
        when(conductorProperties.getAppId()).thenReturn("test");
        when(jetStreamProperties.getDurableName()).thenReturn("test-durable");
        when(jetStreamProperties.getAckWait()).thenReturn(java.time.Duration.ofSeconds(30));
        when(jetStreamProperties.getMaxDeliver()).thenReturn(3);
        when(jetStreamProperties.getMaxAckPending()).thenReturn(1000L);

        JetStreamObservableQueue queue =
                new JetStreamObservableQueue(
                        conductorProperties,
                        jetStreamProperties,
                        "test",
                        "order.workflow.*.completed:processors",
                        null,
                        eventPublisher);

        ConsumerInfo mockConsumerInfo = mock(ConsumerInfo.class);
        when(jsm.addOrUpdateConsumer(any(String.class), any(ConsumerConfiguration.class)))
                .thenReturn(mockConsumerInfo);

        Method createConsumerMethod =
                JetStreamObservableQueue.class.getDeclaredMethod(
                        "createConsumer", JetStreamManagement.class);
        createConsumerMethod.setAccessible(true);
        ConsumerConfiguration result =
                (ConsumerConfiguration) createConsumerMethod.invoke(queue, jsm);

        verify(jsm)
                .addOrUpdateConsumer(
                        eq("TEST_JSM_NOTIFY_ORDER_WORKFLOW_ANY_COMPLETED"),
                        any(ConsumerConfiguration.class));

        assertNotNull(result, "Should return a consumer configuration");
        assertEquals("test-durable", result.getName(), "Consumer name should match durable name");
        assertEquals(
                "processors", result.getDeliverGroup(), "Deliver group should match queue group");
        assertEquals("test-durable", result.getDurable(), "Durable name should match");
        assertEquals(
                java.time.Duration.ofSeconds(30),
                result.getAckWait(),
                "Ack wait should match properties");
        assertEquals(3, result.getMaxDeliver(), "Max deliver should match properties");
        assertEquals(1000L, result.getMaxAckPending(), "Max ack pending should match properties");
        assertEquals(AckPolicy.Explicit, result.getAckPolicy(), "Ack policy should be Explicit");
        assertEquals(DeliverPolicy.New, result.getDeliverPolicy(), "Deliver policy should be New");
        assertEquals(
                "test_jsm_notify_order.workflow.*.completed-deliver",
                result.getDeliverSubject(),
                "Deliver subject should be subject + '-deliver'");
    }

    @Test
    public void testConstructorSetsSanitizedStreamName() throws Exception {
        when(conductorProperties.getStack()).thenReturn(null);
        when(conductorProperties.getAppId()).thenReturn("myapp");

        JetStreamObservableQueue queue =
                new JetStreamObservableQueue(
                        conductorProperties,
                        jetStreamProperties,
                        "test",
                        "workflow.task.*.status.>:workers",
                        null,
                        eventPublisher);

        Field streamNameField = JetStreamObservableQueue.class.getDeclaredField("streamName");
        streamNameField.setAccessible(true);
        String actualStreamName = (String) streamNameField.get(queue);

        assertEquals(
                "MYAPP_JSM_NOTIFY_WORKFLOW_TASK_ANY_STATUS_ALL",
                actualStreamName,
                "Constructor should set sanitized stream name");

        assertFalse(actualStreamName.contains("."), "Stream name should not contain dots");
        assertFalse(actualStreamName.contains("*"), "Stream name should not contain asterisks");
        assertFalse(actualStreamName.contains(">"), "Stream name should not contain >");
        assertTrue(actualStreamName.contains("ANY"), "Stream name should contain ANY for *");
        assertTrue(actualStreamName.contains("ALL"), "Stream name should contain ALL for >");
        assertTrue(
                actualStreamName.equals(actualStreamName.toUpperCase()),
                "Stream name should be uppercase");
    }
}
