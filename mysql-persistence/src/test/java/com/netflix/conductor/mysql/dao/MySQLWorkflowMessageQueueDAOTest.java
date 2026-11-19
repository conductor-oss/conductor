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
package com.netflix.conductor.mysql.dao;

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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.conductor.common.config.TestObjectMapperConfiguration;
import com.netflix.conductor.common.model.WorkflowMessage;
import com.netflix.conductor.core.config.WorkflowMessageQueueProperties;
import com.netflix.conductor.core.dao.JdbcWorkflowMessageQueueDAO;
import com.netflix.conductor.mysql.config.MySQLConfiguration;

import tools.jackson.databind.ObjectMapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The durable workflow message queue on MySQL: claims, the claim fence, and delivery order. */
@ContextConfiguration(
        classes = {
            TestObjectMapperConfiguration.class,
            MySQLConfiguration.class,
            FlywayAutoConfiguration.class
        })
@RunWith(SpringRunner.class)
@SpringBootTest(properties = "spring.flyway.clean-disabled=false")
public class MySQLWorkflowMessageQueueDAOTest {

    @Qualifier("dataSource")
    @Autowired
    private DataSource dataSource;

    @Autowired private ObjectMapper objectMapper;

    @Autowired private Flyway flyway;

    private JdbcWorkflowMessageQueueDAO dao;

    @Before
    public void before() {
        flyway.migrate();
        WorkflowMessageQueueProperties props = new WorkflowMessageQueueProperties();
        props.setMaxQueueSize(10);
        dao = new JdbcWorkflowMessageQueueDAO(dataSource, objectMapper, props, true);
    }

    private static WorkflowMessage message(String id, String wf) {
        WorkflowMessage m = new WorkflowMessage();
        m.setId(id);
        m.setWorkflowId(wf);
        m.setPayload(Map.of("kind", "signal", "name", "deposit"));
        m.setReceivedAt("2026-11-08T00:00:00Z");
        return m;
    }

    private static List<String> ids(List<WorkflowMessage> messages) {
        return messages.stream().map(WorkflowMessage::getId).toList();
    }

    @Test
    public void popReturnsMessagesOnceInOrder() {
        String wf = "my-pop-" + UUID.randomUUID();
        dao.push(wf, message("a", wf));
        dao.push(wf, message("b", wf));
        assertEquals(List.of("a"), ids(dao.pop(wf, 1)));
        assertEquals(List.of("b"), ids(dao.pop(wf, 5)));
        assertTrue(dao.pop(wf, 5).isEmpty());
    }

    @Test
    public void claimsAreRepeatableFencedAndDeletedByTheNextIteration() {
        String wf = "my-claims-" + UUID.randomUUID();
        dao.push(wf, message("a", wf));
        dao.push(wf, message("b", wf));
        assertEquals(List.of("a", "b"), ids(dao.claim(wf, 5, "pull", 1)));
        assertEquals(List.of("a", "b"), ids(dao.claim(wf, 5, "pull", 1))); // repeatable
        assertEquals(0, dao.size(wf)); // claimed messages are not queued
        assertTrue(dao.claim(wf, 5, "pull", 2).isEmpty()); // deletes a and b
        dao.push(wf, message("close", wf));
        assertTrue(dao.claim(wf, 5, "pull", 1).isEmpty()); // a stale run takes nothing new
        assertEquals(List.of("close"), ids(dao.claim(wf, 5, "pull", 2)));
        dao.delete(wf);
        assertEquals(0, dao.size(wf));
    }

    /** The iteration of one pull says nothing about another pull. */
    @Test
    public void aPullAfterALoopOrInASecondLoopGetsMessages() {
        String wf = "my-loops-" + UUID.randomUUID();
        dao.push(wf, message("a", wf));
        assertEquals(List.of("a"), ids(dao.claim(wf, 5, "loop_pull", 3)));
        dao.push(wf, message("b", wf));
        dao.push(wf, message("c", wf));

        assertEquals(List.of("b"), ids(dao.claim(wf, 1, "final_pull", 0)));
        assertEquals(List.of("c"), ids(dao.claim(wf, 1, "second_loop_pull", 1)));
    }

    /** Two pulls in parallel branches keep their claims; claimed messages are not queued. */
    @Test
    public void aClaimLeavesTheClaimsOfOtherPulls() {
        String wf = "my-parallel-" + UUID.randomUUID();
        dao.push(wf, message("a", wf));
        dao.push(wf, message("b", wf));
        dao.push(wf, message("c", wf));

        assertEquals(List.of("a"), ids(dao.claim(wf, 1, "pull_a", 0)));
        assertEquals(List.of("b"), ids(dao.claim(wf, 1, "pull_b", 0)));
        assertEquals(List.of("a"), ids(dao.claim(wf, 1, "pull_a", 0)));
        assertEquals(List.of("b"), ids(dao.claim(wf, 1, "pull_b", 0)));
        assertEquals(1, dao.size(wf));
    }

    /** Claimed messages are delivered: they leave room in the queue (10 in this test). */
    @Test
    public void claimedMessagesDoNotCountTowardTheMaximum() {
        String wf = "my-max-" + UUID.randomUUID();
        for (int i = 0; i < 10; i++) {
            dao.push(wf, message("m" + i, wf));
        }
        assertEquals(10, dao.claim(wf, 10, "pull", 0).size());

        dao.push(wf, message("next", wf));
        assertEquals(1, dao.size(wf));
    }

    @Test
    public void concurrentClaimsBySamePullGetTheSameMessages() throws Exception {
        String wf = "my-concurrent-" + UUID.randomUUID();
        for (int i = 0; i < 10; i++) {
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
                                return ids(dao.claim(wf, 5, "pull", 1));
                            }));
        }
        go.countDown();
        Set<List<String>> distinct = new HashSet<>();
        for (Future<List<String>> r : runs) {
            distinct.add(r.get());
        }
        pool.shutdown();
        assertEquals(Set.of(List.of("m0", "m1", "m2", "m3", "m4")), distinct);
    }
}
