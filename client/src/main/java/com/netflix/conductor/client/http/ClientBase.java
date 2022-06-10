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

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.client.config.ConductorClientConfiguration;
import com.netflix.conductor.client.config.DefaultConductorClientConfiguration;
import com.netflix.conductor.client.exception.ConductorClientException;
import com.netflix.conductor.client.exception.RequestHandlerException;
import com.netflix.conductor.client.http.jersey.JerseyRequestHandler;
import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.run.ExternalStorageLocation;
import com.netflix.conductor.common.utils.ExternalPayloadStorage;
import com.netflix.conductor.common.validation.ErrorResponse;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/** Abstract client for the REST server */
public abstract class ClientBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientBase.class);

    private final RequestHandler requestHandler;

    private String root = "";

    protected final ObjectMapper objectMapper;

    private final PayloadStorage payloadStorage;

    protected final ConductorClientConfiguration conductorClientConfiguration;

    protected ClientBase(
            RequestHandler requestHandler, ConductorClientConfiguration clientConfiguration) {
        this.objectMapper = new ObjectMapperProvider().getObjectMapper();

        // https://github.com/FasterXML/jackson-databind/issues/2683
        if (isNewerJacksonVersion()) {
            objectMapper.registerModule(new JavaTimeModule());
        }

        this.requestHandler = ObjectUtils.defaultIfNull(requestHandler, new JerseyRequestHandler());
        this.conductorClientConfiguration =
                ObjectUtils.defaultIfNull(
                        clientConfiguration, new DefaultConductorClientConfiguration());
        this.payloadStorage = new PayloadStorage();
    }

    public void setRootURI(String root) {
        this.root = root;
    }

    protected void delete(String url, Object... uriVariables) {
        deleteWithUriVariables(url, null, uriVariables);
    }

    protected void deleteWithUriVariables(
            String url, Object[] queryParams, Object... uriVariables) {
        URI uri = getURIBuilder(getFullUrl(url), queryParams).build(uriVariables);
        try {
            requestHandler.delete(uri);
        } catch (RequestHandlerException rhe) {
            throw createClientException(rhe);
        }
    }

    protected void put(String url, Object[] queryParams, Object request, Object... uriVariables) {
        URI uri = getURIBuilder(getFullUrl(url), queryParams).build(uriVariables);
        try {
            requestHandler.put(uri, request);
        } catch (RequestHandlerException rhe) {
            throw createClientException(rhe);
        }
    }

    protected void post(String url, Object request) {
        postForEntity(url, request, null, null);
    }

    protected void postWithUriVariables(String url, Object... uriVariables) {
        postForEntity(url, null, null, null, uriVariables);
    }

    protected <T> T postForEntity(
            String url,
            Object request,
            Object[] queryParams,
            Class<T> responseType,
            Object... uriVariables) {
        URI uri = getURIBuilder(getFullUrl(url), queryParams).build(uriVariables);

        try {
            InputStream response = requestHandler.post(uri, request);
            if (responseType == null) {
                return null;
            }
            return convertToType(response, responseType);
        } catch (RequestHandlerException rhe) {
            throw createClientException(rhe);
        }
    }

    protected String postForString(
            String url, Object request, Object[] queryParams, Object... uriVariables) {
        URI uri = getURIBuilder(getFullUrl(url), queryParams).build(uriVariables);
        try {
            InputStream response = requestHandler.post(uri, request);
            return convertToString(response);
        } catch (RequestHandlerException rhe) {
            throw createClientException(rhe);
        }
    }

    protected <T> T getForEntity(
            String url, Object[] queryParams, Class<T> responseType, Object... uriVariables) {
        InputStream response = getForEntity(url, queryParams, uriVariables);
        return convertToType(response, responseType);
    }

    protected <T> T getForEntity(
            String url,
            Object[] queryParams,
            TypeReference<T> responseType,
            Object... uriVariables) {
        InputStream response = getForEntity(url, queryParams, uriVariables);
        return convertToType(response, responseType);
    }

    /**
     * Uses the {@link PayloadStorage} for storing large payloads. Gets the uri for storing the
     * payload from the server and then uploads to this location.
     *
     * @param payloadType the {@link
     *     com.netflix.conductor.common.utils.ExternalPayloadStorage.PayloadType} to be uploaded.
     * @param payloadBytes the byte array containing the payload.
     * @param payloadSize the size of the payload.
     * @return the path where the payload is stored in external storage.
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

    private String getFullUrl(String url) {
        return root + url;
    }

    private UriBuilder getURIBuilder(String path, Object[] queryParams) {
        path = StringUtils.trimToEmpty(path);

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

    private InputStream getForEntity(String url, Object[] queryParams, Object... uriVariables) {
        URI uri = getURIBuilder(getFullUrl(url), queryParams).build(uriVariables);
        try {
            return requestHandler.get(uri);
        } catch (RequestHandlerException rhe) {
            throw createClientException(rhe);
        }
    }

    private ConductorClientException createClientException(RequestHandlerException rhe) {
        if (rhe.hasResponse()) {
            ErrorResponse errorResponse = convertToType(rhe.getResponse(), ErrorResponse.class);
            if (errorResponse != null) {
                return new ConductorClientException(rhe.getStatus(), errorResponse);
            }
        }

        return new ConductorClientException(rhe.getMessage(), rhe.getCause());
    }

    private String convertToString(InputStream inputStream) {
        try {
            return IOUtils.toString(inputStream, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ConductorClientException("Error converting response to String", e);
        } finally {
            try {
                inputStream.close();
            } catch (IOException e) {
                LOGGER.error("Error closing input stream", e);
            }
        }
    }

    private <T> T convertToType(InputStream inputStream, Class<T> responseType) {
        try {
            String value = convertToString(inputStream);
            return StringUtils.isNotBlank(value)
                    ? objectMapper.readValue(value, responseType)
                    : null;
        } catch (IOException e) {
            throw new ConductorClientException("Error converting response to " + responseType, e);
        }
    }

    private <T> T convertToType(InputStream inputStream, TypeReference<T> responseType) {
        try {
            String value = convertToString(inputStream);
            return StringUtils.isNotBlank(value)
                    ? objectMapper.readValue(value, responseType)
                    : null;
        } catch (IOException e) {
            throw new ConductorClientException("Error converting response to " + responseType, e);
        }
    }

    /** An implementation of {@link ExternalPayloadStorage} for storing large JSON payload data. */
    class PayloadStorage implements ExternalPayloadStorage {

        /**
         * This method is not intended to be used in the client. The client makes a request to the
         * server to get the {@link ExternalStorageLocation}
         */
        @Override
        public ExternalStorageLocation getLocation(
                Operation operation, PayloadType payloadType, String path) {
            String url;
            switch (payloadType) {
                case WORKFLOW_INPUT:
                case WORKFLOW_OUTPUT:
                    url = "workflow";
                    break;
                case TASK_INPUT:
                case TASK_OUTPUT:
                    url = "tasks";
                    break;
                default:
                    throw new ConductorClientException(
                            String.format(
                                    "Invalid payload type: %s for operation: %s",
                                    payloadType, operation.toString()));
            }
            return getForEntity(
                    url + "/externalstoragelocation",
                    new Object[] {
                        "path",
                        path,
                        "operation",
                        operation.toString(),
                        "payloadType",
                        payloadType.toString()
                    },
                    ExternalStorageLocation.class);
        }

        /**
         * Uploads the payload to the uri specified.
         *
         * @param uri the location to which the object is to be uploaded
         * @param payload an {@link InputStream} containing the json payload which is to be uploaded
         * @param payloadSize the size of the json payload in bytes
         * @throws ConductorClientException if the upload fails due to an invalid path or an error
         *     from external storage
         */
        @Override
        public void upload(String uri, InputStream payload, long payloadSize) {
            HttpURLConnection connection = null;
            try {
                URL url = new URI(uri).toURL();

                connection = (HttpURLConnection) url.openConnection();
                connection.setDoOutput(true);
                connection.setRequestMethod("PUT");

                try (BufferedOutputStream bufferedOutputStream =
                        new BufferedOutputStream(connection.getOutputStream())) {
                    long count = IOUtils.copy(payload, bufferedOutputStream);
                    bufferedOutputStream.flush();
                    // Check the HTTP response code
                    int responseCode = connection.getResponseCode();
                    if (Response.Status.fromStatusCode(responseCode).getFamily()
                            != Response.Status.Family.SUCCESSFUL) {
                        String errorMsg =
                                String.format("Unable to upload. Response code: %d", responseCode);
                        LOGGER.error(errorMsg);
                        throw new ConductorClientException(errorMsg);
                    }
                    LOGGER.debug(
                            "Uploaded {} bytes to uri: {}, with HTTP response code: {}",
                            count,
                            uri,
                            responseCode);
                }
            } catch (URISyntaxException | MalformedURLException e) {
                String errorMsg = String.format("Invalid path specified: %s", uri);
                LOGGER.error(errorMsg, e);
                throw new ConductorClientException(errorMsg, e);
            } catch (IOException e) {
                String errorMsg = String.format("Error uploading to path: %s", uri);
                LOGGER.error(errorMsg, e);
                throw new ConductorClientException(errorMsg, e);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
                try {
                    if (payload != null) {
                        payload.close();
                    }
                } catch (IOException e) {
                    LOGGER.warn("Unable to close inputstream when uploading to uri: {}", uri);
                }
            }
        }

        /**
         * Downloads the payload from the given uri.
         *
         * @param uri the location from where the object is to be downloaded
         * @return an inputstream of the payload in the external storage
         * @throws ConductorClientException if the download fails due to an invalid path or an error
         *     from external storage
         */
        @Override
        public InputStream download(String uri) {
            HttpURLConnection connection = null;
            String errorMsg;
            try {
                URL url = new URI(uri).toURL();
                connection = (HttpURLConnection) url.openConnection();
                connection.setDoOutput(false);

                // Check the HTTP response code
                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    LOGGER.debug(
                            "Download completed with HTTP response code: {}",
                            connection.getResponseCode());
                    return org.apache.commons.io.IOUtils.toBufferedInputStream(
                            connection.getInputStream());
                }
                errorMsg = String.format("Unable to download. Response code: %d", responseCode);
                LOGGER.error(errorMsg);
                throw new ConductorClientException(errorMsg);
            } catch (URISyntaxException | MalformedURLException e) {
                errorMsg = String.format("Invalid uri specified: %s", uri);
                LOGGER.error(errorMsg, e);
                throw new ConductorClientException(errorMsg, e);
            } catch (IOException e) {
                errorMsg = String.format("Error downloading from uri: %s", uri);
                LOGGER.error(errorMsg, e);
                throw new ConductorClientException(errorMsg, e);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
    }
}
