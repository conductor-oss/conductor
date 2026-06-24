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
package com.netflix.conductor.core.execution.tasks;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.conductoross.conductor.common.integrations.gdrive.GDriveConnection;
import org.conductoross.conductor.common.integrations.gdrive.GDriveIntegrationException;
import org.conductoross.conductor.common.integrations.gdrive.GDriveIntegrationService;
import org.conductoross.conductor.common.integrations.gdrive.GDriveLoadRequest;
import org.conductoross.conductor.common.integrations.gdrive.GDriveLoadResponse;
import org.conductoross.conductor.dao.GDriveConnectionDAO;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_GDRIVE_READ;

/**
 * System task that reads Google Drive file metadata through a stored account-level connection.
 * Folder and file restrictions are supplied as task inputs.
 */
@Component(TASK_TYPE_GDRIVE_READ)
public class GDriveRead extends WorkflowSystemTask {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapperProvider().getObjectMapper();
    private static final TypeReference<List<Object>> OBJECT_LIST_TYPE = new TypeReference<>() {};

    public static final String INPUT_CONNECTION_ID = "connectionId";
    public static final String INPUT_WORKFLOW_CONNECTION_ID = "gdriveConnectionId";
    public static final String INPUT_LEGACY_WORKFLOW_CONNECTION_ID = "gdriveconnectionId";
    public static final String INPUT_FOLDER_ID = "folderId";
    public static final String INPUT_LEGACY_FOLDER_ID = "driveFolderId";
    public static final String INPUT_FOLDER_IDS = "folderIds";
    public static final String INPUT_LEGACY_FOLDER_IDS = "driveFolderIds";
    public static final String INPUT_FILE_IDS = "fileIds";
    public static final String INPUT_LEGACY_FILE_IDS = "driveFileIds";
    public static final String INPUT_OAUTH_TOKEN_JSON = "oauthTokenJson";
    public static final String INPUT_MAX_FILES = "maxFiles";
    public static final String INPUT_MIME_TYPES = "mimeTypes";
    public static final String OUTPUT_CONNECTION_ID = "connectionId";
    public static final String OUTPUT_FOLDER_ID = "folderId";
    public static final String OUTPUT_FOLDER_IDS = "folderIds";
    public static final String OUTPUT_FILE_IDS = "fileIds";
    public static final String OUTPUT_FILES = "files";
    public static final String OUTPUT_COUNT = "count";
    public static final String OUTPUT_ERROR = "error";

    private final GDriveIntegrationService gDriveIntegrationService;
    private final GDriveConnectionDAO gDriveConnectionDAO;

    public GDriveRead(
            GDriveIntegrationService gDriveIntegrationService,
            GDriveConnectionDAO gDriveConnectionDAO) {
        super(TASK_TYPE_GDRIVE_READ);
        this.gDriveIntegrationService = gDriveIntegrationService;
        this.gDriveConnectionDAO = gDriveConnectionDAO;
    }

    @Override
    public boolean execute(
            WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        try {
            GDriveLoadRequest request = toGDriveLoadRequest(workflow, task.getInputData());
            GDriveLoadResponse response = gDriveIntegrationService.loadFolder(request);
            String connectionId = request.getConnectionId();
            task.addOutput(
                    OUTPUT_CONNECTION_ID, isBlank(connectionId) ? null : connectionId.trim());
            task.addOutput(OUTPUT_FOLDER_ID, response.getFolderId());
            task.addOutput(OUTPUT_FOLDER_IDS, response.getFolderIds());
            task.addOutput(OUTPUT_FILE_IDS, response.getFileIds());
            task.addOutput(OUTPUT_FILES, response.getFiles());
            task.addOutput(OUTPUT_COUNT, response.getCount());
            task.setStatus(TaskModel.Status.COMPLETED);
        } catch (IllegalArgumentException e) {
            task.setStatus(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR);
            task.setReasonForIncompletion(e.getMessage());
            task.addOutput(OUTPUT_ERROR, e.getMessage());
        } catch (GDriveIntegrationException e) {
            task.setStatus(TaskModel.Status.FAILED);
            task.setReasonForIncompletion(e.getMessage());
            task.addOutput(OUTPUT_ERROR, e.getMessage());
        } catch (Exception e) {
            task.setStatus(TaskModel.Status.FAILED);
            task.setReasonForIncompletion(e.getMessage());
            task.addOutput(OUTPUT_ERROR, e.getMessage());
        }

        return true;
    }

