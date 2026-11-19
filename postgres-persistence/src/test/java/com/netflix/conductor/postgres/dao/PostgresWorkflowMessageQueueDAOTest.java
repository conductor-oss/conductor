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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
        String wf = "wf-claims-" + UUID.randomUUID();
        for (int i = 0; i < 10; i++) { // the test queue holds 10
            dao.push(wf, message("m" + i, wf));
        }
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<List<String>>> runs = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            runs.add(
                    pool.submit(
                            () -> {
                                go.await();
                                return dao.claim(wf, 5, "pull", 1).stream()
                                        .map(WorkflowMessage::getId)
                                        .toList();
                            }));
        }
        go.countDown();
        Set<List<String>> distinct = new HashSet<>();
        for (Future<List<String>> r : runs) {
            distinct.add(r.get());
        }
        pool.shutdown();
        assertEquals(Set.of(List.of("m0", "m1", "m2", "m3", "m4")), distinct);
        assertEquals(
                List.of("m5", "m6", "m7", "m8", "m9"),
                dao.claim(wf, 5, "pull", 2).stream().map(WorkflowMessage::getId).toList());
        assertEquals(0, dao.size(wf)); // m0..m4 deleted by the next iteration, m5..m9 claimed
    }

    @Test
    public void aStaleRunOfAPullTakesNoNewMessages() {
        String wf = "wf-fence-" + UUID.randomUUID();
        dao.push(wf, message("a", wf));
        assertEquals(1, dao.claim(wf, 5, "pull", 10).size());
        assertTrue(dao.claim(wf, 5, "pull", 11).isEmpty());
        dao.push(wf, message("close", wf));
        assertTrue(dao.claim(wf, 5, "pull", 10).isEmpty());
        assertEquals("close", dao.claim(wf, 5, "pull", 11).get(0).getId());
    }

    /** The iteration of one pull says nothing about another pull. */
    @Test
    public void aPullAfterALoopOrInASecondLoopGetsMessages() {
        String wf = "wf-loops-" + UUID.randomUUID();
        dao.push(wf, message("a", wf));
        assertEquals("a", dao.claim(wf, 5, "loop_pull", 3).get(0).getId());
        dao.push(wf, message("b", wf));
        dao.push(wf, message("c", wf));

        assertEquals("b", dao.claim(wf, 1, "final_pull", 0).get(0).getId());
        assertEquals("c", dao.claim(wf, 1, "second_loop_pull", 1).get(0).getId());
    }

    /** Two pulls in parallel branches keep their claims; claimed messages are not queued. */
    @Test
    public void aClaimLeavesTheClaimsOfOtherPulls() {
        String wf = "wf-parallel-" + UUID.randomUUID();
        dao.push(wf, message("a", wf));
        dao.push(wf, message("b", wf));
        dao.push(wf, message("c", wf));

        assertEquals("a", dao.claim(wf, 1, "pull_a", 0).get(0).getId());
        assertEquals("b", dao.claim(wf, 1, "pull_b", 0).get(0).getId());
        assertEquals("a", dao.claim(wf, 1, "pull_a", 0).get(0).getId());
        assertEquals("b", dao.claim(wf, 1, "pull_b", 0).get(0).getId());
        assertEquals(1, dao.size(wf));
    }

    /** Claimed messages are delivered: they leave room in the queue (10 in this test). */
    @Test
    public void claimedMessagesDoNotCountTowardTheMaximum() {
        String wf = "wf-max-" + UUID.randomUUID();
        for (int i = 0; i < 10; i++) {
            dao.push(wf, message("m" + i, wf));
        }
        assertEquals(10, dao.claim(wf, 10, "pull", 0).size());

        dao.push(wf, message("next", wf));
        assertEquals(1, dao.size(wf));
    }
}
