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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Locale;

import org.apache.http.client.methods.HttpPost;
import org.apache.http.util.EntityUtils;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RestClientManagerTest {

    private RestClientManager restClientManager;

    @Before
    public void setup() {
        restClientManager = new RestClientManager(new StatusNotifierNotificationProperties());
    }

    @Test
    public void createPostRequestKeepsContentLengthInSyncForNonAsciiPayload() throws IOException {
        // en dash and non-breaking space are single chars but multibyte in UTF-8; with the
        // default ISO-8859-1 StringEntity the declared Content-Length desyncs from the body
        String payload = "{\"note\":\"in–network summary\"}";
        byte[] utf8Bytes = payload.getBytes(StandardCharsets.UTF_8);

        HttpPost post =
                restClientManager.createPostRequest(
                        "http://localhost/events/workflow", payload, Collections.emptyMap());

        assertEquals(utf8Bytes.length, post.getEntity().getContentLength());
        assertArrayEquals(utf8Bytes, EntityUtils.toByteArray(post.getEntity()));
        assertTrue(
                post.getEntity()
                        .getContentType()
                        .getValue()
                        .toLowerCase(Locale.ROOT)
                        .contains("utf-8"));
    }
}
