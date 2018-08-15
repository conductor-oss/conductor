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
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.List;


/**
 * @author Alex
 */
@Api(value = "/workflow/bulk", produces = MediaType.APPLICATION_JSON, consumes = MediaType.APPLICATION_JSON, tags = "Workflow Bulk Management")
@Path("/workflow/bulk")
@Produces({MediaType.APPLICATION_JSON})
@Consumes({MediaType.APPLICATION_JSON})
@Singleton
public class WorkflowBulkResource {

    private static final int MAX_REQUEST_ITEMS = 1000;
    private final WorkflowBulkService workflowBulkService;

    @Inject
    public WorkflowBulkResource(WorkflowBulkService workflowBulkService) {
        this.workflowBulkService = workflowBulkService;
    }

    @PUT
    @Path("/pause")
    @ApiOperation("Pause list of workflows")
    @Consumes(MediaType.WILDCARD)
    public void pauseWorkflow(List<String> workflowIds) {
        workflowBulkService.pauseWorkflow(workflowIds);
    }

    @PUT
    @Path("/resume")
    @ApiOperation("Resume list of workflows")
    @Consumes(MediaType.WILDCARD)
    public void resumeWorkflow(List<String> workflowIds) {
        workflowBulkService.resumeWorkflow(workflowIds);
    }


    @POST
    @Path("/restart")
    @ApiOperation("Restart list of completed workflow")
    @Consumes(MediaType.WILDCARD)
    public void restart(List<String> workflowIds) {
        workflowBulkService.restart(workflowIds);
    }

    @POST
    @Path("/retry")
    @ApiOperation("Retry last failed task for each workflow from the list")
    @Consumes(MediaType.WILDCARD)
    public void retry(List<String> workflowIds) {
        workflowBulkService.retry(workflowIds);
    }

    @DELETE
    @Path("/")
    @ApiOperation("Terminate workflows execution")
    @Consumes(MediaType.WILDCARD)
    public void terminate(List<String> workflowIds,
                          @QueryParam("reason") String reason) {
        workflowBulkService.terminate(workflowIds, reason);
    }
}
