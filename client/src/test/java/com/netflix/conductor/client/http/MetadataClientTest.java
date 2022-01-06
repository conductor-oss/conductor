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
package com.netflix.conductor.client.http;

import java.net.URI;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.conductor.client.exception.ConductorClientException;

import com.sun.jersey.api.client.ClientHandler;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.config.ClientConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
public class MetadataClientTest {

    @Mock private ClientHandler clientHandler;

    @Mock private ClientConfig clientConfig;

    private MetadataClient metadataClient;

    @Before
    public void before() {
        this.metadataClient = new MetadataClient(clientConfig, clientHandler);
        this.metadataClient.setRootURI("http://myuri:8080/");
    }

    @Test
    public void testWorkflowDelete() {
        when(clientHandler.handle(
                        argThat(
                                argument ->
                                        argument.getURI()
                                                .equals(
                                                        URI.create(
                                                                "http://myuri:8080/metadata/workflow/test/1")))))
                .thenReturn(mock(ClientResponse.class));
        metadataClient.unregisterWorkflowDef("test", 1);
        verify(clientHandler).handle(any());
    }

    @Test
    public void testWorkflowDeleteThrowException() {
        ClientResponse clientResponse = mock(ClientResponse.class);
        when(clientResponse.getStatus()).thenReturn(404);
        when(clientResponse.getEntity(String.class))
                .thenReturn(
                        "{\n"
                                + "  \"status\": 404,\n"
                                + "  \"message\": \"No such workflow definition: test version: 1\",\n"
                                + "  \"instance\": \"conductor-server\",\n"
                                + "  \"retryable\": false\n"
                                + "}");
        UniformInterfaceException uniformInterfaceException = mock(UniformInterfaceException.class);
        when(uniformInterfaceException.getResponse()).thenReturn(clientResponse);
        when(clientHandler.handle(
                        argThat(
                                argument ->
                                        argument.getURI()
                                                .equals(
                                                        URI.create(
                                                                "http://myuri:8080/metadata/workflow/test/1")))))
                .thenThrow(uniformInterfaceException);
        ConductorClientException exception =
                assertThrows(
                        ConductorClientException.class,
                        () -> metadataClient.unregisterWorkflowDef("test", 1));
        assertEquals("No such workflow definition: test version: 1", exception.getMessage());
        assertEquals(404, exception.getStatus());
    }

    @Test
    public void testWorkflowDeleteVersionMissing() {
        NullPointerException exception =
                assertThrows(
                        NullPointerException.class,
                        () -> metadataClient.unregisterWorkflowDef("test", null));
        assertEquals("Version cannot be null", exception.getMessage());
    }

    @Test
    public void testWorkflowDeleteNameMissing() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> metadataClient.unregisterWorkflowDef(null, 1));
        assertEquals("Workflow name cannot be blank", exception.getMessage());
    }
}
