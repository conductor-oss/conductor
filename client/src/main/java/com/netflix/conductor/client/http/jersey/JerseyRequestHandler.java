/*
 * Copyright 2022 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.client.http.jersey;

import java.io.InputStream;
import java.net.URI;

import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.client.exception.RequestHandlerException;
import com.netflix.conductor.client.http.RequestHandler;
import com.netflix.conductor.common.config.ObjectMapperProvider;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandler;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.client.filter.ClientFilter;

/** A {@link RequestHandler} implementation that uses the Jersey HTTP Client. */
public class JerseyRequestHandler implements RequestHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(JerseyRequestHandler.class);

    private final Client client;

    public JerseyRequestHandler() {
        this(new ClientFilter[0]);
    }

    public JerseyRequestHandler(ClientFilter... filters) {
        this(null, filters);
    }

    public JerseyRequestHandler(ClientHandler clientHandler, ClientFilter... filters) {
        this(null, clientHandler, filters);
    }

    public JerseyRequestHandler(
            ClientConfig config, ClientHandler handler, ClientFilter... filters) {

        if (config == null) {
            config = new DefaultClientConfig();
            ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();

            // https://github.com/FasterXML/jackson-databind/issues/2683
            if (isNewerJacksonVersion()) {
                objectMapper.registerModule(new JavaTimeModule());
            }

            JacksonJsonProvider provider = new JacksonJsonProvider(objectMapper);
            config.getSingletons().add(provider);
        }

        if (handler == null) {
            this.client = Client.create(config);
        } else {
            this.client = new Client(handler, config);
        }

        for (ClientFilter filter : filters) {
            this.client.addFilter(filter);
        }
    }

    @Override
    public void delete(URI uri) {
        try {
            client.resource(uri).delete();
        } catch (UniformInterfaceException e) {
            handleUniformInterfaceException(e, uri);
        } catch (RuntimeException e) {
            handleRuntimeException(e, uri);
        }
    }

    @Override
    public InputStream put(URI uri, Object body) {
        ClientResponse clientResponse;
        try {
            clientResponse = getWebResourceBuilder(uri, body).put(ClientResponse.class);
            return clientResponse.getEntityInputStream();
        } catch (RuntimeException e) {
            handleException(uri, e);
        }

        return null;
    }

    @Override
    public InputStream post(URI uri, Object body) {
        ClientResponse clientResponse;
        try {
            clientResponse = getWebResourceBuilder(uri, body).post(ClientResponse.class);
            return clientResponse.getEntityInputStream();
        } catch (UniformInterfaceException e) {
            handleUniformInterfaceException(e, uri);
        } catch (RuntimeException e) {
            handleRuntimeException(e, uri);
        }
        return null;
    }

    @Override
    public InputStream get(URI uri) {
        ClientResponse clientResponse;
        try {
            clientResponse =
                    client.resource(uri)
                            .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN)
                            .get(ClientResponse.class);
            if (clientResponse.getStatus() < 300) {
                return clientResponse.getEntityInputStream();
            } else {
                throw new UniformInterfaceException(clientResponse);
            }
        } catch (UniformInterfaceException e) {
            handleUniformInterfaceException(e, uri);
        } catch (RuntimeException e) {
            handleRuntimeException(e, uri);
        }

        return null;
    }

    private void handleClientHandlerException(ClientHandlerException exception, URI uri) {
        String errorMessage =
                String.format(
                        "Unable to invoke Conductor API with uri: %s, failure to process request or response",
                        uri);
        LOGGER.error(errorMessage, exception);
        throw new RequestHandlerException(errorMessage, exception);
    }

    private void handleRuntimeException(RuntimeException exception, URI uri) {
        String errorMessage =
                String.format(
                        "Unable to invoke Conductor API with uri: %s, runtime exception occurred",
                        uri);
        LOGGER.error(errorMessage, exception);
        throw new RequestHandlerException(errorMessage, exception);
    }

    private void handleUniformInterfaceException(UniformInterfaceException exception, URI uri) {
        ClientResponse clientResponse = exception.getResponse();
        if (clientResponse == null) {
            throw new RequestHandlerException(
                    String.format("Unable to invoke Conductor API with uri: %s", uri));
        }
        try {
            if (clientResponse.getStatus() < 300) {
                return;
            }
            LOGGER.warn(
                    "Unable to invoke Conductor API with uri: {}, unexpected response from server: statusCode={}",
                    uri,
                    clientResponse.getStatus());
            throw new RequestHandlerException(
                    clientResponse.getEntityInputStream(), clientResponse.getStatus());
        } catch (RequestHandlerException e) {
            throw e;
        } catch (ClientHandlerException e) {
            handleClientHandlerException(e, uri);
        } catch (RuntimeException e) {
            handleRuntimeException(e, uri);
        } finally {
            clientResponse.close();
        }
    }

    private void handleException(URI uri, RuntimeException e) {
        if (e instanceof UniformInterfaceException) {
            handleUniformInterfaceException(((UniformInterfaceException) e), uri);
        } else if (e instanceof ClientHandlerException) {
            handleClientHandlerException((ClientHandlerException) e, uri);
        } else {
            handleRuntimeException(e, uri);
        }
    }

    private WebResource.Builder getWebResourceBuilder(URI URI, Object entity) {
        return client.resource(URI)
                .type(MediaType.APPLICATION_JSON)
                .entity(entity)
                .accept(MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON);
    }

    private boolean isNewerJacksonVersion() {
        Version version = com.fasterxml.jackson.databind.cfg.PackageVersion.VERSION;
        return version.getMajorVersion() == 2 && version.getMinorVersion() >= 12;
    }
}