    private GDriveLoadRequest toGDriveLoadRequest(
            WorkflowModel workflow, Map<String, Object> input) {
        String connectionId = stringInput(input, INPUT_CONNECTION_ID);
        if (isBlank(connectionId)) {
            connectionId = workflowInput(workflow, INPUT_WORKFLOW_CONNECTION_ID);
        }
        if (isBlank(connectionId)) {
            connectionId = workflowInput(workflow, INPUT_LEGACY_WORKFLOW_CONNECTION_ID);
        }
        String folderId = stringInput(input, INPUT_FOLDER_ID);
        if (isBlank(folderId)) {
            folderId = stringInput(input, INPUT_LEGACY_FOLDER_ID);
        }
        List<String> folderIds = stringListInput(input.get(INPUT_FOLDER_IDS));
        if (folderIds.isEmpty()) {
            folderIds = stringListInput(input.get(INPUT_LEGACY_FOLDER_IDS));
        }
        List<String> fileIds = stringListInput(input.get(INPUT_FILE_IDS));
        if (fileIds.isEmpty()) {
            fileIds = stringListInput(input.get(INPUT_LEGACY_FILE_IDS));
        }
        String oauthTokenJson = stringInput(input, INPUT_OAUTH_TOKEN_JSON);
        GDriveConnection connection = resolveConnection(connectionId, oauthTokenJson);
        if (connection != null) {
            connectionId = connection.getConnectionId();
            oauthTokenJson = connection.getOauthTokenJson();
        }

        if (isBlank(oauthTokenJson)) {
            throw new IllegalArgumentException(
                    "connectionId input is required or create a Google Drive connection in the UI");
        }

        GDriveLoadRequest request = new GDriveLoadRequest();
        request.setConnectionId(isBlank(connectionId) ? null : connectionId.trim());
        request.setFolderId(folderId);
        request.setFolderIds(folderIds);
        request.setFileIds(fileIds);
        request.setOauthTokenJson(oauthTokenJson);
        request.setMaxFiles(integerInput(input.get(INPUT_MAX_FILES)));
        request.setMimeTypes(stringListInput(input.get(INPUT_MIME_TYPES)));
        return request;
    }

    private GDriveConnection resolveConnection(String connectionId, String oauthTokenJson) {
        if (isBlank(connectionId)) {
            if (!isBlank(oauthTokenJson)) {
                return null;
            }
            return latestConnection();
        }

        String normalizedConnectionId = connectionId.trim();
        GDriveConnection connection = gDriveConnectionDAO.getConnection(normalizedConnectionId);
        if (connection == null) {
            throw new IllegalArgumentException(
                    "No Google Drive connection found for connectionId " + normalizedConnectionId);
        }
        return connection;
    }

    private GDriveConnection latestConnection() {
        return gDriveConnectionDAO.getAllConnections().stream()
                .filter(Objects::nonNull)
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

    private String stringInput(Map<String, Object> input, String key) {
        Object value = input.get(key);
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return isUnresolvedInputExpression(text.trim()) ? null : text;
    }

    private String workflowInput(WorkflowModel workflow, String key) {
        if (workflow == null || workflow.getInput() == null) {
            return null;
        }
        return stringInput(workflow.getInput(), key);
    }

    private Integer integerInput(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String && !isBlank((String) value)) {
            return Integer.valueOf((String) value);
        }
        return null;
    }

    private List<String> stringListInput(Object value) {
        if (value instanceof List<?>) {
            return normalizeStringList((List<?>) value);
        }
        if (value instanceof String && !isBlank((String) value)) {
            String text = ((String) value).trim();
            if (isUnresolvedInputExpression(text) || "null".equalsIgnoreCase(text)) {
                return Collections.emptyList();
            }
            if (text.startsWith("[") && text.endsWith("]")) {
                try {
                    return normalizeStringList(OBJECT_MAPPER.readValue(text, OBJECT_LIST_TYPE));
                } catch (Exception ignored) {
                    // Fall back to delimiter parsing so malformed input still gets a clear ID
                    // error.
                }
            }
            return Arrays.stream(text.split("[,\\s]+"))
                    .map(String::trim)
                    .filter(item -> !isBlank(item))
                    .filter(item -> !isUnresolvedInputExpression(item))
                    .filter(item -> !"null".equalsIgnoreCase(item))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private List<String> normalizeStringList(List<?> values) {
        return values.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .map(String::trim)
                .filter(text -> !isBlank(text))
                .filter(text -> !isUnresolvedInputExpression(text))
                .filter(text -> !"null".equalsIgnoreCase(text))
                .collect(Collectors.toList());
    }

    private boolean isUnresolvedInputExpression(String value) {
        return value.startsWith("${") && value.endsWith("}");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
