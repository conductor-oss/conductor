package com.netflix.conductor.contribs.queue.nats;

import io.nats.client.*;
import io.nats.client.JetStreamApiException;
import io.nats.client.MessageHandler;
import io.nats.client.JetStreamManagement;
import io.nats.client.api.ApiResponse;
import io.nats.client.api.AckPolicy;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.ConsumerInfo;
import io.nats.client.api.DeliverPolicy;
import io.nats.client.api.RetentionPolicy;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.api.StreamInfo;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import com.netflix.conductor.contribs.queue.nats.config.JetStreamProperties;
import com.netflix.conductor.core.config.ConductorProperties;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

import static org.junit.Assert.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import org.mockito.ArgumentCaptor;

public class JetStreamObservableQueueTest {

    @Mock
    private JetStreamManagement jsm;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private StreamInfo streamInfo;

    @Mock
    private ConductorProperties conductorProperties;

    @Mock
    private JetStreamProperties jetStreamProperties;

    @Mock
    private Connection natsConnection;

    @Mock
    private JetStream jetStream;

    @Mock
    private Dispatcher dispatcher;

    @Mock
    private JetStreamSubscription subscription;

    private JetStreamObservableQueue queue;
    private Method createStreamMethod;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        when(conductorProperties.getStack()).thenReturn("test-stack");
        when(conductorProperties.getAppId()).thenReturn("test-app");

        when(jetStreamProperties.getReplicas()).thenReturn(1);
        when(jetStreamProperties.getStreamMaxBytes()).thenReturn(1024L * 1024 * 100); // 100MB
        when(jetStreamProperties.getStreamStorageType()).thenReturn("Memory");

        queue = new JetStreamObservableQueue(
            conductorProperties,
            jetStreamProperties,
            "test-queue-type",
            "test-subject",
            null,
            eventPublisher
        );

