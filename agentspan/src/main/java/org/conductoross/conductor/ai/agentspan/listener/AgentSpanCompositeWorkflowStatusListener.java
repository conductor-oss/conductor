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

import com.netflix.conductor.core.listener.WorkflowStatusListener;
import com.netflix.conductor.model.WorkflowModel;

/**
 * A {@link WorkflowStatusListener} that fans every callback out to all other {@code
 * WorkflowStatusListener} beans in the context.
 *
 * <p>Conductor injects a <b>single</b> {@code WorkflowStatusListener} bean (see {@code
 * WorkflowExecutorOps}/{@code ExecutionService}), so when the embedded {@code conductor-agentspan}
 * library contributes its {@code @Primary AgentEventListener}, that one bean would otherwise shadow
 * any operator-configured publisher ({@code queue_publisher}, {@code kafka}, {@code archive},
 * {@code workflow_publisher}). To let agentspan's SSE listener and the configured publisher
 * coexist, this composite is registered as the sole {@code @Primary} listener and re-broadcasts
 * each event to every registered listener.
 *
 * <p>Each callback delegates to the <b>same</b> method on every other listener so that each
 * delegate applies its own gating semantics: the {@code *IfEnabled} variants honour each listener's
 * choice to gate on {@code workflowStatusListenerEnabled}, while agentspan's listener (which
 * overrides the {@code *IfEnabled} methods directly) fires unconditionally as it intends. The
 * composite excludes itself from the fan-out to avoid infinite recursion, and isolates delegates so
 * a failure in one listener cannot suppress the others.
 *
 * <p>Delegates are resolved lazily through an {@link ObjectProvider} so that wiring this
 * {@code @Primary} bean (which is itself a {@code WorkflowStatusListener}) does not create a
 * self-referential construction cycle.
 */
public class AgentSpanCompositeWorkflowStatusListener implements WorkflowStatusListener {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(AgentSpanCompositeWorkflowStatusListener.class);

    private final ObjectProvider<WorkflowStatusListener> delegates;

    public AgentSpanCompositeWorkflowStatusListener(
            ObjectProvider<WorkflowStatusListener> delegates) {
        this.delegates = delegates;
    }

    /**
     * Invoke {@code action} on every registered listener except this composite, isolating each
     * delegate so one listener's failure does not stop the rest.
     */
    private void fanOut(Consumer<WorkflowStatusListener> action) {
        delegates.stream()
                .filter(listener -> listener != this)
                .forEach(
                        listener -> {
                            try {
                                action.accept(listener);
                            } catch (Exception e) {
                                LOGGER.warn(
                                        "WorkflowStatusListener {} threw during fan-out: {}",
                                        listener.getClass().getName(),
                                        e.getMessage(),
                                        e);
                            }
                        });
    }

    // ── *IfEnabled variants — the primary callback path used by WorkflowExecutorOps ──

    @Override
    public void onWorkflowCompletedIfEnabled(WorkflowModel workflow) {
        fanOut(listener -> listener.onWorkflowCompletedIfEnabled(workflow));
    }

    @Override
    public void onWorkflowTerminatedIfEnabled(WorkflowModel workflow) {
        fanOut(listener -> listener.onWorkflowTerminatedIfEnabled(workflow));
    }

    @Override
    public void onWorkflowFinalizedIfEnabled(WorkflowModel workflow) {
        fanOut(listener -> listener.onWorkflowFinalizedIfEnabled(workflow));
    }

    @Override
    public void onWorkflowStartedIfEnabled(WorkflowModel workflow) {
        fanOut(listener -> listener.onWorkflowStartedIfEnabled(workflow));
    }

    @Override
    public void onWorkflowRestartedIfEnabled(WorkflowModel workflow) {
        fanOut(listener -> listener.onWorkflowRestartedIfEnabled(workflow));
    }

    @Override
    public void onWorkflowRerunIfEnabled(WorkflowModel workflow) {
        fanOut(listener -> listener.onWorkflowRerunIfEnabled(workflow));
    }

    @Override
    public void onWorkflowRetriedIfEnabled(WorkflowModel workflow) {
        fanOut(listener -> listener.onWorkflowRetriedIfEnabled(workflow));
    }

    @Override
    public void onWorkflowPausedIfEnabled(WorkflowModel workflow) {
        fanOut(listener -> listener.onWorkflowPausedIfEnabled(workflow));
    }

    @Override
    public void onWorkflowResumedIfEnabled(WorkflowModel workflow) {
        fanOut(listener -> listener.onWorkflowResumedIfEnabled(workflow));
    }

    // ── Direct variants — fan out for any caller that bypasses the *IfEnabled path ──

    @Override
    public void onWorkflowCompleted(WorkflowModel workflow) {
        fanOut(listener -> listener.onWorkflowCompleted(workflow));
    }

    @Override
    public void onWorkflowTerminated(WorkflowModel workflow) {
        fanOut(listener -> listener.onWorkflowTerminated(workflow));
    }

    @Override
    public void onWorkflowFinalized(WorkflowModel workflow) {
        fanOut(listener -> listener.onWorkflowFinalized(workflow));
    }

    @Override
    public void onWorkflowStarted(WorkflowModel workflow) {
        fanOut(listener -> listener.onWorkflowStarted(workflow));
    }

    @Override
    public void onWorkflowRestarted(WorkflowModel workflow) {
        fanOut(listener -> listener.onWorkflowRestarted(workflow));
    }

    @Override
    public void onWorkflowRerun(WorkflowModel workflow) {
        fanOut(listener -> listener.onWorkflowRerun(workflow));
    }

    @Override
    public void onWorkflowPaused(WorkflowModel workflow) {
        fanOut(listener -> listener.onWorkflowPaused(workflow));
    }

    @Override
    public void onWorkflowResumed(WorkflowModel workflow) {
        fanOut(listener -> listener.onWorkflowResumed(workflow));
    }

    @Override
    public void onWorkflowRetried(WorkflowModel workflow) {
        fanOut(listener -> listener.onWorkflowRetried(workflow));
    }
}
