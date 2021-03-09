package com.netflix.conductor.core.execution;

import com.netflix.conductor.common.run.Workflow;

/**
 * Listener for the completed and terminated workflows
 */
public interface WorkflowStatusListener {

    default void onWorkflowCompletedIfEnabled(Workflow workflow) {
        if (workflow.getWorkflowDefinition().isWorkflowStatusListenerEnabled()) {
            onWorkflowCompleted(workflow);
        }
    }

    default void onWorkflowTerminatedIfEnabled(Workflow workflow) {
        if (workflow.getWorkflowDefinition().isWorkflowStatusListenerEnabled()) {
            onWorkflowTerminated(workflow);
        }
    }

    default void onWorkflowFinalizedIfEnabled(Workflow workflow) {
        if (workflow.getWorkflowDefinition().isWorkflowStatusListenerEnabled()) {
            onWorkflowFinalized(workflow);
        }
    }

    void onWorkflowCompleted(Workflow workflow);

    void onWorkflowTerminated(Workflow workflow);

    default void onWorkflowFinalized(Workflow workflow) {
    }
}
