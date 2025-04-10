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

class TestWorkflowSchedulePojoMethods {

    @Test
    void testCreateTimeGetterAndSetter() {
        WorkflowSchedule workflowSchedule = new WorkflowSchedule();
        Long value = 1234567890L;

        workflowSchedule.setCreateTime(value);
        assertEquals(value, workflowSchedule.getCreateTime());

        // Test builder pattern method
        WorkflowSchedule result = workflowSchedule.createTime(value);
        assertSame(workflowSchedule, result);
        assertEquals(value, workflowSchedule.getCreateTime());
    }

    @Test
    void testCreatedByGetterAndSetter() {
        WorkflowSchedule workflowSchedule = new WorkflowSchedule();
        String value = "testUser";

        workflowSchedule.setCreatedBy(value);
        assertEquals(value, workflowSchedule.getCreatedBy());

        // Test builder pattern method
        WorkflowSchedule result = workflowSchedule.createdBy(value);
        assertSame(workflowSchedule, result);
        assertEquals(value, workflowSchedule.getCreatedBy());
    }

    @Test
    void testCronExpressionGetterAndSetter() {
        WorkflowSchedule workflowSchedule = new WorkflowSchedule();
        String value = "0 0 12 * * ?";

        workflowSchedule.setCronExpression(value);
        assertEquals(value, workflowSchedule.getCronExpression());

        // Test builder pattern method
        WorkflowSchedule result = workflowSchedule.cronExpression(value);
        assertSame(workflowSchedule, result);
        assertEquals(value, workflowSchedule.getCronExpression());
    }

    @Test
    void testNameGetterAndSetter() {
        WorkflowSchedule workflowSchedule = new WorkflowSchedule();
        String value = "testSchedule";

        workflowSchedule.setName(value);
        assertEquals(value, workflowSchedule.getName());

        // Test builder pattern method
        WorkflowSchedule result = workflowSchedule.name(value);
        assertSame(workflowSchedule, result);
        assertEquals(value, workflowSchedule.getName());
    }

    @Test
    void testPausedGetterAndSetter() {
        WorkflowSchedule workflowSchedule = new WorkflowSchedule();
        Boolean value = true;

        workflowSchedule.setPaused(value);
        assertEquals(value, workflowSchedule.isPaused());

        // Test builder pattern method
        WorkflowSchedule result = workflowSchedule.paused(value);
        assertSame(workflowSchedule, result);
        assertEquals(value, workflowSchedule.isPaused());
    }

    @Test
    void testRunCatchupScheduleInstancesGetterAndSetter() {
        WorkflowSchedule workflowSchedule = new WorkflowSchedule();
        Boolean value = true;

        workflowSchedule.setRunCatchupScheduleInstances(value);
        assertEquals(value, workflowSchedule.isRunCatchupScheduleInstances());

        // Test builder pattern method
        WorkflowSchedule result = workflowSchedule.runCatchupScheduleInstances(value);
        assertSame(workflowSchedule, result);
        assertEquals(value, workflowSchedule.isRunCatchupScheduleInstances());
    }

    @Test
    void testScheduleEndTimeGetterAndSetter() {
        WorkflowSchedule workflowSchedule = new WorkflowSchedule();
        Long value = 1234567890L;

        workflowSchedule.setScheduleEndTime(value);
        assertEquals(value, workflowSchedule.getScheduleEndTime());

        // Test builder pattern method
        WorkflowSchedule result = workflowSchedule.scheduleEndTime(value);
        assertSame(workflowSchedule, result);
        assertEquals(value, workflowSchedule.getScheduleEndTime());
    }

    @Test
    void testScheduleStartTimeGetterAndSetter() {
        WorkflowSchedule workflowSchedule = new WorkflowSchedule();
        Long value = 1234567890L;

        workflowSchedule.setScheduleStartTime(value);
        assertEquals(value, workflowSchedule.getScheduleStartTime());

        // Test builder pattern method
        WorkflowSchedule result = workflowSchedule.scheduleStartTime(value);
        assertSame(workflowSchedule, result);
        assertEquals(value, workflowSchedule.getScheduleStartTime());
    }

    @Test
    void testStartWorkflowRequestGetterAndSetter() {
        WorkflowSchedule workflowSchedule = new WorkflowSchedule();
        StartWorkflowRequest value = new StartWorkflowRequest();

        workflowSchedule.setStartWorkflowRequest(value);
        assertEquals(value, workflowSchedule.getStartWorkflowRequest());

        // Test builder pattern method
        WorkflowSchedule result = workflowSchedule.startWorkflowRequest(value);
        assertSame(workflowSchedule, result);
        assertEquals(value, workflowSchedule.getStartWorkflowRequest());
    }

    @Test
    void testUpdatedByGetterAndSetter() {
        WorkflowSchedule workflowSchedule = new WorkflowSchedule();
        String value = "testUpdater";

        workflowSchedule.setUpdatedBy(value);
        assertEquals(value, workflowSchedule.getUpdatedBy());

        // Test builder pattern method
        WorkflowSchedule result = workflowSchedule.updatedBy(value);
        assertSame(workflowSchedule, result);
        assertEquals(value, workflowSchedule.getUpdatedBy());
    }

    @Test
    void testUpdatedTimeGetterAndSetter() {
        WorkflowSchedule workflowSchedule = new WorkflowSchedule();
        Long value = 1234567890L;

        workflowSchedule.setUpdatedTime(value);
        assertEquals(value, workflowSchedule.getUpdatedTime());

        // Test builder pattern method
        WorkflowSchedule result = workflowSchedule.updatedTime(value);
        assertSame(workflowSchedule, result);
        assertEquals(value, workflowSchedule.getUpdatedTime());
    }

    @Test
    void testZoneIdGetterAndSetter() {
        WorkflowSchedule workflowSchedule = new WorkflowSchedule();
        String value = "UTC";

        workflowSchedule.setZoneId(value);
        assertEquals(value, workflowSchedule.getZoneId());

        // Test builder pattern method
        WorkflowSchedule result = workflowSchedule.zoneId(value);
        assertSame(workflowSchedule, result);
        assertEquals(value, workflowSchedule.getZoneId());
    }

    @Test
    void testEqualsAndHashCode() {
        WorkflowSchedule ws1 = new WorkflowSchedule()
                .name("test")
                .cronExpression("0 0 12 * * ?")
                .createdBy("user1");

        WorkflowSchedule ws2 = new WorkflowSchedule()
                .name("test")
                .cronExpression("0 0 12 * * ?")
                .createdBy("user1");

        WorkflowSchedule ws3 = new WorkflowSchedule()
                .name("different")
                .cronExpression("0 0 12 * * ?")
                .createdBy("user1");

        // Test equals
        assertEquals(ws1, ws2);
        assertNotEquals(ws1, ws3);

        // Test hashCode
        assertEquals(ws1.hashCode(), ws2.hashCode());
        assertNotEquals(ws1.hashCode(), ws3.hashCode());
    }

    @Test
    void testToString() {
        WorkflowSchedule workflowSchedule = new WorkflowSchedule()
                .name("testSchedule")
                .cronExpression("0 0 12 * * ?");

        String toString = workflowSchedule.toString();

        // Verify toString contains important fields
        assertTrue(toString.contains("testSchedule"));
        assertTrue(toString.contains("0 0 12 * * ?"));
    }
}