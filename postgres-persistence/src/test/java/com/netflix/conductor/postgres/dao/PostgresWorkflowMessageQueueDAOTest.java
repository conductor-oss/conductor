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
package com.netflix.conductor.postgres.dao;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.conductor.common.config.TestObjectMapperConfiguration;
import com.netflix.conductor.common.model.WorkflowMessage;
import com.netflix.conductor.core.config.WorkflowMessageQueueProperties;
import com.netflix.conductor.core.dao.JdbcWorkflowMessageQueueDAO;
import com.netflix.conductor.postgres.config.PostgresConfiguration;

import tools.jackson.databind.ObjectMapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The durable workflow message queue on Postgres, with the table from V20. */
@RunWith(SpringRunner.class)
@ContextConfiguration(
        classes = {
            TestObjectMapperConfiguration.class,
            PostgresConfiguration.class,
            FlywayAutoConfiguration.class
        })
@TestPropertySource(properties = {"spring.flyway.clean-disabled=false"})
@SpringBootTest
public class PostgresWorkflowMessageQueueDAOTest {

    @Autowired private DataSource dataSource;

    @Autowired private ObjectMapper objectMapper;

    @Autowired private Flyway flyway;

    private JdbcWorkflowMessageQueueDAO dao;

    @Before
    public void before() {
        flyway.migrate();
        WorkflowMessageQueueProperties props = new WorkflowMessageQueueProperties();
        props.setMaxQueueSize(10);
        dao = new JdbcWorkflowMessageQueueDAO(dataSource, objectMapper, props, true);
        dao.delete("pg-wf-1");
        dao.delete("pg-wf-2");
    }

    private static WorkflowMessage message(String id, String wf) {
        WorkflowMessage m = new WorkflowMessage();
        m.setId(id);
        m.setWorkflowId(wf);
        m.setPayload(Map.of("kind", "update", "name", "addNote", "args", "hello"));
        m.setReceivedAt("2026-11-08T00:00:00Z");
        return m;
    }

    @Test
    public void pushPopInOrderAndDelete() {
        dao.push("pg-wf-1", message("a", "pg-wf-1"));
        dao.push("pg-wf-1", message("b", "pg-wf-1"));
        dao.push("pg-wf-2", message("c", "pg-wf-2"));
        assertEquals(2, dao.size("pg-wf-1"));

        List<WorkflowMessage> popped = dao.pop("pg-wf-1", 5);
        assertEquals(List.of("a", "b"), popped.stream().map(WorkflowMessage::getId).toList());
        assertEquals("hello", popped.get(0).getPayload().get("args"));
        assertTrue(dao.pop("pg-wf-1", 5).isEmpty());
        assertEquals(1, dao.size("pg-wf-2"));
        dao.delete("pg-wf-2");
        assertEquals(0, dao.size("pg-wf-2"));
    }

    /**
     * Two servers running the same pull at once must get the same messages: the claim locks the
     * workflow's rows, so the second run waits and then sees the first run's claims.
     */
    @Test
    public void concurrentClaimsBySamePullGetTheSameMessages() throws Exception {
        String wf = "wf-claims-" + java.util.UUID.randomUUID();
        for (int i = 0; i < 10; i++) { // the test queue holds 10
            dao.push(wf, message("m" + i, wf));
        }
        java.util.concurrent.ExecutorService pool =
                java.util.concurrent.Executors.newFixedThreadPool(8);
        java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
        java.util.List<java.util.concurrent.Future<java.util.List<String>>> runs =
                new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            runs.add(
                    pool.submit(
                            () -> {
                                go.await();
                                return dao.claim(wf, 5, "signal__1").stream()
                                        .map(WorkflowMessage::getId)
                                        .toList();
                            }));
        }
        go.countDown();
        java.util.Set<java.util.List<String>> distinct = new java.util.HashSet<>();
        for (java.util.concurrent.Future<java.util.List<String>> r : runs) {
            distinct.add(r.get());
        }
        pool.shutdown();
        assertEquals(java.util.Set.of(java.util.List.of("m0", "m1", "m2", "m3", "m4")), distinct);
        assertEquals(
                java.util.List.of("m5", "m6", "m7", "m8", "m9"),
                dao.claim(wf, 5, "signal__2").stream().map(WorkflowMessage::getId).toList());
        assertEquals(5, dao.size(wf)); // m0..m4 acknowledged by the second pull
    }

    @Test
    public void aStalePullTakesNoNewMessages() {
        String wf = "wf-fence-" + java.util.UUID.randomUUID();
        dao.push(wf, message("a", wf));
        assertEquals(1, dao.claim(wf, 5, "signal__10", 10).size());
        assertTrue(dao.claim(wf, 5, "signal__11", 11).isEmpty());
        dao.push(wf, message("close", wf));
        assertTrue(dao.claim(wf, 5, "signal__10", 10).isEmpty());
        assertEquals("close", dao.claim(wf, 5, "signal__11", 11).get(0).getId());
    }
}
