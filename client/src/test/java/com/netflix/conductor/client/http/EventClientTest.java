/*
 * Copyright 2022 Netflix, Inc.
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

import com.netflix.conductor.common.metadata.events.EventHandler;

import com.sun.jersey.api.client.ClientHandler;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.config.ClientConfig;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
public class EventClientTest {

    @Mock private ClientHandler clientHandler;

    @Mock private ClientConfig clientConfig;

    private EventClient eventClient;

    @Before
    public void before() {
        this.eventClient = new EventClient(clientConfig, clientHandler);
        this.eventClient.setRootURI("http://myuri:8080/");
    }

    @Test
    public void testRegisterEventHandler() {
        EventHandler eventHandler = mock(EventHandler.class);
        when(clientHandler.handle(
                        argThat(
                                argument ->
                                        argument.getURI()
                                                .equals(URI.create("http://myuri:8080/event")))))
                .thenReturn(mock(ClientResponse.class));
        eventClient.registerEventHandler(eventHandler);
        verify(clientHandler).handle(any());
    }

    @Test
    public void testUpdateEventHandler() {
        EventHandler eventHandler = mock(EventHandler.class);
        when(clientHandler.handle(
                        argThat(
                                argument ->
                                        argument.getURI()
                                                .equals(URI.create("http://myuri:8080/event")))))
                .thenReturn(mock(ClientResponse.class));
        eventClient.updateEventHandler(eventHandler);
        verify(clientHandler).handle(any());
    }

    @Test
    public void testGetEventHandlers() {
        when(clientHandler.handle(
                        argThat(
                                argument ->
                                        argument.getURI()
                                                .equals(
                                                        URI.create(
                                                                "http://myuri:8080/event/test?activeOnly=true")))))
                .thenReturn(mock(ClientResponse.class));
        eventClient.getEventHandlers("test", true);
        verify(clientHandler).handle(any());
    }

    @Test
    public void testUnregisterEventHandler() {
        when(clientHandler.handle(
                        argThat(
                                argument ->
                                        argument.getURI()
                                                .equals(
                                                        URI.create(
                                                                "http://myuri:8080/event/test")))))
                .thenReturn(mock(ClientResponse.class));
        eventClient.unregisterEventHandler("test");
        verify(clientHandler).handle(any());
    }
}
