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

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.execution.ApplicationException;
import com.netflix.conductor.core.execution.ApplicationException.Code;
import com.netflix.conductor.metrics.Monitors;
import com.sun.jersey.api.core.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
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
public class GenericExceptionMapper implements ExceptionMapper<Throwable> {

	private static final Logger LOGGER = LoggerFactory.getLogger(GenericExceptionMapper.class);

	@Context 
    private HttpContext context;

	@Context
	private UriInfo uriInfo;

	private String host;
	
	@Inject
	public GenericExceptionMapper(Configuration config) {
		this.host = config.getServerId();
	}
	
	@Override
	public Response toResponse(Throwable exception) {
		LOGGER.error(String.format("Error %s url: '%s'", exception.getClass().getSimpleName(), uriInfo.getPath()), exception);

		Monitors.error("error", "error");

		ApplicationException applicationException = null;

        if (exception instanceof IllegalArgumentException || exception instanceof InvalidFormatException) {
            applicationException = new ApplicationException(Code.INVALID_INPUT, exception.getMessage(), exception);
        } else {
            applicationException = new ApplicationException(Code.INTERNAL_ERROR, exception.getMessage(), exception);
        }
		
		Map<String, Object> entityMap = applicationException.toMap();
		entityMap.put("instance", host);
		
		return Response.status(applicationException.getHttpStatusCode()).entity(entityMap).type(MediaType.APPLICATION_JSON_TYPE).build();
	}
	
}
