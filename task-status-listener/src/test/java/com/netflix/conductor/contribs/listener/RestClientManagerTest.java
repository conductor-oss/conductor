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
package com.netflix.conductor.contribs.listener;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpPost;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies that {@link RestClientManager} encodes the POST body as UTF-8 so that non-ASCII
 * characters (e.g. an en dash U+2013) do not cause a Content-Length mismatch (issue #1399).
 */
class RestClientManagerTest {

    private RestClientManager manager;
    private Method createPostRequest;

    @BeforeEach
    void setUp() throws Exception {
        StatusNotifierNotificationProperties config =
                mock(StatusNotifierNotificationProperties.class);
        when(config.getConnectionPoolMaxRequest()).thenReturn(10);
        when(config.getConnectionPoolMaxRequestPerRoute()).thenReturn(5);
        when(config.getRequestTimeOutMsConnect()).thenReturn(1000);
        when(config.getRequestTimeoutMsread()).thenReturn(1000);
        when(config.getRequestTimeoutMsConnMgr()).thenReturn(1000);
        when(config.getRequestRetryCount()).thenReturn(3);
        when(config.getRequestRetryCountIntervalMs()).thenReturn(100);

        manager = new RestClientManager(config);

        createPostRequest =
                RestClientManager.class.getDeclaredMethod(
                        "createPostRequest", String.class, String.class, java.util.Map.class);
        createPostRequest.setAccessible(true);
    }

    @Test
    void createPostRequest_contentLengthMatchesUtf8ByteLength_forNonAsciiPayload()
            throws Exception {
        String payload = "{\"note\":\"in–network\"}"; // contains en dash U+2013 (3 bytes in UTF-8)
        long expectedBytes = payload.getBytes(StandardCharsets.UTF_8).length;

        HttpPost post =
                (HttpPost)
                        createPostRequest.invoke(
                                manager, "http://example.com", payload, Collections.emptyMap());
        HttpEntity entity = post.getEntity();

        assertThat(entity.getContentLength()).isEqualTo(expectedBytes);
    }

    @Test
    void createPostRequest_contentTypeIsJson() throws Exception {
        HttpPost post =
                (HttpPost)
                        createPostRequest.invoke(
                                manager, "http://example.com", "{}", Collections.emptyMap());

        assertThat(post.getEntity().getContentType().getValue())
                .containsIgnoringCase("application/json");
    }
}
