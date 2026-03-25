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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.core.exception.ConflictException;
import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.dao.MetadataDAO;

/**
 * Business-logic layer for {@link WebhookConfig} lifecycle management.
 *
 * <p>When a webhook config is created or updated, this service pre-computes the <em>matcher
 * index</em> — a map of {@code workflowName;version;taskRefName} → {@code matches} criteria. This
 * index is persisted in {@link WebhookConfigDAO} and used at inbound-event time to compute routing
 * hashes without re-scanning workflow definitions on every request.
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
    private final MetadataDAO metadataDAO;

    public WebhookConfigService(WebhookConfigDAO webhookConfigDAO, MetadataDAO metadataDAO) {
        this.webhookConfigDAO = webhookConfigDAO;
        this.metadataDAO = metadataDAO;
    }

    /**
     * Creates a new webhook config. If the config already has an id and one already exists under
     * that id, a {@link ConflictException} is thrown (→ HTTP 409). If no id is supplied, a random
     * UUID is assigned.
     *
     * @param config the config to create
     * @throws ConflictException if a config with the supplied id already exists
     */
    public void createWebhook(WebhookConfig config) {
        if (config.getId() != null) {
            WebhookConfig existing = webhookConfigDAO.get(config.getId());
            if (existing != null) {
                throw new ConflictException(
                        "Webhook config with id " + config.getId() + " already exists");
            }
        } else {
            config.setId(UUID.randomUUID().toString());
        }
        webhookConfigDAO.save(config.getId(), config);
        webhookConfigDAO.saveMatchers(config.getId(), computeMatchers(config));
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
        webhookConfigDAO.saveMatchers(config.getId(), computeMatchers(config));
        LOGGER.debug("Updated webhook config id={} name={}", config.getId(), config.getName());
    }

    /**
     * Deletes a webhook config by id, including its matcher index.
     *
     * @param id the webhook config id
     * @throws NotFoundException if no config exists for the given id
     */
    public void deleteWebhook(String id) {
        WebhookConfig existing = webhookConfigDAO.get(id);
        if (existing == null) {
            throw new NotFoundException("Webhook config with id " + id + " does not exist");
        }
        webhookConfigDAO.removeMatchers(id);
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
     * <p>Each returned config is a shallow copy so that masking the {@code secretValue} field does
     * not mutate the stored instance. This matters for in-memory DAO implementations where {@code
     * getAll()} returns references to the live stored objects.
     *
     * @return list of all configs with secrets replaced by {@link #SECRET_MASK}; empty list if none
     */
    public List<WebhookConfig> getAllWebhooks() {
        return webhookConfigDAO.getAll().stream()
                .map(
                        c -> {
                            WebhookConfig masked = c.shallowCopy();
                            masked.setSecretValue(SECRET_MASK);
                            return masked;
                        })
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Matcher computation
    // -------------------------------------------------------------------------

    /**
     * Scans the workflow definitions listed in {@link
     * WebhookConfig#getReceiverWorkflowNamesToVersions()} and builds the matcher index.
     *
     * <p>The key is {@code workflowName;version;taskRefName} (the base hash prefix). The value is
     * the {@code matches} map from the task's {@code inputParameters} — the unresolved template
     * (e.g. {@code "$['data']['orderId']": "${workflow.input.orderId}"}). At inbound event time,
     * {@link WebhookHashingService#computeInboundHash} extracts actual values from the payload
     * using these paths; any expected value starting with {@code "$"} is treated as a wildcard.
     *
     * <p>If a workflow definition does not exist yet it is silently skipped — matchers will be
     * recomputed on the next update.
     */
    @SuppressWarnings("unchecked")
    Map<String, Map<String, Object>> computeMatchers(WebhookConfig config) {
        if (CollectionUtils.isEmpty(config.getReceiverWorkflowNamesToVersions())) {
            return Collections.emptyMap();
        }
        Map<String, Map<String, Object>> matchers = new HashMap<>();
        config.getReceiverWorkflowNamesToVersions()
                .forEach(
                        (workflowName, version) -> {
                            Optional<WorkflowDef> defOpt =
                                    metadataDAO.getWorkflowDef(workflowName, version);
                            if (defOpt.isEmpty()) {
                                LOGGER.warn(
                                        "Workflow def not found for matcher computation: name={} version={} — skipping",
                                        workflowName,
                                        version);
                                return;
                            }
                            WorkflowDef def = defOpt.get();
                            def.collectTasks().stream()
                                    .filter(
                                            t ->
                                                    TaskType.TASK_TYPE_WAIT_FOR_WEBHOOK.equals(
                                                            t.getType()))
                                    .forEach(
                                            task -> {
                                                Map<String, Object> matches =
                                                        (Map<String, Object>)
                                                                task.getInputParameters()
                                                                        .get("matches");
                                                if (!CollectionUtils.isEmpty(matches)) {
                                                    String key =
                                                            workflowName
                                                                    + WebhookHashingService
                                                                            .DELIMITER
                                                                    + version
                                                                    + WebhookHashingService
                                                                            .DELIMITER
                                                                    + WebhookHashingService
                                                                            .stripIterationSuffix(
                                                                                    task
                                                                                            .getTaskReferenceName());
                                                    matchers.put(key, matches);
                                                }
                                            });
                        });
        return matchers;
    }
}
