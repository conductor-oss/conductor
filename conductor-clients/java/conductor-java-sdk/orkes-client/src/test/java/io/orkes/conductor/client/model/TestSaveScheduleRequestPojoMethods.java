/*
 * Copyright 2025 Conductor Authors.
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
package io.orkes.conductor.client.model;

import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;

import static org.junit.jupiter.api.Assertions.*;

class TestSaveScheduleRequestPojoMethods {

    @Test
    void testCreatedBy() {
        SaveScheduleRequest request = new SaveScheduleRequest();
        assertNull(request.getCreatedBy());

        String createdBy = "user1";
        request.setCreatedBy(createdBy);
        assertEquals(createdBy, request.getCreatedBy());

        SaveScheduleRequest chainedRequest = new SaveScheduleRequest().createdBy(createdBy);
        assertEquals(createdBy, chainedRequest.getCreatedBy());
    }

    @Test
    void testCronExpression() {
        SaveScheduleRequest request = new SaveScheduleRequest();
        assertNull(request.getCronExpression());

        String cronExpression = "0 0 12 * * ?";
        request.setCronExpression(cronExpression);
        assertEquals(cronExpression, request.getCronExpression());

        SaveScheduleRequest chainedRequest = new SaveScheduleRequest().cronExpression(cronExpression);
        assertEquals(cronExpression, chainedRequest.getCronExpression());
    }

    @Test
    void testName() {
        SaveScheduleRequest request = new SaveScheduleRequest();
        assertNull(request.getName());

        String name = "testSchedule";
        request.setName(name);
        assertEquals(name, request.getName());

        SaveScheduleRequest chainedRequest = new SaveScheduleRequest().name(name);
        assertEquals(name, chainedRequest.getName());
    }

    @Test
    void testPaused() {
        SaveScheduleRequest request = new SaveScheduleRequest();
        assertNull(request.isPaused());

        Boolean paused = true;
        request.setPaused(paused);
        assertEquals(paused, request.isPaused());

        SaveScheduleRequest chainedRequest = new SaveScheduleRequest().paused(paused);
        assertEquals(paused, chainedRequest.isPaused());
    }

    @Test
    void testRunCatchupScheduleInstances() {
        SaveScheduleRequest request = new SaveScheduleRequest();
        assertNull(request.isRunCatchupScheduleInstances());

        Boolean runCatchup = true;
        request.setRunCatchupScheduleInstances(runCatchup);
        assertEquals(runCatchup, request.isRunCatchupScheduleInstances());

        SaveScheduleRequest chainedRequest = new SaveScheduleRequest().runCatchupScheduleInstances(runCatchup);
        assertEquals(runCatchup, chainedRequest.isRunCatchupScheduleInstances());
    }

    @Test
    void testScheduleEndTime() {
        SaveScheduleRequest request = new SaveScheduleRequest();
        assertNull(request.getScheduleEndTime());

        Long endTime = 1660000000000L;
        request.setScheduleEndTime(endTime);
        assertEquals(endTime, request.getScheduleEndTime());

        SaveScheduleRequest chainedRequest = new SaveScheduleRequest().scheduleEndTime(endTime);
        assertEquals(endTime, chainedRequest.getScheduleEndTime());
    }

    @Test
    void testScheduleStartTime() {
        SaveScheduleRequest request = new SaveScheduleRequest();
        assertNull(request.getScheduleStartTime());

        Long startTime = 1650000000000L;
        request.setScheduleStartTime(startTime);
        assertEquals(startTime, request.getScheduleStartTime());

        SaveScheduleRequest chainedRequest = new SaveScheduleRequest().scheduleStartTime(startTime);
        assertEquals(startTime, chainedRequest.getScheduleStartTime());
    }

    @Test
    void testStartWorkflowRequest() {
        SaveScheduleRequest request = new SaveScheduleRequest();
        assertNull(request.getStartWorkflowRequest());

        StartWorkflowRequest workflowRequest = new StartWorkflowRequest();
        request.setStartWorkflowRequest(workflowRequest);
        assertEquals(workflowRequest, request.getStartWorkflowRequest());

        SaveScheduleRequest chainedRequest = new SaveScheduleRequest().startWorkflowRequest(workflowRequest);
        assertEquals(workflowRequest, chainedRequest.getStartWorkflowRequest());
    }

    @Test
    void testUpdatedBy() {
        SaveScheduleRequest request = new SaveScheduleRequest();
        assertNull(request.getUpdatedBy());

        String updatedBy = "user2";
        request.setUpdatedBy(updatedBy);
        assertEquals(updatedBy, request.getUpdatedBy());

        SaveScheduleRequest chainedRequest = new SaveScheduleRequest().updatedBy(updatedBy);
        assertEquals(updatedBy, chainedRequest.getUpdatedBy());
    }

    @Test
    void testZoneId() {
        SaveScheduleRequest request = new SaveScheduleRequest();
        assertNull(request.getZoneId());

        String zoneId = "UTC";
        request.setZoneId(zoneId);
        assertEquals(zoneId, request.getZoneId());

        SaveScheduleRequest chainedRequest = new SaveScheduleRequest().zoneId(zoneId);
        assertEquals(zoneId, chainedRequest.getZoneId());
    }

    @Test
    void testEqualsAndHashCode() {
        SaveScheduleRequest request1 = new SaveScheduleRequest()
                .name("schedule1")
                .cronExpression("0 0 12 * * ?")
                .createdBy("user1");

        SaveScheduleRequest request2 = new SaveScheduleRequest()
                .name("schedule1")
                .cronExpression("0 0 12 * * ?")
                .createdBy("user1");

        SaveScheduleRequest request3 = new SaveScheduleRequest()
                .name("schedule2")
                .cronExpression("0 0 12 * * ?")
                .createdBy("user1");

        // Test equality
        assertEquals(request1, request2);
        assertNotEquals(request1, request3);

        // Test hashCode
        assertEquals(request1.hashCode(), request2.hashCode());
        assertNotEquals(request1.hashCode(), request3.hashCode());
    }

    @Test
    void testToString() {
        SaveScheduleRequest request = new SaveScheduleRequest()
                .name("testSchedule")
                .cronExpression("0 0 12 * * ?");

        String toString = request.toString();

        // Verify toString contains important fields
        assertTrue(toString.contains("testSchedule"));
        assertTrue(toString.contains("0 0 12 * * ?"));
    }
}