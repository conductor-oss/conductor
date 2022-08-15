/*
 * Copyright 2020 Netflix, Inc.
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

import javax.ws.rs.core.UriBuilder;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.client.config.ConductorClientConfiguration;
import com.netflix.conductor.client.config.DefaultConductorClientConfiguration;
import com.netflix.conductor.client.exception.ConductorClientException;
import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.model.BulkResponse;
import com.netflix.conductor.common.run.ExternalStorageLocation;
import com.netflix.conductor.common.utils.ExternalPayloadStorage;
import com.netflix.conductor.common.validation.ErrorResponse;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource.Builder;

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
        delete(queryParams, url, uriVariables, null);
    }

    protected BulkResponse deleteWithRequestBody(Object[] queryParams, String url, Object body) {
        return delete(queryParams, url, null, body);
    }

    private BulkResponse delete(
            Object[] queryParams, String url, Object[] uriVariables, Object body) {
        URI uri = null;
        BulkResponse response = null;
        try {
            uri = getURIBuilder(root + url, queryParams).build(uriVariables);
            response = requestHandler.delete(uri, body);
        } catch (UniformInterfaceException e) {
            handleUniformInterfaceException(e, uri);
        } catch (RuntimeException e) {
            handleRuntimeException(e, uri);
        }
        return response;
    }

    protected void put(String url, Object[] queryParams, Object request, Object... uriVariables) {
        URI uri = null;
        try {
            uri = getURIBuilder(root + url, queryParams).build(uriVariables);
            requestHandler.getWebResourceBuilder(uri, request).put();
        } catch (RuntimeException e) {
            handleException(uri, e);
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
                builder -> builder.post(responseType),
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
                builder -> builder.post(responseType),
                uriVariables);
    }

    private <T> T postForEntity(
            String url,
            Object request,
            Object[] queryParams,
            Object responseType,
            Function<Builder, T> postWithEntity,
            Object... uriVariables) {
        URI uri = null;
        try {
            uri = getURIBuilder(root + url, queryParams).build(uriVariables);
            Builder webResourceBuilder = requestHandler.getWebResourceBuilder(uri, request);
            if (responseType == null) {
                webResourceBuilder.post();
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
                url, queryParams, response -> response.getEntity(responseType), uriVariables);
    }

    protected <T> T getForEntity(
            String url, Object[] queryParams, GenericType<T> responseType, Object... uriVariables) {
        return getForEntity(
                url, queryParams, response -> response.getEntity(responseType), uriVariables);
    }

    private <T> T getForEntity(
            String url,
            Object[] queryParams,
            Function<ClientResponse, T> entityProvider,
            Object... uriVariables) {
        URI uri = null;
        ClientResponse clientResponse;
        try {
            uri = getURIBuilder(root + url, queryParams).build(uriVariables);
            clientResponse = requestHandler.get(uri);
            if (clientResponse.getStatus() < 300) {
                return entityProvider.apply(clientResponse);
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

    private void handleClientHandlerException(ClientHandlerException exception, URI uri) {
        String errorMessage =
                String.format(
                        "Unable to invoke Conductor API with uri: %s, failure to process request or response",
                        uri);
        LOGGER.error(errorMessage, exception);
        throw new ConductorClientException(errorMessage, exception);
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
        ClientResponse clientResponse = exception.getResponse();
        if (clientResponse == null) {
            throw new ConductorClientException(
                    String.format("Unable to invoke Conductor API with uri: %s", uri));
        }
        try {
            if (clientResponse.getStatus() < 300) {
                return;
            }
            String errorMessage = clientResponse.getEntity(String.class);
            LOGGER.warn(
                    "Unable to invoke Conductor API with uri: {}, unexpected response from server: statusCode={}, responseBody='{}'.",
                    uri,
                    clientResponse.getStatus(),
                    errorMessage);
            ErrorResponse errorResponse;
            try {
                errorResponse = objectMapper.readValue(errorMessage, ErrorResponse.class);
            } catch (IOException e) {
                throw new ConductorClientException(clientResponse.getStatus(), errorMessage);
            }
            throw new ConductorClientException(clientResponse.getStatus(), errorResponse);
        } catch (ConductorClientException e) {
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

    /**
     * Converts ClientResponse object to string with detailed debug information including status
     * code, media type, response headers, and response body if exists.
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
                // Ignore if there is no response body, or IO error - it may have already been read
                // in certain scenario.
            }
        }
        builder.append(", response headers: ").append(response.getHeaders());
        builder.append("]");
        return builder.toString();
    }
}
