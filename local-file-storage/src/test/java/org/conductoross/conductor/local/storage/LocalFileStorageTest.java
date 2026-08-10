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
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.conductoross.conductor.core.storage.StorageFileInfo;
import org.conductoross.conductor.local.config.LocalFileStorageProperties;
import org.conductoross.conductor.model.file.StorageType;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

public class LocalFileStorageTest {

    @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

    private LocalFileStorage storage;

    @Before
    public void setUp() {
        LocalFileStorageProperties props = new LocalFileStorageProperties();
        props.setDirectory(tempFolder.getRoot().getAbsolutePath());
        storage = new LocalFileStorage(props);
    }

    @Test
    public void testGetStorageType() {
        assertEquals(StorageType.LOCAL, storage.getStorageType());
    }

    @Test
    public void testGenerateUploadUrlReturnsFileUri() {
        String url = storage.generateUploadUrl("files/abc/report.pdf", Duration.ofSeconds(60));
        assertNotNull(url);
        assertTrue("expected file:// URI, got: " + url, url.startsWith("file:///"));
        Path resolved = Path.of(URI.create(url));
        assertTrue(resolved.isAbsolute());
        assertTrue(resolved.endsWith(Path.of("files", "abc", "report.pdf")));
    }

    @Test
    public void testGenerateDownloadUrlReturnsFileUri() {
        String url = storage.generateDownloadUrl("files/abc/report.pdf", Duration.ofSeconds(60));
        assertNotNull(url);
        assertTrue("expected file:// URI, got: " + url, url.startsWith("file:///"));
        Path resolved = Path.of(URI.create(url));
        assertTrue(resolved.endsWith(Path.of("files", "abc", "report.pdf")));
    }

    @Test
    public void testGetStorageFileInfoForExistingFile() throws IOException {
        Path dir = tempFolder.getRoot().toPath().resolve("files/abc");
        Files.createDirectories(dir);
        Path file = dir.resolve("report.pdf");
        Files.write(file, new byte[] {1, 2, 3, 4, 5});

        StorageFileInfo info = storage.getStorageFileInfo("files/abc/report.pdf");

        assertNotNull(info);
        assertTrue(info.isExists());
        assertNull(info.getContentHash());
        assertEquals(5, info.getContentSize());
    }

    @Test
    public void testGetStorageFileInfoForMissingFile() {
        StorageFileInfo info = storage.getStorageFileInfo("files/missing/file.pdf");
        assertNull(info);
    }

    @Test
    public void testMultipartMethodsAreNoOp() {
        String uploadId = storage.initiateMultipartUpload("files/abc/report.pdf");
        assertEquals("local-noop", uploadId);

        String partUrl =
                storage.generatePartUploadUrl(
                        "files/abc/report.pdf", uploadId, 1, Duration.ofSeconds(60));
        assertNotNull(partUrl);
        assertTrue("expected file:// URI, got: " + partUrl, partUrl.startsWith("file:///"));

        // complete is no-op — no exception
        storage.completeMultipartUpload("files/abc/report.pdf", uploadId, List.of());
    }
}
