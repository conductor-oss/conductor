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
package com.netflix.conductor.core.listener;

import org.junit.Test;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.model.TaskModel;

import static org.junit.Assert.assertEquals;

public class TaskStatusListenerTest {

    private static class TestTaskStatusListener implements TaskStatusListener {
        int scheduledCount = 0;
        int inProgressCount = 0;
        int completedCount = 0;
        int completedWithErrorsCount = 0;
        int canceledCount = 0;
        int failedCount = 0;
        int failedWithTerminalErrorCount = 0;
        int timedOutCount = 0;
        int skippedCount = 0;

        @Override
        public void onTaskScheduled(TaskModel task) {
            scheduledCount++;
        }

        @Override
        public void onTaskInProgress(TaskModel task) {
            inProgressCount++;
        }

        @Override
        public void onTaskCompleted(TaskModel task) {
            completedCount++;
        }

        @Override
        public void onTaskCompletedWithErrors(TaskModel task) {
            completedWithErrorsCount++;
        }

        @Override
        public void onTaskCanceled(TaskModel task) {
            canceledCount++;
        }

        @Override
        public void onTaskFailed(TaskModel task) {
            failedCount++;
        }

        @Override
        public void onTaskFailedWithTerminalError(TaskModel task) {
            failedWithTerminalErrorCount++;
        }

        @Override
        public void onTaskTimedOut(TaskModel task) {
            timedOutCount++;
        }

        @Override
        public void onTaskSkipped(TaskModel task) {
            skippedCount++;
        }

        void reset() {
            scheduledCount = 0;
            inProgressCount = 0;
            completedCount = 0;
            completedWithErrorsCount = 0;
            canceledCount = 0;
            failedCount = 0;
            failedWithTerminalErrorCount = 0;
            timedOutCount = 0;
            skippedCount = 0;
        }
    }

    @Test
    public void testTaskStatusListenerEnabled() {
        TestTaskStatusListener listener = new TestTaskStatusListener();
        TaskModel task = createTaskWithListenerEnabled(true);

        // Test all methods with enabled listener
        listener.onTaskScheduledIfEnabled(task);
        assertEquals(1, listener.scheduledCount);

        listener.onTaskInProgressIfEnabled(task);
        assertEquals(1, listener.inProgressCount);

        listener.onTaskCompletedIfEnabled(task);
        assertEquals(1, listener.completedCount);

        listener.onTaskCompletedWithErrorsIfEnabled(task);
        assertEquals(1, listener.completedWithErrorsCount);

        listener.onTaskCanceledIfEnabled(task);
        assertEquals(1, listener.canceledCount);

        listener.onTaskFailedIfEnabled(task);
        assertEquals(1, listener.failedCount);

        listener.onTaskFailedWithTerminalErrorIfEnabled(task);
        assertEquals(1, listener.failedWithTerminalErrorCount);

        listener.onTaskTimedOutIfEnabled(task);
        assertEquals(1, listener.timedOutCount);

        listener.onTaskSkippedIfEnabled(task);
        assertEquals(1, listener.skippedCount);
    }

    @Test
    public void testTaskStatusListenerDisabled() {
        TestTaskStatusListener listener = new TestTaskStatusListener();
        TaskModel task = createTaskWithListenerEnabled(false);

        // Test all methods with disabled listener
        listener.onTaskScheduledIfEnabled(task);
        assertEquals(0, listener.scheduledCount);

        listener.onTaskInProgressIfEnabled(task);
        assertEquals(0, listener.inProgressCount);

        listener.onTaskCompletedIfEnabled(task);
        assertEquals(0, listener.completedCount);

        listener.onTaskCompletedWithErrorsIfEnabled(task);
        assertEquals(0, listener.completedWithErrorsCount);

        listener.onTaskCanceledIfEnabled(task);
        assertEquals(0, listener.canceledCount);

        listener.onTaskFailedIfEnabled(task);
        assertEquals(0, listener.failedCount);

        listener.onTaskFailedWithTerminalErrorIfEnabled(task);
        assertEquals(0, listener.failedWithTerminalErrorCount);

        listener.onTaskTimedOutIfEnabled(task);
        assertEquals(0, listener.timedOutCount);

        listener.onTaskSkippedIfEnabled(task);
        assertEquals(0, listener.skippedCount);
    }

    @Test
    public void testTaskStatusListenerDefaultBehaviorWhenTaskDefIsNull() {
        // This tests backward compatibility - when TaskDef is null (system tasks without explicit
        // TaskDef),
        // the listener should be enabled by default
        TestTaskStatusListener listener = new TestTaskStatusListener();
        TaskModel task = new TaskModel();
        // TaskDef is null by default

        // Test all methods with null TaskDef - should default to enabled
        listener.onTaskScheduledIfEnabled(task);
        assertEquals(1, listener.scheduledCount);

        listener.onTaskInProgressIfEnabled(task);
        assertEquals(1, listener.inProgressCount);

        listener.onTaskCompletedIfEnabled(task);
        assertEquals(1, listener.completedCount);

        listener.onTaskCompletedWithErrorsIfEnabled(task);
        assertEquals(1, listener.completedWithErrorsCount);

        listener.onTaskCanceledIfEnabled(task);
        assertEquals(1, listener.canceledCount);

        listener.onTaskFailedIfEnabled(task);
        assertEquals(1, listener.failedCount);

        listener.onTaskFailedWithTerminalErrorIfEnabled(task);
        assertEquals(1, listener.failedWithTerminalErrorCount);

        listener.onTaskTimedOutIfEnabled(task);
        assertEquals(1, listener.timedOutCount);

        listener.onTaskSkippedIfEnabled(task);
        assertEquals(1, listener.skippedCount);
    }

    @Test
    public void testTaskStatusListenerDefaultValue() {
        // Test that the default value of taskStatusListenerEnabled is true
        TaskDef taskDef = new TaskDef();
        assertEquals(true, taskDef.isTaskStatusListenerEnabled());
    }

    private TaskModel createTaskWithListenerEnabled(boolean enabled) {
        TaskModel task = new TaskModel();
        WorkflowTask workflowTask = new WorkflowTask();
        TaskDef taskDef = new TaskDef();
        taskDef.setTaskStatusListenerEnabled(enabled);
        workflowTask.setTaskDefinition(taskDef);
        task.setWorkflowTask(workflowTask);
        return task;
    }
}
