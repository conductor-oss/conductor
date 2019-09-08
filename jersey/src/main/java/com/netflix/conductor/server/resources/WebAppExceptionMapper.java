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

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;


import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.execution.Code;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @author Viren
 *
 */
@Provider
public class WebAppExceptionMapper implements ExceptionMapper<WebApplicationException> {

    private static Logger logger = LoggerFactory.getLogger(WebAppExceptionMapper.class);

    @Context
    private UriInfo uriInfo;

    private final String host;

    private Code code;

    @Inject
    public WebAppExceptionMapper(Configuration config) {
        this.host = config.getServerId();
    }

	@Override
	public Response toResponse(WebApplicationException exception) {
        logger.error(String.format("Error %s url: '%s'", exception.getClass().getSimpleName(),
                uriInfo.getPath()), exception);

        Response response = exception.getResponse();
        this.code = Code.forValue(response.getStatus());
        Map<String, Object> entityMap = new LinkedHashMap<>();
        entityMap.put("instance", host);
        entityMap.put("code", Optional.ofNullable(code).map(Code::name).orElse(null));
        entityMap.put("message", exception.getCause());
        entityMap.put("retryable", false);

        return Response.status(response.getStatus()).entity(entityMap).build();
	}

}

