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

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.conductoross.conductor.common.integrations.gdrive.GDriveConnection;
import org.conductoross.conductor.common.integrations.gdrive.GDriveConnectionRequest;
import org.conductoross.conductor.common.integrations.gdrive.GDriveConnectionResponse;
import org.conductoross.conductor.common.integrations.gdrive.GDriveIntegrationException;
import org.conductoross.conductor.common.integrations.gdrive.GDriveIntegrationService;
import org.conductoross.conductor.common.integrations.gdrive.GDriveLoadRequest;
import org.conductoross.conductor.common.integrations.gdrive.GDriveLoadResponse;
import org.conductoross.conductor.common.integrations.gdrive.GDriveOAuthTokenRequest;
import org.conductoross.conductor.common.integrations.gdrive.GDriveOAuthTokenResponse;
import org.conductoross.conductor.common.integrations.gemini.GeminiConnection;
import org.conductoross.conductor.common.integrations.gemini.GeminiConnectionRequest;
import org.conductoross.conductor.common.integrations.gemini.GeminiConnectionResponse;
import org.conductoross.conductor.common.integrations.gemini.GeminiIntegrationException;
import org.conductoross.conductor.common.integrations.gemini.GeminiIntegrationService;
import org.conductoross.conductor.common.integrations.zoho.ZohoBooksConnection;
import org.conductoross.conductor.common.integrations.zoho.ZohoBooksConnectionRequest;
import org.conductoross.conductor.common.integrations.zoho.ZohoBooksConnectionResponse;
import org.conductoross.conductor.common.integrations.zoho.ZohoBooksFetchRequest;
import org.conductoross.conductor.common.integrations.zoho.ZohoBooksFetchResponse;
import org.conductoross.conductor.common.integrations.zoho.ZohoBooksIntegrationException;
import org.conductoross.conductor.common.integrations.zoho.ZohoBooksIntegrationService;
import org.conductoross.conductor.common.integrations.zoho.ZohoBooksInvoicesResponse;
import org.conductoross.conductor.core.dao.InMemoryGDriveConnectionDAO;
import org.conductoross.conductor.core.dao.InMemoryZohoBooksConnectionDAO;
import org.conductoross.conductor.dao.GDriveConnectionDAO;
import org.conductoross.conductor.dao.ZohoBooksConnectionDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.swagger.v3.oas.annotations.Operation;

import static com.netflix.conductor.rest.config.RequestMappingConstants.INTEGRATIONS;

@RestController
@RequestMapping(value = INTEGRATIONS)
public class IntegrationsResource {

    private static final Pattern SAFE_CONNECTION_ID_PATTERN = Pattern.compile("[A-Za-z0-9._-]+");

    private final GDriveIntegrationService gDriveIntegrationService;
    private final GeminiIntegrationService geminiIntegrationService;
    private final ZohoBooksIntegrationService zohoBooksIntegrationService;
    private final GDriveConnectionDAO gDriveConnectionDAO;
    private final ZohoBooksConnectionDAO zohoBooksConnectionDAO;

    public IntegrationsResource(GDriveIntegrationService gDriveIntegrationService) {
        this(
                gDriveIntegrationService,
                new GeminiIntegrationService(),
                new ZohoBooksIntegrationService(),
                new InMemoryGDriveConnectionDAO(),
                new InMemoryZohoBooksConnectionDAO());
    }

    @Autowired
    public IntegrationsResource(
            GDriveIntegrationService gDriveIntegrationService,
            GeminiIntegrationService geminiIntegrationService,
            ZohoBooksIntegrationService zohoBooksIntegrationService,
            GDriveConnectionDAO gDriveConnectionDAO,
            ZohoBooksConnectionDAO zohoBooksConnectionDAO) {
        this.gDriveIntegrationService = gDriveIntegrationService;
        this.geminiIntegrationService = geminiIntegrationService;
        this.zohoBooksIntegrationService = zohoBooksIntegrationService;
        this.gDriveConnectionDAO = gDriveConnectionDAO;
        this.zohoBooksConnectionDAO = zohoBooksConnectionDAO;
    }

    IntegrationsResource(
            GDriveIntegrationService gDriveIntegrationService,
            GDriveConnectionDAO gDriveConnectionDAO) {
        this(
                gDriveIntegrationService,
                new GeminiIntegrationService(),
                new ZohoBooksIntegrationService(),
                gDriveConnectionDAO,
                new InMemoryZohoBooksConnectionDAO());
    }

