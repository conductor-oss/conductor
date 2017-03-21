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
package com.netflix.conductor.server.resources;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Properties;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.service.ExecutionService;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

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

	private static Logger logger = LoggerFactory.getLogger(AdminResource.class);
	
	private Configuration config;

	private ExecutionService service;
	
	private QueueDAO queue;
	
	private String version;
	
	private String buildDate;
	
	@Inject
	public AdminResource(Configuration config, ExecutionService service, QueueDAO queue) {
		this.config = config;
		this.service = service;
		this.queue = queue;
		this.version = "UNKNOWN";
		this.buildDate = "UNKNOWN";
		
		try {
			
			InputStream propertiesIs = this.getClass().getClassLoader().getResourceAsStream("META-INF/conductor-core.properties");
			Properties prop = new Properties();
			prop.load(propertiesIs);
			this.version = prop.getProperty("Implementation-Version");
			this.buildDate = prop.getProperty("Build-Date");
		}catch(Exception e) {
			logger.error(e.getMessage(), e);
		}
	}

	@ApiOperation(value = "Get all the configuration parameters")
	@GET
	@Consumes(MediaType.TEXT_PLAIN)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/config")
	public Map<String, Object> getAllConfig() {
		Map<String, Object> map = config.getAll();
		map.put("version", version);
		map.put("buildDate", buildDate);
		return map;
	}
	
	
	@GET
	@Path("/task/{tasktype}")
	@ApiOperation("Get the list of pending tasks for a given task type")
	@Consumes({ MediaType.WILDCARD })
	public List<Task> view(@PathParam("tasktype") String taskType,  @DefaultValue("0") @QueryParam("start") Integer start, @DefaultValue("100") @QueryParam("count") Integer count) throws Exception {
		List<Task> tasks = service.getPendingTasksForTaskType(taskType);
		int total = start + count;
		total = (tasks.size() > total) ? total : tasks.size();
		if(start > tasks.size()) start = tasks.size();
		return tasks.subList(start, total); 
	}

	@POST
	@Path("/sweep/requeue/{workflowId}")
	@ApiOperation("Queue up all the running workflows for sweep")
	@Consumes({ MediaType.WILDCARD })
	@Produces({ MediaType.TEXT_PLAIN })
	public String requeueSweep(@PathParam("workflowId") String workflowId) throws Exception {
		boolean pushed = queue.pushIfNotExists(WorkflowExecutor.deciderQueue, workflowId, config.getSweepFrequency());
		return pushed + "." + workflowId;
	}
	
}
