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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.conductoross.conductor.common.integrations.gdrive.GDriveIntegrationException;
import org.conductoross.conductor.common.integrations.gdrive.GDriveIntegrationService;
import org.conductoross.conductor.common.integrations.gdrive.GDriveLoadRequest;
import org.conductoross.conductor.common.integrations.gdrive.GDriveLoadResponse;
import org.springframework.stereotype.Component;

import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_GDRIVE_READ;

/**
 * System task that reads Google Drive folder metadata with an OAuth token generated through the
 * integrations UI or supplied directly in workflow input.
 */
@Component(TASK_TYPE_GDRIVE_READ)
public class GDriveRead extends WorkflowSystemTask {

    public static final String INPUT_FOLDER_ID = "folderId";
    public static final String INPUT_LEGACY_FOLDER_ID = "driveFolderId";
    public static final String INPUT_OAUTH_TOKEN_JSON = "oauthTokenJson";
    public static final String INPUT_MAX_FILES = "maxFiles";
    public static final String INPUT_MIME_TYPES = "mimeTypes";
    public static final String OUTPUT_FOLDER_ID = "folderId";
    public static final String OUTPUT_FILES = "files";
    public static final String OUTPUT_COUNT = "count";
    public static final String OUTPUT_ERROR = "error";

    private final GDriveIntegrationService gDriveIntegrationService;

    public GDriveRead(GDriveIntegrationService gDriveIntegrationService) {
        super(TASK_TYPE_GDRIVE_READ);
        this.gDriveIntegrationService = gDriveIntegrationService;
    }

    @Override
    public boolean execute(
            WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        try {
            GDriveLoadResponse response =
                    gDriveIntegrationService.loadFolder(toGDriveLoadRequest(task.getInputData()));
            task.addOutput(OUTPUT_FOLDER_ID, response.getFolderId());
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

    private GDriveLoadRequest toGDriveLoadRequest(Map<String, Object> input) {
        String folderId = stringInput(input, INPUT_FOLDER_ID);
        if (isBlank(folderId)) {
            folderId = stringInput(input, INPUT_LEGACY_FOLDER_ID);
        }
        String oauthTokenJson = stringInput(input, INPUT_OAUTH_TOKEN_JSON);

        if (isBlank(folderId)) {
            throw new IllegalArgumentException("folderId input is required");
        }
        if (isBlank(oauthTokenJson)) {
            throw new IllegalArgumentException("oauthTokenJson input is required");
        }

        GDriveLoadRequest request = new GDriveLoadRequest();
        request.setFolderId(folderId);
        request.setOauthTokenJson(oauthTokenJson);
        request.setMaxFiles(integerInput(input.get(INPUT_MAX_FILES)));
        request.setMimeTypes(stringListInput(input.get(INPUT_MIME_TYPES)));
        return request;
    }

    private String stringInput(Map<String, Object> input, String key) {
        Object value = input.get(key);
        return value == null ? null : value.toString();
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
            return ((List<?>) value)
                    .stream()
                            .filter(Objects::nonNull)
                            .map(Object::toString)
                            .filter(text -> !isBlank(text))
                            .collect(Collectors.toList());
        }
        if (value instanceof String && !isBlank((String) value)) {
            return Arrays.stream(((String) value).split(","))
                    .map(String::trim)
                    .filter(text -> !isBlank(text))
                    .collect(Collectors.toList());
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
