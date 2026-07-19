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
package org.conductoross.conductor.service.webhook;

import java.util.Set;

import com.netflix.conductor.model.TaskModel;

public interface WebhookTaskService {

    void remove(String hash, String taskId);

    /**
     * @param hash
     * @return List of matching task ids
     */
    Set<String> get(String hash);

    /**
     * @param taskModel
     * @param workflowVersion
     */
    void put(TaskModel taskModel, int workflowVersion);

    /**
     * Returns all task IDs waiting on the given webhookId. Implementations that maintain a
     * webhookId secondary index override this; others return an empty set.
     *
     * @param webhookId webhook identifier value from the task's matches map
     * @return snapshot set of task IDs (never null)
     */
    default Set<String> getByWebhookId(String webhookId) {
        return Set.of();
    }

    /**
     * Removes a task from the webhookId index. No-op if the implementation does not maintain one.
     *
     * @param webhookId webhook identifier value from the task's matches map
     * @param taskId task ID to remove
     */
    default void removeByWebhookId(String webhookId, String taskId) {}

    class Constants {
        public static final String WAIT_FOR_WEBHOOK = "WAIT_FOR_WEBHOOK";
        public static final String WEBHOOK_DELIMITER = ";";
    }
}
