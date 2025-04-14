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
package io.orkes.conductor.client.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestExternalStorageLocationPojoMethods {

    @Test
    public void testEmptyConstructor() {
        ExternalStorageLocation location = new ExternalStorageLocation();
        assertNull(location.getPath());
        assertNull(location.getUri());
    }

    @Test
    public void testGetSetPath() {
        ExternalStorageLocation location = new ExternalStorageLocation();
        String path = "/some/path";

        location.setPath(path);
        assertEquals(path, location.getPath());
    }

    @Test
    public void testGetSetUri() {
        ExternalStorageLocation location = new ExternalStorageLocation();
        String uri = "s3://bucket/key";

        location.setUri(uri);
        assertEquals(uri, location.getUri());
    }

    @Test
    public void testFluentPath() {
        String path = "/some/path";
        ExternalStorageLocation location = new ExternalStorageLocation()
                .path(path);

        assertEquals(path, location.getPath());
    }

    @Test
    public void testFluentUri() {
        String uri = "s3://bucket/key";
        ExternalStorageLocation location = new ExternalStorageLocation()
                .uri(uri);

        assertEquals(uri, location.getUri());
    }

    @Test
    public void testFluentChaining() {
        String path = "/some/path";
        String uri = "s3://bucket/key";

        ExternalStorageLocation location = new ExternalStorageLocation()
                .path(path)
                .uri(uri);

        assertEquals(path, location.getPath());
        assertEquals(uri, location.getUri());
    }

    @Test
    public void testEqualsAndHashCode() {
        ExternalStorageLocation location1 = new ExternalStorageLocation()
                .path("/path1")
                .uri("uri1");

        ExternalStorageLocation location2 = new ExternalStorageLocation()
                .path("/path1")
                .uri("uri1");

        ExternalStorageLocation location3 = new ExternalStorageLocation()
                .path("/path2")
                .uri("uri2");

        // Test equals
        assertEquals(location1, location2);
        assertNotEquals(location1, location3);

        // Test hashCode
        assertEquals(location1.hashCode(), location2.hashCode());
        assertNotEquals(location1.hashCode(), location3.hashCode());
    }

    @Test
    public void testToString() {
        ExternalStorageLocation location = new ExternalStorageLocation()
                .path("/some/path")
                .uri("s3://bucket/key");

        String toString = location.toString();

        // Verify toString contains the field values
        assertTrue(toString.contains("/some/path"));
        assertTrue(toString.contains("s3://bucket/key"));
    }
}