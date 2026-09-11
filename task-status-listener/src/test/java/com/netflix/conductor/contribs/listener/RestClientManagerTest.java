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
import java.util.Map;

import org.apache.http.client.methods.HttpPost;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;

/** Unit test for RestClientManager UTF-8 Content-Length entity initialization. */
public class RestClientManagerTest {

    @Test
    public void testCreatePostRequest_withNonAsciiCharacters_setsUtf8ContentLength() throws Exception {
        StatusNotifierNotificationProperties config = mock(StatusNotifierNotificationProperties.class);
        RestClientManager clientManager = new RestClientManager(config);

        String nonAsciiData = "{\"output\":\"Task finished with special chars: – — € 🚀\"}";
        Map<String, String> headers = Collections.emptyMap();

        Method createPostRequestMethod = RestClientManager.class.getDeclaredMethod("createPostRequest", String.class, String.class, Map.class);
        createPostRequestMethod.setAccessible(true);

        HttpPost request = (HttpPost) createPostRequestMethod.invoke(clientManager, "http://localhost:8080/webhook", nonAsciiData, headers);

        assertNotNull(request.getEntity());
        assertEquals("application/json; charset=UTF-8", request.getEntity().getContentType().getValue());
        
        long expectedUtf8ByteCount = nonAsciiData.getBytes(StandardCharsets.UTF_8).length;
        assertEquals(expectedUtf8ByteCount, request.getEntity().getContentLength());
    }
}
