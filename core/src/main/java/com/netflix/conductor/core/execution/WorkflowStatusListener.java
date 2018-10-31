package com.netflix.conductor.core.execution;

import com.netflix.conductor.common.run.Workflow;

/**
 * Listener for the completed and terminated workflows
 *
 */
public interface WorkflowStatusListener {
    void onWorkflowCompleted(Workflow workflow);
    void onWorkflowTerminated(Workflow workflow);
}
