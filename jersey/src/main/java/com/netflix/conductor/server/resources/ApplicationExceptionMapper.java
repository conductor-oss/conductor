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
import javax.inject.Singleton;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Variant;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.execution.ApplicationException;
import com.netflix.conductor.metrics.Monitors;
import com.sun.jersey.api.core.HttpContext;

/**
 * @author Viren
 *
 */
@Provider
@Singleton
public class ApplicationExceptionMapper implements ExceptionMapper<ApplicationException> {

	private static Logger logger = LoggerFactory.getLogger(ApplicationExceptionMapper.class);
	
	private static List<Variant> supportedMediaTypes = Variant.mediaTypes(MediaType.APPLICATION_JSON_TYPE, MediaType.TEXT_HTML_TYPE, MediaType.TEXT_PLAIN_TYPE).add().build();
	
	@Context 
    private HttpContext context;
	
	private String host;
	
	@Inject
	public ApplicationExceptionMapper(Configuration config) {
		this.host = config.getServerId();
	}
	
	@Override
	public Response toResponse(ApplicationException e) {
		logger.error(e.getMessage(), e);
		if(e.getHttpStatusCode() == 500) {
			Monitors.error("error", "error");
		}
		MediaType mediaType = context.getRequest().selectVariant(supportedMediaTypes).getMediaType();
		if(mediaType == null){
			mediaType = MediaType.APPLICATION_JSON_TYPE;
		}
		
		Map<String, Object> entityMap = e.toMap();
		entityMap.put("instance", host);
		Object entity = entityMap;
		
		if (mediaType == null || MediaType.APPLICATION_JSON_TYPE != mediaType) {
			entity = e.toMap().toString();
		}
		return Response.status(e.getHttpStatusCode()).entity(entity).type(mediaType).build();
	}
	
}
