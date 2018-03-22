/**
 * Copyright 2016 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
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

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandler;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource.Builder;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.Collection;

/**
 * Abstract client for the REST template
 */
public abstract class ClientBase {
    private static Logger logger = LoggerFactory.getLogger(ClientBase.class);

    private ObjectMapper objectMapper;

    protected Client client;

    protected String root = "";

    protected ClientBase() {
        this(new DefaultClientConfig(), null);
    }

    protected ClientBase(ClientConfig clientConfig) {
        this(clientConfig, null);
    }

    protected ClientBase(ClientConfig clientConfig, ClientHandler handler) {
        JacksonJsonProvider provider = new JacksonJsonProvider(getObjectMapper());
        clientConfig.getSingletons().add(provider);
        if (handler == null) {
            this.client = Client.create(clientConfig);
        } else {
            this.client = new Client(handler, clientConfig);
        }
    }

    public void setRootURI(String root) {
        this.root = root;
    }

    private UriBuilder getURIBuilder(String path, Object[] queryParams) {
        if (path == null) {
            path = "";
        }
        UriBuilder builder = UriBuilder.fromPath(path);
        if (queryParams != null) {
            for (int i = 0; i < queryParams.length; i += 2) {
                String param = queryParams[i].toString();
                Object value = queryParams[i + 1];
                if (value != null) {
                    if (value instanceof Collection) {
                        Object[] values = ((Collection<?>) value).toArray();
                        builder.queryParam(param, values);
                    } else {
                        builder.queryParam(param, value);
                    }
                }
            }
        }
        return builder;
    }

    protected void delete(String url, Object... uriVariables) {
        delete(null, url, uriVariables);
    }

    protected void delete(Object[] queryParams, String url, Object... uriVariables) {
        try {
            URI URI = getURIBuilder(root + url, queryParams).build(uriVariables);
            client.resource(URI).delete();
        } catch (Exception e) {
            handleException(e);
        }

    }

    protected void put(String url, Object[] queryParams, Object request, Object... uriVariables) {
        try {
            URI URI = getURIBuilder(root + url, queryParams).build(uriVariables);
            getWebResourceBuilder(URI, request).put();
        } catch (Exception e) {
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
            URI URI = getURIBuilder(root + url, queryParams).build(uriVariables);
            if (responseType == null) {
                getWebResourceBuilder(URI, request).post();
                return null;
            }
            return getWebResourceBuilder(URI, request).post(responseType);
        } catch (Exception e) {
            handleException(e);
        }
        return null;
    }

    protected <T> T postForEntity(String url, Object request, Object[] queryParams, GenericType<T> responseType, Object... uriVariables) {
        try {
            URI URI = getURIBuilder(root + url, queryParams).build(uriVariables);
            if (responseType == null) {
                getWebResourceBuilder(URI, request).post();
                return null;
            }
            return getWebResourceBuilder(URI, request).post(responseType);
        } catch (Exception e) {
            handleException(e);
        }
        return null;
    }


    protected <T> T getForEntity(String url, Object[] queryParams, Class<T> responseType, Object... uriVariables) {
        try {
            URI URI = getURIBuilder(root + url, queryParams).build(uriVariables);
            ClientResponse response = client.resource(URI)
                    .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN)
                    .get(ClientResponse.class);
            return response.getEntity(responseType);
        } catch (Exception e) {
            handleException(e);
        }
        return null;
    }

    protected <T> T getForEntity(String url, Object[] queryParams, GenericType<T> responseType, Object... uriVariables) {
        try {
            URI URI = getURIBuilder(root + url, queryParams).build(uriVariables);
            ClientResponse response = client.resource(URI)
                    .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN)
                    .get(ClientResponse.class);
            return response.getEntity(responseType);
        } catch (Exception e) {
            handleException(e);
        }
        return null;
    }

    private Builder getWebResourceBuilder(URI URI, Object entity) {
        return client.resource(URI).type(MediaType.APPLICATION_JSON).entity(entity).accept(MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON);
    }

    private void handleException(Exception e) {
        if (e instanceof ClientHandlerException) {
            logger.error("Unable to process the request", e);
        } else if (e instanceof UniformInterfaceException) {
            logger.error("Error processing response: {}", ((UniformInterfaceException) e).getResponse());
            logger.error("Unexpected response from server", e);
        }
        throw new RuntimeException(e);
    }

    private ObjectMapper getObjectMapper() {
        if (objectMapper != null) {
            return objectMapper;
        }
        objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false);
        objectMapper.setSerializationInclusion(Include.NON_NULL);
        objectMapper.setSerializationInclusion(Include.NON_EMPTY);
        return objectMapper;
    }
}
