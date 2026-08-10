/*
 * Copyright 2026 Conductor Authors.
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
package org.conductoross.conductor.local.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.conductoross.conductor.core.storage.FileStorage;
import org.conductoross.conductor.core.storage.StorageFileInfo;
import org.conductoross.conductor.local.config.LocalFileStorageProperties;
import org.conductoross.conductor.model.file.StorageType;

import com.netflix.conductor.core.exception.NonTransientException;

/**
 * {@link FileStorage} backed by the server-local filesystem (zero-infra default). Multipart is a
 * no-op — the SDK writes directly via {@code LocalFileStorageBackend}.
 */
public class LocalFileStorage implements FileStorage {

    private final Path baseDirectory;

    public LocalFileStorage(LocalFileStorageProperties properties) {
        this.baseDirectory = Path.of(properties.getDirectory());
        try {
            Files.createDirectories(baseDirectory);
        } catch (IOException e) {
            throw new NonTransientException(
                    "Failed to create local file storage directory: " + baseDirectory, e);
        }
    }

    @Override
    public StorageType getStorageType() {
        return StorageType.LOCAL;
    }

    @Override
    public String generateUploadUrl(String storagePath, Duration expiration) {
        return resolveUri(storagePath);
    }

    @Override
    public String generateDownloadUrl(String storagePath, Duration expiration) {
        return resolveUri(storagePath);
    }

    @Override
    public StorageFileInfo getStorageFileInfo(String storagePath) {
        Path path = baseDirectory.resolve(storagePath);
        if (!Files.exists(path)) {
            return null;
        }
        StorageFileInfo info = new StorageFileInfo();
        info.setExists(true);
        info.setContentHash(null);
        try {
            info.setContentSize(Files.size(path));
        } catch (IOException e) {
            throw new NonTransientException("Failed to get file size: " + path, e);
        }
        return info;
    }

    @Override
    public String initiateMultipartUpload(String storagePath) {
        return "local-noop";
    }

    @Override
    public String generatePartUploadUrl(
            String storagePath, String uploadId, int partNumber, Duration expiration) {
        return resolveUri(storagePath);
    }

    @Override
    public void completeMultipartUpload(
            String storagePath, String uploadId, List<String> partETags) {
        // no-op for local storage
    }

    private String resolveUri(String storagePath) {
        return baseDirectory.resolve(storagePath).toAbsolutePath().toUri().toString();
    }
}
