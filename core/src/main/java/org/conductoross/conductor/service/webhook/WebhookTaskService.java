/*
 * Copyright 2022 Conductor Authors.
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
     * Compute the correlation hash from the task + workflow version (same formula {@link #put}
     * uses) and remove the taskId from the corresponding set. Use this when cancelling a
     * WAIT_FOR_WEBHOOK task whose hash isn't already known — notably when the parent workflow is
     * terminated before a matching event arrives, in which case {@code WebhookWorker.handleEvent}
     * never fires and the set entry would otherwise leak indefinitely.
     */
    void remove(TaskModel taskModel, int workflowVersion);

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

    class Constants {

        public static final String WAIT_FOR_WEBHOOK = "WAIT_FOR_WEBHOOK";
        public static final String WEBHOOK_DELIMITER = ";";
    }
}
