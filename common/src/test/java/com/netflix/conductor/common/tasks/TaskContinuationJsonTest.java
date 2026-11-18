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
    public void aMarkerIsStoredWithoutWhatIsDerivedFromIt() throws Exception {
        TaskContinuation marker = new TaskContinuation();
        marker.setLeased(true);

        String json = objectMapper.writeValueAsString(marker);

        assertEquals("{\"leased\":true}", json);
    }

    @Test
    public void aMarkerNotYetRunIsStoredEmpty() throws Exception {
        // which continuation it is lives on the task's entry, not on the marker
        assertEquals("{}", objectMapper.writeValueAsString(new TaskContinuation()));
    }

    @Test
    public void aMarkerStoredWithoutItsEmptyValuesReadsThemBack() {
        String stored = "{\"endTime\":100,\"done\":true}";

        TaskContinuation marker = objectMapper.readValue(stored, TaskContinuation.class);

        assertTrue(marker.isApplied());
        assertFalse(marker.isLeased());
        assertEquals(0, marker.getRetryCount());
        assertEquals(100, marker.getEndTime());
        assertTrue(marker.isDone());
        assertTrue(marker.getRecorded().isEmpty());
        assertTrue(marker.getResumedWith().isEmpty());
    }

    @Test
    public void theHandlerLaneIsStoredWithItsRunNumberOnly() throws Exception {
        com.netflix.conductor.common.run.NativeState state =
                new com.netflix.conductor.common.run.NativeState();
        state.setBootstrap(new TaskContinuation());
        state.setHandlers(TaskContinuation.handlerRun(4));

        String json = objectMapper.writeValueAsString(state);

        assertTrue(json, json.contains("\"bootstrap\":{}"));
        assertTrue(json, json.contains("\"handlers\":{\"n\":4}"));
        com.netflix.conductor.common.run.NativeState back =
                objectMapper.readValue(json, com.netflix.conductor.common.run.NativeState.class);
        assertFalse(back.getBootstrap().isApplied());
        assertEquals(4, back.getHandlers().getN());
    }

    @Test
    public void aMarkerStoredByAnEarlierBuildStillReads() {
        // fields since moved to the run or the entry, renamed or dropped: all ignored
        String stored =
                "{\"lane\":\"main\",\"n\":2,\"state\":\"APPLIED\",\"done\":true,\"attempts\":3,\"step\":4,"
                        + "\"worker\":\"w\",\"queue\":\"q\",\"error\":{\"message\":\"boom\"},\"result\":{},"
                        + "\"epoch\":2,\"leasedAt\":5}";

        TaskContinuation marker = objectMapper.readValue(stored, TaskContinuation.class);

        assertTrue(marker.isDone());
        assertEquals(2, marker.getN());
    }
}
