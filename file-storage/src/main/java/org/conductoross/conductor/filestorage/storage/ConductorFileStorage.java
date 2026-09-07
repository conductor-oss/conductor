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
package org.conductoross.conductor.filestorage.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.conductoross.conductor.core.exception.FileStorageException;
import org.conductoross.conductor.core.storage.ConductorFileStorageProperties;
import org.conductoross.conductor.core.storage.FileStorage;
import org.conductoross.conductor.core.storage.FileStorageUrlSigner;
import org.conductoross.conductor.core.storage.FileStorageUrlSigner.Operation;
import org.conductoross.conductor.core.storage.FileStorageUrlSigner.SignedUrl;
import org.conductoross.conductor.core.storage.StorageFileInfo;
import org.conductoross.conductor.model.file.StorageType;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import com.netflix.conductor.core.exception.NonTransientException;

/**
 * {@link FileStorage} backed by Conductor's filesystem and transfer-content endpoints. Files are
 * committed atomically so readers never observe an interrupted upload.
 */
public class ConductorFileStorage implements FileStorage {

    private static final String CONTENT_PATH = "/api/files/content/";
    private static final int BUFFER_SIZE = 8192;

    private final Path baseDirectory;
    private final ConductorFileStorageProperties properties;
    private final FileStorageUrlSigner urlSigner;

    public ConductorFileStorage(ConductorFileStorageProperties properties) {
        this.properties = properties;
        this.urlSigner = new FileStorageUrlSigner(properties.getSigning());
        this.baseDirectory = properties.getDirectory().toAbsolutePath().normalize();
        try {
            Files.createDirectories(baseDirectory);
        } catch (IOException e) {
            throw new NonTransientException(
                    "Failed to create Conductor file storage directory: " + baseDirectory, e);
        }
    }

    @Override
    public StorageType getStorageType() {
        return StorageType.CONDUCTOR;
    }

    @Override
    public String generateUploadUrl(String storagePath, Duration expiration) {
        return contentUrl(storagePath, expiration, Operation.UPLOAD);
    }

    @Override
    public String generateDownloadUrl(String storagePath, Duration expiration) {
        return contentUrl(storagePath, expiration, Operation.DOWNLOAD);
    }

