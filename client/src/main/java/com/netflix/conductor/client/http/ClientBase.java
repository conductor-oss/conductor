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
import java.util.function.Function;

/**
 * Abstract client for the REST template
 */
public abstract class ClientBase {
    private static Logger logger = LoggerFactory.getLogger(ClientBase.class);
    protected final Client client;
    protected String root = "";

    protected ClientBase() {
        this(new DefaultClientConfig(), null);
    }

    protected ClientBase(ClientConfig clientConfig) {
        this(clientConfig, null);
    }

    protected ClientBase(ClientConfig clientConfig, ClientHandler handler) {
        JacksonJsonProvider provider = new JacksonJsonProvider(createObjectMapper());
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

    protected void delete(String url, Object... uriVariables) {
        delete(null, url, uriVariables);
    }

    protected void delete(Object[] queryParams, String url, Object... uriVariables) {
        URI uri = null;
        try {
            uri = getURIBuilder(root + url, queryParams).build(uriVariables);
            client.resource(uri).delete();
        } catch (RuntimeException e) {
            handleException(uri, e);
        }
    }

    protected void put(String url, Object[] queryParams, Object request, Object... uriVariables) {
        URI uri = null;
        try {
            uri = getURIBuilder(root + url, queryParams).build(uriVariables);
            getWebResourceBuilder(uri, request).put();
        } catch (RuntimeException e) {
            handleException(uri, e);
        }
    }

    /**
     * @deprecated replaced by {@link #postForEntityWithRequestOnly(String, Object)} ()}
     */
    @Deprecated
    protected void postForEntity(String url, Object request) {
        postForEntityWithRequestOnly(url, request);
    }


    protected void postForEntityWithRequestOnly(String url, Object request) {
        Class<?> type = null;
        postForEntity(url, request, null, type);
    }

    /**
     * @deprecated replaced by {@link #postForEntityWithUriVariablesOnly(String, Object...)} ()}
     */
    @Deprecated
    protected void postForEntity1(String url, Object... uriVariables) {
        postForEntityWithUriVariablesOnly(url, uriVariables);
    }

    protected void postForEntityWithUriVariablesOnly(String url, Object... uriVariables) {
        Class<?> type = null;
        postForEntity(url, null, null, type, uriVariables);
    }


    protected <T> T postForEntity(String url, Object request, Object[] queryParams, Class<T> responseType, Object... uriVariables) {
        return postForEntity(url, request, queryParams, responseType, builder -> builder.post(responseType), uriVariables);
    }

    protected <T> T postForEntity(String url, Object request, Object[] queryParams, GenericType<T> responseType, Object... uriVariables) {
        return postForEntity(url, request, queryParams, responseType, builder -> builder.post(responseType), uriVariables);
    }

    private <T> T postForEntity(String url, Object request, Object[] queryParams, Object responseType, Function<Builder, T> postWithEntity, Object... uriVariables) {
        URI uri = null;
        try {
            uri = getURIBuilder(root + url, queryParams).build(uriVariables);
            Builder webResourceBuilder = getWebResourceBuilder(uri, request);
            if (responseType == null) {
                webResourceBuilder.post();
                return null;
            }
            return postWithEntity.apply(webResourceBuilder);
        } catch (RuntimeException e) {
            handleException(uri, e);
        }
        return null;
    }

    protected <T> T getForEntity(String url, Object[] queryParams, Class<T> responseType, Object... uriVariables) {
        return getForEntity(url, queryParams, response -> response.getEntity(responseType), uriVariables);
    }

    protected <T> T getForEntity(String url, Object[] queryParams, GenericType<T> responseType, Object... uriVariables) {
        return getForEntity(url, queryParams, response -> response.getEntity(responseType), uriVariables);
    }

    private <T> T getForEntity(String url, Object[] queryParams, Function<ClientResponse, T> entityPvoider, Object... uriVariables) {
        URI uri = null;
        try {
            uri = getURIBuilder(root + url, queryParams).build(uriVariables);
            ClientResponse response = client.resource(uri)
                    .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN)
                    .get(ClientResponse.class);
            if (response.getStatus() < 300) {
                return entityPvoider.apply(response);
            } else {
                throw new UniformInterfaceException(response); // let handleException to handle unexpected response consistently
            }
        } catch (RuntimeException e) {
            handleException(uri, e);
        }
        return null;
    }

    private Builder getWebResourceBuilder(URI URI, Object entity) {
        return client.resource(URI).type(MediaType.APPLICATION_JSON).entity(entity).accept(MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON);
    }

    private void handleException(URI uri, RuntimeException e) {
        if (e instanceof ClientHandlerException) {
            logger.error("Unable to invoke Conductor API with uri: {}, failure to process request or response", uri, e);
        } else if (e instanceof UniformInterfaceException) {
            logger.error("Unable to invoke Conductor API with uri: {}, unexpected response from server: {}", uri, clientResponseToString(((UniformInterfaceException) e).getResponse()), e);
        } else {
            logger.error("Unable to invoke Conductor API with uri: {}, runtime exception occurred", uri, e);
        }
        throw e;
    }

    /**
     * Converts ClientResponse object to string with detailed debug information including status code, media type,
     * response headers, and response body if exists.
     */
    private String clientResponseToString(ClientResponse response) {
        if (response == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        builder.append("[status: ").append(response.getStatus());
        builder.append(", media type: ").append(response.getType());
        if (response.getStatus() != 404) {
            try {
                String responseBody = response.getEntity(String.class);
                if (responseBody != null) {
                    builder.append(", response body: ").append(responseBody);
                }
            } catch (RuntimeException ignore) {
                // Ignore if there is no response body, or IO error - it may have already been read in certain scenario.
            }
        }
        builder.append(", response headers: ").append(response.getHeaders());
        builder.append("]");
        return builder.toString();
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

    private ObjectMapper createObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();

        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false);
        objectMapper.setSerializationInclusion(Include.NON_NULL);
        objectMapper.setSerializationInclusion(Include.NON_EMPTY);

        return objectMapper;
    }
}
