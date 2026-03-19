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

/**
 * Stores the mapping from a webhook routing hash to the set of task IDs that are waiting for a
 * matching inbound webhook event.
 *
 * <p>The default implementation ({@link InMemoryWebhookTaskDAO}) is suitable for single-node
 * deployments. Alternative implementations backed by durable storage (e.g., Postgres, Redis) can be
 * registered as Spring beans to replace the default.
 */
public interface WebhookTaskDAO {

    /**
     * Register a waiting task against a routing hash.
     *
     * @param hash the routing hash computed from the task's {@code matches} expressions
     * @param taskId the ID of the task to complete when a matching webhook event arrives
     */
    void put(String hash, String taskId);

    /**
     * Return all task IDs registered for the given hash.
     *
     * @param hash the routing hash computed from an inbound webhook event
     * @return list of task IDs that should be completed; empty list if none
     */
    List<String> get(String hash);

    /**
     * Remove a task registration, typically after it has been completed or cancelled.
     *
     * @param hash the routing hash
     * @param taskId the task ID to deregister
     */
    void remove(String hash, String taskId);
}
