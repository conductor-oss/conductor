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
package com.netflix.conductor.common.utils;

import java.io.InputStream;

import com.netflix.conductor.common.run.ExternalStorageLocation;

/**
 * Interface used to externalize the storage of large JSON payloads in workflow and task
 * input/output
 */
public interface ExternalPayloadStorage {

    enum Operation {
        READ,
        WRITE
    }

    enum PayloadType {
        WORKFLOW_INPUT,
        WORKFLOW_OUTPUT,
        TASK_INPUT,
        TASK_OUTPUT
    }

    /**
     * Obtain a uri used to store/access a json payload in external storage.
     *
     * @param operation the type of {@link Operation} to be performed with the uri
     * @param payloadType the {@link PayloadType} that is being accessed at the uri
     * @param path (optional) the relative path for which the external storage location object is to
     *     be populated. If path is not specified, it will be computed and populated.
     * @return a {@link ExternalStorageLocation} object which contains the uri and the path for the
     *     json payload
     */
    ExternalStorageLocation getLocation(Operation operation, PayloadType payloadType, String path);

    /**
     * Obtain an uri used to store/access a json payload in external storage with deduplication of
     * data based on payloadBytes digest.
     *
     * @param operation the type of {@link Operation} to be performed with the uri
     * @param payloadType the {@link PayloadType} that is being accessed at the uri
     * @param path (optional) the relative path for which the external storage location object is to
     *     be populated. If path is not specified, it will be computed and populated.
     * @param payloadBytes for calculating digest which is used for objectKey
     * @return a {@link ExternalStorageLocation} object which contains the uri and the path for the
     *     json payload
     */
    default ExternalStorageLocation getLocation(
            Operation operation, PayloadType payloadType, String path, byte[] payloadBytes) {
        return getLocation(operation, payloadType, path);
    }

    /**
     * Upload a json payload to the specified external storage location.
     *
     * @param path the location to which the object is to be uploaded
     * @param payload an {@link InputStream} containing the json payload which is to be uploaded
     * @param payloadSize the size of the json payload in bytes
     */
    void upload(String path, InputStream payload, long payloadSize);

    /**
     * Download the json payload from the specified external storage location.
     *
     * @param path the location from where the object is to be downloaded
     * @return an {@link InputStream} of the json payload at the specified location
     */
    InputStream download(String path);
}
