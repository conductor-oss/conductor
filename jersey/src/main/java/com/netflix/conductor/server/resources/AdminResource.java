/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.conductor.server.resources;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.service.AdminService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;

/**
 * @author Viren
 *
 */
@Api(value = "/admin", produces = MediaType.APPLICATION_JSON, consumes = MediaType.APPLICATION_JSON, tags = "Admin")
@Path("/admin")
@Produces({ MediaType.APPLICATION_JSON })
@Consumes({ MediaType.APPLICATION_JSON })
@Singleton
public class AdminResource {
	private final AdminService adminService;

    @Inject
	public AdminResource(AdminService adminService) {
		this.adminService = adminService;
	}

	@ApiOperation(value = "Get all the configuration parameters")
	@GET
	@Consumes(MediaType.TEXT_PLAIN)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/config")
	public Map<String, Object> getAllConfig() {
        return adminService.getAllConfig();
	}

	@GET
	@Path("/task/{tasktype}")
	@ApiOperation("Get the list of pending tasks for a given task type")
	@Consumes({ MediaType.WILDCARD })
	public List<Task> view(@PathParam("tasktype") String taskType,
                           @DefaultValue("0") @QueryParam("start") Integer start,
                           @DefaultValue("100") @QueryParam("count") Integer count) {
        return adminService.getListOfPendingTask(taskType, start, count);
	}

	@POST
	@Path("/sweep/requeue/{workflowId}")
	@ApiOperation("Queue up all the running workflows for sweep")
	@Consumes({ MediaType.WILDCARD })
	@Produces({ MediaType.TEXT_PLAIN })
	public String requeueSweep(@PathParam("workflowId") String workflowId) {
        return adminService.requeueSweep(workflowId);
	}


	@POST
	@Path("/consistency/verifyAndRepair/{workflowId}")
	@ApiOperation("Verify and repair workflow consistency")
	@Consumes({ MediaType.WILDCARD })
	@Produces({ MediaType.TEXT_PLAIN })
	public String verifyAndRepairWorkflowConsistency(@PathParam("workflowId") String workflowId) {
		return String.valueOf(adminService.verifyAndRepairWorkflowConsistency(workflowId));
	}

}
