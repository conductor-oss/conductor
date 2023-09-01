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
package com.netflix.conductor.core.storage;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.netflix.conductor.common.run.ExternalStorageLocation;
import com.netflix.conductor.common.utils.ExternalPayloadStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A dummy implementation of {@link ExternalPayloadStorage} used when no external payload is
 * configured
 */
public class DummyPayloadStorage implements ExternalPayloadStorage {

    private static final Logger LOGGER = LoggerFactory.getLogger(DummyPayloadStorage.class);

    private final Map<String, byte[]> dummyDataStore = new ConcurrentHashMap<>();

    private static final String DUMMY_DATA_STORE_KEY = "DUMMY_PAYLOAD_STORE_KEY";

    @Override
    public ExternalStorageLocation getLocation(
            Operation operation, PayloadType payloadType, String path) {
        ExternalStorageLocation externalStorageLocation = new ExternalStorageLocation();
        externalStorageLocation.setPath(path != null ? path : "");
        return externalStorageLocation;
    }

    @Override
    public void upload(String path, InputStream payload, long payloadSize) {
        try {
            final byte[] payloadBytes = new byte[(int) payloadSize];
            final int bytesRead = payload.read(new byte[(int) payloadSize]);

            if (bytesRead > 0) {
                dummyDataStore.put(path == null || path.isEmpty() ? DUMMY_DATA_STORE_KEY : path, payloadBytes);
            }
        } catch (Exception e) {
            LOGGER.error("Error encountered while uploading payload {}", e.getMessage());
        }
    }

    @Override
    public InputStream download(String path) {
        final byte[] data = dummyDataStore.get(path == null || path.isEmpty() ? DUMMY_DATA_STORE_KEY : path);
        if (data != null) {
            return new ByteArrayInputStream(data);
        } else {
            return null;
        }
    }
}
