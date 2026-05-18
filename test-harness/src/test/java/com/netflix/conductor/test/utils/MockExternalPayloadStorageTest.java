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
package com.netflix.conductor.test.utils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.conductor.common.config.TestObjectMapperConfiguration;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@ContextConfiguration(classes = {TestObjectMapperConfiguration.class})
@RunWith(SpringRunner.class)
public class MockExternalPayloadStorageTest {

    private MockExternalPayloadStorage storage;

    @Autowired private ObjectMapper objectMapper;

    @Before
    public void setup() throws IOException {
        storage = new MockExternalPayloadStorage(objectMapper);
    }

    @Test
    public void testNormalUploadAndDownload() {
        // Test normal file upload and download
        String validPath = "test-file.json";
        String content = "test content";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        storage.upload(validPath, inputStream, content.length());

        InputStream downloaded = storage.download(validPath);
        assertNotNull("Should be able to download a valid file", downloaded);
    }

    @Test
    public void testPathTraversalAttackWithDoubleDots() {
        // Test that path traversal with "../" is blocked
        String maliciousPath = "../../etc/passwd";
        String content = "malicious content";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        // Upload should not throw exception but should fail silently (logged)
        storage.upload(maliciousPath, inputStream, content.length());

        // Download should return null for path traversal attempts
        InputStream downloaded = storage.download(maliciousPath);
        assertNull("Path traversal attack should be blocked", downloaded);
    }

    @Test
    public void testPathTraversalAttackWithEncodedDots() {
        // Test various path traversal patterns
        String[] maliciousPaths = {
            "../../../etc/passwd",
            "foo/../../bar/../../../etc/passwd",
            "./../../sensitive-file.txt"
        };

        for (String maliciousPath : maliciousPaths) {
            InputStream downloaded = storage.download(maliciousPath);
            assertNull(
                    "Path traversal attack should be blocked for: " + maliciousPath,
                    downloaded);
        }
    }

    @Test
    public void testValidNestedPath() {
        // Test that valid nested paths still work
        String validPath = "subdir/nested/file.json";
        String content = "test content";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        storage.upload(validPath, inputStream, content.length());

        InputStream downloaded = storage.download(validPath);
        assertNotNull("Valid nested path should work", downloaded);
    }

    @Test
    public void testPathWithDotInFilename() {
        // Test that files with dots in the name (not path traversal) work fine
        String validPath = "my.test.file.json";
        String content = "test content";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        storage.upload(validPath, inputStream, content.length());

        InputStream downloaded = storage.download(validPath);
        assertNotNull("Files with dots in filename should work", downloaded);
    }
}
