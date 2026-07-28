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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import org.conductoross.conductor.core.exception.FileStorageException;
import org.conductoross.conductor.core.storage.ConductorFileStorageProperties;
import org.conductoross.conductor.core.storage.FileStorageUrlSigner;
import org.conductoross.conductor.core.storage.FileStorageUrlSigner.Operation;
import org.conductoross.conductor.core.storage.StorageFileInfo;
import org.conductoross.conductor.filestorage.config.ConductorFileStorageConfiguration;
import org.conductoross.conductor.model.file.StorageType;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.filter.ForwardedHeaderFilter;

import com.netflix.conductor.core.exception.NonTransientException;

import jakarta.servlet.http.HttpServletRequest;

import static org.junit.Assert.*;

public class ConductorFileStorageTest {

    @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

    private ConductorFileStorage storage;

    @Before
    public void setUp() {
        ConductorFileStorageProperties properties = new ConductorFileStorageProperties();
        properties.setDirectory(tempFolder.getRoot().toPath());
        properties.setBaseUrl("https://conductor.example");
        storage = new ConductorFileStorage(properties);
    }

    @Test
    public void getStorageTypeReturnsConductor() {
        assertEquals(StorageType.CONDUCTOR, storage.getStorageType());
    }

    @Test
    public void generatedUrlsUseTheConfiguredHttpOrigin() {
        URI uploadUrl =
                URI.create(
                        storage.generateUploadUrl(
                                "conductor/workflow-1/file-1", Duration.ofSeconds(60)));
        URI downloadUrl =
                URI.create(
                        storage.generateDownloadUrl(
                                "conductor/workflow-1/file-1", Duration.ofSeconds(60)));

        assertEquals("https", uploadUrl.getScheme());
        assertEquals("conductor.example", uploadUrl.getHost());
        assertEquals("/api/files/content/workflow-1/file-1", uploadUrl.getPath());
        assertEquals(uploadUrl, downloadUrl);
    }

    @Test
    public void generatedUrlsUseTheForwardedRequestOrigin() throws Exception {
        ConductorFileStorageProperties properties = new ConductorFileStorageProperties();
        properties.setDirectory(tempFolder.getRoot().toPath());
        storage = new ConductorFileStorage(properties);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/conductor/api/files");
        request.setScheme("http");
        request.setServerName("conductor-internal");
        request.setServerPort(8080);
        request.setContextPath("/conductor");
        request.addHeader("X-Forwarded-Proto", "https");
        request.addHeader("X-Forwarded-Host", "files.example");
        request.addHeader("X-Forwarded-Port", "443");

        ForwardedHeaderFilter forwardedHeaderFilter =
                new ConductorFileStorageConfiguration().forwardedHeaderFilter();
        forwardedHeaderFilter.doFilter(
                request,
                new MockHttpServletResponse(),
                (filteredRequest, ignoredResponse) -> {
                    RequestContextHolder.setRequestAttributes(
                            new ServletRequestAttributes((HttpServletRequest) filteredRequest));
                    try {
                        assertEquals(
                                "https://files.example/conductor/api/files/content/workflow-1/file-1",
                                storage.generateDownloadUrl(
                                        "conductor/workflow-1/file-1", Duration.ofSeconds(60)));
                    } finally {
                        RequestContextHolder.resetRequestAttributes();
                    }
                });
    }

    @Test
    public void signedUrlsIncludeOperationExpirationAndHmacParameters() {
        ConductorFileStorageProperties properties = new ConductorFileStorageProperties();
        properties.setDirectory(tempFolder.getRoot().toPath());
        properties.setBaseUrl("https://conductor.example");
        properties.getSigning().setEnabled(true);
        properties.getSigning().setKeys(List.of(signingKey("current", "signing-secret")));
        storage = new ConductorFileStorage(properties);

        URI uploadUrl =
                URI.create(
                        storage.generateUploadUrl(
                                "conductor/workflow-1/file-1", Duration.ofSeconds(60)));
        String expiration = queryParameter(uploadUrl, "exp");

        assertEquals("upload", queryParameter(uploadUrl, "op"));
        assertEquals("current", queryParameter(uploadUrl, "kid"));
        assertNotNull(queryParameter(uploadUrl, "sig"));
        assertEquals(
                FileStorageUrlSigner.VerificationResult.VALID,
                new FileStorageUrlSigner(properties.getSigning())
                        .verify(
                                Operation.UPLOAD,
                                "workflow-1",
                                "file-1",
                                Long.parseLong(expiration),
                                null,
                                null,
                                queryParameter(uploadUrl, "kid"),
                                queryParameter(uploadUrl, "sig"),
                                Instant.now()));
    }

