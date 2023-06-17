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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.model.TaskModel;

/** Stub listener default implementation */
public class TaskStatusListenerStub implements TaskStatusListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskStatusListenerStub.class);

    @Override
    public void onTaskScheduled(TaskModel task) {
        LOGGER.debug("Task {} is scheduled", task.getTaskId());
    }

    @Override
    public void onTaskCanceled(TaskModel task) {
        LOGGER.debug("Task {} is canceled", task.getTaskId());
    }

    @Override
    public void onTaskCompleted(TaskModel task) {
        LOGGER.debug("Task {} is completed", task.getTaskId());
    }

    @Override
    public void onTaskCompletedWithErrors(TaskModel task) {
        LOGGER.debug("Task {} is completed with errors", task.getTaskId());
    }

    @Override
    public void onTaskFailed(TaskModel task) {
        LOGGER.debug("Task {} is failed", task.getTaskId());
    }

    @Override
    public void onTaskFailedWithTerminalError(TaskModel task) {
        LOGGER.debug("Task {} is failed with terminal error", task.getTaskId());
    }

    @Override
    public void onTaskInProgress(TaskModel task) {
        LOGGER.debug("Task {} is in-progress", task.getTaskId());
    }

    @Override
    public void onTaskSkipped(TaskModel task) {
        LOGGER.debug("Task {} is skipped", task.getTaskId());
    }

    @Override
    public void onTaskTimedOut(TaskModel task) {
        LOGGER.debug("Task {} is timed out", task.getTaskId());
    }
}
