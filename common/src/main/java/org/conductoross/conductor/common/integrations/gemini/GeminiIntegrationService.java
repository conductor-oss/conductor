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
package org.conductoross.conductor.common.integrations.gemini;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

@Component
public class GeminiIntegrationService {

    public static final String DEFAULT_CONNECTION_ID = "gemini-default";
    public static final String DEFAULT_MODEL = "gemini-2.5-flash";

    private static final Pattern SAFE_CONNECTION_ID_PATTERN = Pattern.compile("[A-Za-z0-9._-]+");
    private static final Pattern SAFE_PROMPT_NAME_PATTERN = Pattern.compile("[A-Za-z0-9._-]+");

    private final Map<String, GeminiConnection> connections = new ConcurrentHashMap<>();

    public GeminiConnection saveConnection(GeminiConnectionRequest request) {
        if (request == null) {
            throw new GeminiIntegrationException("Request body is required");
        }
        String connectionId = normalizeConnectionId(request.getConnectionId());
        String apiKey = request.getApiKey();
        if (isBlank(apiKey)) {
            apiKey = configuredApiKey();
        }
        if (isBlank(apiKey)) {
            throw new GeminiIntegrationException(
                    "Gemini API key is required in CONDUCTOR_GEMINI_API_KEY or request body");
        }
        GeminiConnection existing = connections.get(connectionId);
        GeminiConnection connection =
                new GeminiConnection(
                        connectionId,
                        apiKey.trim(),
                        normalizeModel(request.getModel()),
                        normalizeOptionalPromptName(request.getPromptName()));
        if (existing != null) {
            connection.setCreatedAt(existing.getCreatedAt());
        }
        connection.setUpdatedAt(System.currentTimeMillis());
        connections.put(connectionId, connection);
        return connection;
    }

    public GeminiConnection getConnection(String connectionId) {
        String normalized = normalizeConnectionId(connectionId);
        GeminiConnection connection = connections.get(normalized);
        if (connection != null) {
            return connection;
        }

        GeminiConnection envConnection = envConnection();
        if (envConnection != null && envConnection.getConnectionId().equals(normalized)) {
            return envConnection;
        }
        throw new GeminiIntegrationException(
                "No Gemini connection found for connectionId " + normalized);
    }

    public GeminiConnection resolveTaskConnection(
            String connectionId, String apiKey, String model) {
        if (!isBlank(apiKey)) {
            return new GeminiConnection(
                    isBlank(connectionId)
                            ? defaultConnectionId()
                            : normalizeConnectionId(connectionId),
                    apiKey.trim(),
                    normalizeModel(model));
        }

        if (!isBlank(connectionId)) {
            GeminiConnection connection = getConnection(connectionId);
            return withModelOverride(connection, model);
        }

        GeminiConnection envConnection = envConnection();
        if (envConnection != null) {
            return withModelOverride(envConnection, model);
        }

        GeminiConnection defaultConnection = connections.get(defaultConnectionId());
        if (defaultConnection != null) {
            return withModelOverride(defaultConnection, model);
        }

        throw new GeminiIntegrationException(
                "Gemini API key is required in CONDUCTOR_GEMINI_API_KEY, request body, or saved default connection");
    }

    public List<GeminiConnectionResponse> listConnections() {
        Map<String, GeminiConnection> all = new LinkedHashMap<>();
        GeminiConnection envConnection = envConnection();
        if (envConnection != null) {
            all.put(envConnection.getConnectionId(), envConnection);
        }
        connections.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> all.put(entry.getKey(), entry.getValue()));

