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
package org.conductoross.conductor.common.integrations.gdrive;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GDriveIntegrationServiceTest {

    @Test
    void normalizeFolderIdAcceptsRawFolderId() {
        assertEquals(
                "folder_123-ABC", GDriveIntegrationService.normalizeFolderId("folder_123-ABC"));
    }

    @Test
    void normalizeFolderIdExtractsFolderUrlId() {
        assertEquals(
                "folder_123-ABC",
                GDriveIntegrationService.normalizeFolderId(
                        "https://drive.google.com/drive/folders/folder_123-ABC?usp=sharing"));
    }

    @Test
    void normalizeFolderIdExtractsOpenUrlId() {
        assertEquals(
                "folder_123-ABC",
                GDriveIntegrationService.normalizeFolderId(
                        "https://drive.google.com/open?id=folder_123-ABC"));
    }

    @Test
    void normalizeFolderIdRejectsBlankFolderId() {
        assertThrows(
                GDriveIntegrationException.class,
                () -> GDriveIntegrationService.normalizeFolderId(" "));
    }

    @Test
    void normalizeFolderIdRejectsUnsafeCharacters() {
        assertThrows(
                GDriveIntegrationException.class,
                () -> GDriveIntegrationService.normalizeFolderId("folder/../bad"));
    }

    @Test
    void normalizeFileIdAcceptsRawFileId() {
        assertEquals("file_123-ABC", GDriveIntegrationService.normalizeFileId("file_123-ABC"));
    }

    @Test
    void normalizeFileIdExtractsFileUrlId() {
        assertEquals(
                "file_123-ABC",
                GDriveIntegrationService.normalizeFileId(
                        "https://drive.google.com/file/d/file_123-ABC/view?usp=sharing"));
    }

    @Test
    void normalizeFileIdExtractsOpenUrlId() {
        assertEquals(
                "file_123-ABC",
                GDriveIntegrationService.normalizeFileId(
                        "https://drive.google.com/open?id=file_123-ABC"));
    }

    @Test
    void normalizeFileIdRejectsUnsafeCharacters() {
        assertThrows(
                GDriveIntegrationException.class,
                () -> GDriveIntegrationService.normalizeFileId("file/../bad"));
    }

    @Test
    void exchangeAuthorizationCodeRejectsMissingClientCredentials() {
        GDriveOAuthTokenRequest request = new GDriveOAuthTokenRequest();
        request.setAuthorizationCode("code");
        request.setRedirectUri("http://localhost:8127/integrations");
        request.setOauthClientJson("{\"installed\":{}}");

        assertThrows(
                GDriveIntegrationException.class,
                () -> new GDriveIntegrationService().exchangeAuthorizationCode(request));
    }

    @Test
    void normalizeOAuthTokenJsonMergesClientCredentials() {
        String tokenJson = "{\"refresh_token\":\"refresh-token\"}";
        String clientJson =
                "{\"installed\":{\"client_id\":\"client-id\",\"client_secret\":\"client-secret\"}}";

        String normalized =
                new GDriveIntegrationService().normalizeOAuthTokenJson(tokenJson, clientJson);

        assertTrue(normalized.contains("\"refresh_token\" : \"refresh-token\""));
        assertTrue(normalized.contains("\"client_id\" : \"client-id\""));
        assertTrue(normalized.contains("\"client_secret\" : \"client-secret\""));
    }
}