    @Override
    public StorageFileInfo getStorageFileInfo(String storagePath) {
        Path path = resolveStoragePath(storagePath);
        if (!Files.isRegularFile(path)) {
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
    public void writeContent(String storagePath, InputStream inputStream) {
        Path target = resolveStoragePath(storagePath);
        Path temporaryFile = null;
        try {
            Files.createDirectories(target.getParent());
            temporaryFile =
                    Files.createTempFile(
                            target.getParent(), target.getFileName().toString() + ".", ".part");

            long copied = 0;
            byte[] buffer = new byte[BUFFER_SIZE];
            try (OutputStream outputStream = Files.newOutputStream(temporaryFile)) {
                for (int read; (read = inputStream.read(buffer)) != -1; ) {
                    copied += read;
                    if (copied > properties.getMaxSize()) {
                        throw new FileStorageException(
                                "Upload exceeds the configured maximum size of "
                                        + properties.getMaxSize()
                                        + " bytes");
                    }
                    outputStream.write(buffer, 0, read);
                }
            }
            moveIntoPlace(temporaryFile, target);
            temporaryFile = null;
        } catch (IOException e) {
            throw new NonTransientException("Failed to write file storage content: " + target, e);
        } finally {
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (IOException ignored) {
                    // The primary write failure is more useful than a cleanup failure.
                }
            }
        }
    }

    @Override
    public InputStream readContent(String storagePath) {
        Path path = resolveStoragePath(storagePath);
        try {
            return Files.newInputStream(path);
        } catch (IOException e) {
            throw new NonTransientException("Failed to read file storage content: " + path, e);
        }
    }

    @Override
    public String initiateMultipartUpload(String storagePath) {
        throw new UnsupportedOperationException(
                "Conductor filesystem storage does not support multipart uploads");
    }

    @Override
    public String generatePartUploadUrl(
            String storagePath, String uploadId, int partNumber, Duration expiration) {
        throw new UnsupportedOperationException(
                "Conductor filesystem storage does not support multipart uploads");
    }

    @Override
    public void completeMultipartUpload(
            String storagePath, String uploadId, List<String> partETags) {
        throw new UnsupportedOperationException(
                "Conductor filesystem storage does not support multipart uploads");
    }

    private String contentUrl(String storagePath, Duration expiration, Operation operation) {
        Path relativePath = validatedRelativePath(storagePath);
        if (relativePath.getNameCount() != 3
                || !"conductor".equals(relativePath.getName(0).toString())) {
            throw new NonTransientException(
                    "Unexpected Conductor file storage path: " + storagePath);
        }
        String workflowId = relativePath.getName(1).toString();
        String fileId = relativePath.getName(2).toString();
        String contentUrl =
                publicBaseUrl()
                        + CONTENT_PATH
                        + UriUtils.encodePathSegment(workflowId, StandardCharsets.UTF_8)
                        + "/"
                        + UriUtils.encodePathSegment(fileId, StandardCharsets.UTF_8);
        if (!properties.getSigning().isEnabled()) {
            return contentUrl;
        }

        long expirationEpochSeconds = Instant.now().plus(expiration).getEpochSecond();
        SignedUrl signedUrl =
                urlSigner.sign(operation, workflowId, fileId, expirationEpochSeconds, null, null);
        return contentUrl
                + "?op="
                + UriUtils.encodeQueryParam(operation.getValue(), StandardCharsets.UTF_8)
                + "&exp="
                + expirationEpochSeconds
                + "&kid="
                + UriUtils.encodeQueryParam(signedUrl.getKeyId(), StandardCharsets.UTF_8)
                + "&sig="
                + UriUtils.encodeQueryParam(signedUrl.getSignature(), StandardCharsets.UTF_8);
    }

    private String publicBaseUrl() {
        if (properties.getBaseUrl() != null && !properties.getBaseUrl().isBlank()) {
            URI configuredBaseUrl = URI.create(properties.getBaseUrl());
            if (!configuredBaseUrl.isAbsolute()
                    || configuredBaseUrl.getHost() == null
                    || !("http".equalsIgnoreCase(configuredBaseUrl.getScheme())
                            || "https".equalsIgnoreCase(configuredBaseUrl.getScheme()))) {
                throw new NonTransientException(
                        "conductor.file-storage.conductor.base-url must be an absolute HTTP(S) URL");
            }
            return trimTrailingSlash(properties.getBaseUrl());
        }

        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes)) {
            throw new NonTransientException(
                    "Cannot derive a public file transfer URL without an HTTP request; configure "
                            + "conductor.file-storage.conductor.base-url");
        }
        return trimTrailingSlash(
                ServletUriComponentsBuilder.fromCurrentContextPath().toUriString());
    }

    private Path resolveStoragePath(String storagePath) {
        Path relativePath = validatedRelativePath(storagePath);
        Path resolvedPath = baseDirectory.resolve(relativePath).normalize();
        if (!resolvedPath.startsWith(baseDirectory)) {
            throw new NonTransientException(
                    "File storage path escapes configured directory: " + storagePath);
        }
        return resolvedPath;
    }

    private Path validatedRelativePath(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            throw new NonTransientException("File storage path is required");
        }
        Path path = Path.of(storagePath).normalize();
        if (path.isAbsolute() || path.startsWith("..")) {
            throw new NonTransientException(
                    "File storage path escapes configured directory: " + storagePath);
        }
        return path;
    }

    protected void moveIntoPlace(Path temporaryFile, Path target) throws IOException {
        Files.move(
                temporaryFile,
                target,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
