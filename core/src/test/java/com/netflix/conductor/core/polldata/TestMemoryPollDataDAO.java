/*
 * Copyright 2022 Conductor Authors.
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
package com.netflix.conductor.core.execution;

import java.util.*;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;

import com.netflix.conductor.common.metadata.tasks.PollData;
import com.netflix.conductor.core.execution.mapper.*;
import com.netflix.conductor.core.execution.tasks.*;
import com.netflix.conductor.core.index.MemoryPollDataDAO;

import static com.netflix.conductor.common.metadata.tasks.TaskType.*;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class TestMemoryPollDataDAO {
    MemoryPollDataDAO pollDataDAO;

    @Before
    public void setup() {
        pollDataDAO = new MemoryPollDataDAO();
        pollDataDAO.updateLastPollData("test-task-one", "one", "worker-id1");
        pollDataDAO.updateLastPollData("test-task-two", "one", "worker-id2");
        pollDataDAO.updateLastPollData("test-task-one", "two", "worker-id3");
        pollDataDAO.updateLastPollData("test-task-two", "two", "worker-id4");
        pollDataDAO.updateLastPollData("test-task-one", null, "worker-id5");
        pollDataDAO.updateLastPollData("test-task-two", null, "worker-id6");
    }

    @Test
    public void testUpdateNewPollData() {
        pollDataDAO.updateLastPollData("test-task-three", "three", "worker-id7");
        PollData pollData = pollDataDAO.getPollData("test-task-three", "three");

        assertNotNull(pollData);
        assertTrue(pollData.getLastPollTime() > 0);
        assertEquals("test-task-three", pollData.getQueueName());
        assertEquals("three", pollData.getDomain());
        assertEquals("worker-id7", pollData.getWorkerId());
    }

    @Test
    public void testUpdateExistingPollData() {
        PollData pollData = pollDataDAO.getPollData("test-task-one", "one");

        assertNotNull(pollData);
        assertEquals("worker-id1", pollData.getWorkerId());
        assertTrue(pollData.getLastPollTime() > 0);
        long previousLastPollTime = pollData.getLastPollTime();

        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
        }

        pollDataDAO.updateLastPollData("test-task-one", "one", "worker-id8");

        pollData = pollDataDAO.getPollData("test-task-one", "one");

        assertNotNull(pollData);
        assertTrue(pollData.getLastPollTime() > 0);
        assertEquals("test-task-one", pollData.getQueueName());
        assertEquals("worker-id8", pollData.getWorkerId());
        assertTrue(pollData.getLastPollTime() > previousLastPollTime);
    }

    @Test
    public void testGetPollDataByDomain() {
        PollData pollData = pollDataDAO.getPollData("test-task-one", "one");

        assertNotNull(pollData);
        assertTrue(pollData.getLastPollTime() > 0);
        assertEquals("test-task-one", pollData.getQueueName());
        assertEquals("one", pollData.getDomain());
        assertEquals("worker-id1", pollData.getWorkerId());

        pollData = pollDataDAO.getPollData("test-task-two", "two");

        assertNotNull(pollData);
        assertTrue(pollData.getLastPollTime() > 0);
        assertEquals("test-task-two", pollData.getQueueName());
        assertEquals("two", pollData.getDomain());
        assertEquals("worker-id4", pollData.getWorkerId());
    }

    @Test
    public void testGetPollDataByDomainNoData() {
        PollData pollData = pollDataDAO.getPollData("test-task-one", "three");

        assertNull(pollData);
    }

    @Test
    public void testGetPollDataWithNullDomain() {
        PollData pollData = pollDataDAO.getPollData("test-task-one", null);

        assertNotNull(pollData);
        assertTrue(pollData.getLastPollTime() > 0);
        assertEquals("test-task-one", pollData.getQueueName());
        assertNull(pollData.getDomain());
        assertEquals("worker-id5", pollData.getWorkerId());

        pollData = pollDataDAO.getPollData("test-task-two", null);

        assertNotNull(pollData);
        assertTrue(pollData.getLastPollTime() > 0);
        assertEquals("test-task-two", pollData.getQueueName());
        assertNull(pollData.getDomain());
        assertEquals("worker-id6", pollData.getWorkerId());
    }

    @Test
    public void testGetAllPollDataForTask() {
        List<PollData> pollData = pollDataDAO.getPollData("test-task-one");

        assertNotNull(pollData);
        assertEquals(3, pollData.size());

        List<String> queueNames =
                pollData.stream().map(x -> x.getQueueName()).collect(Collectors.toList());
        assertEquals("test-task-one", queueNames.get(0));
        assertEquals("test-task-one", queueNames.get(1));
        assertEquals("test-task-one", queueNames.get(2));

        List<String> domains =
                pollData.stream().map(x -> x.getDomain()).collect(Collectors.toList());
        assertTrue(domains.contains("one"));
        assertTrue(domains.contains("two"));
        assertTrue(domains.contains(null));

        List<String> workerIds =
                pollData.stream().map(x -> x.getWorkerId()).collect(Collectors.toList());
        assertTrue(workerIds.contains("worker-id1"));
        assertTrue(workerIds.contains("worker-id3"));
        assertTrue(workerIds.contains("worker-id5"));
    }

    @Test
    public void testGetPollDatForTaskNoData() {
        List<PollData> pollData = pollDataDAO.getPollData("test-task-three");

        assertEquals(0, pollData.size());
    }

    @Test
    public void testGetAllPollData() {
        List<PollData> pollData = pollDataDAO.getAllPollData();

        assertNotNull(pollData);
        assertEquals(6, pollData.size());

        List<String> queueNames =
                pollData.stream().map(x -> x.getQueueName()).collect(Collectors.toList());
        assertEquals(3, Collections.frequency(queueNames, "test-task-one"));
        assertEquals(3, Collections.frequency(queueNames, "test-task-two"));

        List<String> domains =
                pollData.stream().map(x -> x.getDomain()).collect(Collectors.toList());
        assertEquals(2, Collections.frequency(domains, "one"));
        assertEquals(2, Collections.frequency(domains, "two"));
        assertEquals(2, Collections.frequency(domains, null));

        List<String> workerIds =
                pollData.stream().map(x -> x.getWorkerId()).collect(Collectors.toList());
        assertTrue(workerIds.contains("worker-id1"));
        assertTrue(workerIds.contains("worker-id2"));
        assertTrue(workerIds.contains("worker-id3"));
        assertTrue(workerIds.contains("worker-id4"));
        assertTrue(workerIds.contains("worker-id5"));
        assertTrue(workerIds.contains("worker-id6"));
    }


    @Test
    public void testGetAllPollDataNoData() {
        pollDataDAO = new MemoryPollDataDAO();

        List<PollData> pollData = pollDataDAO.getAllPollData();

        assertEquals(0, pollData.size());
    }
}
