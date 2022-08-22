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
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.client.exception.ConductorClientException;
import com.netflix.conductor.common.run.ExternalStorageLocation;
import com.netflix.conductor.common.utils.ExternalPayloadStorage;

import com.amazonaws.util.IOUtils;

/** An implementation of {@link ExternalPayloadStorage} for storing large JSON payload data. */
class PayloadStorage implements ExternalPayloadStorage {

    private static final Logger LOGGER = LoggerFactory.getLogger(PayloadStorage.class);

    private final ClientBase clientBase;

    PayloadStorage(ClientBase clientBase) {
        this.clientBase = clientBase;
    }

    /**
     * This method is not intended to be used in the client. The client makes a request to the
     * server to get the {@link ExternalStorageLocation}
     */
    @Override
    public ExternalStorageLocation getLocation(
            Operation operation, PayloadType payloadType, String path) {
        String uri;
        switch (payloadType) {
            case WORKFLOW_INPUT:
            case WORKFLOW_OUTPUT:
                uri = "workflow";
                break;
            case TASK_INPUT:
            case TASK_OUTPUT:
                uri = "tasks";
                break;
            default:
                throw new ConductorClientException(
                        String.format(
                                "Invalid payload type: %s for operation: %s",
                                payloadType.toString(), operation.toString()));
        }
        return clientBase.getForEntity(
                String.format("%s/externalstoragelocation", uri),
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
     * @throws ConductorClientException if the upload fails due to an invalid path or an error from
     *     external storage
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