    @Test
    public void writeContentCommitsTheCompleteStreamAndReadContentStreamsItBack()
            throws IOException {
        storage.writeContent(
                "conductor/workflow-1/file-1",
                new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));

        StorageFileInfo info = storage.getStorageFileInfo("conductor/workflow-1/file-1");
        assertNotNull(info);
        assertTrue(info.isExists());
        assertEquals(5, info.getContentSize());
        try (InputStream inputStream = storage.readContent("conductor/workflow-1/file-1")) {
            assertEquals("hello", new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    public void rejectsTraversalOutsideTheConfiguredDirectory() {
        assertThrows(
                NonTransientException.class,
                () -> storage.writeContent("../outside", new ByteArrayInputStream(new byte[] {1})));
        assertThrows(NonTransientException.class, () -> storage.readContent("../outside"));
    }

    @Test
    public void oversizedWriteCleansUpTheTemporaryFileAndDoesNotCreateTheTarget()
            throws IOException {
        ConductorFileStorageProperties properties = new ConductorFileStorageProperties();
        properties.setDirectory(tempFolder.getRoot().toPath());
        properties.setBaseUrl("https://conductor.example");
        properties.setMaxSize(4);
        storage = new ConductorFileStorage(properties);

        assertThrows(
                FileStorageException.class,
                () ->
                        storage.writeContent(
                                "conductor/workflow-1/file-1",
                                new ByteArrayInputStream(
                                        "hello".getBytes(StandardCharsets.UTF_8))));

        assertNull(storage.getStorageFileInfo("conductor/workflow-1/file-1"));
        try (Stream<Path> paths = Files.walk(tempFolder.getRoot().toPath())) {
            assertFalse(paths.anyMatch(path -> path.getFileName().toString().endsWith(".part")));
        }
    }

    @Test
    public void atomicMoveFailureCleansUpTheTemporaryFileAndDoesNotCreateTheTarget()
            throws IOException {
        ConductorFileStorageProperties properties = new ConductorFileStorageProperties();
        properties.setDirectory(tempFolder.getRoot().toPath());
        properties.setBaseUrl("https://conductor.example");
        storage = new AtomicMoveFailingConductorFileStorage(properties);

        assertThrows(
                NonTransientException.class,
                () ->
                        storage.writeContent(
                                "conductor/workflow-1/file-1",
                                new ByteArrayInputStream(
                                        "hello".getBytes(StandardCharsets.UTF_8))));

        assertNull(storage.getStorageFileInfo("conductor/workflow-1/file-1"));
        try (Stream<Path> paths = Files.walk(tempFolder.getRoot().toPath())) {
            assertFalse(paths.anyMatch(path -> path.getFileName().toString().endsWith(".part")));
        }
    }

    @Test
    public void multipartOperationsAreUnsupported() {
        assertThrows(
                UnsupportedOperationException.class,
                () -> storage.initiateMultipartUpload("conductor/workflow-1/file-1"));
        assertThrows(
                UnsupportedOperationException.class,
                () ->
                        storage.generatePartUploadUrl(
                                "conductor/workflow-1/file-1",
                                "upload-1",
                                1,
                                Duration.ofSeconds(60)));
        assertThrows(
                UnsupportedOperationException.class,
                () ->
                        storage.completeMultipartUpload(
                                "conductor/workflow-1/file-1", "upload-1", List.of()));
    }

    private ConductorFileStorageProperties.Key signingKey(String id, String secret) {
        ConductorFileStorageProperties.Key key = new ConductorFileStorageProperties.Key();
        key.setId(id);
        key.setSecret(secret);
        return key;
    }

    private String queryParameter(URI uri, String name) {
        for (String parameter : uri.getRawQuery().split("&")) {
            String[] keyAndValue = parameter.split("=", 2);
            if (name.equals(keyAndValue[0])) {
                return keyAndValue[1];
            }
        }
        return null;
    }

    private static class AtomicMoveFailingConductorFileStorage extends ConductorFileStorage {

        AtomicMoveFailingConductorFileStorage(ConductorFileStorageProperties properties) {
            super(properties);
        }

        @Override
        protected void moveIntoPlace(Path temporaryFile, Path target) throws IOException {
            throw new AtomicMoveNotSupportedException(
                    temporaryFile.toString(), target.toString(), "Atomic moves are unsupported");
        }
    }
}
