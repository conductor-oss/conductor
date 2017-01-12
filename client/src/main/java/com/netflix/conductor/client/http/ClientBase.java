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
package com.netflix.conductor.client.http;

import java.net.URI;
import java.util.Collection;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandler;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.api.client.WebResource.Builder;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;

/**
 * 
 * Abstract client for the RESTtemplate
 *
 */
public abstract class ClientBase {

	protected Client client;
	
	protected String root = "";;
	
	protected ClientBase() {
		this(new DefaultClientConfig(), null);
	}
	
	protected ClientBase(ClientConfig cc) {
		this(cc, null);
	}
	
	protected ClientBase(ClientConfig cc, ClientHandler handler) {
		JacksonJsonProvider provider = new JacksonJsonProvider(objectMapper());
		cc.getSingletons().add(provider);
		if (handler == null) {
			this.client = Client.create(cc);
		} else {
			this.client = new Client(handler, cc);
		}		
	}
	
	public void setRootURI(String root) {
		this.root = root;
	}
	
	private UriBuilder getURI(String path, Object[] queryParams) {
		if(path == null){
			path = "";
		}
		UriBuilder builder = UriBuilder.fromPath(path);
		if(queryParams != null){
			int i = 0; 
			for(; i < queryParams.length; ){
				String param = queryParams[i].toString();
				Object value = queryParams[i+1];
				if(value != null){
					if(value instanceof Collection){
						Object[] values = ((Collection<?>)value).toArray();
						builder.queryParam(param, values);
					}else{
						builder.queryParam(param, value);
					}
				}
				i+=2;
			}
		}
		return builder;
	}

	protected void delete(String url, Object... uriVariables) {
		delete(null, url, uriVariables);
	}
	protected void delete(Object[] queryParams, String url, Object... uriVariables) {
		try {
			
			URI URI = getURI(root + url, queryParams).build(uriVariables);
			client.resource(URI).delete();
		} catch (Exception e) {
			handleException(e);
		}

	}

	protected void put(String url, Object[] queryParams, Object request, Object... uriVariables) {
		try {
			URI URI = getURI(root + url, queryParams).build(uriVariables);
			resource(URI, request).put();
		} catch(Exception e){
			handleException(e);
		}
		
	}
	
	protected void postForEntity(String url, Object request) {
		Class<?> type = null;
		postForEntity(url, request, null, type);
	}
	
	protected void postForEntity1(String url, Object... uriVariables) {
		Class<?> type = null;
		postForEntity(url, null, null, type, uriVariables);
	}
	
	protected <T> T postForEntity(String url, Object request, Object[] queryParams, Class<T> responseType, Object... uriVariables) {
		try {
			
			URI URI = getURI(root + url, queryParams).build(uriVariables);
			if(responseType == null) {
				resource(URI, request).post();
				return null;
			}
			T response = resource(URI, request).post(responseType);
			return response;
		} catch(Exception e){
			handleException(e);
		}
		return null;
	}
	
	protected <T> T postForEntity(String url, Object request, Object[] queryParams, GenericType<T> responseType, Object... uriVariables) {
		try {
			
			URI URI = getURI(root + url, queryParams).build(uriVariables);
			if(responseType == null) {
				resource(URI, request).post();
				return null;
			}
			T response = resource(URI, request).post(responseType);
			return response;
		} catch(Exception e){
			handleException(e);
		}
		return null;
	}
	

	protected <T> T getForEntity(String url, Object[] queryParams, Class<T> responseType, Object... uriVariables) {
		try {
			URI URI = getURI(root + url, queryParams).build(uriVariables);
			T response = client.resource(URI).accept(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN).get(responseType);
			return response;
		} catch (Exception e) {
			handleException(e);
		}
		return null;
	}
	
	protected <T> T getForEntity(String url, Object[] queryParams, GenericType<T> responseType, Object... uriVariables) {
		try {
			URI URI = getURI(root + url, queryParams).build(uriVariables);
			T response = client.resource(URI).accept(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN).get(responseType);
			return response;
		} catch (Exception e) {
			handleException(e);
		}
		return null;
	}
	
	private Builder resource(URI URI, Object entity) {
		return client.resource(URI).type(MediaType.APPLICATION_JSON).entity(entity).accept(MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON);
	}

	private void handleException(Exception e) {
		throw new RuntimeException(e);		
	}	
	
	
	protected ObjectMapper objectMapper() {
	    final ObjectMapper om = new ObjectMapper();
        om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        om.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
        om.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false);
        om.setSerializationInclusion(Include.NON_NULL);
        om.setSerializationInclusion(Include.NON_EMPTY);
	    return om;
	}
}
