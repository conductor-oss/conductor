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
package io.orkes.conductor.client.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestCreateAccessKeyResponsePojoMethods {

    @Test
    void testGetAndSetId() {
        // Arrange
        CreateAccessKeyResponse response = new CreateAccessKeyResponse();
        String expectedId = "test-id-123";

        // Act
        response.setId(expectedId);
        String actualId = response.getId();

        // Assert
        assertEquals(expectedId, actualId, "getId should return the value set by setId");
    }

    @Test
    void testGetAndSetSecret() {
        // Arrange
        CreateAccessKeyResponse response = new CreateAccessKeyResponse();
        String expectedSecret = "secret-key-456";

        // Act
        response.setSecret(expectedSecret);
        String actualSecret = response.getSecret();

        // Assert
        assertEquals(expectedSecret, actualSecret, "getSecret should return the value set by setSecret");
    }

    @Test
    void testInitialState() {
        // Arrange & Act
        CreateAccessKeyResponse response = new CreateAccessKeyResponse();

        // Assert
        assertNull(response.getId(), "Initial id should be null");
        assertNull(response.getSecret(), "Initial secret should be null");
    }

    @Test
    void testIdNullability() {
        // Arrange
        CreateAccessKeyResponse response = new CreateAccessKeyResponse();

        // Act
        response.setId("some-id");
        response.setId(null);

        // Assert
        assertNull(response.getId(), "getId should return null after setting id to null");
    }

    @Test
    void testSecretNullability() {
        // Arrange
        CreateAccessKeyResponse response = new CreateAccessKeyResponse();

        // Act
        response.setSecret("some-secret");
        response.setSecret(null);

        // Assert
        assertNull(response.getSecret(), "getSecret should return null after setting secret to null");
    }
}