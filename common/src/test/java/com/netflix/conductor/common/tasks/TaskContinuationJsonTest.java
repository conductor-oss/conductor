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
import static org.junit.Assert.assertTrue;

/** Markers stored before a field changed still read: an execution outlives the server version. */
public class TaskContinuationJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();

    @Test
    public void aMarkerIsStoredWithItsStateOnlyNotTheFlagsDerivedFromIt() throws Exception {
        TaskContinuation marker = new TaskContinuation("main", 2);
        marker.setState(TaskContinuation.LEASED);

        String json = objectMapper.writeValueAsString(marker);

        org.junit.Assert.assertTrue(json, json.contains("\"state\":\"LEASED\""));
        for (String flag : new String[] {"waiting", "runnable", "leased", "applied"}) {
            org.junit.Assert.assertFalse(json, json.contains("\"" + flag + "\""));
        }
    }

    @Test
    public void aMarkerStoredWithoutAResultReadsAsNone() {
        String stored =
                "{\"lane\":\"main\",\"n\":3,\"state\":\"APPLIED\",\"epoch\":2,"
                        + "\"queuedAt\":100,\"step\":7,\"done\":false,\"recorded\":{},\"resumedWith\":{}}";

        TaskContinuation marker = objectMapper.readValue(stored, TaskContinuation.class);

        assertEquals(3, marker.getN());
        assertTrue(marker.getResult().isEmpty());
    }

    @Test
    public void aMarkerStoredWithANullResultReadsAsNone() {
        String stored = "{\"lane\":\"main\",\"n\":1,\"state\":\"WAITING\",\"result\":null}";

        assertTrue(objectMapper.readValue(stored, TaskContinuation.class).getResult().isEmpty());
    }

    @Test
    public void aMarkerStoredByAnEarlierBuildStillReads() {
        // the error now lives on the run (its reason) and attempts is the epoch: both are ignored
        String stored =
                "{\"lane\":\"main\",\"n\":2,\"state\":\"APPLIED\",\"done\":true,\"attempts\":3,"
                        + "\"worker\":\"w\",\"queue\":\"q\",\"error\":{\"message\":\"boom\"}}";

        TaskContinuation marker = objectMapper.readValue(stored, TaskContinuation.class);

        assertTrue(marker.isDone());
        assertEquals(2, marker.getN());
    }
}
