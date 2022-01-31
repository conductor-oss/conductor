/*
 * Copyright 2022 Netflix, Inc.
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
package com.netflix.conductor.dao;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.model.TaskModel;

/**
 * A contract to support concurrency limits of tasks.
 *
 * @since v3.3.5.
 */
public interface ConcurrentExecutionLimitDAO {

    default void addTaskToLimit(TaskModel task) {
        throw new UnsupportedOperationException(
                getClass() + " does not support addTaskToLimit method.");
    }

    default void removeTaskFromLimit(TaskModel task) {
        throw new UnsupportedOperationException(
                getClass() + " does not support removeTaskFromLimit method.");
    }

    /**
     * Checks if the number of tasks in progress for the given taskDef will exceed the limit if the
     * task is scheduled to be in progress (given to the worker or for system tasks start() method
     * called)
     *
     * @param task The task to be executed. Limit is set in the Task's definition
     * @return true if by executing this task, the limit is breached. false otherwise.
     * @see TaskDef#concurrencyLimit()
     */
    boolean exceedsLimit(TaskModel task);
}
