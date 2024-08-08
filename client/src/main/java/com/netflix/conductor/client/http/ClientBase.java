/*
 * Copyright 2020 Conductor Authors.
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
package com.netflix.conductor.client.http;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.client.config.ConductorClientConfiguration;
import com.netflix.conductor.client.config.DefaultConductorClientConfiguration;
import com.netflix.conductor.client.exception.ConductorClientException;
import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.run.ExternalStorageLocation;
import com.netflix.conductor.common.utils.ExternalPayloadStorage;
import com.netflix.conductor.common.validation.ErrorResponse;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import lombok.Getter;

/** Abstract client for the REST server */
public abstract class ClientBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientBase.class);

    protected ClientRequestHandler requestHandler;

    protected String root = "";

    protected ObjectMapper objectMapper;

    protected PayloadStorage payloadStorage;

    protected ConductorClientConfiguration conductorClientConfiguration;

    protected ClientBase(
            ClientRequestHandler requestHandler, ConductorClientConfiguration clientConfiguration) {
        this.objectMapper = new ObjectMapperProvider().getObjectMapper();

        // https://github.com/FasterXML/jackson-databind/issues/2683
        if (isNewerJacksonVersion()) {
            objectMapper.registerModule(new JavaTimeModule());
        }

        this.requestHandler = requestHandler;
        this.conductorClientConfiguration =
                ObjectUtils.defaultIfNull(
                        clientConfiguration, new DefaultConductorClientConfiguration());
        this.payloadStorage = new PayloadStorage(this);
    }

    public void setRootURI(String root) {
        this.root = root;
    }

    protected void delete(String url, Object... uriVariables) {
        deleteWithUriVariables(null, url, uriVariables);
    }

    protected void deleteWithUriVariables(
            Object[] queryParams, String url, Object... uriVariables) {
        delete(queryParams, url, uriVariables);
    }

    private void delete(Object[] queryParams, String url, Object[] uriVariables) {
        URI uri = null;
        try {
            uri = getURIBuilder(root + url, queryParams).build(uriVariables);
            Response response = requestHandler.delete(uri);
            if (response.getStatus() >= 300) {
                throw new UniformInterfaceException(response);
            }
        } catch (UniformInterfaceException e) {
            handleUniformInterfaceException(e, uri);
        } catch (RuntimeException e) {
            handleRuntimeException(e, uri);
        }
    }

    protected void put(String url, Object[] queryParams, Object request, Object... uriVariables) {
        URI uri = null;
        try {
            uri = getURIBuilder(root + url, queryParams).build(uriVariables);
            Entity<Object> entity;
            if (request != null) {
                entity = Entity.json(request);
            } else {
                entity = Entity.text("");
            }
            Response response = requestHandler.getWebResourceBuilder(uri).put(entity);
            if (response.getStatus() >= 300) {
                throw new UniformInterfaceException(response);
            }
        } catch (RuntimeException e) {
            handleException(e, uri);
        }
    }

    protected void postForEntityWithRequestOnly(String url, Object request) {
        Class<?> type = null;
        postForEntity(url, request, null, type);
    }

    protected void postForEntityWithUriVariablesOnly(String url, Object... uriVariables) {
        Class<?> type = null;
        postForEntity(url, null, null, type, uriVariables);
    }

    protected <T> T postForEntity(
            String url,
            Object request,
            Object[] queryParams,
            Class<T> responseType,
            Object... uriVariables) {
        return postForEntity(
                url,
                request,
                queryParams,
                responseType,
                builder -> builder.post(Entity.json(request), responseType),
                uriVariables);
    }

    protected <T> T postForEntity(
            String url,
            Object request,
            Object[] queryParams,
            GenericType<T> responseType,
            Object... uriVariables) {
        return postForEntity(
                url,
                request,
                queryParams,
                responseType,
                builder -> builder.post(Entity.json(request), responseType),
                uriVariables);
    }

    private <T> T postForEntity(
            String url,
            Object request,
            Object[] queryParams,
            Object responseType,
            Function<Invocation.Builder, T> postWithEntity,
            Object... uriVariables) {
        URI uri = null;
        try {
            uri = getURIBuilder(root + url, queryParams).build(uriVariables);
            Invocation.Builder webResourceBuilder = requestHandler.getWebResourceBuilder(uri);
            if (responseType == null) {
                Response response = webResourceBuilder.post(Entity.json(request));
                if (response.getStatus() >= 300) {
                    throw new UniformInterfaceException(response);
                }
                return null;
            }
            return postWithEntity.apply(webResourceBuilder);
        } catch (UniformInterfaceException e) {
            handleUniformInterfaceException(e, uri);
        } catch (RuntimeException e) {
            handleRuntimeException(e, uri);
        }
        return null;
    }

    protected <T> T getForEntity(
            String url, Object[] queryParams, Class<T> responseType, Object... uriVariables) {
        return getForEntity(
                url, queryParams, response -> response.readEntity(responseType), uriVariables);
    }

    protected <T> T getForEntity(
            String url, Object[] queryParams, GenericType<T> responseType, Object... uriVariables) {
        return getForEntity(
                url, queryParams, response -> response.readEntity(responseType), uriVariables);
    }

    private <T> T getForEntity(
            String url,
            Object[] queryParams,
            Function<Response, T> entityProvider,
            Object... uriVariables) {
        URI uri = null;
        Response response;
        try {
            uri = getURIBuilder(root + url, queryParams).build(uriVariables);
            response = requestHandler.get(uri);
            if (response.getStatus() < 300) {
                return entityProvider.apply(response);
            } else {
                throw new UniformInterfaceException(response);
            }
        } catch (UniformInterfaceException e) {
            handleUniformInterfaceException(e, uri);
        } catch (RuntimeException e) {
            handleRuntimeException(e, uri);
        }
        return null;
    }

    /**
     * Uses the {@link PayloadStorage} for storing large payloads. Gets the uri for storing the
     * payload from the server and then uploads to this location
     *
     * @param payloadType the {@link
     *     com.netflix.conductor.common.utils.ExternalPayloadStorage.PayloadType} to be uploaded
     * @param payloadBytes the byte array containing the payload
     * @param payloadSize the size of the payload
     * @return the path where the payload is stored in external storage
     */
    protected String uploadToExternalPayloadStorage(
            ExternalPayloadStorage.PayloadType payloadType, byte[] payloadBytes, long payloadSize) {
        Validate.isTrue(
                payloadType.equals(ExternalPayloadStorage.PayloadType.WORKFLOW_INPUT)
                        || payloadType.equals(ExternalPayloadStorage.PayloadType.TASK_OUTPUT),
                "Payload type must be workflow input or task output");
        ExternalStorageLocation externalStorageLocation =
                payloadStorage.getLocation(ExternalPayloadStorage.Operation.WRITE, payloadType, "");
        payloadStorage.upload(
                externalStorageLocation.getUri(),
                new ByteArrayInputStream(payloadBytes),
                payloadSize);
        return externalStorageLocation.getPath();
    }

    /**
     * Uses the {@link PayloadStorage} for downloading large payloads to be used by the client. Gets
     * the uri of the payload fom the server and then downloads from this location.
     *
     * @param payloadType the {@link
     *     com.netflix.conductor.common.utils.ExternalPayloadStorage.PayloadType} to be downloaded
     * @param path the relative of the payload in external storage
     * @return the payload object that is stored in external storage
     */
    @SuppressWarnings("unchecked")
    protected Map<String, Object> downloadFromExternalStorage(
            ExternalPayloadStorage.PayloadType payloadType, String path) {
        Validate.notBlank(path, "uri cannot be blank");
        ExternalStorageLocation externalStorageLocation =
                payloadStorage.getLocation(
                        ExternalPayloadStorage.Operation.READ, payloadType, path);
        try (InputStream inputStream = payloadStorage.download(externalStorageLocation.getUri())) {
            return objectMapper.readValue(inputStream, Map.class);
        } catch (IOException e) {
            String errorMsg =
                    String.format(
                            "Unable to download payload from external storage location: %s", path);
            LOGGER.error(errorMsg, e);
            throw new ConductorClientException(errorMsg, e);
        }
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

    protected boolean isNewerJacksonVersion() {
        Version version = com.fasterxml.jackson.databind.cfg.PackageVersion.VERSION;
        return version.getMajorVersion() == 2 && version.getMinorVersion() >= 12;
    }

    private void handleRuntimeException(RuntimeException exception, URI uri) {
        String errorMessage =
                String.format(
                        "Unable to invoke Conductor API with uri: %s, runtime exception occurred",
                        uri);
        LOGGER.error(errorMessage, exception);
        throw new ConductorClientException(errorMessage, exception);
    }

    private void handleUniformInterfaceException(UniformInterfaceException exception, URI uri) {
        Response response = exception.getResponse();
        try (response) {
            if (response == null) {
                throw new ConductorClientException(
                        String.format("Unable to invoke Conductor API with uri: %s", uri));
            }
            if (response.getStatus() < 300) {
                return;
            }
            String errorMessage = response.readEntity(String.class);
            LOGGER.warn(
                    "Unable to invoke Conductor API with uri: {}, unexpected response from server: statusCode={}, responseBody='{}'.",
                    uri,
                    response.getStatus(),
                    errorMessage);
            ErrorResponse errorResponse;
            try {
                errorResponse = objectMapper.readValue(errorMessage, ErrorResponse.class);
            } catch (IOException e) {
                throw new ConductorClientException(response.getStatus(), errorMessage);
            }
            throw new ConductorClientException(response.getStatus(), errorResponse);
        } catch (ConductorClientException e) {
            throw e;
        } catch (RuntimeException e) {
            handleRuntimeException(e, uri);
        }
    }

    private void handleException(RuntimeException e, URI uri) {
        if (e instanceof UniformInterfaceException) {
            handleUniformInterfaceException(((UniformInterfaceException) e), uri);
        } else {
            handleRuntimeException(e, uri);
        }
    }

    @Getter
    static class UniformInterfaceException extends RuntimeException {
        private final Response response;

        public UniformInterfaceException(Response response) {
            this.response = response;
        }
    }
}
