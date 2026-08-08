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
package org.conductoross.conductor.webhook.dao.memory;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.conductoross.conductor.webhook.model.IncomingWebhookEvent;
import org.conductoross.conductor.webhook.model.WebhookConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.dao.MetadataDAO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InMemoryWebhookDAOTest {

    @Mock private MetadataDAO metadataDAO;

    private InMemoryWebhookDAO dao;

    @BeforeEach
    void setUp() {
        dao = new InMemoryWebhookDAO(metadataDAO);
    }

    @Test
    void crud_webhookConfig() {
        WebhookConfig config = new WebhookConfig();
        config.setId("hook-1");

        dao.createWebhook("hook-1", config);

        assertThat(dao.getWebhook("hook-1")).isSameAs(config);
        assertThat(dao.getAllWebhooks()).containsExactly(config);

        dao.removeWebhook("hook-1");
        assertThat(dao.getWebhook("hook-1")).isNull();
        assertThat(dao.getAllWebhooks()).isEmpty();
    }

    @Test
    void crud_incomingWebhookEvent() {
        IncomingWebhookEvent event = IncomingWebhookEvent.builder().id("ev-1").build();

        dao.createIncomingWebhookEvent("ev-1", event);
        assertThat(dao.getWebhookEvent("ev-1")).isSameAs(event);

        dao.removeWebhookEvent("ev-1");
        assertThat(dao.getWebhookEvent("ev-1")).isNull();
    }

    @Test
    void createMatchers_nullOverride_storesEmpty() {
        WebhookConfig config = new WebhookConfig();
        config.setId("hook-1");

        dao.createMatchers(config, null);

        assertThat(dao.getMatchers("hook-1")).isEmpty();
    }

    @Test
    void createMatchers_workflowNotFound_skipped() {
        WebhookConfig config = new WebhookConfig();
        config.setId("hook-1");
        when(metadataDAO.getWorkflowDef("wf-missing", 1)).thenReturn(Optional.empty());

        dao.createMatchers(config, Map.of("wf-missing", 1));

        assertThat(dao.getMatchers("hook-1")).isEmpty();
    }

    @Test
    void createMatchers_taskWithMatches_stored() {
        WebhookConfig config = new WebhookConfig();
        config.setId("hook-1");

        WorkflowTask waitTask = new WorkflowTask();
        waitTask.setType("WAIT_FOR_WEBHOOK");
        waitTask.setTaskReferenceName("wait_ref");
        waitTask.setInputParameters(Map.of("matches", Map.of("event", "push")));

        WorkflowDef def = new WorkflowDef();
        def.setName("wf-a");
        def.setVersion(1);
        def.setTasks(List.of(waitTask));

        when(metadataDAO.getWorkflowDef("wf-a", 1)).thenReturn(Optional.of(def));

        dao.createMatchers(config, Map.of("wf-a", 1));

        Map<String, Map<String, Object>> matchers = dao.getMatchers("hook-1");
        assertThat(matchers).hasSize(1);
        assertThat(matchers).containsKey("wf-a;1;wait_ref");
        assertThat(matchers.get("wf-a;1;wait_ref")).containsEntry("event", "push");
    }

    @Test
    void createMatchers_taskWithoutMatches_skipped() {
        WebhookConfig config = new WebhookConfig();
        config.setId("hook-1");

        WorkflowTask waitTask = new WorkflowTask();
        waitTask.setType("WAIT_FOR_WEBHOOK");
        waitTask.setTaskReferenceName("wait_ref");
        waitTask.setInputParameters(Map.of()); // no "matches" key

        WorkflowDef def = new WorkflowDef();
        def.setName("wf-a");
        def.setVersion(1);
        def.setTasks(List.of(waitTask));

        when(metadataDAO.getWorkflowDef("wf-a", 1)).thenReturn(Optional.of(def));

        dao.createMatchers(config, Map.of("wf-a", 1));

        assertThat(dao.getMatchers("hook-1")).isEmpty();
    }

    @Test
    void createMatchers_nonWebhookTask_skipped() {
        WebhookConfig config = new WebhookConfig();
        config.setId("hook-1");

        WorkflowTask simpleTask = new WorkflowTask();
        simpleTask.setType("SIMPLE");
        simpleTask.setTaskReferenceName("simple_ref");
        simpleTask.setInputParameters(Map.of("matches", Map.of("event", "push")));

        WorkflowDef def = new WorkflowDef();
        def.setName("wf-a");
        def.setVersion(1);
        def.setTasks(List.of(simpleTask));

        when(metadataDAO.getWorkflowDef("wf-a", 1)).thenReturn(Optional.of(def));

        dao.createMatchers(config, Map.of("wf-a", 1));

        assertThat(dao.getMatchers("hook-1")).isEmpty();
    }

    @Test
    void getMatchers_reflectsWorkflowDefUpdates_noStaleCache() {
        WebhookConfig config = new WebhookConfig();
        config.setId("hook-1");

        WorkflowTask original = new WorkflowTask();
        original.setType("WAIT_FOR_WEBHOOK");
        original.setTaskReferenceName("wait_ref");
        original.setInputParameters(Map.of("matches", Map.of("event", "push")));
        WorkflowDef def1 = new WorkflowDef();
        def1.setName("wf-a");
        def1.setVersion(1);
        def1.setTasks(List.of(original));

        when(metadataDAO.getWorkflowDef("wf-a", 1)).thenReturn(Optional.of(def1));
        dao.createMatchers(config, Map.of("wf-a", 1));

        assertThat(dao.getMatchers("hook-1").get("wf-a;1;wait_ref")).containsEntry("event", "push");

        // Simulate a WorkflowDef update: same name+version, different matches.
        WorkflowTask updated = new WorkflowTask();
        updated.setType("WAIT_FOR_WEBHOOK");
        updated.setTaskReferenceName("wait_ref");
        updated.setInputParameters(Map.of("matches", Map.of("event", "merge")));
        WorkflowDef def2 = new WorkflowDef();
        def2.setName("wf-a");
        def2.setVersion(1);
        def2.setTasks(List.of(updated));
        when(metadataDAO.getWorkflowDef("wf-a", 1)).thenReturn(Optional.of(def2));

        // Without re-running createMatchers, the next getMatchers must reflect
        // the new WorkflowDef. Old impl cached at createMatchers time and would
        // return the stale "push" criteria.
        assertThat(dao.getMatchers("hook-1").get("wf-a;1;wait_ref"))
                .containsEntry("event", "merge");
    }

    @Test
    void removeMatchers_drops() {
        WebhookConfig config = new WebhookConfig();
        config.setId("hook-1");
        dao.createMatchers(config, null);

        dao.removeMatchers("hook-1");

        assertThat(dao.getMatchers("hook-1")).isEmpty();
    }
}
