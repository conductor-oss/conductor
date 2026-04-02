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
package org.conductoross.conductor.common.webhook;

import java.util.List;
import java.util.Map;

/**
 * Storage interface for {@link WebhookConfig} objects.
 *
 * <p>The default implementation ({@code InMemoryWebhookConfigDAO}) holds configs in memory and
 * loses them on restart. Production deployments should register a durable implementation (Postgres,
 * Redis, etc.) as a Spring bean — the {@code @ConditionalOnMissingBean} on the in-memory
 * implementation ensures it will be skipped automatically.
 *
 * <p>No {@code orgId} parameter appears anywhere in this interface. Multi-tenancy is the
 * implementation's concern: Orkes Enterprise implementations read the org from their request
 * context internally, keeping the interface portable.
 */
public interface WebhookConfigDAO {

    /**
     * Stores a webhook config. Creates a new entry if no config with this id exists; replaces the
     * existing entry otherwise.
     *
     * @param id the webhook config id (must not be null)
     * @param config the config to store
     */
    void save(String id, WebhookConfig config);

    /**
     * Retrieves a webhook config by id.
     *
     * @param id the webhook config id
     * @return the config, or {@code null} if not found
     */
    WebhookConfig get(String id);

    /**
     * Removes the webhook config with the given id. A no-op if no such config exists.
     *
     * @param id the webhook config id
     */
    void remove(String id);

    /**
     * Returns all stored webhook configs.
     *
     * @return a list of all configs; empty list if none
     */
    List<WebhookConfig> getAll();

    // -------------------------------------------------------------------------
    // Matchers — pre-computed routing index
    // -------------------------------------------------------------------------

    /**
     * Stores the matcher index for a webhook config. The map key is the base hash ({@code
     * workflowName;version;taskRefName}) and the value is the {@code matches} criteria map from the
     * corresponding {@code WAIT_FOR_WEBHOOK} task definition.
     *
     * <p>Replaces any previously stored matchers for the same id.
     *
     * @param webhookId the webhook config id
     * @param matchers the matcher map (may be empty but not null)
     */
    void saveMatchers(String webhookId, Map<String, Map<String, Object>> matchers);

    /**
     * Returns the matcher index for a webhook config, or an empty map if none is stored.
     *
     * @param webhookId the webhook config id
     * @return matcher map; never null
     */
    Map<String, Map<String, Object>> getMatchers(String webhookId);

    /**
     * Removes the matcher index for a webhook config. A no-op if none exists.
     *
     * @param webhookId the webhook config id
     */
    void removeMatchers(String webhookId);
}