        return all.values().stream()
                .map(connection -> new GeminiConnectionResponse(connection, true))
                .toList();
    }

    public void deleteConnection(String connectionId) {
        connections.remove(normalizeConnectionId(connectionId));
    }

    public List<String> listPromptNames() {
        Path promptDir = promptDirectory();
        if (!Files.isDirectory(promptDir)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.list(promptDir)) {
            return paths.filter(path -> path.getFileName().toString().endsWith(".j2"))
                    .map(path -> path.getFileName().toString().replaceFirst("\\.j2$", ""))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new GeminiIntegrationException("Unable to list Gemini prompt templates", e);
        }
    }

    public String loadPrompt(String promptName, Map<String, Object> variables) {
        String normalized = normalizePromptName(promptName);
        Path promptPath = promptDirectory().resolve(normalized + ".j2").normalize();
        if (!promptPath.startsWith(promptDirectory().normalize())) {
            throw new GeminiIntegrationException("Invalid Gemini prompt name");
        }
        if (!Files.exists(promptPath)) {
            throw new GeminiIntegrationException("Gemini prompt template not found: " + normalized);
        }
        try {
            String template = Files.readString(promptPath, StandardCharsets.UTF_8);
            return renderTemplate(template, variables);
        } catch (IOException e) {
            throw new GeminiIntegrationException("Unable to read Gemini prompt template", e);
        }
    }

    public String renderPrompt(String prompt, Map<String, Object> variables) {
        if (isBlank(prompt)) {
            throw new GeminiIntegrationException("Gemini prompt is required");
        }
        return renderTemplate(prompt, variables);
    }

    public String defaultConnectionId() {
        return value(
                "conductor.integrations.gemini.connection-id",
                "CONDUCTOR_GEMINI_CONNECTION_ID",
                DEFAULT_CONNECTION_ID);
    }

    public String defaultModel() {
        return value(
                "conductor.integrations.gemini.model", "CONDUCTOR_GEMINI_MODEL", DEFAULT_MODEL);
    }

    public Path promptDirectory() {
        String configured =
                value(
                        "conductor.integrations.gemini.prompt-dir",
                        "CONDUCTOR_GEMINI_PROMPT_DIR",
                        "prompts");
        return Path.of(configured);
    }

    private GeminiConnection envConnection() {
        String apiKey = configuredApiKey();
        if (isBlank(apiKey)) {
            return null;
        }
        return new GeminiConnection(defaultConnectionId(), apiKey, defaultModel(), null);
    }

    private GeminiConnection withModelOverride(GeminiConnection connection, String model) {
        if (isBlank(model) || normalizeModel(model).equals(connection.getModel())) {
            return connection;
        }
        GeminiConnection copy =
                new GeminiConnection(
                        connection.getConnectionId(),
                        connection.getApiKey(),
                        normalizeModel(model),
                        connection.getPromptName());
        copy.setCreatedAt(connection.getCreatedAt());
        copy.setUpdatedAt(connection.getUpdatedAt());
        return copy;
    }

    private String configuredApiKey() {
        String apiKey =
                value("conductor.integrations.gemini.api-key", "CONDUCTOR_GEMINI_API_KEY", "");
        if (isBlank(apiKey)) {
            apiKey = value("conductor.ai.gemini.api-key", "CONDUCTOR_AI_GEMINI_API_KEY", "");
        }
        return apiKey;
    }

    private String renderTemplate(String template, Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return template;
        }
        String rendered = template;
        for (Map.Entry<String, Object> entry : flatten(variables).entrySet()) {
            String value = entry.getValue() == null ? "" : String.valueOf(entry.getValue());
            rendered = rendered.replace("{{ " + entry.getKey() + " }}", value);
            rendered = rendered.replace("{{" + entry.getKey() + "}}", value);
        }
        return rendered;
    }

    private Map<String, Object> flatten(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        flattenInto("", source, result);
        return result;
    }

    @SuppressWarnings("unchecked")
    private void flattenInto(
            String prefix, Map<String, Object> source, Map<String, Object> target) {
        List<String> keys = new ArrayList<>(source.keySet());
        keys.sort(Comparator.naturalOrder());
        for (String key : keys) {
            Object value = source.get(key);
            String next = prefix.isEmpty() ? key : prefix + "." + key;
            if (value instanceof Map<?, ?> valueMap) {
                flattenInto(next, (Map<String, Object>) valueMap, target);
            } else {
                target.put(next, value);
            }
        }
    }

    private String normalizeConnectionId(String connectionId) {
        String normalized = isBlank(connectionId) ? defaultConnectionId() : connectionId.trim();
        if (!SAFE_CONNECTION_ID_PATTERN.matcher(normalized).matches()) {
            throw new GeminiIntegrationException(
                    "Gemini connectionId must contain only letters, numbers, dot, underscore, or dash");
        }
        return normalized;
    }

    private String normalizePromptName(String promptName) {
        if (isBlank(promptName)) {
            throw new GeminiIntegrationException("Gemini promptName is required");
        }
        String normalized = promptName.trim().replace('\\', '/');
        int separator = normalized.lastIndexOf('/');
        if (separator >= 0) {
            normalized = normalized.substring(separator + 1);
        }
        normalized = normalized.replaceFirst("\\.j2$", "");
        if (!SAFE_PROMPT_NAME_PATTERN.matcher(normalized).matches()) {
            throw new GeminiIntegrationException(
                    "Gemini promptName must contain only letters, numbers, dot, underscore, or dash");
        }
        return normalized;
    }

    private String normalizeOptionalPromptName(String promptName) {
        return isBlank(promptName) ? null : normalizePromptName(promptName);
    }

    private String normalizeModel(String model) {
        return isBlank(model) ? defaultModel() : model.trim();
    }

    private static String value(String propertyName, String envName, String defaultValue) {
        String systemValue = System.getProperty(propertyName);
        if (!isBlank(systemValue)) {
            return systemValue.trim();
        }
        String envValue = System.getenv(envName);
        if (!isBlank(envValue)) {
            return envValue.trim();
        }
        return defaultValue;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
