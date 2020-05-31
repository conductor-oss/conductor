package com.netflix.conductor.core.events.sqs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.ListQueuesRequest;
import com.amazonaws.services.sqs.model.ListQueuesResult;
import com.netflix.conductor.contribs.queue.sqs.SQSObservableQueue;
import com.netflix.conductor.core.config.Configuration;
import org.junit.Before;
import org.junit.Test;
import rx.schedulers.Schedulers;

import java.util.concurrent.Executors;

public class TestSQSEventQueueProvider {
    private AmazonSQSClient amazonSQSClient;
    private Configuration configuration;

    @Before
    public void setup() {
        amazonSQSClient = mock(AmazonSQSClient.class);
        configuration = mock(Configuration.class);
    }

    @Test
    public void testGetQueueWithDefaultConfiguration() {
        when(configuration.getIntProperty(anyString(), anyInt())).thenAnswer(invocation -> invocation.getArguments()[1]);

        ListQueuesResult listQueuesResult = new ListQueuesResult().withQueueUrls("test_queue_1");
        when(amazonSQSClient.listQueues(any(ListQueuesRequest.class))).thenReturn(listQueuesResult);

        SQSEventQueueProvider sqsEventQueueProvider = new SQSEventQueueProvider(amazonSQSClient, configuration, Schedulers.from(Executors.newSingleThreadExecutor()));
        SQSObservableQueue sqsObservableQueue = (SQSObservableQueue) sqsEventQueueProvider.getQueue("test_queue_1");

        assertNotNull(sqsObservableQueue);
        assertEquals(1, sqsObservableQueue.getBatchSize());
        assertEquals(100, sqsObservableQueue.getPollTimeInMS());
        assertEquals(60, sqsObservableQueue.getVisibilityTimeoutInSeconds());
    }

    @Test
    public void testGetQueueWithCustomConfiguration() {
        when(configuration.getIntProperty(eq("workflow.event.queues.sqs.batchSize"), anyInt())).thenReturn(10);
        when(configuration.getIntProperty(eq("workflow.event.queues.sqs.pollTimeInMS"), anyInt())).thenReturn(50);
        when(configuration.getIntProperty(eq("workflow.event.queues.sqs.visibilityTimeoutInSeconds"), anyInt())).thenReturn(30);

        ListQueuesResult listQueuesResult = new ListQueuesResult().withQueueUrls("test_queue_1");
        when(amazonSQSClient.listQueues(any(ListQueuesRequest.class))).thenReturn(listQueuesResult);

        SQSEventQueueProvider sqsEventQueueProvider = new SQSEventQueueProvider(amazonSQSClient, configuration, Schedulers.from(Executors.newSingleThreadExecutor()));
        SQSObservableQueue sqsObservableQueue = (SQSObservableQueue) sqsEventQueueProvider.getQueue("test_queue_1");

        assertNotNull(sqsObservableQueue);
        assertEquals(10, sqsObservableQueue.getBatchSize());
        assertEquals(50, sqsObservableQueue.getPollTimeInMS());
        assertEquals(30, sqsObservableQueue.getVisibilityTimeoutInSeconds());
    }

}
