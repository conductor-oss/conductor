/*
 * Copyright 2024 Conductor Authors.
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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import org.jetbrains.annotations.NotNull;

import com.netflix.conductor.client.exception.ConductorClientException;
import com.netflix.conductor.common.run.ExternalStorageLocation;
import com.netflix.conductor.common.utils.ExternalPayloadStorage;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;

/** An implementation of {@link ExternalPayloadStorage} for storing large JSON payload data. */
@Slf4j
class PayloadStorage implements ExternalPayloadStorage {

    private static final int BUFFER_SIZE = 1024 * 32;

    private final ConductorClient client;

    PayloadStorage(ConductorClient client) {
        this.client = client;
    }

    @Override
    public ExternalStorageLocation getLocation(Operation operation, PayloadType payloadType, String path) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(ConductorClientRequest.Method.GET)
                .path("/{resource}/externalstoragelocation")
                .addPathParam("resource", getResource(operation, payloadType))
                .addQueryParam("path", path)
                .addQueryParam("operation", operation.toString())
                .addQueryParam("payloadType", payloadType.toString())
                .build();

        ConductorClientResponse<ExternalStorageLocation> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }

    @NotNull
    private String getResource(Operation operation, PayloadType payloadType) {
        switch (payloadType) {
            case WORKFLOW_INPUT:
            case WORKFLOW_OUTPUT:
                return "workflow";
            case TASK_INPUT:
            case TASK_OUTPUT:
                return "tasks";
        }

        throw new ConductorClientException(String.format("Invalid payload type: %s for operation: %s",
                payloadType, operation));
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

            try (BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(connection.getOutputStream())) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                long totalBytes = 0;
                while ((bytesRead = payload.read(buffer)) != -1) {
                    bufferedOutputStream.write(buffer, 0, bytesRead);
                    totalBytes += bytesRead;
                }
                bufferedOutputStream.flush();

                int responseCode = connection.getResponseCode();
                if (!isSuccessful(responseCode)) {
                    String errorMsg = String.format("Unable to upload. Response code: %d", responseCode);
                    log.error(errorMsg);
                    throw new ConductorClientException(errorMsg);
                }
                log.debug("Uploaded {} bytes to uri: {}, with HTTP response code: {}", totalBytes, uri, responseCode);
            }
        } catch (URISyntaxException | MalformedURLException e) {
            String errorMsg = String.format("Invalid path specified: %s", uri);
            log.error(errorMsg, e);
            throw new ConductorClientException(e);
        } catch (IOException e) {
            String errorMsg = String.format("Error uploading to path: %s", uri);
            log.error(errorMsg, e);
            throw new ConductorClientException(e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            try {
                if (payload != null) {
                    payload.close();
                }
            } catch (IOException e) {
                log.warn("Unable to close input stream when uploading to uri: {}", uri);
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
        try {
            URL url = new URI(uri).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoOutput(false);

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                log.debug("Download completed with HTTP response code: {}", connection.getResponseCode());
                return new BufferedInputStream(connection.getInputStream());
            }
            String errorMsg = String.format("Unable to download. Response code: %d", responseCode);
            log.error(errorMsg);
            throw new ConductorClientException(errorMsg);
        } catch (URISyntaxException | MalformedURLException e) {
            String errorMsg = String.format("Invalid uri specified: %s", uri);
            log.error(errorMsg, e);
            throw new ConductorClientException(e);
        } catch (IOException e) {
            String errorMsg = String.format("Error downloading from uri: %s", uri);
            log.error(errorMsg, e);
            throw new ConductorClientException(e);
        }
    }

    private boolean isSuccessful(int responseCode) {
        return responseCode >= 200 && responseCode < 300;
    }
}
