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
package org.conductoross.conductor.sqlite.dao;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.conductoross.conductor.webhook.model.IncomingWebhookEvent;
import org.conductoross.conductor.webhook.model.WebhookConfig;
import org.flywaydb.core.Flyway;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.conductor.common.config.TestObjectMapperConfiguration;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.sqlite.config.SqliteConfiguration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@ContextConfiguration(
        classes = {
            TestObjectMapperConfiguration.class,
            SqliteConfiguration.class,
            FlywayAutoConfiguration.class
        })
@RunWith(SpringRunner.class)
@SpringBootTest(properties = "spring.flyway.clean-disabled=false")
public class SqliteWebhookDAOTest {

    @Autowired private SqliteWebhookDAO dao;
    @Autowired private Flyway flyway;
    @MockBean private MetadataDAO metadataDAO;

    @Before
    public void before() {
        flyway.clean();
        flyway.migrate();
    }

    @Test
    public void crud_webhookConfig_roundtrip() {
        WebhookConfig config = new WebhookConfig();
        config.setId("hook-1");
        config.setName("my-hook");

        dao.createWebhook("hook-1", config);

        WebhookConfig loaded = dao.getWebhook("hook-1");
        assertNotNull(loaded);
        assertEquals("hook-1", loaded.getId());
        assertEquals(1, dao.getAllWebhooks().size());

        dao.removeWebhook("hook-1");
        assertNull(dao.getWebhook("hook-1"));
        assertTrue(dao.getAllWebhooks().isEmpty());
    }

    @Test
    public void createWebhook_upsertsExistingId() {
        WebhookConfig v1 = new WebhookConfig();
        v1.setId("hook-1");
        v1.setName("original");
        dao.createWebhook("hook-1", v1);

        WebhookConfig v2 = new WebhookConfig();
        v2.setId("hook-1");
        v2.setName("updated");
        dao.createWebhook("hook-1", v2);

        assertEquals("updated", dao.getWebhook("hook-1").getName());
    }

    @Test
    public void removeWebhook_unknownId_throwsNotFound() {
        assertThrows(NotFoundException.class, () -> dao.removeWebhook("missing"));
    }

    @Test
    public void crud_incomingWebhookEvent_roundtrip() {
        IncomingWebhookEvent event =
                IncomingWebhookEvent.builder()
                        .id("ev-1")
                        .webhookId("hook-1")
                        .body("{\"event\":\"push\"}")
                        .timeStamp(123L)
                        .build();
        dao.createIncomingWebhookEvent("ev-1", event);
        assertEquals("hook-1", dao.getWebhookEvent("ev-1").getWebhookId());
        dao.removeWebhookEvent("ev-1");
        assertNull(dao.getWebhookEvent("ev-1"));
    }

    @Test
    public void getMatchers_emptyOrNullTargets_returnsEmpty() {
        WebhookConfig config = new WebhookConfig();
        config.setId("hook-1");
        dao.createWebhook("hook-1", config);

        dao.createMatchers(config, Map.of());
        assertTrue(dao.getMatchers("hook-1").isEmpty());

        dao.createMatchers(config, null);
        assertTrue(dao.getMatchers("hook-1").isEmpty());
    }

    @Test
    public void getMatchers_recomputesFromMetadataDAO_onEachCall_noStaleCache() {
        WebhookConfig config = new WebhookConfig();
        config.setId("hook-1");
        dao.createWebhook("hook-1", config);
        dao.createMatchers(config, Map.of("wf-a", 1));

        WorkflowTask t1 = new WorkflowTask();
        t1.setType("WAIT_FOR_WEBHOOK");
        t1.setTaskReferenceName("wait_ref");
        t1.setInputParameters(Map.of("matches", Map.of("event", "push")));
        WorkflowDef def1 = new WorkflowDef();
        def1.setName("wf-a");
        def1.setVersion(1);
        def1.setTasks(List.of(t1));
        when(metadataDAO.getWorkflowDef("wf-a", 1)).thenReturn(Optional.of(def1));

        assertEquals(Map.of("event", "push"), dao.getMatchers("hook-1").get("wf-a;1;wait_ref"));

        WorkflowTask t2 = new WorkflowTask();
        t2.setType("WAIT_FOR_WEBHOOK");
        t2.setTaskReferenceName("wait_ref");
        t2.setInputParameters(Map.of("matches", Map.of("event", "merge")));
        WorkflowDef def2 = new WorkflowDef();
        def2.setName("wf-a");
        def2.setVersion(1);
        def2.setTasks(List.of(t2));
        when(metadataDAO.getWorkflowDef("wf-a", 1)).thenReturn(Optional.of(def2));

        assertEquals(Map.of("event", "merge"), dao.getMatchers("hook-1").get("wf-a;1;wait_ref"));
    }

    @Test
    public void removeMatchers_drops() {
        WebhookConfig config = new WebhookConfig();
        config.setId("hook-1");
        dao.createWebhook("hook-1", config);
        dao.createMatchers(config, Map.of("wf-a", 1));

        dao.removeMatchers("hook-1");
        assertTrue(dao.getMatchers("hook-1").isEmpty());
    }
}
