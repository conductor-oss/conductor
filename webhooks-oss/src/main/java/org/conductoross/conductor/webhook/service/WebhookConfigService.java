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
package org.conductoross.conductor.webhook.service;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.conductoross.conductor.dao.webhook.WebhookDAO;
import org.conductoross.conductor.service.webhook.TargetWorkflowCollector;
import org.conductoross.conductor.webhook.model.WebhookConfig;
import org.springframework.stereotype.Service;

import com.netflix.conductor.core.exception.ConflictException;
import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.core.utils.IDGenerator;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@AllArgsConstructor
public class WebhookConfigService {

    public static final String SECRET = "***";

    private final WebhookDAO webhookDAO;
    private final TargetWorkflowCollector targetWorkflowCollector;
    private final IDGenerator idGenerator;

    public void createWebhook(WebhookConfig webhookConfig) {
        if (webhookConfig.getId() != null) {
            WebhookConfig existing = getWebhook(webhookConfig.getId());
            if (existing != null) {
                throw new ConflictException(
                        "Webhook with id " + webhookConfig.getId() + " already exists");
            }
        } else {
            webhookConfig.setId(idGenerator.generate());
        }
        createMatchers(webhookConfig);
        webhookDAO.createWebhook(webhookConfig.getId(), webhookConfig);
    }

    public void storeWebhook(WebhookConfig webhookConfig) {
        webhookDAO.createWebhook(webhookConfig.getId(), webhookConfig);
        createMatchers(webhookConfig);
    }

    private void createMatchers(WebhookConfig webhookConfig) {
        webhookConfig.accept(targetWorkflowCollector);
        webhookDAO.createMatchers(
                webhookConfig, targetWorkflowCollector.getWorkflowsToCompleteWebhooks());
    }

    public void removeWebhook(String id) {
        webhookDAO.removeMatchers(id);
        webhookDAO.removeWebhook(id);
    }

    public WebhookConfig getWebhook(String id) {
        return webhookDAO.getWebhook(id);
    }

    public List<WebhookConfig> getWebhooks() {
        return webhookDAO.getAllWebhooks().stream()
                .map(c -> c.toBuilder().secretValue(SECRET).build())
                .toList();
    }

    public void updateWebhook(WebhookConfig webhookConfig) {
        WebhookConfig existing = getWebhook(webhookConfig.getId());
        if (existing == null) {
            throw new NotFoundException(
                    "Webhook with id " + webhookConfig.getId() + " does not exist");
        }
        if (!StringUtils.isEmpty(webhookConfig.getSecretValue())
                && SECRET.equals(webhookConfig.getSecretValue())) {
            webhookConfig.setSecretValue(existing.getSecretValue());
        }
        webhookConfig.setUrlVerified(existing.isUrlVerified());
        storeWebhook(webhookConfig);
    }
}
