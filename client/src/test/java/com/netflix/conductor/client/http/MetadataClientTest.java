package com.netflix.conductor.client.http;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;


/**
 *
 * @author fjhaveri
 *
 */
public class MetadataClientTest {
    
    private MetadataClient metadataClient;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void before() {
        this.metadataClient = new MetadataClient();
    }

    @Test
    public void testWorkflowDelete() {
        MetadataClient mockClient = Mockito.mock(MetadataClient.class);
        mockClient.unregisterWorkflowDef("hello", 1);
        verify(mockClient, times(1)).unregisterWorkflowDef(anyString(), any());
    }

    @Test
    public void testWorkflowDeleteThrowException() {
        MetadataClient mockClient = Mockito.mock(MetadataClient.class);
        expectedException.expect(RuntimeException.class);
        expectedException.expectMessage("Invalid Workflow name");
        doThrow(new RuntimeException("Invalid Workflow name")).when(mockClient).unregisterWorkflowDef(anyString(), any());
        mockClient.unregisterWorkflowDef("hello", 1);
    }

    @Test
    public void testWorkflowDeleteVersionMissing() {
        expectedException.expect(NullPointerException.class);
        expectedException.expectMessage("Version cannot be null");
        metadataClient.unregisterWorkflowDef("hello", null);
    }

    @Test
    public void testWorkflowDeleteNameMissing() {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Workflow name cannot be blank");
        metadataClient.unregisterWorkflowDef(null, 1);
    }
}
