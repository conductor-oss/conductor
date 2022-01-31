/*
 * Copyright 2022 Netflix, Inc.
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
package com.netflix.conductor.contribs.listener.archive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.core.dal.ExecutionDAOFacade;
import com.netflix.conductor.core.listener.WorkflowStatusListener;
import com.netflix.conductor.metrics.Monitors;
import com.netflix.conductor.model.WorkflowModel;

/**
 * Provides default implementation of workflow archiving immediately after workflow is completed or
 * terminated.
 *
 * @author pavel.halabala
 */
public class ArchivingWorkflowStatusListener implements WorkflowStatusListener {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(ArchivingWorkflowStatusListener.class);
    private final ExecutionDAOFacade executionDAOFacade;

    public ArchivingWorkflowStatusListener(ExecutionDAOFacade executionDAOFacade) {
        this.executionDAOFacade = executionDAOFacade;
    }

    @Override
    public void onWorkflowCompleted(WorkflowModel workflow) {
        LOGGER.info("Archiving workflow {} on completion ", workflow.getWorkflowId());
        this.executionDAOFacade.removeWorkflow(workflow.getWorkflowId(), true);
        Monitors.recordWorkflowArchived(workflow.getWorkflowName(), workflow.getStatus());
    }

    @Override
    public void onWorkflowTerminated(WorkflowModel workflow) {
        LOGGER.info("Archiving workflow {} on termination", workflow.getWorkflowId());
        this.executionDAOFacade.removeWorkflow(workflow.getWorkflowId(), true);
        Monitors.recordWorkflowArchived(workflow.getWorkflowName(), workflow.getStatus());
    }
}