        createStreamMethod = JetStreamObservableQueue.class.getDeclaredMethod("createStream", JetStreamManagement.class);
        createStreamMethod.setAccessible(true);
    }

    @Test
    public void testCreateStreamUsesSanitizedStreamName() throws Exception {
        when(conductorProperties.getStack()).thenReturn(null);
        when(conductorProperties.getAppId()).thenReturn("testapp");
        when(jetStreamProperties.getReplicas()).thenReturn(1);
        when(jetStreamProperties.getStreamMaxBytes()).thenReturn(1024L * 1024 * 100);
        when(jetStreamProperties.getStreamStorageType()).thenReturn("Memory");

        JetStreamObservableQueue testQueue = new JetStreamObservableQueue(
            conductorProperties,
            jetStreamProperties,
            "test",
            "workflow.task.*.status:workers",
            null,
            eventPublisher
        );

        when(jsm.addStream(any(StreamConfiguration.class))).thenReturn(streamInfo);

        java.lang.reflect.Method createStreamMethod = JetStreamObservableQueue.class.getDeclaredMethod("createStream", JetStreamManagement.class);
        createStreamMethod.setAccessible(true);
        createStreamMethod.invoke(testQueue, jsm);

        verify(jsm).addStream(argThat(config -> {
            String expectedStreamName = "TESTAPP_JSM_NOTIFY_WORKFLOW_TASK_ANY_STATUS";
            assertEquals("Stream name should be sanitized", expectedStreamName, config.getName());

            assertTrue("Subjects should contain original subject",
                config.getSubjects().contains("testapp_jsm_notify_workflow.task.*.status"));

            assertEquals("Replicas should match properties", 1, config.getReplicas());

            assertEquals("Retention policy should be Limits",
                RetentionPolicy.Limits, config.getRetentionPolicy());

            assertEquals("Max bytes should match properties",
                1024L * 1024 * 100, config.getMaxBytes());

            assertEquals("Storage type should be Memory",
                StorageType.Memory, config.getStorageType());

            return true;
        }));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    public void testCreateStreamIOException() throws Exception {
        // Arrange
        IOException ioException = new IOException("Network error");
        when(jsm.addStream(any(StreamConfiguration.class))).thenThrow(ioException);

        // Act
        createStreamMethod.invoke(queue, jsm);

        // Assert
        verify(jsm).addStream(any(StreamConfiguration.class));
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    public void testCreateStreamJetStreamApiException() throws Exception {
        // Arrange - Create a proper JetStreamApiException with Error
        io.nats.client.api.Error mockError = mock(io.nats.client.api.Error.class);
        when(mockError.toString()).thenReturn("API error");
        JetStreamApiException apiException = new JetStreamApiException(mockError);
        when(jsm.addStream(any(StreamConfiguration.class))).thenThrow(apiException);

        // Act
        createStreamMethod.invoke(queue, jsm);

        // Assert
        verify(jsm).addStream(any(StreamConfiguration.class));
        verify(eventPublisher).publishEvent(any());
    }


    @Test
    public void testStreamNameFromSubjectMethod() throws Exception {
        Method method = JetStreamObservableQueue.class.getDeclaredMethod("streamNameFromSubject", String.class);
        method.setAccessible(true);

        String result1 = (String) method.invoke(null, "conductor.workflow.task.status");
        assertEquals("CONDUCTOR_WORKFLOW_TASK_STATUS", result1);

        String result2 = (String) method.invoke(null, "events.*");
        assertEquals("EVENTS_ANY", result2);

        String result3 = (String) method.invoke(null, "events.>");
        assertEquals("EVENTS_ALL", result3);

        // Test mixed replacements
        String result4 = (String) method.invoke(null, "test.*.status.>");
        assertEquals("TEST_ANY_STATUS_ALL", result4);

        String result5 = (String) method.invoke(null, "simple_name");
        assertEquals("SIMPLE_NAME", result5);

        String result6 = (String) method.invoke(null, "");
        assertEquals("", result6);
    }

    @Test
    public void testStreamNameFromSubjectComprehensive() throws Exception {
        Method method = JetStreamObservableQueue.class.getDeclaredMethod("streamNameFromSubject", String.class);
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
            assertEquals("Failed for input: " + input, expected, actual);
        }
    }

    @Test
    public void testStreamNameFromSubjectNullInput() throws Exception {
        Method method = JetStreamObservableQueue.class.getDeclaredMethod("streamNameFromSubject", String.class);
        method.setAccessible(true);

        try {
            method.invoke(null, (String) null);
            fail("Should have thrown NullPointerException for null input");
        } catch (Exception e) {
            assertTrue(e.getCause() instanceof NullPointerException ||
                      e.getCause() instanceof IllegalArgumentException);
        }
    }

    @Test
    public void testStreamNameFromSubjectEdgeCases() throws Exception {
        Method method = JetStreamObservableQueue.class.getDeclaredMethod("streamNameFromSubject", String.class);
        method.setAccessible(true);

        String result1 = (String) method.invoke(null, "test..*..>");
        assertEquals("TEST__ANY__ALL", result1);

        String result2 = (String) method.invoke(null, "Test.Subject.*.>");
        assertEquals("TEST_SUBJECT_ANY_ALL", result2);

        String result3 = (String) method.invoke(null, "app_1.v2.*.logs.>");
        assertEquals("APP_1_V2_ANY_LOGS_ALL", result3);
    }




    @Test
    public void testSubscribeMethodWorksCorrectly() throws Exception {
        when(conductorProperties.getStack()).thenReturn(null);
        when(conductorProperties.getAppId()).thenReturn("testapp");

        JetStreamObservableQueue queue = new JetStreamObservableQueue(
            conductorProperties,
            jetStreamProperties,
            "test",
            "order.workflow.*.completed:processors",
            null,
            eventPublisher
        );

        Field streamNameField = JetStreamObservableQueue.class.getDeclaredField("streamName");
        streamNameField.setAccessible(true);
        String expectedStreamName = (String) streamNameField.get(queue);

        when(natsConnection.jetStream()).thenReturn(jetStream);
        when(natsConnection.createDispatcher()).thenReturn(dispatcher);
        when(jetStream.subscribe(any(String.class), any(String.class), any(Dispatcher.class), any(MessageHandler.class), eq(false), any(PushSubscribeOptions.class)))
            .thenReturn(subscription);

        ConsumerConfiguration consumerConfig = ConsumerConfiguration.builder()
            .name("test-consumer")
            .durable("test-consumer")
            .build();

        java.lang.reflect.Method subscribeMethod = JetStreamObservableQueue.class.getDeclaredMethod(
            "subscribe", Connection.class, ConsumerConfiguration.class);
        subscribeMethod.setAccessible(true);
        subscribeMethod.invoke(queue, natsConnection, consumerConfig);

        verify(natsConnection).jetStream();
        verify(natsConnection).createDispatcher();

        verify(jetStream).subscribe(
            eq("testapp_jsm_notify_order.workflow.*.completed"),
            eq("processors"),
            eq(dispatcher),
            any(MessageHandler.class),
            eq(false),
            any(PushSubscribeOptions.class)
        );

        Field subField = JetStreamObservableQueue.class.getDeclaredField("sub");
        subField.setAccessible(true);
        Object actualSub = subField.get(queue);
        assertEquals("Subscription should be stored in sub field", subscription, actualSub);

        Field runningField = JetStreamObservableQueue.class.getDeclaredField("running");
        runningField.setAccessible(true);
        AtomicBoolean running = (AtomicBoolean) runningField.get(queue);
        assertTrue("Running flag should be set to true", running.get());

        // 6. Verify stream name sanitization worked
        assertEquals("Stream name should be properly sanitized", "TESTAPP_JSM_NOTIFY_ORDER_WORKFLOW_ANY_COMPLETED", expectedStreamName);
    }

    @Test
    public void testSubscribeMethodMessageHandler() throws Exception {
        when(conductorProperties.getStack()).thenReturn(null);
        when(conductorProperties.getAppId()).thenReturn("testapp");

        JetStreamObservableQueue queue = new JetStreamObservableQueue(
            conductorProperties,
            jetStreamProperties,
            "test",
            "test.subject:workers",
            null,
            eventPublisher
        );

        Field streamNameField = JetStreamObservableQueue.class.getDeclaredField("streamName");
        Field subjectField = JetStreamObservableQueue.class.getDeclaredField("subject");
        Field queueGroupField = JetStreamObservableQueue.class.getDeclaredField("queueGroup");

        streamNameField.setAccessible(true);
        subjectField.setAccessible(true);
        queueGroupField.setAccessible(true);

        String actualStreamName = (String) streamNameField.get(queue);
        String actualSubject = (String) subjectField.get(queue);
        String actualQueueGroup = (String) queueGroupField.get(queue);

        assertEquals("Subject should include prefix", "testapp_jsm_notify_test.subject", actualSubject);
        assertEquals("Queue group should be extracted from subject:queueGroup", "workers", actualQueueGroup);
        assertEquals("Stream name should be sanitized", "TESTAPP_JSM_NOTIFY_TEST_SUBJECT", actualStreamName);

        // Mock the subscription setup
        when(natsConnection.jetStream()).thenReturn(jetStream);
        when(natsConnection.createDispatcher()).thenReturn(dispatcher);

        ArgumentCaptor<MessageHandler> messageHandlerCaptor = ArgumentCaptor.forClass(MessageHandler.class);

        when(jetStream.subscribe(any(String.class), any(String.class), any(Dispatcher.class),
                messageHandlerCaptor.capture(), eq(false), any(PushSubscribeOptions.class)))
            .thenReturn(subscription);

        ConsumerConfiguration consumerConfig = ConsumerConfiguration.builder()
            .name("test-consumer")
            .durable("test-consumer")
            .build();

        java.lang.reflect.Method subscribeMethod = JetStreamObservableQueue.class.getDeclaredMethod(
            "subscribe", Connection.class, ConsumerConfiguration.class);
        subscribeMethod.setAccessible(true);
        subscribeMethod.invoke(queue, natsConnection, consumerConfig);

        verify(jetStream).subscribe(
            eq(actualSubject),
            eq(actualQueueGroup),
            eq(dispatcher),
            messageHandlerCaptor.capture(),
            eq(false),
            any(PushSubscribeOptions.class)
        );

        Field subField = JetStreamObservableQueue.class.getDeclaredField("sub");
        Field runningField = JetStreamObservableQueue.class.getDeclaredField("running");
        subField.setAccessible(true);
        runningField.setAccessible(true);

        Object actualSub = subField.get(queue);
        AtomicBoolean running = (AtomicBoolean) runningField.get(queue);

        assertEquals("Subscription should be stored in sub field", subscription, actualSub);
        assertTrue("Running flag should be set to true", running.get());

        Message mockMessage = mock(Message.class);
        String testPayload = "test message data";
        when(mockMessage.getData()).thenReturn(testPayload.getBytes());

        Field messagesField = JetStreamObservableQueue.class.getDeclaredField("messages");
        messagesField.setAccessible(true);
        BlockingQueue<Message> messages = (BlockingQueue<Message>) messagesField.get(queue);

        MessageHandler capturedHandler = messageHandlerCaptor.getValue();
        capturedHandler.onMessage(mockMessage);

        assertEquals("One message should be in the queue", 1, messages.size());

        JsmMessage processedMessage = (JsmMessage) messages.poll();
        assertNotNull("Message should be processed", processedMessage);
        assertEquals("Message payload should match", testPayload, processedMessage.getPayload());
        assertNotNull("Message should have JSM message set", processedMessage.getJsmMsg());
        assertNotNull("Message should have ID generated", processedMessage.getId());
    }

    @Test
    public void testCreateConsumerUsesSanitizedStreamName() throws Exception {
        // Test that createConsumer method uses sanitized streamName as first parameter to addOrUpdateConsumer

        when(conductorProperties.getStack()).thenReturn(null);
        when(conductorProperties.getAppId()).thenReturn("test");
        when(jetStreamProperties.getDurableName()).thenReturn("test-durable");
        when(jetStreamProperties.getAckWait()).thenReturn(java.time.Duration.ofSeconds(30));
        when(jetStreamProperties.getMaxDeliver()).thenReturn(3);
        when(jetStreamProperties.getMaxAckPending()).thenReturn(1000L);

        // Create queue with subject containing special chars that need sanitization
        JetStreamObservableQueue queue = new JetStreamObservableQueue(
            conductorProperties,
            jetStreamProperties,
            "test",
            "order.workflow.*.completed:processors", // Subject with * that gets sanitized
            null,
            eventPublisher
        );

        // Mock the consumer creation
        ConsumerInfo mockConsumerInfo = mock(ConsumerInfo.class);
        when(jsm.addOrUpdateConsumer(any(String.class), any(ConsumerConfiguration.class)))
            .thenReturn(mockConsumerInfo);

        // Act - Call createConsumer method directly
        java.lang.reflect.Method createConsumerMethod = JetStreamObservableQueue.class.getDeclaredMethod("createConsumer", JetStreamManagement.class);
        createConsumerMethod.setAccessible(true);
        ConsumerConfiguration result = (ConsumerConfiguration) createConsumerMethod.invoke(queue, jsm);

        // Assert - Verify addOrUpdateConsumer was called with the SANITIZED stream name
        // This is critical: the consumer must be created on the sanitized stream name, not the original subject
        verify(jsm).addOrUpdateConsumer(eq("TEST_JSM_NOTIFY_ORDER_WORKFLOW_ANY_COMPLETED"), any(ConsumerConfiguration.class));

        // Verify the returned ConsumerConfiguration has ALL expected properties
        assertNotNull("Should return a consumer configuration", result);
        assertEquals("Consumer name should match durable name", "test-durable", result.getName());
        assertEquals("Deliver group should match queue group", "processors", result.getDeliverGroup());
        assertEquals("Durable name should match", "test-durable", result.getDurable());
        assertEquals("Ack wait should match properties", java.time.Duration.ofSeconds(30), result.getAckWait());
        assertEquals("Max deliver should match properties", 3, result.getMaxDeliver());
        assertEquals("Max ack pending should match properties", 1000L, result.getMaxAckPending());
        assertEquals("Ack policy should be Explicit", AckPolicy.Explicit, result.getAckPolicy());
        assertEquals("Deliver policy should be New", DeliverPolicy.New, result.getDeliverPolicy());
        assertEquals("Deliver subject should be subject + '-deliver'",
            "test_jsm_notify_order.workflow.*.completed-deliver", result.getDeliverSubject());
    }

    @Test
    public void testConstructorSetsSanitizedStreamName() throws Exception {
        // Test that the constructor properly sets streamName using streamNameFromSubject

        when(conductorProperties.getStack()).thenReturn(null);
        when(conductorProperties.getAppId()).thenReturn("myapp");

        // Create queue with various special characters that need sanitization
        JetStreamObservableQueue queue = new JetStreamObservableQueue(
            conductorProperties,
            jetStreamProperties,
            "test",
            "workflow.task.*.status.>:workers", // Subject with *, >, and . that need sanitization
            null,
            eventPublisher
        );

        // Verify the constructor set streamName correctly
        Field streamNameField = JetStreamObservableQueue.class.getDeclaredField("streamName");
        streamNameField.setAccessible(true);
        String actualStreamName = (String) streamNameField.get(queue);

        // The constructor should have called streamNameFromSubject and set the result
        String expectedStreamName = "MYAPP_JSM_NOTIFY_WORKFLOW_TASK_ANY_STATUS_ALL";
        assertEquals("Constructor should set sanitized stream name", expectedStreamName, actualStreamName);

        // Verify the sanitization rules
        assertFalse("Stream name should not contain dots", actualStreamName.contains("."));
        assertFalse("Stream name should not contain asterisks", actualStreamName.contains("*"));
        assertFalse("Stream name should not contain >", actualStreamName.contains(">"));
        assertTrue("Stream name should contain ANY for *", actualStreamName.contains("ANY"));
        assertTrue("Stream name should contain ALL for >", actualStreamName.contains("ALL"));
        assertTrue("Stream name should be uppercase", actualStreamName.equals(actualStreamName.toUpperCase()));
    }

}
