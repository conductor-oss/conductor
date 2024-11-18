/*
 * Copyright 2021 Conductor Authors.
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
package com.netflix.conductor.rest.controllers;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.netflix.conductor.common.model.BulkResponse;
import com.netflix.conductor.model.WorkflowModel;
import com.netflix.conductor.service.WorkflowBulkService;

import io.swagger.v3.oas.annotations.Operation;

import static com.netflix.conductor.rest.config.RequestMappingConstants.WORKFLOW_BULK;

/** Synchronous Bulk APIs to process the workflows in batches */
@RestController
@RequestMapping(WORKFLOW_BULK)
public class WorkflowBulkResource {

    private final WorkflowBulkService workflowBulkService;

    public WorkflowBulkResource(WorkflowBulkService workflowBulkService) {
        this.workflowBulkService = workflowBulkService;
    }

    /**
     * Pause the list of workflows.
     *
     * @param workflowIds - list of workflow Ids to perform pause operation on
     * @return bulk response object containing a list of succeeded workflows and a list of failed
     *     ones with errors
     */
    @PutMapping("/pause")
    @Operation(summary = "Pause the list of workflows")
    public BulkResponse<String> pauseWorkflow(@RequestBody List<String> workflowIds) {
        return workflowBulkService.pauseWorkflow(workflowIds);
    }

    /**
     * Resume the list of workflows.
     *
     * @param workflowIds - list of workflow Ids to perform resume operation on
     * @return bulk response object containing a list of succeeded workflows and a list of failed
     *     ones with errors
     */
    @PutMapping("/resume")
    @Operation(summary = "Resume the list of workflows")
    public BulkResponse<String> resumeWorkflow(@RequestBody List<String> workflowIds) {
        return workflowBulkService.resumeWorkflow(workflowIds);
    }

    /**
     * Restart the list of workflows.
     *
     * @param workflowIds - list of workflow Ids to perform restart operation on
     * @param useLatestDefinitions if true, use latest workflow and task definitions upon restart
     * @return bulk response object containing a list of succeeded workflows and a list of failed
     *     ones with errors
     */
    @PostMapping("/restart")
    @Operation(summary = "Restart the list of completed workflow")
    public BulkResponse<String> restart(
            @RequestBody List<String> workflowIds,
            @RequestParam(value = "useLatestDefinitions", defaultValue = "false", required = false)
                    boolean useLatestDefinitions) {
        return workflowBulkService.restart(workflowIds, useLatestDefinitions);
    }

    /**
     * Retry the last failed task for each workflow from the list.
     *
     * @param workflowIds - list of workflow Ids to perform retry operation on
     * @return bulk response object containing a list of succeeded workflows and a list of failed
     *     ones with errors
     */
    @PostMapping("/retry")
    @Operation(summary = "Retry the last failed task for each workflow from the list")
    public BulkResponse<String> retry(@RequestBody List<String> workflowIds) {
        return workflowBulkService.retry(workflowIds);
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
    @PostMapping("/terminate")
    @Operation(summary = "Terminate workflows execution")
    public BulkResponse<String> terminate(
            @RequestBody List<String> workflowIds,
            @RequestParam(value = "reason", required = false) String reason) {
        return workflowBulkService.terminate(workflowIds, reason);
    }

    /**
     * Delete the list of workflows.
     *
     * @param workflowIds - list of workflow Ids to be deleted
     * @return bulk reponse object containing a list of successfully deleted workflows
     */
    @DeleteMapping("/remove")
    public BulkResponse<String> deleteWorkflow(
            @RequestBody List<String> workflowIds,
            @RequestParam(value = "archiveWorkflow", defaultValue = "true", required = false)
                    boolean archiveWorkflow) {
        return workflowBulkService.deleteWorkflow(workflowIds, archiveWorkflow);
    }

    /**
     * Terminate then delete the list of workflows.
     *
     * @param workflowIds - list of workflow Ids to be deleted
     * @return bulk response object containing a list of successfully deleted workflows
     */
    @DeleteMapping("/terminate-remove")
    public BulkResponse<String> terminateRemove(
            @RequestBody List<String> workflowIds,
            @RequestParam(value = "archiveWorkflow", defaultValue = "true", required = false)
                    boolean archiveWorkflow,
            @RequestParam(value = "reason", required = false) String reason) {
        return workflowBulkService.terminateRemove(workflowIds, reason, archiveWorkflow);
    }

    /**
     * Search workflows for given list of workflows.
     *
     * @param workflowIds - list of workflow Ids to be searched
     * @return bulk response object containing a list of workflows
     */
    @PostMapping("/search")
    public BulkResponse<WorkflowModel> searchWorkflow(
            @RequestBody List<String> workflowIds,
            @RequestParam(value = "includeTasks", defaultValue = "true", required = false)
                    boolean includeTasks) {
        return workflowBulkService.searchWorkflow(workflowIds, includeTasks);
    }
}
