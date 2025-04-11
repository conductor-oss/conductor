/*
 * Copyright 2025 Conductor Authors.
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
package com.netflix.conductor.common.run;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestExternalStorageLocationPojoMethods {

    @Test
    public void testSetAndGetUri() {
        // Arrange
        ExternalStorageLocation location = new ExternalStorageLocation();
        String expectedUri = "s3://bucket/path/to/file.json";

        // Act
        location.setUri(expectedUri);
        String actualUri = location.getUri();

        // Assert
        assertEquals(expectedUri, actualUri, "URI should match the value that was set");
    }

    @Test
    public void testSetAndGetPath() {
        // Arrange
        ExternalStorageLocation location = new ExternalStorageLocation();
        String expectedPath = "path/to/file.json";

        // Act
        location.setPath(expectedPath);
        String actualPath = location.getPath();

        // Assert
        assertEquals(expectedPath, actualPath, "Path should match the value that was set");
    }

    @Test
    public void testToString() {
        // Arrange
        ExternalStorageLocation location = new ExternalStorageLocation();
        String uri = "s3://bucket/path/to/file.json";
        String path = "path/to/file.json";
        location.setUri(uri);
        location.setPath(path);

        // Act
        String result = location.toString();

        // Assert
        assertTrue(result.contains("uri='" + uri + "'"), "toString should contain the URI");
        assertTrue(result.contains("path='" + path + "'"), "toString should contain the path");
        assertTrue(result.contains("ExternalStorageLocation"), "toString should contain the class name");
    }

    @Test
    public void testNullValues() {
        // Arrange
        ExternalStorageLocation location = new ExternalStorageLocation();

        // Act & Assert
        assertNull(location.getUri(), "URI should be null by default");
        assertNull(location.getPath(), "Path should be null by default");
    }

    @Test
    public void testMultipleSetOperations() {
        // Arrange
        ExternalStorageLocation location = new ExternalStorageLocation();
        String initialUri = "s3://bucket/initial.json";
        String updatedUri = "s3://bucket/updated.json";
        String initialPath = "initial.json";
        String updatedPath = "updated.json";

        // Act & Assert - initial values
        location.setUri(initialUri);
        location.setPath(initialPath);
        assertEquals(initialUri, location.getUri(), "URI should match initial value");
        assertEquals(initialPath, location.getPath(), "Path should match initial value");

        // Act & Assert - updated values
        location.setUri(updatedUri);
        location.setPath(updatedPath);
        assertEquals(updatedUri, location.getUri(), "URI should be updated to new value");
        assertEquals(updatedPath, location.getPath(), "Path should be updated to new value");
    }
}