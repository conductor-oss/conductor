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
package com.netflix.conductor.sqlite.dao;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.sqlite.SQLiteDataSource;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.model.WorkflowMessage;
import com.netflix.conductor.core.config.WorkflowMessageQueueProperties;
import com.netflix.conductor.core.dao.JdbcWorkflowMessageQueueDAO;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SqliteWorkflowMessageQueueDAOTest {

    private static SQLiteDataSource dataSource(Path file) throws Exception {
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + file);
        String ddl =
                new String(
                        SqliteWorkflowMessageQueueDAOTest.class
                                .getResourceAsStream(
                                        "/db/migration_sqlite/V9__workflow_message_queue.sql")
                                .readAllBytes());
        try (Connection c = ds.getConnection();
                Statement s = c.createStatement()) {
            for (String stmt : ddl.split(";")) {
                String sql = stmt.replaceAll("(?m)^--.*$", "").trim();
                if (!sql.isEmpty()) {
                    try {
                        s.execute(sql);
                    } catch (java.sql.SQLException e) {
                        // the same file migrated again, as after a restart
                        if (!String.valueOf(e.getMessage()).contains("duplicate column")) {
                            throw e;
                        }
                    }
                }
            }
        }
        return ds;
    }

    private static JdbcWorkflowMessageQueueDAO dao(SQLiteDataSource ds, int max) {
        WorkflowMessageQueueProperties props = new WorkflowMessageQueueProperties();
        props.setMaxQueueSize(max);
        return new JdbcWorkflowMessageQueueDAO(
                ds, new ObjectMapperProvider().getObjectMapper(), props, false);
    }

    private static WorkflowMessage message(String id, String wf) {
        WorkflowMessage m = new WorkflowMessage();
        m.setId(id);
        m.setWorkflowId(wf);
        m.setPayload(Map.of("kind", "signal", "name", "cancel"));
        m.setReceivedAt("2026-11-08T00:00:00Z");
        return m;
    }

    @Test
    public void messagesArePoppedOnceInOrderAndSurviveARestart() throws Exception {
        Path file = Files.createTempFile("wmq", ".db");
        SQLiteDataSource ds = dataSource(file);
        JdbcWorkflowMessageQueueDAO before = dao(ds, 100);
        before.push("wf-1", message("m1", "wf-1"));
        before.push("wf-1", message("m2", "wf-1"));
        before.push("wf-2", message("m3", "wf-2"));
        assertEquals(2, before.size("wf-1"));

        // a new DAO on the same file: what the server sees after a restart
        JdbcWorkflowMessageQueueDAO after = dao(dataSource(file), 100);
        List<WorkflowMessage> first = after.pop("wf-1", 1);
        assertEquals("m1", first.get(0).getId());
        assertEquals("cancel", first.get(0).getPayload().get("name"));
        assertEquals("m2", after.pop("wf-1", 10).get(0).getId());
        assertTrue(after.pop("wf-1", 10).isEmpty());
        assertEquals(1, after.size("wf-2"));

        after.delete("wf-2");
        assertEquals(0, after.size("wf-2"));
    }

    /**
     * Concurrent pushes and pops on a WAL database: SQLite reports SQLITE_BUSY_SNAPSHOT when a pop
     * that read first tries to delete after another connection wrote. Every message must still be
     * delivered exactly once, with no error escaping the DAO.
     */
    @Test
    public void concurrentPushAndPopOnWalDeliverEveryMessageOnce() throws Exception {
        Path file = Files.createTempFile("wmq", ".db");
        dataSource(file);
        SQLiteDataSource wal = new SQLiteDataSource();
        wal.setUrl("jdbc:sqlite:" + file + "?busy_timeout=15000&journal_mode=WAL");
        JdbcWorkflowMessageQueueDAO dao = dao(wal, 100_000);

        int writers = 4, perWriter = 100, total = writers * perWriter;
        java.util.Set<String> seen = java.util.concurrent.ConcurrentHashMap.newKeySet();
        java.util.concurrent.atomic.AtomicInteger duplicates =
                new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.ExecutorService pool =
                java.util.concurrent.Executors.newFixedThreadPool(writers + 3);
        java.util.List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
        for (int w = 0; w < writers; w++) {
            int writer = w;
            futures.add(
                    pool.submit(
                            () -> {
                                for (int i = 0; i < perWriter; i++) {
                                    dao.push("wf", message(writer + "-" + i, "wf"));
                                }
                            }));
        }
        long deadline = System.currentTimeMillis() + 60_000;
        for (int r = 0; r < 3; r++) {
            futures.add(
                    pool.submit(
                            () -> {
                                while (seen.size() < total
                                        && System.currentTimeMillis() < deadline) {
                                    for (WorkflowMessage m : dao.pop("wf", 5)) {
                                        if (!seen.add(m.getId())) {
                                            duplicates.incrementAndGet();
                                        }
                                    }
                                }
                            }));
        }
        for (java.util.concurrent.Future<?> f : futures) {
            f.get(); // rethrows any exception from the DAO
        }
        pool.shutdown();
        assertEquals(total, seen.size());
        assertEquals(0, duplicates.get());
        assertEquals(0, dao.size("wf"));
    }

    /**
     * A pull that runs again, because its server died mid-run or two servers ran it at once, gets
     * the messages the first run claimed; the next pull acknowledges them. Found by the durability
     * bench on a three-server cluster: a pull reserved for an hour stalled the handler lane.
     */
    @Test
    public void claimsAreRepeatableAndAcknowledgedByTheNextClaimant() throws Exception {
        JdbcWorkflowMessageQueueDAO dao = dao(dataSource(Files.createTempFile("wmq", ".db")), 100);
        dao.push("wf", message("a", "wf"));
        dao.push("wf", message("b", "wf"));
        dao.push("wf", message("c", "wf"));

        List<WorkflowMessage> first = dao.claim("wf", 2, "signal__1");
        assertEquals(List.of("a", "b"), first.stream().map(WorkflowMessage::getId).toList());
        // the same pull again: same messages, nothing new claimed, nothing lost
        assertEquals(
                List.of("a", "b"),
                dao.claim("wf", 2, "signal__1").stream().map(WorkflowMessage::getId).toList());
        assertEquals(3, dao.size("wf"));

        // the next pull acknowledges a and b, and claims c
        assertEquals(
                List.of("c"),
                dao.claim("wf", 2, "signal__2").stream().map(WorkflowMessage::getId).toList());
        assertEquals(1, dao.size("wf"));
        assertTrue(dao.claim("wf", 2, "signal__3").isEmpty());
        assertEquals(0, dao.size("wf"));
    }

    /**
     * A stale run of an older pull, resumed after a pause, takes no new messages once a newer pull
     * has claimed: otherwise the newer pull would delete them as delivered though no handler saw
     * them. Found by the durability bench on a three-server cluster.
     */
    @Test
    public void aStalePullTakesNoNewMessages() throws Exception {
        JdbcWorkflowMessageQueueDAO dao = dao(dataSource(Files.createTempFile("wmq", ".db")), 100);
        dao.push("wf", message("a", "wf"));
        assertEquals(List.of("a"), ids(dao.claim("wf", 5, "signal__10", 10)));
        assertTrue(dao.claim("wf", 5, "signal__11", 11).isEmpty()); // acknowledges a
        dao.push("wf", message("close", "wf"));
        // the stale pull of iteration 10 resumes: it must not take the new message
        assertTrue(dao.claim("wf", 5, "signal__10", 10).isEmpty());
        assertEquals(List.of("close"), ids(dao.claim("wf", 5, "signal__11", 11)));
        // after the execution ends its fence is gone, so a restart starts from iteration 1
        dao.delete("wf");
        dao.push("wf", message("again", "wf"));
        assertEquals(List.of("again"), ids(dao.claim("wf", 5, "signal__1", 1)));
    }

    private static List<String> ids(List<WorkflowMessage> messages) {
        return messages.stream().map(WorkflowMessage::getId).toList();
    }

    @Test(expected = IllegalStateException.class)
    public void refusesMessagesBeyondTheMaximum() throws Exception {
        JdbcWorkflowMessageQueueDAO dao = dao(dataSource(Files.createTempFile("wmq", ".db")), 1);
        dao.push("wf", message("a", "wf"));
        dao.push("wf", message("b", "wf"));
    }
}