    @PostMapping("/gdrive/load")
    @Operation(summary = "Load file metadata from Google Drive")
    public GDriveLoadResponse loadGoogleDriveFolder(@RequestBody GDriveLoadRequest request) {
        try {
            return gDriveIntegrationService.loadFolder(resolveConnection(request));
        } catch (GDriveIntegrationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    @PostMapping("/gdrive/oauth/token")
    @Operation(summary = "Exchange a Google OAuth authorization code for token JSON")
    public GDriveOAuthTokenResponse exchangeGoogleDriveAuthorizationCode(
            @RequestBody GDriveOAuthTokenRequest request) {
        try {
            GDriveOAuthTokenResponse tokenResponse =
                    gDriveIntegrationService.exchangeAuthorizationCode(request);
            if (isBlank(request.getConnectionId())) {
                return tokenResponse;
            }

            GDriveConnection connection =
                    new GDriveConnection(
                            normalizeConnectionId(request.getConnectionId()),
                            normalizeAccountName(
                                    request.getAccountName(), request.getConnectionId()),
                            tokenResponse.getOauthTokenJson());
            gDriveConnectionDAO.saveConnection(connection);
            GDriveConnection stored =
                    gDriveConnectionDAO.getConnection(connection.getConnectionId());
            return new GDriveOAuthTokenResponse(
                    stored.getConnectionId(), tokenResponse.getOauthTokenJson());
        } catch (GDriveIntegrationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    @GetMapping("/gdrive/oauth/client")
    @Operation(summary = "Get Google Drive OAuth client configuration status")
    public GDriveIntegrationService.GDriveOAuthClientConfig getGoogleDriveOAuthClientConfig() {
        return gDriveIntegrationService.defaultOAuthClientConfig();
    }

    @PostMapping("/gdrive/connections")
    @Operation(summary = "Store Google Drive OAuth credentials for a connection")
    public GDriveConnectionResponse saveGoogleDriveConnection(
            @RequestBody GDriveConnectionRequest request) {
        try {
            if (request == null) {
                throw new GDriveIntegrationException("Request body is required");
            }
            String connectionId = normalizeConnectionId(request.getConnectionId());
            String oauthTokenJson =
                    gDriveIntegrationService.normalizeOAuthTokenJson(
                            request.getOauthTokenJson(), request.getOauthClientJson());
            gDriveConnectionDAO.saveConnection(
                    new GDriveConnection(
                            connectionId,
                            normalizeAccountName(request.getAccountName(), connectionId),
                            oauthTokenJson));
            return toResponse(gDriveConnectionDAO.getConnection(connectionId));
        } catch (GDriveIntegrationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    @GetMapping("/gdrive/connections")
    @Operation(summary = "List stored Google Drive connections")
    public List<GDriveConnectionResponse> listGoogleDriveConnections() {
        return gDriveConnectionDAO.getAllConnections().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @GetMapping("/gdrive/connections/{connectionId}")
    @Operation(summary = "Get a stored Google Drive connection")
    public GDriveConnectionResponse getGoogleDriveConnection(
            @PathVariable("connectionId") String connectionId) {
        GDriveConnection connection =
                gDriveConnectionDAO.getConnection(normalizeConnectionId(connectionId));
        if (connection == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Google Drive connection not found");
        }
        return toResponse(connection);
    }

    @DeleteMapping("/gdrive/connections/{connectionId}")
    @Operation(summary = "Delete a stored Google Drive connection")
    public void deleteGoogleDriveConnection(@PathVariable("connectionId") String connectionId) {
        gDriveConnectionDAO.deleteConnection(normalizeConnectionId(connectionId));
    }

    @PostMapping("/gemini/connections")
    @Operation(summary = "Store Gemini API credentials for a connection")
    public GeminiConnectionResponse saveGeminiConnection(
            @RequestBody GeminiConnectionRequest request) {
        try {
            GeminiConnection connection = geminiIntegrationService.saveConnection(request);
            return new GeminiConnectionResponse(connection, true);
        } catch (GeminiIntegrationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    @GetMapping("/gemini/connections")
    @Operation(summary = "List Gemini LLM connections")
    public List<GeminiConnectionResponse> listGeminiConnections() {
        return geminiIntegrationService.listConnections();
    }

    @DeleteMapping("/gemini/connections/{connectionId}")
    @Operation(summary = "Delete a Gemini LLM connection")
    public void deleteGeminiConnection(@PathVariable("connectionId") String connectionId) {
        geminiIntegrationService.deleteConnection(connectionId);
    }

    @GetMapping("/gemini/prompts")
    @Operation(summary = "List Gemini prompt templates")
    public Map<String, Object> listGeminiPrompts() {
        return Map.of(
                "promptDirectory",
                geminiIntegrationService.promptDirectory().toString(),
                "prompts",
                geminiIntegrationService.listPromptNames(),
                "defaultConnectionId",
                geminiIntegrationService.defaultConnectionId(),
                "defaultModel",
                geminiIntegrationService.defaultModel());
    }

    @PostMapping("/zoho-books/connections")
    @Operation(summary = "Store Zoho Books credentials for a connection")
    public ZohoBooksConnectionResponse saveZohoBooksConnection(
            @RequestBody ZohoBooksConnectionRequest request) {
        try {
            ZohoBooksConnection connection = zohoBooksIntegrationService.saveConnection(request);
            connection.setConnectionId(
                    normalizeConnectionId(connection.getConnectionId(), "Zoho Books"));
            zohoBooksConnectionDAO.saveConnection(connection);
            return new ZohoBooksConnectionResponse(
                    zohoBooksConnectionDAO.getConnection(connection.getConnectionId()));
        } catch (ZohoBooksIntegrationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    @GetMapping("/zoho-books/connections")
    @Operation(summary = "List stored Zoho Books connections")
    public List<ZohoBooksConnectionResponse> listZohoBooksConnections() {
        return zohoBooksConnectionDAO.getAllConnections().stream()
                .map(ZohoBooksConnectionResponse::new)
                .collect(Collectors.toList());
    }

    @DeleteMapping("/zoho-books/connections/{connectionId}")
    @Operation(summary = "Delete a stored Zoho Books connection")
    public void deleteZohoBooksConnection(@PathVariable("connectionId") String connectionId) {
        zohoBooksConnectionDAO.deleteConnection(normalizeConnectionId(connectionId, "Zoho Books"));
    }

    @PostMapping("/zoho-books/fetch")
    @Operation(summary = "Fetch GRN and POD records for a Zoho Books bill")
    public ZohoBooksFetchResponse fetchZohoBooksInvoiceDocuments(
            @RequestBody ZohoBooksFetchRequest request) {
        try {
            ZohoBooksConnection connection = zohoBooksConnection(request.getConnectionId());
            request.setConnectionId(connection.getConnectionId());
            return zohoBooksIntegrationService.fetchInvoiceDocuments(connection, request);
        } catch (ZohoBooksIntegrationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    @PostMapping({
        "/zoho-books/connections/{connectionId}/bills",
        "/zoho-books/connections/{connectionId}/invoices"
    })
    @Operation(summary = "Fetch all bills for a stored Zoho Books connection")
    public ZohoBooksInvoicesResponse fetchZohoBooksInvoices(
            @PathVariable("connectionId") String connectionId) {
        try {
            ZohoBooksConnection connection = zohoBooksConnection(connectionId);
            return zohoBooksIntegrationService.fetchInvoices(connection);
        } catch (ZohoBooksIntegrationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    private GDriveLoadRequest resolveConnection(GDriveLoadRequest request) {
        if (request == null) {
            return request;
        }

        GDriveConnection connection;
        String connectionId = request.getConnectionId();
        if (isBlank(connectionId)) {
            if (!isBlank(request.getOauthTokenJson())) {
                return request;
            }
            connection = latestConnection();
            if (connection == null) {
                throw new GDriveIntegrationException(
                        "connectionId is required or create a Google Drive connection in the UI");
            }
            connectionId = connection.getConnectionId();
        } else {
            connectionId = normalizeConnectionId(connectionId);
            connection = gDriveConnectionDAO.getConnection(connectionId);
        }
        if (connection == null) {
            throw new GDriveIntegrationException(
                    "No Google Drive connection found for connectionId " + connectionId);
        }

        GDriveLoadRequest resolved = new GDriveLoadRequest();
        resolved.setConnectionId(connectionId);
        resolved.setFolderId(request.getFolderId());
        resolved.setFolderIds(request.getFolderIds());
        resolved.setFileIds(request.getFileIds());
        resolved.setOauthTokenJson(connection.getOauthTokenJson());
        resolved.setMaxFiles(request.getMaxFiles());
        resolved.setMimeTypes(request.getMimeTypes());
        return resolved;
    }

    private GDriveConnectionResponse toResponse(GDriveConnection connection) {
        return new GDriveConnectionResponse(
                connection.getConnectionId(),
                normalizeAccountName(connection.getAccountName(), connection.getConnectionId()),
                connection.getCreatedAt(),
                connection.getUpdatedAt());
    }

    private GDriveConnection latestConnection() {
        return gDriveConnectionDAO.getAllConnections().stream()
                .max(
                        Comparator.comparingLong(
                                        (GDriveConnection connection) ->
                                                timestamp(connection.getUpdatedAt()))
                                .thenComparingLong(
                                        connection -> timestamp(connection.getCreatedAt()))
                                .thenComparing(GDriveConnection::getConnectionId))
                .orElse(null);
    }

    private long timestamp(Long value) {
        return value == null ? 0L : value;
    }

    private String normalizeConnectionId(String connectionId) {
        return normalizeConnectionId(connectionId, "Google Drive");
    }

    private String normalizeConnectionId(String connectionId, String providerName) {
        if (isBlank(connectionId)) {
            throw new GDriveIntegrationException(providerName + " connectionId is required");
        }
        String normalized = connectionId.trim();
        if (!SAFE_CONNECTION_ID_PATTERN.matcher(normalized).matches()) {
            throw new GDriveIntegrationException(
                    providerName
                            + " connectionId must contain only letters, numbers, dot, underscore, or dash");
        }
        return normalized;
    }

    private ZohoBooksConnection zohoBooksConnection(String connectionId) {
        String normalizedConnectionId = normalizeConnectionId(connectionId, "Zoho Books");
        ZohoBooksConnection connection =
                zohoBooksConnectionDAO.getConnection(normalizedConnectionId);
        if (connection == null) {
            throw new ZohoBooksIntegrationException(
                    "No Zoho Books connection found for connectionId " + normalizedConnectionId);
        }
        return connection;
    }

    private String normalizeAccountName(String accountName, String connectionId) {
        if (!isBlank(accountName)) {
            return accountName.trim();
        }
        return normalizeConnectionId(connectionId);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
