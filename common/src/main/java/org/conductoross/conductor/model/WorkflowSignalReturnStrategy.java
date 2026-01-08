/*
 * Copyright 2025 Conductor Authors.
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
package org.conductoross.conductor.model;

public enum WorkflowSignalReturnStrategy {
    /**
     * The state of the workflow that was specified via workflow (execution) ID is returned, even if
     * the currently blocking task belongs to a subworkflow.
     */
    TARGET_WORKFLOW,

    /**
     * The state of the workflow that is currently blocking is returned. This might be a potentially
     * deep subworkflow of the workflow specified in the initial API request.
     */
    BLOCKING_WORKFLOW,

    /**
     * The state of the task that is currently blocking is returned. This might be a task in a
     * potentially deep subworkflow of the workflow specified in the initial API request.
     */
    BLOCKING_TASK,

    /**
     * The input for the task that is currently blocking is returned. This might be a task in a
     * potentially deep subworkflow of the workflow specified in the initial API request.
     */
    BLOCKING_TASK_INPUT;

    // This unfortunately got much more difficult to implement when the notification service was
    // made to notify with
    // subworkflow data directly rather than notify the parent.
    /// **
    // * The state of each task that is currently blocking is returned. This might include tasks in
    // potentially deep
    // * subworkflows of the workflow specified in the initial API request.
    // */
    // ALL_BLOCKING_TASKS,
}
