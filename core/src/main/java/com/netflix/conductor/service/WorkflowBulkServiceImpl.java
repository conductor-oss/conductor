/*
 * Copyright 2022 Conductor Authors.
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
package com.netflix.conductor.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.netflix.conductor.annotations.Audit;
import com.netflix.conductor.annotations.Trace;
import com.netflix.conductor.common.model.BulkResponse;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.model.WorkflowModel;

@Audit
@Trace
@Service
public class WorkflowBulkServiceImpl implements WorkflowBulkService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowBulkService.class);
    private final WorkflowExecutor workflowExecutor;
    private final WorkflowService workflowService;

    public WorkflowBulkServiceImpl(
            WorkflowExecutor workflowExecutor, WorkflowService workflowService) {
        this.workflowExecutor = workflowExecutor;
        this.workflowService = workflowService;
    }

    /**
     * Pause the list of workflows.
     *
     * @param workflowIds - list of workflow Ids to perform pause operation on
     * @return bulk response object containing a list of succeeded workflows and a list of failed
     *     ones with errors
     */
    public BulkResponse<String> pauseWorkflow(List<String> workflowIds) {

        BulkResponse<String> bulkResponse = new BulkResponse<>();
        for (String workflowId : workflowIds) {
            try {
                workflowExecutor.pauseWorkflow(workflowId);
                bulkResponse.appendSuccessResponse(workflowId);
            } catch (Exception e) {
                LOGGER.error(
                        "bulk pauseWorkflow exception, workflowId {}, message: {} ",
                        workflowId,
                        e.getMessage(),
                        e);
                bulkResponse.appendFailedResponse(workflowId, e.getMessage());
            }
        }

        return bulkResponse;
    }

    /**
     * Resume the list of workflows.
     *
     * @param workflowIds - list of workflow Ids to perform resume operation on
     * @return bulk response object containing a list of succeeded workflows and a list of failed
     *     ones with errors
     */
    public BulkResponse<String> resumeWorkflow(List<String> workflowIds) {
        BulkResponse<String> bulkResponse = new BulkResponse<>();
        for (String workflowId : workflowIds) {
            try {
                workflowExecutor.resumeWorkflow(workflowId);
                bulkResponse.appendSuccessResponse(workflowId);
            } catch (Exception e) {
                LOGGER.error(
                        "bulk resumeWorkflow exception, workflowId {}, message: {} ",
                        workflowId,
                        e.getMessage(),
                        e);
                bulkResponse.appendFailedResponse(workflowId, e.getMessage());
            }
        }
        return bulkResponse;
    }

    /**
     * Restart the list of workflows.
     *
     * @param workflowIds - list of workflow Ids to perform restart operation on
     * @param useLatestDefinitions if true, use latest workflow and task definitions upon restart
     * @return bulk response object containing a list of succeeded workflows and a list of failed
     *     ones with errors
     */
    public BulkResponse<String> restart(List<String> workflowIds, boolean useLatestDefinitions) {
        BulkResponse<String> bulkResponse = new BulkResponse<>();
        for (String workflowId : workflowIds) {
            try {
                workflowExecutor.restart(workflowId, useLatestDefinitions);
                bulkResponse.appendSuccessResponse(workflowId);
            } catch (Exception e) {
                LOGGER.error(
                        "bulk restart exception, workflowId {}, message: {} ",
                        workflowId,
                        e.getMessage(),
                        e);
                bulkResponse.appendFailedResponse(workflowId, e.getMessage());
            }
        }
        return bulkResponse;
    }

    /**
     * Retry the last failed task for each workflow from the list.
     *
     * @param workflowIds - list of workflow Ids to perform retry operation on
     * @return bulk response object containing a list of succeeded workflows and a list of failed
     *     ones with errors
     */
    public BulkResponse<String> retry(List<String> workflowIds) {
        BulkResponse<String> bulkResponse = new BulkResponse<>();
        for (String workflowId : workflowIds) {
            try {
                workflowExecutor.retry(workflowId, false);
                bulkResponse.appendSuccessResponse(workflowId);
            } catch (Exception e) {
                LOGGER.error(
                        "bulk retry exception, workflowId {}, message: {} ",
                        workflowId,
                        e.getMessage(),
                        e);
                bulkResponse.appendFailedResponse(workflowId, e.getMessage());
            }
        }
        return bulkResponse;
    }

    /**
     * Terminate workflows execution.
     *
     * @param workflowIds - list of workflow Ids to perform terminate operation on
     * @param reason - description to be specified for the terminated workflow for future
     *     references.
     * @return bulk response object containing a list of succeeded workflows and a list of failed
     *     ones with errors
     */
    public BulkResponse<String> terminate(List<String> workflowIds, String reason) {
        BulkResponse<String> bulkResponse = new BulkResponse<>();
        for (String workflowId : workflowIds) {
            try {
                workflowExecutor.terminateWorkflow(workflowId, reason);
                bulkResponse.appendSuccessResponse(workflowId);
            } catch (Exception e) {
                LOGGER.error(
                        "bulk terminate exception, workflowId {}, message: {} ",
                        workflowId,
                        e.getMessage(),
                        e);
                bulkResponse.appendFailedResponse(workflowId, e.getMessage());
            }
        }
        return bulkResponse;
    }

    /**
     * Removes a list of workflows from the system.
     *
     * @param workflowIds List of WorkflowIDs of the workflows you want to remove from system.
     * @param archiveWorkflow Archives the workflow and associated tasks instead of removing them.
     */
    public BulkResponse<String> deleteWorkflow(List<String> workflowIds, boolean archiveWorkflow) {
        BulkResponse<String> bulkResponse = new BulkResponse<>();
        for (String workflowId : workflowIds) {
            try {
                workflowService.deleteWorkflow(
                        workflowId,
                        archiveWorkflow); // TODO: change this to method that cancels then deletes
                bulkResponse.appendSuccessResponse(workflowId);
            } catch (Exception e) {
                LOGGER.error(
                        "bulk delete exception, workflowId {}, message: {} ",
                        workflowId,
                        e.getMessage(),
                        e);
                bulkResponse.appendFailedResponse(workflowId, e.getMessage());
            }
        }
        return bulkResponse;
    }

    /**
     * Terminates execution for workflows in a list, then removes each workflow.
     *
     * @param workflowIds List of workflow IDs to terminate and delete.
     * @param reason Reason for terminating the workflow.
     * @param archiveWorkflow Archives the workflow and associated tasks instead of removing them.
     * @return bulk response object containing a list of succeeded workflows and a list of failed
     *     ones with errors
     */
    public BulkResponse<String> terminateRemove(
            List<String> workflowIds, String reason, boolean archiveWorkflow) {
        BulkResponse<String> bulkResponse = new BulkResponse<>();
        for (String workflowId : workflowIds) {
            try {
                workflowExecutor.terminateWorkflow(workflowId, reason);
                bulkResponse.appendSuccessResponse(workflowId);
            } catch (Exception e) {
                LOGGER.error(
                        "bulk terminate exception, workflowId {}, message: {} ",
                        workflowId,
                        e.getMessage(),
                        e);
                bulkResponse.appendFailedResponse(workflowId, e.getMessage());
            }

            try {
                workflowService.deleteWorkflow(workflowId, archiveWorkflow);
                bulkResponse.appendSuccessResponse(workflowId);
            } catch (Exception e) {
                LOGGER.error(
                        "bulk delete exception, workflowId {}, message: {} ",
                        workflowId,
                        e.getMessage(),
                        e);
                bulkResponse.appendFailedResponse(workflowId, e.getMessage());
            }
        }
        return bulkResponse;
    }

    /**
     * Fetch workflow details for given workflowIds.
     *
     * @param workflowIds List of workflow IDs to terminate and delete.
     * @param includeTasks includes tasks from workflow
     * @return bulk response object containing a list of workflow details
     */
    @Override
    public BulkResponse<WorkflowModel> searchWorkflow(
            List<String> workflowIds, boolean includeTasks) {
        BulkResponse<WorkflowModel> bulkResponse = new BulkResponse<>();
        for (String workflowId : workflowIds) {
            try {
                WorkflowModel workflowModel =
                        workflowExecutor.getWorkflow(workflowId, includeTasks);
                bulkResponse.appendSuccessResponse(workflowModel);
            } catch (Exception e) {
                LOGGER.error(
                        "bulk search exception, workflowId {}, message: {} ",
                        workflowId,
                        e.getMessage(),
                        e);
                bulkResponse.appendFailedResponse(workflowId, e.getMessage());
            }
        }
        return bulkResponse;
    }
}
