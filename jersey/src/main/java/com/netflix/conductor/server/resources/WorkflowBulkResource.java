/**
 * Copyright 2016 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 *
 */
package com.netflix.conductor.server.resources;

import com.netflix.conductor.service.WorkflowBulkService;
import com.netflix.conductor.service.common.BulkResponse;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.List;


/**
 * Synchronous Bulk APIs to process the workflows in batches
 */
@Api(value = "/workflow/bulk", produces = MediaType.APPLICATION_JSON, consumes = MediaType.APPLICATION_JSON, tags = "Workflow Bulk Management")
@Path("/workflow/bulk")
@Produces({MediaType.APPLICATION_JSON})
@Consumes({MediaType.APPLICATION_JSON})
@Singleton
public class WorkflowBulkResource {

    private WorkflowBulkService workflowBulkService;

    @Inject
    public WorkflowBulkResource(WorkflowBulkService workflowBulkService) {
        this.workflowBulkService = workflowBulkService;
    }

    /**
     * Pause the list of workflows.
     * @param workflowIds - list of workflow Ids  to perform pause operation on
     * @return bulk response object containing a list of succeeded workflows and a list of failed ones with errors
     */
    @PUT
    @Path("/pause")
    @ApiOperation("Pause the list of workflows")
    public BulkResponse pauseWorkflow(List<String> workflowIds) {
        return workflowBulkService.pauseWorkflow(workflowIds);
    }

    /**
     * Resume the list of workflows.
     * @param workflowIds - list of workflow Ids  to perform resume operation on
     * @return bulk response object containing a list of succeeded workflows and a list of failed ones with errors
     */
    @PUT
    @Path("/resume")
    @ApiOperation("Resume the list of workflows")
    public BulkResponse resumeWorkflow(List<String> workflowIds)  {
        return workflowBulkService.resumeWorkflow(workflowIds);
    }

    /**
     * Restart the list of workflows.
     *
     * @param workflowIds          - list of workflow Ids  to perform restart operation on
     * @param useLatestDefinitions if true, use latest workflow and task definitions upon restart
     * @return bulk response object containing a list of succeeded workflows and a list of failed ones with errors
     */
    @POST
    @Path("/restart")
    @ApiOperation("Restart the list of completed workflow")
    public BulkResponse restart(List<String> workflowIds, @QueryParam("useLatestDefinitions") @DefaultValue("false") boolean useLatestDefinitions) {
        return workflowBulkService.restart(workflowIds, useLatestDefinitions);
    }

    /**
     * Retry the last failed task for each workflow from the list.
     * @param workflowIds - list of workflow Ids  to perform retry operation on
     * @return bulk response object containing a list of succeeded workflows and a list of failed ones with errors
     */
    @POST
    @Path("/retry")
    @ApiOperation("Retry the last failed task for each workflow from the list")
    public BulkResponse retry(List<String> workflowIds) {
       return  workflowBulkService.retry(workflowIds);
    }

    /**
     * Terminate workflows execution.
     * @param workflowIds - list of workflow Ids  to perform terminate operation on
     * @param reason - description to be specified for the terminated workflow for future references.
     * @return bulk response object containing a list of succeeded workflows and a list of failed ones with errors
     */
    @DELETE
    @Path("/terminate")
    @ApiOperation("Terminate workflows execution")
    public BulkResponse terminate(List<String> workflowIds, @QueryParam("reason") String reason) {
        return workflowBulkService.terminate(workflowIds, reason);
    }
}
