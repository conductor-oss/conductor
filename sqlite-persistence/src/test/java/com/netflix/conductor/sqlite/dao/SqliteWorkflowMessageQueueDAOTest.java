/*
 * Copyright 2024 Conductor Authors.
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
package com.netflix.conductor.sqlite.dao;

import java.sql.Connection;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.conductor.common.config.TestObjectMapperConfiguration;
import com.netflix.conductor.common.model.WorkflowMessage;
import com.netflix.conductor.core.config.WorkflowMessageQueueConfiguration;
import com.netflix.conductor.sqlite.config.SqliteConfiguration;

import static org.junit.Assert.*;

@ContextConfiguration(
        classes = {
            TestObjectMapperConfiguration.class,
            SqliteConfiguration.class,
            FlywayAutoConfiguration.class,
            WorkflowMessageQueueConfiguration.class,
        })
@RunWith(SpringRunner.class)
@SpringBootTest(
        properties = {
            "spring.flyway.clean-disabled=false",
            "conductor.workflow-message-queue.enabled=true",
            "conductor.workflow-message-queue.maxQueueSize=5"
        })
public class SqliteWorkflowMessageQueueDAOTest {

    @Autowired private SqliteWorkflowMessageQueueDAO dao;

    @Qualifier("dataSource")
    @Autowired
    private DataSource dataSource;

    @Autowired Flyway flyway;

    @Before
    public void before() {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(true);
            conn.prepareStatement("DELETE FROM workflow_message_queue").executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testPushAndPopSingleMessage() {
        WorkflowMessage msg =
                new WorkflowMessage(
                        "msg-1", "wf-1", Map.of("key", "value"), "2026-01-01T00:00:00Z");
        dao.push("wf-1", msg);

        assertEquals(1, dao.size("wf-1"));

        List<WorkflowMessage> popped = dao.pop("wf-1", 1);
        assertEquals(1, popped.size());
        assertEquals("msg-1", popped.get(0).getId());
        assertEquals("wf-1", popped.get(0).getWorkflowId());
        assertEquals("value", popped.get(0).getPayload().get("key"));
        assertEquals("2026-01-01T00:00:00Z", popped.get(0).getReceivedAt());

        assertEquals(0, dao.size("wf-1"));
    }

    @Test
    public void testFifoOrdering() {
        for (int i = 0; i < 5; i++) {
            WorkflowMessage msg =
                    new WorkflowMessage(
                            "msg-" + i, "wf-1", Map.of("order", i), "2026-01-01T00:00:00Z");
            dao.push("wf-1", msg);
        }

        List<WorkflowMessage> popped = dao.pop("wf-1", 5);
        assertEquals(5, popped.size());
        for (int i = 0; i < 5; i++) {
            assertEquals("msg-" + i, popped.get(i).getId());
            assertEquals(i, popped.get(i).getPayload().get("order"));
        }
    }

    @Test
    public void testPopWithBatchSize() {
        for (int i = 0; i < 5; i++) {
            dao.push(
                    "wf-1",
                    new WorkflowMessage(
                            "msg-" + i, "wf-1", Map.of("i", i), "2026-01-01T00:00:00Z"));
        }

        List<WorkflowMessage> first = dao.pop("wf-1", 2);
        assertEquals(2, first.size());
        assertEquals("msg-0", first.get(0).getId());
        assertEquals("msg-1", first.get(1).getId());

        List<WorkflowMessage> second = dao.pop("wf-1", 10);
        assertEquals(3, second.size());
        assertEquals("msg-2", second.get(0).getId());
        assertEquals("msg-3", second.get(1).getId());
        assertEquals("msg-4", second.get(2).getId());
    }

    @Test
    public void testPopEmptyQueue() {
        List<WorkflowMessage> popped = dao.pop("nonexistent", 5);
        assertNotNull(popped);
        assertTrue(popped.isEmpty());
    }

    @Test
    public void testMaxQueueSizeEnforced() {
        for (int i = 0; i < 5; i++) {
            dao.push(
                    "wf-1",
                    new WorkflowMessage("msg-" + i, "wf-1", Map.of(), "2026-01-01T00:00:00Z"));
        }
        // 6th push should fail (maxQueueSize=5)
        try {
            dao.push(
                    "wf-1", new WorkflowMessage("msg-5", "wf-1", Map.of(), "2026-01-01T00:00:00Z"));
            fail("Expected exception for exceeding max queue size");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("maximum size of 5"));
        }
    }

    @Test
    public void testSizeReflectsQueueDepth() {
        assertEquals(0, dao.size("wf-1"));

        dao.push("wf-1", new WorkflowMessage("msg-1", "wf-1", Map.of(), "2026-01-01T00:00:00Z"));
        assertEquals(1, dao.size("wf-1"));

        dao.push("wf-1", new WorkflowMessage("msg-2", "wf-1", Map.of(), "2026-01-01T00:00:00Z"));
        assertEquals(2, dao.size("wf-1"));

        dao.pop("wf-1", 1);
        assertEquals(1, dao.size("wf-1"));
    }

    @Test
    public void testDeleteRemovesAllMessages() {
        for (int i = 0; i < 3; i++) {
            dao.push(
                    "wf-1",
                    new WorkflowMessage("msg-" + i, "wf-1", Map.of(), "2026-01-01T00:00:00Z"));
        }
        assertEquals(3, dao.size("wf-1"));

        dao.delete("wf-1");
        assertEquals(0, dao.size("wf-1"));

        List<WorkflowMessage> popped = dao.pop("wf-1", 10);
        assertTrue(popped.isEmpty());
    }

    @Test
    public void testDeleteOnEmptyQueueIsNoop() {
        dao.delete("nonexistent");
        assertEquals(0, dao.size("nonexistent"));
    }

    @Test
    public void testWorkflowIsolation() {
        dao.push(
                "wf-1",
                new WorkflowMessage("msg-1", "wf-1", Map.of("wf", "1"), "2026-01-01T00:00:00Z"));
        dao.push(
                "wf-2",
                new WorkflowMessage("msg-2", "wf-2", Map.of("wf", "2"), "2026-01-01T00:00:00Z"));

        assertEquals(1, dao.size("wf-1"));
        assertEquals(1, dao.size("wf-2"));

        List<WorkflowMessage> wf1 = dao.pop("wf-1", 10);
        assertEquals(1, wf1.size());
        assertEquals("1", wf1.get(0).getPayload().get("wf"));

        // wf-2 should be unaffected
        assertEquals(1, dao.size("wf-2"));

        dao.delete("wf-2");
        assertEquals(0, dao.size("wf-2"));
        assertEquals(0, dao.size("wf-1"));
    }
}
