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
package com.netflix.conductor.contribs.listener.composite;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.core.listener.WorkflowStatusListener;
import com.netflix.conductor.model.WorkflowModel;

/**
 * Composite workflow status listener that delegates to multiple listeners in parallel. Failures in
 * one listener do not affect others, and slow listeners do not block fast ones.
 *
 * <p>Uses a thread pool to execute listener invocations concurrently, ensuring proper failure
 * isolation and preventing cascading delays.
 */
public class CompositeWorkflowStatusListener implements WorkflowStatusListener {

    private final ExecutorService executorService;

    private static final Logger LOGGER =
            LoggerFactory.getLogger(CompositeWorkflowStatusListener.class);
    private final List<WorkflowStatusListener> listeners;

    /**
     * Creates a composite workflow status listener with the given delegates.
     *
     * @param listeners List of workflow status listeners to delegate to
     */
    public CompositeWorkflowStatusListener(List<WorkflowStatusListener> listeners) {
        this.listeners = listeners;
        this.executorService =
                Executors.newFixedThreadPool(
                        Math.max(listeners.size(), 2),
                        r -> {
                            Thread thread = new Thread(r);
                            thread.setName("composite-wf-listener-" + thread.getId());
                            thread.setDaemon(true);
                            return thread;
                        });
        LOGGER.info(
                "Initialized composite workflow listener with {} listeners (parallel execution): {}",
                listeners.size(),
                listeners.stream().map(d -> d.getClass().getSimpleName()).toList());
    }

    @Override
    public void onWorkflowCompleted(WorkflowModel workflow) {
        delegateToListeners(
                workflow,
                "onWorkflowCompleted",
                listener -> listener.onWorkflowCompleted(workflow));
    }

    @Override
    public void onWorkflowTerminated(WorkflowModel workflow) {
        delegateToListeners(
                workflow,
                "onWorkflowTerminated",
                listener -> listener.onWorkflowTerminated(workflow));
    }

    @Override
    public void onWorkflowFinalized(WorkflowModel workflow) {
        delegateToListeners(
                workflow,
                "onWorkflowFinalized",
                listener -> listener.onWorkflowFinalized(workflow));
    }

    @Override
    public void onWorkflowStarted(WorkflowModel workflow) {
        delegateToListeners(
                workflow, "onWorkflowStarted", listener -> listener.onWorkflowStarted(workflow));
    }

    @Override
    public void onWorkflowRestarted(WorkflowModel workflow) {
        delegateToListeners(
                workflow,
                "onWorkflowRestarted",
                listener -> listener.onWorkflowRestarted(workflow));
    }

    @Override
    public void onWorkflowRerun(WorkflowModel workflow) {
        delegateToListeners(
                workflow, "onWorkflowRerun", listener -> listener.onWorkflowRerun(workflow));
    }

    @Override
    public void onWorkflowPaused(WorkflowModel workflow) {
        delegateToListeners(
                workflow, "onWorkflowPaused", listener -> listener.onWorkflowPaused(workflow));
    }

    @Override
    public void onWorkflowResumed(WorkflowModel workflow) {
        delegateToListeners(
                workflow, "onWorkflowResumed", listener -> listener.onWorkflowResumed(workflow));
    }

    @Override
    public void onWorkflowRetried(WorkflowModel workflow) {
        delegateToListeners(
                workflow, "onWorkflowRetried", listener -> listener.onWorkflowRetried(workflow));
    }

    /**
     * Delegates workflow event to all listeners in parallel with error isolation. Ensures that slow
     * or blocked listeners do not delay others.
     *
     * @param workflow the workflow model
     * @param methodName the name of the method being invoked (for logging)
     * @param action the action to perform on each listener
     */
    private void delegateToListeners(
            WorkflowModel workflow, String methodName, Consumer<WorkflowStatusListener> action) {
        CompletableFuture<?>[] futures =
                listeners.stream()
                        .map(
                                listener ->
                                        CompletableFuture.runAsync(
                                                () ->
                                                        safeInvoke(
                                                                () -> action.accept(listener),
                                                                listener.getClass().getSimpleName(),
                                                                methodName,
                                                                workflow.getWorkflowId()),
                                                executorService))
                        .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(futures).join();
    }

    /**
     * Safely invokes a listener action with exception handling. Does not propagate exceptions to
     * ensure failure isolation.
     *
     * @param action The action to execute
     * @param listenerName Name of the listener for logging
     * @param methodName Name of the method being invoked
     * @param workflowId Workflow ID for context
     */
    private void safeInvoke(
            Runnable action, String listenerName, String methodName, String workflowId) {
        try {
            action.run();
        } catch (Exception e) {
            LOGGER.error(
                    "Error in listener {} during {} for workflow {}: {}",
                    listenerName,
                    methodName,
                    workflowId,
                    e.getMessage(),
                    e);
        }
    }
}
