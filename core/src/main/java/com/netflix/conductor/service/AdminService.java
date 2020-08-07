/*
 * Copyright 2018 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.conductor.service;

import com.netflix.conductor.common.metadata.tasks.Task;

import javax.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;

public interface AdminService {

    /**
     * Queue up all the running workflows for sweep.
     *
     * @param workflowId Id of the workflow
     * @return the id of the workflow instance that can be use for tracking.
     */
    String requeueSweep(@NotEmpty(message = "WorkflowId cannot be null or empty.") String workflowId);

    /**
     * Get all the configuration parameters.
     * @return all the configuration parameters.
     */
    Map<String, Object> getAllConfig();

    /**
     * Get the list of pending tasks for a given task type.
     *
     * @param taskType Name of the task
     * @param start Start index of pagination
     * @param count Number of entries
     * @return list of pending {@link Task}
     */
    List<Task> getListOfPendingTask(@NotEmpty(message = "TaskType cannot be null or empty.") String taskType,
                                    Integer start, Integer count);

    /**
     * Verify that the Workflow is consistent, and run repairs as needed.
     * @param workflowId
     * @return
     */
    boolean verifyAndRepairWorkflowConsistency(@NotEmpty(message = "WorkflowId cannot be null or empty.") String workflowId);
}
