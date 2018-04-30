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
package com.netflix.conductor.server;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Singleton;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.google.inject.Provides;
import com.sun.jersey.api.core.PackagesResourceConfig;
import com.sun.jersey.api.core.ResourceConfig;
import com.sun.jersey.guice.JerseyServletModule;
import com.sun.jersey.guice.spi.container.servlet.GuiceContainer;

/**
 * 
 * @author Viren
 *
 */
public final class JerseyModule extends JerseyServletModule {
	
    @Override
    protected void configureServlets() {


    	filter("/*").through(apiOriginFilter());
        
        Map<String, String> jerseyParams = new HashMap<>();	
		jerseyParams.put("com.sun.jersey.config.feature.FilterForwardOn404", "true");
		jerseyParams.put("com.sun.jersey.config.property.WebPageContentRegex", "/(((webjars|api-docs|swagger-ui/docs|manage)/.*)|(favicon\\.ico))");
		jerseyParams.put(PackagesResourceConfig.PROPERTY_PACKAGES, "com.netflix.conductor.server.resources;io.swagger.jaxrs.json;io.swagger.jaxrs.listing");
		jerseyParams.put(ResourceConfig.FEATURE_DISABLE_WADL, "false");
		serve("/api/*").with(GuiceContainer.class, jerseyParams);
    }
    
    @Provides 
	@Singleton
	public ObjectMapper objectMapper() {
	    final ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false);
        objectMapper.setSerializationInclusion(Include.NON_NULL);
        objectMapper.setSerializationInclusion(Include.NON_EMPTY);
	    return objectMapper;
	}

	@Provides 
	@Singleton
	JacksonJsonProvider jacksonJsonProvider(ObjectMapper mapper) {
	    return new JacksonJsonProvider(mapper);
	}
	
	@Provides
    @Singleton
    public Filter apiOriginFilter() {
        return new Filter(){

			@Override
			public void init(FilterConfig filterConfig) throws ServletException {}

			@Override
			public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		        HttpServletResponse res = (HttpServletResponse) response;
		        if (!res.containsHeader("Access-Control-Allow-Origin")) {
		            res.setHeader("Access-Control-Allow-Origin", "*");
		        }
		        res.addHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, PUT");
		        res.addHeader("Access-Control-Allow-Headers", "Content-Type, api_key, Authorization");
		        
		        chain.doFilter(request, response);
		    }
			@Override
			public void destroy() {}
        	
        };
    }
    @Override
    public boolean equals(Object obj) {
        return obj != null && getClass().equals(obj.getClass());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }


}
