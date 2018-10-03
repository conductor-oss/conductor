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

import com.google.common.annotations.VisibleForTesting;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.execution.ApplicationException;
import com.netflix.conductor.metrics.Monitors;
import com.sun.jersey.api.core.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.Map;

/**
 * @author Viren
 *
 */
@Provider
@Singleton
public class ApplicationExceptionMapper implements ExceptionMapper<ApplicationException> {
	private static Logger LOGGER = LoggerFactory.getLogger(ApplicationExceptionMapper.class);

	@Context
	private HttpContext context;

	@Context
	private UriInfo uriInfo;

	@Context
	private javax.inject.Provider<Request> request;
	private String host;

	@Inject
	public ApplicationExceptionMapper(Configuration config) {
		this.host = config.getServerId();
	}

	@Override
	public Response toResponse(ApplicationException e) {
		logException(e);

		Response.ResponseBuilder responseBuilder = Response.status(e.getHttpStatusCode());

		if(e.getHttpStatusCode() == 500) {
			Monitors.error("error", "error");
		}

		Map<String, Object> entityMap = e.toMap();
		entityMap.put("instance", host);

		responseBuilder.type(MediaType.APPLICATION_JSON_TYPE);
		responseBuilder.entity(entityMap);

		return responseBuilder.build();
	}

	@VisibleForTesting
	UriInfo getUriInfo() {
		return uriInfo;
	}

	@VisibleForTesting
	Request getRequest() {
		return request.get();
	}

	private void logException(ApplicationException exception) {
		LOGGER.error(String.format("Error %s url: '%s'", exception.getClass().getSimpleName(),
				getUriInfo().getPath()), exception);
	}
	
}
