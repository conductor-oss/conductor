package com.netflix.conductor.asq.eventqueue;

import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.QueueServiceClient;
import com.azure.storage.queue.models.QueueMessageItem;
import com.azure.core.http.rest.PagedIterable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.Assert.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import com.azure.core.util.BinaryData;


public class AzureObservableQueueTest {

    private AzureObservableQueue azureObservableQueue;
    private QueueServiceClient mockQueueServiceClient;
    private QueueClient mockQueueClient;

    @BeforeEach
    public void setUp() {
        mockQueueServiceClient = Mockito.mock(QueueServiceClient.class);
        mockQueueClient =  Mockito.mock(QueueClient.class);

        // Mock the behavior of getQueueClient()
        when(mockQueueServiceClient.getQueueClient("testQueue")).thenReturn(mockQueueClient);

        try {
            // Initialize AzureObservableQueue with the mocked dependencies
            azureObservableQueue = new AzureObservableQueue(
                "testQueue",              
                mockQueueServiceClient,    
                10,                        
                1,                        
                1000L,                    
                null                      
            );
        } catch (Exception e) {
            fail("Exception occurred during setup: " + e.getMessage());
        }
    }

    @Test
    public void testReceiveOneMessage() {
        QueueMessageItem item = mock(QueueMessageItem.class);
        when(item.getMessageId()).thenReturn("id-1");
        when(item.getPopReceipt()).thenReturn("receipt-1");
        when(item.getBody()).thenReturn(BinaryData.fromString("hello"));
    
        when(mockQueueClient.receiveMessages(eq(1)))
        .thenReturn(Mockito.mock(PagedIterable.class, invocation -> List.of(item).iterator())); // ✅
    
        List<com.netflix.conductor.core.events.queue.Message> msgs =
                azureObservableQueue.receiveMessages();
    
        assertNotNull(msgs);
        assertEquals(1, msgs.size());
        assertEquals("id-1", msgs.get(0).getId());
        assertEquals("hello", msgs.get(0).getPayload());
        assertEquals("receipt-1", msgs.get(0).getReceipt());
    }
}