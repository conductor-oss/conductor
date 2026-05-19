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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.conductoross.conductor.dao.webhook.WebhookDAO;
import org.conductoross.conductor.webhook.model.IncomingWebhookEvent;
import org.conductoross.conductor.webhook.model.WebhookConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.dao.MetadataDAO;

import static org.conductoross.conductor.service.webhook.WebhookTaskService.Constants.WAIT_FOR_WEBHOOK;
import static org.conductoross.conductor.service.webhook.WebhookTaskService.Constants.WEBHOOK_DELIMITER;

/**
 * Default single-node implementation of {@link WebhookDAO}.
 *
 * <p>Backed by in-process maps; suitable for single-server deployments and tests. Multi-node
 * deployments should bind a persistent implementation (lands in a later PR).
 */
@Component
@ConditionalOnMissingBean(name = "webhookDAO")
public class InMemoryWebhookDAO implements WebhookDAO {

    private final MetadataDAO metadataDAO;
    private final ConcurrentHashMap<String, WebhookConfig> configs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, IncomingWebhookEvent> events =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Map<String, Map<String, Object>>> matchers =
            new ConcurrentHashMap<>();

    public InMemoryWebhookDAO(MetadataDAO metadataDAO) {
        this.metadataDAO = metadataDAO;
    }

    @Override
    public void createWebhook(String id, WebhookConfig config) {
        configs.put(id, config);
    }

    @Override
    public WebhookConfig getWebhook(String id) {
        return configs.get(id);
    }

    @Override
    public List<WebhookConfig> getAllWebhooks() {
        return new ArrayList<>(configs.values());
    }

    @Override
    public void removeWebhook(String id) {
        configs.remove(id);
    }

    @Override
    public void createIncomingWebhookEvent(String id, IncomingWebhookEvent event) {
        events.put(id, event);
    }

    @Override
    public IncomingWebhookEvent getWebhookEvent(String id) {
        return events.get(id);
    }

    @Override
    public void removeWebhookEvent(String id) {
        events.remove(id);
    }

    @Override
    public Map<String, Map<String, Object>> getMatchers(String webhookId) {
        return matchers.getOrDefault(webhookId, Collections.emptyMap());
    }

    @Override
    @SuppressWarnings("unchecked")
    public void createMatchers(
            WebhookConfig webhookConfig,
            Map<String, Integer> receiverWorkflowNamesToVersionsOverride) {
        if (receiverWorkflowNamesToVersionsOverride == null) {
            matchers.put(webhookConfig.getId(), Collections.emptyMap());
            return;
        }
        Map<String, Map<String, Object>> computed = new HashMap<>();
        receiverWorkflowNamesToVersionsOverride.forEach(
                (workflowName, wfVersion) -> {
                    Optional<WorkflowDef> def = metadataDAO.getWorkflowDef(workflowName, wfVersion);
                    if (def.isEmpty()) {
                        return;
                    }
                    for (WorkflowTask task : def.get().collectTasks()) {
                        String type = task.getType();
                        if (!WAIT_FOR_WEBHOOK.equals(type)
                                && !TaskType.WAIT.toString().equals(type)) {
                            continue;
                        }
                        Object raw = task.getInputParameters().get("matches");
                        if (raw instanceof Map<?, ?> m && !CollectionUtils.isEmpty(m)) {
                            String key =
                                    workflowName
                                            + WEBHOOK_DELIMITER
                                            + wfVersion
                                            + WEBHOOK_DELIMITER
                                            + task.getTaskReferenceName();
                            computed.put(key, (Map<String, Object>) m);
                        }
                    }
                });
        matchers.put(webhookConfig.getId(), computed);
    }

    @Override
    public void removeMatchers(String id) {
        matchers.remove(id);
    }
}
