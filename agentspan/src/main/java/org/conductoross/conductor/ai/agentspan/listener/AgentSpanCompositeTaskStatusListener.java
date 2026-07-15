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

import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import com.netflix.conductor.core.listener.TaskStatusListener;
import com.netflix.conductor.model.TaskModel;

/**
 * A {@link TaskStatusListener} that fans every callback out to all other {@code TaskStatusListener}
 * beans in the context.
 *
 * <p>The motivation mirrors {@link AgentSpanCompositeWorkflowStatusListener}: Conductor injects a
 * <b>single</b> {@code TaskStatusListener} bean, so the embedded {@code conductor-agentspan}
 * library's {@code @Primary AgentEventListener} would otherwise shadow an operator-configured
 * {@code task_publisher}. Registering this composite as the sole {@code @Primary} listener and
 * re-broadcasting to every registered listener lets agentspan's SSE listener and the configured
 * publisher coexist.
 *
 * <p>Each callback delegates to the <b>same</b> method on every other listener so each delegate
 * keeps its own gating semantics. The composite excludes itself to avoid recursion and isolates
 * delegates so one listener's failure cannot suppress the others. Delegates are resolved lazily via
 * {@link ObjectProvider} to avoid a self-referential construction cycle for this {@code @Primary}
 * bean.
 */
public class AgentSpanCompositeTaskStatusListener implements TaskStatusListener {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(AgentSpanCompositeTaskStatusListener.class);

    private final ObjectProvider<TaskStatusListener> delegates;

    public AgentSpanCompositeTaskStatusListener(ObjectProvider<TaskStatusListener> delegates) {
        this.delegates = delegates;
    }

    /**
     * Invoke {@code action} on every registered listener except this composite, isolating each
     * delegate so one listener's failure does not stop the rest.
     */
    private void fanOut(Consumer<TaskStatusListener> action) {
        delegates.stream()
                .filter(listener -> listener != this)
                .forEach(
                        listener -> {
                            try {
                                action.accept(listener);
                            } catch (Exception e) {
                                LOGGER.warn(
                                        "TaskStatusListener {} threw during fan-out: {}",
                                        listener.getClass().getName(),
                                        e.getMessage(),
                                        e);
                            }
                        });
    }

    // ── *IfEnabled variants — the primary callback path used by WorkflowExecutorOps ──

    @Override
    public void onTaskScheduledIfEnabled(TaskModel task) {
        fanOut(listener -> listener.onTaskScheduledIfEnabled(task));
    }

    @Override
    public void onTaskInProgressIfEnabled(TaskModel task) {
        fanOut(listener -> listener.onTaskInProgressIfEnabled(task));
    }

    @Override
    public void onTaskCanceledIfEnabled(TaskModel task) {
        fanOut(listener -> listener.onTaskCanceledIfEnabled(task));
    }

    @Override
    public void onTaskFailedIfEnabled(TaskModel task) {
        fanOut(listener -> listener.onTaskFailedIfEnabled(task));
    }

    @Override
    public void onTaskFailedWithTerminalErrorIfEnabled(TaskModel task) {
        fanOut(listener -> listener.onTaskFailedWithTerminalErrorIfEnabled(task));
    }

    @Override
    public void onTaskCompletedIfEnabled(TaskModel task) {
        fanOut(listener -> listener.onTaskCompletedIfEnabled(task));
    }

    @Override
    public void onTaskCompletedWithErrorsIfEnabled(TaskModel task) {
        fanOut(listener -> listener.onTaskCompletedWithErrorsIfEnabled(task));
    }

    @Override
    public void onTaskTimedOutIfEnabled(TaskModel task) {
        fanOut(listener -> listener.onTaskTimedOutIfEnabled(task));
    }

    @Override
    public void onTaskSkippedIfEnabled(TaskModel task) {
        fanOut(listener -> listener.onTaskSkippedIfEnabled(task));
    }

    // ── Direct variants — fan out for any caller that bypasses the *IfEnabled path ──

    @Override
    public void onTaskScheduled(TaskModel task) {
        fanOut(listener -> listener.onTaskScheduled(task));
    }

    @Override
    public void onTaskInProgress(TaskModel task) {
        fanOut(listener -> listener.onTaskInProgress(task));
    }

    @Override
    public void onTaskCanceled(TaskModel task) {
        fanOut(listener -> listener.onTaskCanceled(task));
    }

    @Override
    public void onTaskFailed(TaskModel task) {
        fanOut(listener -> listener.onTaskFailed(task));
    }

    @Override
    public void onTaskFailedWithTerminalError(TaskModel task) {
        fanOut(listener -> listener.onTaskFailedWithTerminalError(task));
    }

    @Override
    public void onTaskCompleted(TaskModel task) {
        fanOut(listener -> listener.onTaskCompleted(task));
    }

    @Override
    public void onTaskCompletedWithErrors(TaskModel task) {
        fanOut(listener -> listener.onTaskCompletedWithErrors(task));
    }

    @Override
    public void onTaskTimedOut(TaskModel task) {
        fanOut(listener -> listener.onTaskTimedOut(task));
    }

    @Override
    public void onTaskSkipped(TaskModel task) {
        fanOut(listener -> listener.onTaskSkipped(task));
    }
}
