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
package org.conductoross.conductor.ai.agentspan.listener;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.listener.TaskStatusListener;
import com.netflix.conductor.core.listener.WorkflowStatusListener;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Runs every task and workflow callback through Spring's real bean provider. Recording listeners
 * are concrete listener implementations, not mocks.
 */
class AgentSpanCompositeListenersIntegrationTest {

    @Test
    void workflowCompositeForwardsEveryDirectAndEnabledCallback() {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean(RecordingWorkflowListener.class);
            context.refresh();
            RecordingWorkflowListener recording = context.getBean(RecordingWorkflowListener.class);
            AgentSpanCompositeWorkflowStatusListener composite =
                    new AgentSpanCompositeWorkflowStatusListener(
                            context.getBeanProvider(WorkflowStatusListener.class));
            // Register the composite in the same real bean factory used by its provider. A failure
            // to exclude itself would recurse here and overflow the stack.
            context.getBeanFactory().registerSingleton("workflowComposite", composite);
            WorkflowModel workflow = workflow(true);

            invokeEveryWorkflowCallback(composite, workflow);

            assertThat(recording.events)
                    .containsExactly(
                            "completed",
                            "terminated",
                            "finalized",
                            "started",
                            "restarted",
                            "rerun",
                            "retried",
                            "paused",
                            "resumed",
                            "completed",
                            "terminated",
                            "finalized",
                            "started",
                            "restarted",
                            "rerun",
                            "retried",
                            "paused",
                            "resumed");
        }
    }

    @Test
    void taskCompositeForwardsEveryDirectAndEnabledCallback() {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean(RecordingTaskListener.class);
            context.refresh();
            RecordingTaskListener recording = context.getBean(RecordingTaskListener.class);
            AgentSpanCompositeTaskStatusListener composite =
                    new AgentSpanCompositeTaskStatusListener(
                            context.getBeanProvider(TaskStatusListener.class));
            context.getBeanFactory().registerSingleton("taskComposite", composite);
            TaskModel task = task(true);

            invokeEveryTaskCallback(composite, task);

            assertThat(recording.events)
                    .containsExactly(
                            "scheduled",
                            "in-progress",
                            "canceled",
                            "failed",
                            "terminal-error",
                            "completed",
                            "completed-with-errors",
                            "timed-out",
                            "skipped",
                            "scheduled",
                            "in-progress",
                            "canceled",
                            "failed",
                            "terminal-error",
                            "completed",
                            "completed-with-errors",
                            "timed-out",
                            "skipped");
        }
    }

    @Test
    void workflowEnabledCallbacksPreserveDelegateGating() {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean(RecordingWorkflowListener.class);
            context.refresh();
            RecordingWorkflowListener recording = context.getBean(RecordingWorkflowListener.class);
            AgentSpanCompositeWorkflowStatusListener composite =
                    new AgentSpanCompositeWorkflowStatusListener(
                            context.getBeanProvider(WorkflowStatusListener.class));

            composite.onWorkflowCompletedIfEnabled(workflow(false));

            assertThat(recording.events).isEmpty();
        }
    }

    @Test
    void workflowCompositeIsolatesAThrowingListenerFromLaterListeners() {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean(ThrowingWorkflowListener.class);
            context.registerBean(RecordingWorkflowListener.class);
            context.refresh();
            RecordingWorkflowListener recording = context.getBean(RecordingWorkflowListener.class);
            AgentSpanCompositeWorkflowStatusListener composite =
                    new AgentSpanCompositeWorkflowStatusListener(
                            context.getBeanProvider(WorkflowStatusListener.class));

            assertThatCode(() -> composite.onWorkflowCompleted(workflow(true)))
                    .doesNotThrowAnyException();
            assertThat(recording.events).containsExactly("completed");
        }
    }

    @Test
    void taskEnabledCallbacksPreserveDelegateGating() {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean(RecordingTaskListener.class);
            context.refresh();
            RecordingTaskListener recording = context.getBean(RecordingTaskListener.class);
            AgentSpanCompositeTaskStatusListener composite =
                    new AgentSpanCompositeTaskStatusListener(
                            context.getBeanProvider(TaskStatusListener.class));

            composite.onTaskCompletedIfEnabled(task(false));

            assertThat(recording.events).isEmpty();
        }
    }

    @Test
    void taskCompositeIsolatesAThrowingListenerFromLaterListeners() {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean(ThrowingTaskListener.class);
            context.registerBean(RecordingTaskListener.class);
            context.refresh();
            RecordingTaskListener recording = context.getBean(RecordingTaskListener.class);
            AgentSpanCompositeTaskStatusListener composite =
                    new AgentSpanCompositeTaskStatusListener(
                            context.getBeanProvider(TaskStatusListener.class));

            assertThatCode(() -> composite.onTaskCompleted(task(true))).doesNotThrowAnyException();
            assertThat(recording.events).containsExactly("completed");
        }
    }

    private static void invokeEveryWorkflowCallback(
            AgentSpanCompositeWorkflowStatusListener listener, WorkflowModel workflow) {
        listener.onWorkflowCompletedIfEnabled(workflow);
        listener.onWorkflowTerminatedIfEnabled(workflow);
        listener.onWorkflowFinalizedIfEnabled(workflow);
        listener.onWorkflowStartedIfEnabled(workflow);
        listener.onWorkflowRestartedIfEnabled(workflow);
        listener.onWorkflowRerunIfEnabled(workflow);
        listener.onWorkflowRetriedIfEnabled(workflow);
        listener.onWorkflowPausedIfEnabled(workflow);
        listener.onWorkflowResumedIfEnabled(workflow);
        listener.onWorkflowCompleted(workflow);
        listener.onWorkflowTerminated(workflow);
        listener.onWorkflowFinalized(workflow);
        listener.onWorkflowStarted(workflow);
        listener.onWorkflowRestarted(workflow);
        listener.onWorkflowRerun(workflow);
        listener.onWorkflowRetried(workflow);
        listener.onWorkflowPaused(workflow);
        listener.onWorkflowResumed(workflow);
    }

    private static void invokeEveryTaskCallback(
            AgentSpanCompositeTaskStatusListener listener, TaskModel task) {
        listener.onTaskScheduledIfEnabled(task);
        listener.onTaskInProgressIfEnabled(task);
        listener.onTaskCanceledIfEnabled(task);
        listener.onTaskFailedIfEnabled(task);
        listener.onTaskFailedWithTerminalErrorIfEnabled(task);
        listener.onTaskCompletedIfEnabled(task);
        listener.onTaskCompletedWithErrorsIfEnabled(task);
        listener.onTaskTimedOutIfEnabled(task);
        listener.onTaskSkippedIfEnabled(task);
        listener.onTaskScheduled(task);
        listener.onTaskInProgress(task);
        listener.onTaskCanceled(task);
        listener.onTaskFailed(task);
        listener.onTaskFailedWithTerminalError(task);
        listener.onTaskCompleted(task);
        listener.onTaskCompletedWithErrors(task);
        listener.onTaskTimedOut(task);
        listener.onTaskSkipped(task);
    }

    private static WorkflowModel workflow(boolean enabled) {
        WorkflowDef definition = new WorkflowDef();
        definition.setName("agentspan-listener-integration");
        definition.setWorkflowStatusListenerEnabled(enabled);
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("workflow-id");
        workflow.setWorkflowDefinition(definition);
        return workflow;
    }

    private static TaskModel task(boolean enabled) {
        TaskDef definition = new TaskDef();
        definition.setName("agentspan-listener-integration");
        definition.setTaskStatusListenerEnabled(enabled);
        TaskModel task = new TaskModel();
        task.setTaskId("task-id");
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setTaskDefinition(definition);
        task.setWorkflowTask(workflowTask);
        return task;
    }

    static final class RecordingWorkflowListener implements WorkflowStatusListener {
        final List<String> events = new ArrayList<>();

        @Override
        public void onWorkflowCompleted(WorkflowModel workflow) {
            events.add("completed");
        }

        @Override
        public void onWorkflowTerminated(WorkflowModel workflow) {
            events.add("terminated");
        }

        @Override
        public void onWorkflowFinalized(WorkflowModel workflow) {
            events.add("finalized");
        }

        @Override
        public void onWorkflowStarted(WorkflowModel workflow) {
            events.add("started");
        }

        @Override
        public void onWorkflowRestarted(WorkflowModel workflow) {
            events.add("restarted");
        }

        @Override
        public void onWorkflowRerun(WorkflowModel workflow) {
            events.add("rerun");
        }

        @Override
        public void onWorkflowRetried(WorkflowModel workflow) {
            events.add("retried");
        }

        @Override
        public void onWorkflowPaused(WorkflowModel workflow) {
            events.add("paused");
        }

        @Override
        public void onWorkflowResumed(WorkflowModel workflow) {
            events.add("resumed");
        }
    }

    static final class RecordingTaskListener implements TaskStatusListener {
        final List<String> events = new ArrayList<>();

        @Override
        public void onTaskScheduled(TaskModel task) {
            events.add("scheduled");
        }

        @Override
        public void onTaskInProgress(TaskModel task) {
            events.add("in-progress");
        }

        @Override
        public void onTaskCanceled(TaskModel task) {
            events.add("canceled");
        }

        @Override
        public void onTaskFailed(TaskModel task) {
            events.add("failed");
        }

        @Override
        public void onTaskFailedWithTerminalError(TaskModel task) {
            events.add("terminal-error");
        }

        @Override
        public void onTaskCompleted(TaskModel task) {
            events.add("completed");
        }

        @Override
        public void onTaskCompletedWithErrors(TaskModel task) {
            events.add("completed-with-errors");
        }

        @Override
        public void onTaskTimedOut(TaskModel task) {
            events.add("timed-out");
        }

        @Override
        public void onTaskSkipped(TaskModel task) {
            events.add("skipped");
        }
    }

    static final class ThrowingTaskListener implements TaskStatusListener {
        @Override
        public void onTaskCompleted(TaskModel task) {
            throw new IllegalStateException("expected listener failure");
        }
    }

    static final class ThrowingWorkflowListener implements WorkflowStatusListener {
        @Override
        public void onWorkflowCompleted(WorkflowModel workflow) {
            throw new IllegalStateException("expected listener failure");
        }

        @Override
        public void onWorkflowTerminated(WorkflowModel workflow) {}
    }
}
