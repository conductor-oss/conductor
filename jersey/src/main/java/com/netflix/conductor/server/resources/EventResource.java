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

import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.core.events.EventProcessor;
import com.netflix.conductor.service.MetadataService;

import io.swagger.annotations.Api;


/**
 * @author Viren
 *
 */
@Api(value="/event", produces=MediaType.APPLICATION_JSON, consumes=MediaType.APPLICATION_JSON, tags="Event Services")
@Path("/event")
@Produces({MediaType.APPLICATION_JSON})
@Consumes({MediaType.APPLICATION_JSON})
public class EventResource {

	private MetadataService service;
	
	private EventProcessor ep;
	
	@Inject
	public EventResource(MetadataService service, EventProcessor ep) {
		this.service = service;
		this.ep = ep;
	}

	@POST
	public void addEventHandler(EventHandler eventHandler) {
		service.addEventHandler(eventHandler);
	}

	@PUT
	public void updateEventHandler(EventHandler eventHandler) {
		service.updateEventHandler(eventHandler);
	}
	

	@DELETE
	@Path("/{name}")
	public void removeEventHandlerStatus(@PathParam("name") String name) {
		service.removeEventHandlerStatus(name);
	}

	@GET
	public List<EventHandler> getEventHandlers() {
		return service.getEventHandlers();
	}
	
	@GET
	@Path("/{event}")
	public List<EventHandler> getEventHandlersForEvent(@PathParam("event") String event, @QueryParam("activeOnly") @DefaultValue("true") boolean activeOnly) {
		return service.getEventHandlersForEvent(event, activeOnly);
	}
	
	@GET
	@Path("/queues")
	public Map<String, ?> getEventQueues(@QueryParam("v") @DefaultValue("false") boolean verbose) {
		return (verbose ? ep.getQueueSizes() : ep.getQueues());
	}

	
}
