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
package org.conductoross.conductor.core.execution.tasks.annotated;

import com.netflix.conductor.model.TaskModel;

/**
 * Context that holds the current Task being executed. This allows annotated methods to access the
 * Task object and its properties.
 */
public class TaskContext {

    private static final ThreadLocal<TaskContext> THREAD_LOCAL = new ThreadLocal<>();

    private final TaskModel taskModel;

    private TaskContext(TaskModel taskModel) {
        this.taskModel = taskModel;
    }

    /**
     * Sets the current TaskContext for the thread.
     *
     * @param taskModel The task model to set
     * @return The created TaskContext
     */
    public static TaskContext set(TaskModel taskModel) {
        TaskContext context = new TaskContext(taskModel);
        THREAD_LOCAL.set(context);
        return context;
    }

    /**
     * @return The current TaskContext or null if not set
     */
    public static TaskContext get() {
        return THREAD_LOCAL.get();
    }

    /** Clears the TaskContext for the current thread. */
    public static void clear() {
        THREAD_LOCAL.remove();
    }

    public TaskModel getTaskModel() {
        return taskModel;
    }

    public String getWorkflowInstanceId() {
        return taskModel.getWorkflowInstanceId();
    }

    public String getTaskId() {
        return taskModel.getTaskId();
    }

    public String getWorkerId() {
        return taskModel.getWorkerId();
    }

    public int getRetryCount() {
        return taskModel.getRetryCount();
    }

    public int getPollCount() {
        return taskModel.getPollCount();
    }

    public long getCallbackAfterSeconds() {
        return taskModel.getCallbackAfterSeconds();
    }

    public void setCallbackAfter(long seconds) {
        taskModel.setCallbackAfterSeconds(seconds);
    }

    public void addLog(String log) {
        // TaskModel does not support logs directly.
        // For system tasks, logging is typically handled via SLF4J or side effects.
        // We might want to support attaching logs to execution metadata or similar in
        // future.
    }
}
