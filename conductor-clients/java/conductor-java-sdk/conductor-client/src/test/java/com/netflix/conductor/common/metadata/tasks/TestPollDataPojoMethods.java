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
package com.netflix.conductor.common.metadata.tasks;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestPollDataPojoMethods {

    @Test
    public void testNoArgsConstructor() {
        PollData pollData = new PollData();
        assertNull(pollData.getQueueName());
        assertNull(pollData.getDomain());
        assertNull(pollData.getWorkerId());
        assertEquals(0, pollData.getLastPollTime());
    }

    @Test
    public void testParameterizedConstructor() {
        String queueName = "test_queue";
        String domain = "test_domain";
        String workerId = "test_worker";
        long lastPollTime = System.currentTimeMillis();

        PollData pollData = new PollData(queueName, domain, workerId, lastPollTime);

        assertEquals(queueName, pollData.getQueueName());
        assertEquals(domain, pollData.getDomain());
        assertEquals(workerId, pollData.getWorkerId());
        assertEquals(lastPollTime, pollData.getLastPollTime());
    }

    @Test
    public void testSettersAndGetters() {
        PollData pollData = new PollData();

        String queueName = "test_queue";
        String domain = "test_domain";
        String workerId = "test_worker";
        long lastPollTime = System.currentTimeMillis();

        pollData.setQueueName(queueName);
        pollData.setDomain(domain);
        pollData.setWorkerId(workerId);
        pollData.setLastPollTime(lastPollTime);

        assertEquals(queueName, pollData.getQueueName());
        assertEquals(domain, pollData.getDomain());
        assertEquals(workerId, pollData.getWorkerId());
        assertEquals(lastPollTime, pollData.getLastPollTime());
    }

    @Test
    public void testEqualsWithSameObject() {
        PollData pollData = new PollData("queue", "domain", "worker", 123L);
        assertTrue(pollData.equals(pollData));
    }

    @Test
    public void testEqualsWithNull() {
        PollData pollData = new PollData("queue", "domain", "worker", 123L);
        assertFalse(pollData.equals(null));
    }

    @Test
    public void testEqualsWithDifferentClass() {
        PollData pollData = new PollData("queue", "domain", "worker", 123L);
        assertFalse(pollData.equals("NotAPollData"));
    }

    @Test
    public void testEqualsWithIdenticalObject() {
        PollData pollData1 = new PollData("queue", "domain", "worker", 123L);
        PollData pollData2 = new PollData("queue", "domain", "worker", 123L);
        assertTrue(pollData1.equals(pollData2));
        assertTrue(pollData2.equals(pollData1));
    }

    @Test
    public void testEqualsWithDifferentQueueName() {
        PollData pollData1 = new PollData("queue1", "domain", "worker", 123L);
        PollData pollData2 = new PollData("queue2", "domain", "worker", 123L);
        assertFalse(pollData1.equals(pollData2));
        assertFalse(pollData2.equals(pollData1));
    }

    @Test
    public void testEqualsWithDifferentDomain() {
        PollData pollData1 = new PollData("queue", "domain1", "worker", 123L);
        PollData pollData2 = new PollData("queue", "domain2", "worker", 123L);
        assertFalse(pollData1.equals(pollData2));
        assertFalse(pollData2.equals(pollData1));
    }

    @Test
    public void testEqualsWithDifferentWorkerId() {
        PollData pollData1 = new PollData("queue", "domain", "worker1", 123L);
        PollData pollData2 = new PollData("queue", "domain", "worker2", 123L);
        assertFalse(pollData1.equals(pollData2));
        assertFalse(pollData2.equals(pollData1));
    }

    @Test
    public void testEqualsWithDifferentLastPollTime() {
        PollData pollData1 = new PollData("queue", "domain", "worker", 123L);
        PollData pollData2 = new PollData("queue", "domain", "worker", 456L);
        assertFalse(pollData1.equals(pollData2));
        assertFalse(pollData2.equals(pollData1));
    }

    @Test
    public void testHashCodeConsistency() {
        PollData pollData = new PollData("queue", "domain", "worker", 123L);
        int initialHashCode = pollData.hashCode();
        assertEquals(initialHashCode, pollData.hashCode());
    }

    @Test
    public void testHashCodeEquality() {
        PollData pollData1 = new PollData("queue", "domain", "worker", 123L);
        PollData pollData2 = new PollData("queue", "domain", "worker", 123L);
        assertEquals(pollData1.hashCode(), pollData2.hashCode());
    }

    @Test
    public void testToString() {
        String queueName = "test_queue";
        String domain = "test_domain";
        String workerId = "test_worker";
        long lastPollTime = 123456789L;

        PollData pollData = new PollData(queueName, domain, workerId, lastPollTime);

        String toString = pollData.toString();

        assertTrue(toString.contains(queueName));
        assertTrue(toString.contains(domain));
        assertTrue(toString.contains(workerId));
        assertTrue(toString.contains(String.valueOf(lastPollTime)));
    }
}