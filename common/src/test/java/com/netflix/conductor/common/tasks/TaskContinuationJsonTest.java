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
    public void aWaitingMarkerIsStoredAsItsLaneNumberAndStateOnly() throws Exception {
        String json = objectMapper.writeValueAsString(new TaskContinuation("fork_1.0", 3));

        assertEquals("{\"lane\":\"fork_1.0\",\"n\":3,\"state\":\"WAITING\"}", json);
    }

    @Test
    public void aMarkerStoredWithoutItsEmptyValuesReadsThemBack() {
        String stored = "{\"lane\":\"main\",\"n\":3,\"state\":\"APPLIED\",\"endTime\":100}";

        TaskContinuation marker = objectMapper.readValue(stored, TaskContinuation.class);

        assertEquals(3, marker.getN());
        assertEquals(0, marker.getRetryCount());
        assertEquals(100, marker.getEndTime());
        assertFalse(marker.isDone());
        assertTrue(marker.getRecorded().isEmpty());
        assertTrue(marker.getResumedWith().isEmpty());
    }

    @Test
    public void theFirstContinuationAndTheHandlerLaneAreStoredWithoutTheirConstantLaneAndNumber()
            throws Exception {
        com.netflix.conductor.common.run.NativeState state =
                new com.netflix.conductor.common.run.NativeState();
        state.setBootstrap(new TaskContinuation("main", 1));
        state.getBootstrap().setState(TaskContinuation.RUNNABLE);
        state.getBootstrap().setScheduledTime(100);
        state.setHandlers(new TaskContinuation("handlers", 4));

        String json = objectMapper.writeValueAsString(state);

        assertTrue(
                json,
                json.contains("\"bootstrap\":{\"scheduledTime\":100,\"state\":\"RUNNABLE\"}"));
        assertTrue(json, json.contains("\"handlers\":{\"n\":4,\"state\":\"WAITING\"}"));
        com.netflix.conductor.common.run.NativeState back =
                objectMapper.readValue(json, com.netflix.conductor.common.run.NativeState.class);
        assertEquals("main", back.getBootstrap().getLane());
        assertEquals(1, back.getBootstrap().getN());
        assertEquals("handlers", back.getHandlers().getLane());
        assertEquals(4, back.getHandlers().getN());
    }

    @Test
    public void aMarkerStoredByAnEarlierBuildStillReads() {
        // fields since moved to the run (error, result), renamed or dropped: all ignored
        String stored =
                "{\"lane\":\"main\",\"n\":2,\"state\":\"APPLIED\",\"done\":true,\"attempts\":3,\"step\":4,"
                        + "\"worker\":\"w\",\"queue\":\"q\",\"error\":{\"message\":\"boom\"},\"result\":{},"
                        + "\"epoch\":2,\"leasedAt\":5}";

        TaskContinuation marker = objectMapper.readValue(stored, TaskContinuation.class);

        assertTrue(marker.isDone());
        assertEquals(2, marker.getN());
    }
}
