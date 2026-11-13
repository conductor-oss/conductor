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
package com.netflix.conductor.common.tasks;

import org.junit.Test;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.tasks.TaskContinuation;

import tools.jackson.databind.ObjectMapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Markers stored before a field changed still read: an execution outlives the server version. */
public class TaskContinuationJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();

    @Test
    public void aMarkerStoredWithoutResultOrErrorReadsAsNeither() {
        String stored =
                "{\"lane\":\"main\",\"n\":3,\"state\":\"APPLIED\",\"epoch\":2,\"attempts\":2,"
                        + "\"queuedAt\":100,\"step\":7,\"done\":false,\"recorded\":{},\"resumedWith\":{}}";

        TaskContinuation marker = objectMapper.readValue(stored, TaskContinuation.class);

        assertEquals(3, marker.getN());
        assertTrue(marker.getResult().isEmpty());
        assertTrue(marker.getError().isEmpty());
        assertFalse(marker.hasError());
    }

    @Test
    public void aMarkerStoredWithNullResultAndErrorReadsAsNeither() {
        String stored =
                "{\"lane\":\"main\",\"n\":1,\"state\":\"WAITING\",\"result\":null,\"error\":null}";

        TaskContinuation marker = objectMapper.readValue(stored, TaskContinuation.class);

        assertTrue(marker.getResult().isEmpty());
        assertFalse(marker.hasError());
    }

    @Test
    public void aMarkerWhoseCodeRaisedHasAnError() {
        String stored =
                "{\"lane\":\"main\",\"n\":2,\"state\":\"APPLIED\",\"done\":true,\"error\":{\"message\":\"boom\"}}";

        TaskContinuation marker = objectMapper.readValue(stored, TaskContinuation.class);

        assertTrue(marker.hasError());
        assertEquals("boom", marker.getError().get("message"));
    }
}
