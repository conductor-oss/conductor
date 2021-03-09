package com.netflix.conductor.core.execution;

import com.netflix.conductor.common.run.Workflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stub listener default implementation
 */
public class WorkflowStatusListenerStub implements WorkflowStatusListener {

    private static final Logger LOG = LoggerFactory.getLogger(WorkflowStatusListenerStub.class);

    @Override
    public void onWorkflowCompleted(Workflow workflow) {
        LOG.debug("Workflow {} is completed", workflow.getWorkflowId());
    }

    @Override
    public void onWorkflowTerminated(Workflow workflow) {
        LOG.debug("Workflow {} is terminated", workflow.getWorkflowId());
    }

    @Override
    public void onWorkflowFinalized(Workflow workflow) {
        LOG.debug("Workflow {} is finalized", workflow.getWorkflowId());
    }
}
