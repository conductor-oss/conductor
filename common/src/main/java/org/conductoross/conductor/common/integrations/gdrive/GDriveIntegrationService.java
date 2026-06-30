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

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.netflix.conductor.common.config.ObjectMapperProvider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Component
public class GDriveIntegrationService {

    private static final String DRIVE_FILES_URL = "https://www.googleapis.com/drive/v3/files";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final Pattern FOLDER_URL_PATTERN = Pattern.compile("/folders/([^/?#]+)");
    private static final Pattern FILE_URL_PATTERN = Pattern.compile("/file/d/([^/?#]+)");
    private static final Pattern ID_QUERY_PATTERN = Pattern.compile("[?&]id=([^&#]+)");
    private static final Pattern SAFE_FOLDER_ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]+");
    private static final Pattern SAFE_FILE_ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]+");
    private static final int DEFAULT_MAX_FILES = 100;
    private static final int DRIVE_MAX_PAGE_SIZE = 1000;
    private static final String FILE_FIELDS =
            "id,name,mimeType,size,modifiedTime,webViewLink,webContentLink";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GDriveIntegrationService() {
        this(
                new ObjectMapperProvider().getObjectMapper(),
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build());
    }

    GDriveIntegrationService(ObjectMapper objectMapper, HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public GDriveLoadResponse loadFolder(GDriveLoadRequest request) {
        if (request == null) {
            throw new GDriveIntegrationException("Request body is required");
        }

        List<String> folderIds = normalizedFolderIds(request);
        List<String> fileIds = normalizedFileIds(request);
        JsonNode tokenJson = readTokenJson(request.getOauthTokenJson());
        String accessToken = firstText(tokenJson, "access_token", "token");

        if (isBlank(accessToken)) {
            accessToken = refreshAccessToken(tokenJson);
        }

        // Retry once with a refreshed token so Conductor executions do not block on browser OAuth.
        try {
            return new GDriveLoadResponse(
                    folderIds, fileIds, readDriveFiles(folderIds, fileIds, accessToken, request));
        } catch (UnauthorizedDriveRequest unauthorized) {
            if (!canRefresh(tokenJson)) {
                throw unauthorized;
            }
            String refreshedAccessToken = refreshAccessToken(tokenJson);
            return new GDriveLoadResponse(
                    folderIds,
                    fileIds,
                    readDriveFiles(folderIds, fileIds, refreshedAccessToken, request));
        }
    }

    public GDriveOAuthTokenResponse exchangeAuthorizationCode(GDriveOAuthTokenRequest request) {
        if (request == null) {
            throw new GDriveIntegrationException("Request body is required");
        }
        if (isBlank(request.getAuthorizationCode())) {
            throw new GDriveIntegrationException("Google OAuth authorization code is required");
        }
        if (isBlank(request.getRedirectUri())) {
            throw new GDriveIntegrationException("Google OAuth redirect URI is required");
        }

        JsonNode clientJson = readTokenJson(oauthClientJsonOrDefault(request.getOauthClientJson()));
        String clientId = clientText(clientJson, "client_id");
        String clientSecret = clientText(clientJson, "client_secret");
        String tokenUri = clientText(clientJson, "token_uri");
        if (isBlank(tokenUri)) {
            tokenUri = TOKEN_URL;
        }

        if (isBlank(clientId) || isBlank(clientSecret)) {
            throw new GDriveIntegrationException(
                    "OAuth client JSON must contain client_id and client_secret");
        }

        String form =
                "client_id="
                        + encode(clientId)
                        + "&client_secret="
                        + encode(clientSecret)
                        + "&code="
                        + encode(request.getAuthorizationCode())
                        + "&redirect_uri="
                        + encode(request.getRedirectUri())
                        + "&grant_type=authorization_code";

        HttpRequest tokenRequest =
                HttpRequest.newBuilder(URI.create(tokenUri))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form))
                        .build();

        JsonNode tokenResponse = sendJsonRequest(tokenRequest);
        ObjectNode tokenJson = tokenResponse.deepCopy();
        tokenJson.put("client_id", clientId);
        tokenJson.put("client_secret", clientSecret);
        tokenJson.put("token_uri", tokenUri);
        tokenJson.put("redirect_uri", request.getRedirectUri());

        try {
            return new GDriveOAuthTokenResponse(
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(tokenJson));
        } catch (IOException e) {
            throw new GDriveIntegrationException("Unable to serialize OAuth token JSON", e);
        }
    }

    public String normalizeOAuthTokenJson(String oauthTokenJson, String oauthClientJson) {
        JsonNode tokenJson = readTokenJson(oauthTokenJson);
        ObjectNode normalizedTokenJson = tokenJson.deepCopy();

        String resolvedOAuthClientJson = oauthClientJsonOrDefault(oauthClientJson);
        if (!isBlank(resolvedOAuthClientJson)) {
            JsonNode clientJson = readTokenJson(resolvedOAuthClientJson);
            copyClientFieldIfMissing(normalizedTokenJson, clientJson, "client_id");
            copyClientFieldIfMissing(normalizedTokenJson, clientJson, "client_secret");
            copyClientFieldIfMissing(normalizedTokenJson, clientJson, "token_uri");
        }

        if (isBlank(firstText(normalizedTokenJson, "access_token", "token"))
                && !canRefresh(normalizedTokenJson)) {
            throw new GDriveIntegrationException(
                    "OAuth token JSON must contain token/access_token or refresh_token, client_id, and client_secret");
        }
        if (!isBlank(firstText(normalizedTokenJson, "refresh_token"))
                && (isBlank(clientText(normalizedTokenJson, "client_id"))
                        || isBlank(clientText(normalizedTokenJson, "client_secret")))) {
            throw new GDriveIntegrationException(
                    "OAuth token JSON with refresh_token must include client_id and client_secret");
        }

        try {
            return objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(normalizedTokenJson);
        } catch (IOException e) {
            throw new GDriveIntegrationException("Unable to serialize OAuth token JSON", e);
        }
    }

    public GDriveOAuthClientConfig defaultOAuthClientConfig() {
        String clientId =
                configuredValue(
                        "conductor.gdrive.oauth.client-id", "CONDUCTOR_GDRIVE_OAUTH_CLIENT_ID", "");
        if (isBlank(clientId)) {
            clientId = configuredValue("google.oauth.client-id", "GOOGLE_OAUTH_CLIENT_ID", "");
        }
        String clientSecret =
                configuredValue(
                        "conductor.gdrive.oauth.client-secret",
                        "CONDUCTOR_GDRIVE_OAUTH_CLIENT_SECRET",
                        "");
        if (isBlank(clientSecret)) {
            clientSecret =
                    configuredValue("google.oauth.client-secret", "GOOGLE_OAUTH_CLIENT_SECRET", "");
        }
        return new GDriveOAuthClientConfig(!isBlank(clientId) && !isBlank(clientSecret), clientId);
    }

    public byte[] downloadFile(String oauthTokenJson, String fileId) {
        JsonNode tokenJson = readTokenJson(oauthTokenJson);
        String accessToken = firstText(tokenJson, "access_token", "token");
        if (isBlank(accessToken)) {
            accessToken = refreshAccessToken(tokenJson);
        }
        try {
            return downloadFileWithAccessToken(fileId, accessToken);
        } catch (UnauthorizedDriveRequest unauthorized) {
            if (!canRefresh(tokenJson)) {
                throw unauthorized;
            }
            return downloadFileWithAccessToken(fileId, refreshAccessToken(tokenJson));
        }
    }

    public static String normalizeFolderId(String folderIdOrUrl) {
        if (isBlank(folderIdOrUrl)) {
            throw new GDriveIntegrationException("Google Drive folder ID is required");
        }

        String value = folderIdOrUrl.trim();
        Matcher folderUrlMatcher = FOLDER_URL_PATTERN.matcher(value);
        if (folderUrlMatcher.find()) {
            value = folderUrlMatcher.group(1);
        } else {
            Matcher idQueryMatcher = ID_QUERY_PATTERN.matcher(value);
            if (idQueryMatcher.find()) {
                value = idQueryMatcher.group(1);
            }
        }

        value = java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
        if (!SAFE_FOLDER_ID_PATTERN.matcher(value).matches()) {
            throw new GDriveIntegrationException(
                    "Google Drive folder ID must contain only letters, numbers, underscore, or dash");
        }
        return value;
    }

    public static String normalizeFileId(String fileIdOrUrl) {
        if (isBlank(fileIdOrUrl)) {
            throw new GDriveIntegrationException("Google Drive file ID is required");
        }

        String value = fileIdOrUrl.trim();
        Matcher fileUrlMatcher = FILE_URL_PATTERN.matcher(value);
        if (fileUrlMatcher.find()) {
            value = fileUrlMatcher.group(1);
        } else {
            Matcher idQueryMatcher = ID_QUERY_PATTERN.matcher(value);
            if (idQueryMatcher.find()) {
                value = idQueryMatcher.group(1);
            }
        }

        value = java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
        if (!SAFE_FILE_ID_PATTERN.matcher(value).matches()) {
            throw new GDriveIntegrationException(
                    "Google Drive file ID must contain only letters, numbers, underscore, or dash");
        }
        return value;
    }

    private List<String> normalizedFolderIds(GDriveLoadRequest request) {
        Set<String> folderIds = new LinkedHashSet<>();
        addNormalizedFolderId(folderIds, request.getFolderId());
        if (request.getFolderIds() != null) {
            for (String folderId : request.getFolderIds()) {
                addNormalizedFolderId(folderIds, folderId);
            }
        }
        return new ArrayList<>(folderIds);
    }

    private void addNormalizedFolderId(Set<String> folderIds, String folderId) {
        if (!isBlank(folderId)) {
            folderIds.add(normalizeFolderId(folderId));
        }
    }

    private List<String> normalizedFileIds(GDriveLoadRequest request) {
        Set<String> fileIds = new LinkedHashSet<>();
        if (request.getFileIds() != null) {
            for (String fileId : request.getFileIds()) {
                addNormalizedFileId(fileIds, fileId);
            }
        }
        return new ArrayList<>(fileIds);
    }

    private void addNormalizedFileId(Set<String> fileIds, String fileId) {
        if (!isBlank(fileId)) {
            fileIds.add(normalizeFileId(fileId));
        }
    }

    private List<GDriveFile> readDriveFiles(
            List<String> folderIds,
            List<String> fileIds,
            String accessToken,
            GDriveLoadRequest loadRequest) {
        int maxFiles = normalizeMaxFiles(loadRequest.getMaxFiles());
        Set<String> allowedMimeTypes = normalizedMimeTypes(loadRequest.getMimeTypes());
        List<GDriveFile> files = new ArrayList<>();
        Set<String> seenFileIds = new HashSet<>();

        for (String fileId : fileIds) {
            GDriveFile file = getFile(fileId, accessToken);
            addFile(files, seenFileIds, file, allowedMimeTypes, maxFiles);
            if (files.size() >= maxFiles) {
                return files;
            }
        }

        if (!folderIds.isEmpty()) {
            for (String folderId : folderIds) {
                String query = "'" + folderId + "' in parents and trashed = false";
                collectFilesByQuery(query, accessToken, loadRequest, files, seenFileIds);
                if (files.size() >= maxFiles) {
                    break;
                }
            }
        } else if (fileIds.isEmpty()) {
            collectFilesByQuery("trashed = false", accessToken, loadRequest, files, seenFileIds);
        }

        return files;
    }

    private void collectFilesByQuery(
            String query,
            String accessToken,
            GDriveLoadRequest loadRequest,
            List<GDriveFile> files,
            Set<String> seenFileIds) {
        int maxFiles = normalizeMaxFiles(loadRequest.getMaxFiles());
        Set<String> allowedMimeTypes = normalizedMimeTypes(loadRequest.getMimeTypes());
        String pageToken = null;

        // Google Drive returns file metadata in pages. Stop when the requested limit is reached.
        do {
            int pageSize = Math.min(DRIVE_MAX_PAGE_SIZE, maxFiles - files.size());
            URI uri = driveFilesUri(query, pageSize, pageToken);
            HttpRequest request =
                    HttpRequest.newBuilder(uri)
                            .timeout(Duration.ofSeconds(30))
                            .header("Authorization", "Bearer " + accessToken)
                            .GET()
                            .build();

            JsonNode responseJson = sendJsonRequest(request);
            for (JsonNode item : responseJson.path("files")) {
                GDriveFile file = treeToFile(item);
                addFile(files, seenFileIds, file, allowedMimeTypes, maxFiles);
                if (files.size() >= maxFiles) {
                    break;
                }
            }

            pageToken = responseJson.path("nextPageToken").asText(null);
        } while (!isBlank(pageToken) && files.size() < maxFiles);
    }

    private GDriveFile getFile(String fileId, String accessToken) {
        HttpRequest request =
                HttpRequest.newBuilder(fileMetadataUri(fileId))
                        .timeout(Duration.ofSeconds(30))
                        .header("Authorization", "Bearer " + accessToken)
                        .GET()
                        .build();

        return treeToFile(sendJsonRequest(request));
    }

    private byte[] downloadFileWithAccessToken(String fileId, String accessToken) {
        HttpRequest request =
                HttpRequest.newBuilder(fileContentUri(normalizeFileId(fileId)))
                        .timeout(Duration.ofSeconds(60))
                        .header("Authorization", "Bearer " + accessToken)
                        .GET()
                        .build();
        try {
            HttpResponse<byte[]> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 401) {
                throw new UnauthorizedDriveRequest(
                        "Google Drive OAuth token is invalid or expired");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new GDriveIntegrationException(
                        "Google Drive file download failed with HTTP " + response.statusCode());
            }
            return response.body();
        } catch (IOException e) {
            throw new GDriveIntegrationException("Unable to download Google Drive file", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GDriveIntegrationException("Google Drive file download was interrupted", e);
        }
    }

    private void addFile(
            List<GDriveFile> files,
            Set<String> seenFileIds,
            GDriveFile file,
            Set<String> allowedMimeTypes,
            int maxFiles) {
        if (files.size() >= maxFiles) {
            return;
        }
        if (!allowedMimeTypes.isEmpty() && !allowedMimeTypes.contains(file.getMimeType())) {
            return;
        }
        if (file.getId() != null && !seenFileIds.add(file.getId())) {
            return;
        }
        files.add(file);
    }

    private URI driveFilesUri(String query, int pageSize, String pageToken) {
        List<String> params = new ArrayList<>();
        params.add("q=" + encode(query));
        params.add("spaces=drive");
        params.add("pageSize=" + pageSize);
        params.add("supportsAllDrives=true");
        params.add("includeItemsFromAllDrives=true");
        params.add("fields=" + encode("nextPageToken,files(" + FILE_FIELDS + ")"));
        if (!isBlank(pageToken)) {
            params.add("pageToken=" + encode(pageToken));
        }
        return URI.create(DRIVE_FILES_URL + "?" + String.join("&", params));
    }

    private URI fileMetadataUri(String fileId) {
        List<String> params = new ArrayList<>();
        params.add("supportsAllDrives=true");
        params.add("fields=" + encode(FILE_FIELDS));
        return URI.create(DRIVE_FILES_URL + "/" + encode(fileId) + "?" + String.join("&", params));
    }

    private URI fileContentUri(String fileId) {
        return URI.create(
                DRIVE_FILES_URL + "/" + encode(fileId) + "?alt=media&supportsAllDrives=true");
    }

    private JsonNode sendJsonRequest(HttpRequest request) {
        try {
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401) {
                throw new UnauthorizedDriveRequest(
                        "Google Drive OAuth token is invalid or expired");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new GDriveIntegrationException(
                        "Google Drive request failed with HTTP "
                                + response.statusCode()
                                + ": "
                                + response.body());
            }
            return objectMapper.readTree(response.body());
        } catch (IOException e) {
            throw new GDriveIntegrationException("Unable to read Google Drive response", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GDriveIntegrationException("Google Drive request was interrupted", e);
        }
    }

    private JsonNode readTokenJson(String oauthTokenJson) {
        if (isBlank(oauthTokenJson)) {
            throw new GDriveIntegrationException("OAuth token JSON is required");
        }
        try {
            JsonNode tokenJson = objectMapper.readTree(oauthTokenJson);
            if (!tokenJson.isObject()) {
                throw new GDriveIntegrationException("OAuth token JSON must be a JSON object");
            }
            return tokenJson;
        } catch (IOException e) {
            throw new GDriveIntegrationException("OAuth token JSON is invalid", e);
        }
    }

    private String refreshAccessToken(JsonNode tokenJson) {
        if (!canRefresh(tokenJson)) {
            throw new GDriveIntegrationException(
                    "OAuth token JSON must contain token/access_token or refresh_token, client_id, and client_secret");
        }

        String tokenUri = clientText(tokenJson, "token_uri");
        if (isBlank(tokenUri)) {
            tokenUri = TOKEN_URL;
        }

        String form =
                "client_id="
                        + encode(clientText(tokenJson, "client_id"))
                        + "&client_secret="
                        + encode(clientText(tokenJson, "client_secret"))
                        + "&refresh_token="
                        + encode(firstText(tokenJson, "refresh_token"))
                        + "&grant_type=refresh_token";

        HttpRequest request =
                HttpRequest.newBuilder(URI.create(tokenUri))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form))
                        .build();

        JsonNode responseJson = sendJsonRequest(request);
        String accessToken = firstText(responseJson, "access_token");
        if (isBlank(accessToken)) {
            throw new GDriveIntegrationException(
                    "OAuth refresh response did not include an access token");
        }
        return accessToken;
    }

    private boolean canRefresh(JsonNode tokenJson) {
        return !isBlank(firstText(tokenJson, "refresh_token"))
                && !isBlank(clientText(tokenJson, "client_id"))
                && !isBlank(clientText(tokenJson, "client_secret"));
    }

    private GDriveFile treeToFile(JsonNode item) {
        try {
            return objectMapper.treeToValue(item, GDriveFile.class);
        } catch (IOException e) {
            throw new GDriveIntegrationException("Unable to parse Google Drive file metadata", e);
        }
    }

    private static int normalizeMaxFiles(Integer maxFiles) {
        if (maxFiles == null || maxFiles < 1) {
            return DEFAULT_MAX_FILES;
        }
        return Math.min(maxFiles, DRIVE_MAX_PAGE_SIZE);
    }

    private static Set<String> normalizedMimeTypes(List<String> mimeTypes) {
        Set<String> values = new HashSet<>();
        if (mimeTypes == null) {
            return values;
        }
        for (String mimeType : mimeTypes) {
            if (!isBlank(mimeType)) {
                values.add(mimeType.trim());
            }
        }
        return values;
    }

    private static String firstText(JsonNode node, String... fieldNames) {
        if (node == null) {
            return "";
        }
        for (String fieldName : fieldNames) {
            JsonNode field = node.get(fieldName);
            if (field != null && !field.isNull() && !isBlank(field.asText())) {
                return field.asText();
            }
        }
        return "";
    }

    private static String clientText(JsonNode node, String fieldName) {
        String value = firstText(node, fieldName);
        if (!isBlank(value)) {
            return value;
        }
        value = firstText(node.path("installed"), fieldName);
        if (!isBlank(value)) {
            return value;
        }
        return firstText(node.path("web"), fieldName);
    }

    private static void copyClientFieldIfMissing(
            ObjectNode tokenJson, JsonNode clientJson, String fieldName) {
        if (!isBlank(clientText(tokenJson, fieldName))) {
            return;
        }
        String value = clientText(clientJson, fieldName);
        if (!isBlank(value)) {
            tokenJson.put(fieldName, value);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String oauthClientJsonOrDefault(String oauthClientJson) {
        if (!isBlank(oauthClientJson)) {
            return oauthClientJson;
        }
        String clientId =
                configuredValue(
                        "conductor.gdrive.oauth.client-id", "CONDUCTOR_GDRIVE_OAUTH_CLIENT_ID", "");
        if (isBlank(clientId)) {
            clientId = configuredValue("google.oauth.client-id", "GOOGLE_OAUTH_CLIENT_ID", "");
        }
        String clientSecret =
                configuredValue(
                        "conductor.gdrive.oauth.client-secret",
                        "CONDUCTOR_GDRIVE_OAUTH_CLIENT_SECRET",
                        "");
        if (isBlank(clientSecret)) {
            clientSecret =
                    configuredValue("google.oauth.client-secret", "GOOGLE_OAUTH_CLIENT_SECRET", "");
        }
        if (isBlank(clientId) || isBlank(clientSecret)) {
            return oauthClientJson;
        }
        ObjectNode clientJson = objectMapper.createObjectNode();
        ObjectNode installed = clientJson.putObject("installed");
        installed.put("client_id", clientId);
        installed.put("client_secret", clientSecret);
        installed.put("auth_uri", "https://accounts.google.com/o/oauth2/auth");
        installed.put("token_uri", TOKEN_URL);
        return clientJson.toString();
    }

    private static String configuredValue(
            String propertyName, String envName, String defaultValue) {
        String propertyValue = System.getProperty(propertyName);
        if (!isBlank(propertyValue)) {
            return propertyValue.trim();
        }
        String envValue = System.getenv(envName);
        if (!isBlank(envValue)) {
            return envValue.trim();
        }
        return defaultValue;
    }

    public record GDriveOAuthClientConfig(boolean configured, String clientId) {}

    private static class UnauthorizedDriveRequest extends GDriveIntegrationException {

        UnauthorizedDriveRequest(String message) {
            super(message);
        }
    }
}
