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

/**
 * Hook for establishing org context before processing a webhook request.
 *
 * <p>In OSS Conductor (single-tenant) this is a no-op registered by {@code
 * WebhookAutoConfiguration}. In Orkes Enterprise, the implementation extracts the {@code orgId}
 * from the webhook ID (encoded via {@code TimeBasedUUIDGenerator}) and sets it on {@code
 * OrkesRequestContext} so that DAO calls are correctly scoped.
 *
 * <p>Called once per inbound HTTP request before any DAO interaction, both for POST (event) and GET
 * (ping/challenge) endpoints.
 */
public interface WebhookOrgContextProvider {

    /**
     * Applies whatever org context is appropriate for the given webhook ID. Called at the start of
     * each inbound webhook request.
     *
     * @param webhookId the webhook configuration ID from the URL path
     */
    void applyContext(String webhookId);
}
