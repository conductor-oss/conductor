/*
 * Copyright 2023 Conductor Authors.
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
package com.netflix.conductor.postgres.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.*;

import javax.sql.DataSource;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.node.ObjectNode;

import com.netflix.conductor.common.config.TestObjectMapperConfiguration;
import com.netflix.conductor.postgres.config.PostgresConfiguration;
import com.netflix.conductor.postgres.config.PostgresProperties;

import static org.junit.Assert.*;

@ContextConfiguration(
        classes = {
            TestObjectMapperConfiguration.class,
            PostgresConfiguration.class,
            FlywayAutoConfiguration.class
        })
@RunWith(SpringRunner.class)
@TestPropertySource(
        properties = {
            "conductor.elasticsearch.version=0",
            "spring.flyway.clean-disabled=false",
            "conductor.database.type=postgres",
            "conductor.postgres.experimentalQueueNotify=true",
            "conductor.postgres.experimentalQueueNotifyStalePeriod=5000"
        })
@SpringBootTest
public class PostgresQueueListenerTest {

    private PostgresQueueListener listener;

    @Qualifier("dataSource")
    @Autowired
    private DataSource dataSource;

    @Autowired private PostgresProperties properties;

    private void clearDb() {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(true);
            conn.prepareStatement("truncate table queue_message").executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private void sendNotification(String queueName, int queueDepth, long nextDelivery) {
        JsonNodeFactory factory = JsonNodeFactory.instance;
        ObjectNode payload = factory.objectNode();
        ObjectNode queueNode = factory.objectNode();
        queueNode.put("depth", queueDepth);
        queueNode.put("nextDelivery", nextDelivery);
        payload.put("__now__", System.currentTimeMillis());
        payload.put(queueName, queueNode);

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(true);
            PreparedStatement stmt =
                    conn.prepareStatement("SELECT pg_notify('conductor_queue_state', ?)");
            stmt.setString(1, payload.toString());
            stmt.execute();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private void createQueueMessage(String queue_name, String message_id) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(true);
            PreparedStatement stmt =
                    conn.prepareStatement(
                            "INSERT INTO queue_message (deliver_on, queue_name, message_id, priority, offset_time_seconds, payload) VALUES (current_timestamp, ?,?,?,?,?)");
            stmt.setString(1, queue_name);
            stmt.setString(2, message_id);
            stmt.setInt(3, 0);
            stmt.setInt(4, 0);
            stmt.setString(5, "dummy-payload");
            stmt.execute();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private void popQueueMessage(String message_id) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(true);
            PreparedStatement stmt =
                    conn.prepareStatement(
                            "UPDATE queue_message SET popped = TRUE where message_id = ?");
            stmt.setString(1, message_id);
            stmt.execute();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private void deleteQueueMessage(String message_id) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(true);
            PreparedStatement stmt =
                    conn.prepareStatement("DELETE FROM queue_message where message_id = ?");
            stmt.setString(1, message_id);
            stmt.execute();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    @Before
    public void before() {
        listener = new PostgresQueueListener(dataSource, properties);
        clearDb();
    }

    @Test
    public void testHasReadyMessages() {
        assertFalse(listener.hasMessagesReady("dummy-task"));
        sendNotification("dummy-task", 3, System.currentTimeMillis() - 1);
        assertTrue(listener.hasMessagesReady("dummy-task"));
    }

    @Test
    public void testHasReadyMessagesInFuture() throws InterruptedException {
        assertFalse(listener.hasMessagesReady("dummy-task"));
        sendNotification("dummy-task", 3, System.currentTimeMillis() + 100);
        assertFalse(listener.hasMessagesReady("dummy-task"));
        Thread.sleep(101);
        assertTrue(listener.hasMessagesReady("dummy-task"));
    }

    @Test
    public void testGetSize() {
        assertEquals(0, listener.getSize("dummy-task").get().intValue());
        sendNotification("dummy-task", 3, System.currentTimeMillis() + 100);
        assertEquals(3, listener.getSize("dummy-task").get().intValue());
    }

    @Test
    public void testTrigger() throws InterruptedException {
        assertEquals(0, listener.getSize("dummy-task").get().intValue());
        assertFalse(listener.hasMessagesReady("dummy-task"));

        createQueueMessage("dummy-task", "dummy-id1");
        createQueueMessage("dummy-task", "dummy-id2");
        assertEquals(2, listener.getSize("dummy-task").get().intValue());
        assertTrue(listener.hasMessagesReady("dummy-task"));

        popQueueMessage("dummy-id2");
        assertEquals(1, listener.getSize("dummy-task").get().intValue());
        assertTrue(listener.hasMessagesReady("dummy-task"));

        deleteQueueMessage("dummy-id2");
        assertEquals(1, listener.getSize("dummy-task").get().intValue());
        assertTrue(listener.hasMessagesReady("dummy-task"));

        deleteQueueMessage("dummy-id1");
        assertEquals(0, listener.getSize("dummy-task").get().intValue());
        assertFalse(listener.hasMessagesReady("test-task"));
    }
}
