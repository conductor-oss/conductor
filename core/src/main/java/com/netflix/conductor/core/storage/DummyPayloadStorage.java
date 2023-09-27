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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.UUID;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.common.run.ExternalStorageLocation;
import com.netflix.conductor.common.utils.ExternalPayloadStorage;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A dummy implementation of {@link ExternalPayloadStorage} used when no external payload is
 * configured
 */
public class DummyPayloadStorage implements ExternalPayloadStorage {

    private static final Logger LOGGER = LoggerFactory.getLogger(DummyPayloadStorage.class);

    private ObjectMapper objectMapper;
    private File payloadDir;

    public DummyPayloadStorage() {
        try {
            this.objectMapper = new ObjectMapper();
            this.payloadDir = Files.createTempDirectory("payloads").toFile();
            LOGGER.info(
                    "{} initialized in directory: {}",
                    this.getClass().getSimpleName(),
                    payloadDir.getAbsolutePath());
        } catch (IOException ioException) {
            LOGGER.error(
                    "Exception encountered while creating payloads directory : {}",
                    ioException.getMessage());
        }
    }

    @Override
    public ExternalStorageLocation getLocation(
            Operation operation, PayloadType payloadType, String path) {
        ExternalStorageLocation location = new ExternalStorageLocation();
        location.setPath(path + UUID.randomUUID() + ".json");
        return location;
    }

    @Override
    public void upload(String path, InputStream payload, long payloadSize) {
        File file = new File(payloadDir, path);
        String filePath = file.getAbsolutePath();
        try {
            if (!file.exists() && file.createNewFile()) {
                LOGGER.debug("Created file: {}", filePath);
            }
            IOUtils.copy(payload, new FileOutputStream(file));
            LOGGER.debug("Written to {}", filePath);
        } catch (IOException e) {
            // just handle this exception here and return empty map so that test will fail in case
            // this exception is thrown
            LOGGER.error("Error writing to {}", filePath);
        } finally {
            try {
                if (payload != null) {
                    payload.close();
                }
            } catch (IOException e) {
                LOGGER.warn("Unable to close input stream when writing to file");
            }
        }
    }

    @Override
    public InputStream download(String path) {
        try {
            LOGGER.debug("Reading from {}", path);
            return new FileInputStream(new File(payloadDir, path));
        } catch (IOException e) {
            LOGGER.error("Error reading {}", path, e);
            return null;
        }
    }
}
