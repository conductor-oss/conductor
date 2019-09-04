/**
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
/**
 * 
 */
package com.netflix.conductor.contribs.queue;

import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.netflix.conductor.common.metadata.tasks.Task.Status;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

/**
 * @author Viren
 *
 */
@Api(value="/queue", produces=MediaType.APPLICATION_JSON, consumes=MediaType.APPLICATION_JSON, tags="Queue Management")
@Path("/queue")
@Produces({ MediaType.APPLICATION_JSON })
@Consumes({ MediaType.APPLICATION_JSON })
@Singleton
public class QueueAdminResource {

	private QueueManager qm;
	
	@Inject
	public QueueAdminResource(QueueManager qm) {
		this.qm = qm;
	}
	
	@ApiOperation("Get the queue length")
	@GET
	@Path("/size")
	@Consumes(MediaType.WILDCARD)
	public Map<String, Long> size() {
		return qm.size();
	}
	
	@ApiOperation("Get Queue Names")
	@GET
	@Path("/")
	@Consumes(MediaType.WILDCARD)
	public Map<Status, String> names() {
		return qm.queues();
	}
	
	@POST
	@Path("/update/{workflowId}/{taskRefName}/{status}")
	@ApiOperation("Publish a message in queue to mark a wait task as completed.")
	public void update(@PathParam("workflowId") String workflowId, @PathParam("taskRefName") String taskRefName, @PathParam("status") Status status, Map<String, Object> output) throws Exception {
		qm.updateByTaskRefName(workflowId, taskRefName, output, status);
	}

	@POST
	@Path("/update/{workflowId}/task/{taskId}/{status}")
	@ApiOperation("Publish a message in queue to mark a wait task (by taskId) as completed.")
	public void updateByTaskId(@PathParam("workflowId") String workflowId, @PathParam("taskId") String taskId, @PathParam("status") Status status, Map<String, Object> output) throws Exception {
		qm.updateByTaskId(workflowId, taskId, output, status);
	}
	
}
