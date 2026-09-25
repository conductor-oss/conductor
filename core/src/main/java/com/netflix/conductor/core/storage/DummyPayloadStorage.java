/*
 * Copyright 2022 Conductor Authors.
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
import java.nio.file.Path;
import java.nio.file.Paths;
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

    /**
     * Validates and resolves a file path to prevent directory traversal attacks.
     *
     * @param path the user-provided path
     * @return a validated File object
     * @throws SecurityException if the path attempts directory traversal
     */
    private File validateAndResolvePath(String path) throws IOException {
        // Normalize the path to remove any ".." or "." components
        Path normalized = Paths.get(path).normalize();

        // Check if the normalized path contains ".." which would indicate traversal attempt
        if (normalized.toString().contains("..")) {
            throw new SecurityException("Path traversal not allowed: " + path);
        }

        // Create the file object
        File file = new File(payloadDir, normalized.toString());

        // Verify the canonical path is still within payloadDir
        String canonicalPath = file.getCanonicalPath();
        String canonicalBaseDir = payloadDir.getCanonicalPath();

        if (!canonicalPath.startsWith(canonicalBaseDir + File.separator)
                && !canonicalPath.equals(canonicalBaseDir)) {
            throw new SecurityException("Access denied - path outside allowed directory: " + path);
        }

        return file;
    }

    /** Visible for testing: the temp directory that all payloads are confined to. */
    File getPayloadDir() {
        return payloadDir;
    }

    @Override
    public void upload(String path, InputStream payload, long payloadSize) {
        try {
            File file = validateAndResolvePath(path);
            String filePath = file.getAbsolutePath();
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                file.createNewFile();
                LOGGER.debug("Created file: {}", filePath);
            }
            IOUtils.copy(payload, new FileOutputStream(file));
            LOGGER.debug("Written to {}", filePath);
        } catch (SecurityException | IOException e) {
            // just handle this exception here so that the test will fail in case it is thrown
            LOGGER.error("Error writing payload for path: {}", path, e);
        } finally {
            // Always close the payload stream, including when validation rejects the path.
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
            File file = validateAndResolvePath(path);
            LOGGER.debug("Reading from {}", path);
            return new FileInputStream(file);
        } catch (SecurityException | IOException e) {
            LOGGER.error("Error reading {}", path, e);
            return null;
        }
    }
}
