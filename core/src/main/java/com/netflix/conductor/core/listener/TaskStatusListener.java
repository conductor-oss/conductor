/*
 * Copyright 2023 Netflix, Inc.
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
package com.netflix.conductor.core.listener;

import com.netflix.conductor.model.TaskModel;

/**
 * Listener for the Task status change. All methods have default implementation so that
 * Implementation can choose to override a subset of interested Task statuses.
 */
public interface TaskStatusListener {

    default void onTaskScheduled(TaskModel task) {}

    default void onTaskInProgress(TaskModel task) {}

    default void onTaskCanceled(TaskModel task) {}

    default void onTaskFailed(TaskModel task) {}

    default void onTaskFailedWithTerminalError(TaskModel task) {}

    default void onTaskCompleted(TaskModel task) {}

    default void onTaskCompletedWithErrors(TaskModel task) {}

    default void onTaskTimedOut(TaskModel task) {}

    default void onTaskSkipped(TaskModel task) {}
}
