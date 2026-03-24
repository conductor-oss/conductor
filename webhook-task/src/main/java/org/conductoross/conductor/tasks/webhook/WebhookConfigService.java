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
package org.conductoross.conductor.tasks.webhook;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.core.exception.NotFoundException;

/**
 * Business-logic layer for {@link WebhookConfig} lifecycle management.
 *
 * <p>This service is intentionally free of enterprise concerns (audit logging, tags, auth). Orkes
 * Enterprise adds those concerns at the REST layer or via decorators.
 *
 * <p>Secret values are masked ("***") in the list response so they are not exposed to API clients.
 */
public class WebhookConfigService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebhookConfigService.class);

    static final String SECRET_MASK = "***";

    private final WebhookConfigDAO webhookConfigDAO;

    public WebhookConfigService(WebhookConfigDAO webhookConfigDAO) {
        this.webhookConfigDAO = webhookConfigDAO;
    }

    /**
     * Creates a new webhook config. If the config already has an id and one already exists under
     * that id, an {@link IllegalArgumentException} is thrown. If no id is supplied, a random UUID
     * is assigned.
     *
     * @param config the config to create
     * @throws IllegalArgumentException if a config with the supplied id already exists
     */
    public void createWebhook(WebhookConfig config) {
        if (config.getId() != null) {
            WebhookConfig existing = webhookConfigDAO.get(config.getId());
            if (existing != null) {
                throw new IllegalArgumentException(
                        "Webhook config with id " + config.getId() + " already exists");
            }
        } else {
            config.setId(UUID.randomUUID().toString());
        }
        webhookConfigDAO.save(config.getId(), config);
        LOGGER.debug("Created webhook config id={} name={}", config.getId(), config.getName());
    }

    /**
     * Updates an existing webhook config.
     *
     * <p>If the incoming {@link WebhookConfig#getSecretValue()} equals {@link #SECRET_MASK}, the
     * existing secret is preserved (the client sent back the masked value unchanged).
     *
     * @param config the updated config (must have a non-null id)
     * @throws NotFoundException if no config exists for the given id
     */
    public void updateWebhook(WebhookConfig config) {
        WebhookConfig existing = webhookConfigDAO.get(config.getId());
        if (existing == null) {
            throw new NotFoundException(
                    "Webhook config with id " + config.getId() + " does not exist");
        }
        // Preserve the stored secret if the client sent back the masked placeholder
        if (SECRET_MASK.equals(config.getSecretValue())) {
            config.setSecretValue(existing.getSecretValue());
        }
        // Preserve urlVerified flag — only the verification flow may set this
        config.setUrlVerified(existing.isUrlVerified());

        webhookConfigDAO.save(config.getId(), config);
        LOGGER.debug("Updated webhook config id={} name={}", config.getId(), config.getName());
    }

    /**
     * Deletes a webhook config by id.
     *
     * @param id the webhook config id
     * @throws NotFoundException if no config exists for the given id
     */
    public void deleteWebhook(String id) {
        WebhookConfig existing = webhookConfigDAO.get(id);
        if (existing == null) {
            throw new NotFoundException("Webhook config with id " + id + " does not exist");
        }
        webhookConfigDAO.remove(id);
        LOGGER.debug("Deleted webhook config id={}", id);
    }

    /**
     * Returns the webhook config for the given id, or {@code null} if not found.
     *
     * @param id the webhook config id
     * @return the config, or {@code null}
     */
    public WebhookConfig getWebhook(String id) {
        return webhookConfigDAO.get(id);
    }

    /**
     * Returns all webhook configs with secret values masked.
     *
     * @return list of all configs; empty list if none
     */
    public List<WebhookConfig> getAllWebhooks() {
        List<WebhookConfig> configs = webhookConfigDAO.getAll();
        configs.forEach(c -> c.setSecretValue(SECRET_MASK));
        return configs;
    }
}
