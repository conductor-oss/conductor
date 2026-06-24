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
package com.netflix.conductor.rest.controllers;

import java.util.List;

import org.conductoross.conductor.common.integrations.gdrive.GDriveIntegrationException;
import org.conductoross.conductor.common.integrations.gdrive.GDriveIntegrationService;
import org.conductoross.conductor.common.integrations.gdrive.GDriveLoadRequest;
import org.conductoross.conductor.common.integrations.gdrive.GDriveLoadResponse;
import org.conductoross.conductor.common.integrations.gdrive.GDriveOAuthTokenRequest;
import org.conductoross.conductor.common.integrations.gdrive.GDriveOAuthTokenResponse;
import org.conductoross.conductor.core.dao.InMemoryGDriveConnectionDAO;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class IntegrationsResourceTest {

    private MockMvc mockMvc;
    private RecordingGDriveIntegrationService gDriveIntegrationService;

    @Before
    public void before() {
        gDriveIntegrationService = new RecordingGDriveIntegrationService();
        this.mockMvc =
                MockMvcBuilders.standaloneSetup(
                                new IntegrationsResource(
                                        gDriveIntegrationService,
                                        new InMemoryGDriveConnectionDAO()))
                        .build();
    }

    @Test
    public void testGoogleDriveLoadIsMappedUnderApiPrefix() throws Exception {
        this.mockMvc
                .perform(
                        MockMvcRequestBuilders.post("/api/integrations/gdrive/load")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testGoogleDriveConnectionCanBeStoredAndUsedForLoad() throws Exception {
        this.mockMvc
                .perform(
                        MockMvcRequestBuilders.post("/api/integrations/gdrive/connections")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"connectionId\":\"gdrive-prod\","
                                                + "\"accountName\":\"Finance Drive\","
                                                + "\"oauthTokenJson\":\"{\\\"access_token\\\":\\\"token\\\"}\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectionId").value("gdrive-prod"))
                .andExpect(jsonPath("$.accountName").value("Finance Drive"))
                .andExpect(jsonPath("$.oauthTokenJson").doesNotExist());

        this.mockMvc
                .perform(
                        MockMvcRequestBuilders.post("/api/integrations/gdrive/load")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"connectionId\":\"gdrive-prod\","
                                                + "\"folderIds\":[\"folder-123\"],"
                                                + "\"fileIds\":[\"file-123\"],"
                                                + "\"maxFiles\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.folderIds[0]").value("folder-123"))
                .andExpect(jsonPath("$.fileIds[0]").value("file-123"));

        assertEquals("gdrive-prod", gDriveIntegrationService.lastRequest.getConnectionId());
        assertEquals(List.of("folder-123"), gDriveIntegrationService.lastRequest.getFolderIds());
        assertEquals(List.of("file-123"), gDriveIntegrationService.lastRequest.getFileIds());
        assertTrue(
                gDriveIntegrationService
                        .lastRequest
                        .getOauthTokenJson()
                        .contains("\"access_token\" : \"token\""));
    }

    @Test
    public void testGoogleDriveLoadFallsBackToStoredConnection() throws Exception {
        this.mockMvc
                .perform(
                        MockMvcRequestBuilders.post("/api/integrations/gdrive/connections")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"connectionId\":\"gdrive-prod\","
                                                + "\"accountName\":\"Finance Drive\","
                                                + "\"oauthTokenJson\":\"{\\\"access_token\\\":\\\"token\\\"}\"}"))
                .andExpect(status().isOk());

        this.mockMvc
                .perform(
                        MockMvcRequestBuilders.post("/api/integrations/gdrive/load")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"folderIds\":[\"folder-123\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.folderIds[0]").value("folder-123"));

        assertEquals("gdrive-prod", gDriveIntegrationService.lastRequest.getConnectionId());
    }

    @Test
    public void testGoogleDriveOAuthExchangeSavesConnectionAndReturnsTokenJson() throws Exception {
        this.mockMvc
                .perform(
                        MockMvcRequestBuilders.post("/api/integrations/gdrive/oauth/token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"connectionId\":\"gdrive-prod\","
                                                + "\"accountName\":\"Finance Drive\","
                                                + "\"authorizationCode\":\"code\","
                                                + "\"redirectUri\":\"http://localhost/integrations\","
                                                + "\"oauthClientJson\":\"{\\\"client_id\\\":\\\"client\\\",\\\"client_secret\\\":\\\"secret\\\"}\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectionId").value("gdrive-prod"))
                .andExpect(jsonPath("$.oauthTokenJson").value("{\"refresh_token\":\"token\"}"));

        this.mockMvc
                .perform(MockMvcRequestBuilders.get("/api/integrations/gdrive/connections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].connectionId").value("gdrive-prod"))
                .andExpect(jsonPath("$[0].accountName").value("Finance Drive"))
                .andExpect(jsonPath("$[0].oauthTokenJson").doesNotExist());
    }

    @Test
    public void testGoogleDriveConnectionsCanBeListedAndDeleted() throws Exception {
        this.mockMvc
                .perform(
                        MockMvcRequestBuilders.post("/api/integrations/gdrive/connections")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"connectionId\":\"gdrive-prod\","
                                                + "\"accountName\":\"Finance Drive\","
                                                + "\"oauthTokenJson\":\"{\\\"access_token\\\":\\\"token\\\"}\"}"))
                .andExpect(status().isOk());

        this.mockMvc
                .perform(MockMvcRequestBuilders.get("/api/integrations/gdrive/connections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].connectionId").value("gdrive-prod"))
                .andExpect(jsonPath("$[0].accountName").value("Finance Drive"))
                .andExpect(jsonPath("$[0].oauthTokenJson").doesNotExist());

        this.mockMvc
                .perform(
                        MockMvcRequestBuilders.delete(
                                "/api/integrations/gdrive/connections/gdrive-prod"))
                .andExpect(status().isOk());

        this.mockMvc
                .perform(MockMvcRequestBuilders.get("/api/integrations/gdrive/connections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    private static class RecordingGDriveIntegrationService extends GDriveIntegrationService {

        private GDriveLoadRequest lastRequest;

        @Override
        public GDriveLoadResponse loadFolder(GDriveLoadRequest request) {
            if (request == null
                    || request.getOauthTokenJson() == null
                    || request.getOauthTokenJson().trim().isEmpty()) {
                throw new GDriveIntegrationException("OAuth token JSON is required");
            }
            lastRequest = request;
            return new GDriveLoadResponse(request.getFolderIds(), request.getFileIds(), List.of());
        }

        @Override
        public GDriveOAuthTokenResponse exchangeAuthorizationCode(GDriveOAuthTokenRequest request) {
            return new GDriveOAuthTokenResponse("{\"refresh_token\":\"token\"}");
        }
    }
}
