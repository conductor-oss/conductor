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

import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.core.Variant;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.execution.ApplicationException;
import com.netflix.conductor.core.execution.ApplicationException.Code;
import com.netflix.conductor.metrics.Monitors;
import com.sun.jersey.api.core.HttpContext;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;

/**
 * @author Viren
 *
 */
@Provider
@Singleton
public class GenericExceptionMapper implements ExceptionMapper<Throwable> {

	private static Logger logger = LoggerFactory.getLogger(GenericExceptionMapper.class);
	
	private static List<Variant> supportedMediaTypes = Variant.mediaTypes(MediaType.APPLICATION_JSON_TYPE, MediaType.TEXT_HTML_TYPE, MediaType.TEXT_PLAIN_TYPE).add().build();
	
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
		logger.error(String.format("Error %s url: '%s'", exception.getClass().getSimpleName(), uriInfo.getPath()), exception);
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
