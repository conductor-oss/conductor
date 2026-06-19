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

import java.util.List;
import java.util.Map;

import org.conductoross.conductor.common.integrations.gdrive.GDriveFile;
import org.conductoross.conductor.common.integrations.gdrive.GDriveIntegrationException;
import org.conductoross.conductor.common.integrations.gdrive.GDriveIntegrationService;
import org.conductoross.conductor.common.integrations.gdrive.GDriveLoadRequest;
import org.conductoross.conductor.common.integrations.gdrive.GDriveLoadResponse;
import org.junit.Test;

import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class GDriveReadTest {

    @Test
    public void executeCompletesWithDriveFiles() {
        GDriveFile file = new GDriveFile();
        file.setId("file-1");
        file.setName("report.pdf");
        GDriveLoadResponse response = new GDriveLoadResponse("folder-123", List.of(file));
        RecordingGDriveIntegrationService service =
                new RecordingGDriveIntegrationService(response, null);
        GDriveRead gDriveRead = new GDriveRead(service);

        TaskModel task =
                taskWithInput(
                        Map.of(
                                GDriveRead.INPUT_FOLDER_ID,
                                "folder-123",
                                GDriveRead.INPUT_OAUTH_TOKEN_JSON,
                                "{\"refresh_token\":\"token\"}",
                                GDriveRead.INPUT_MAX_FILES,
                                "10",
                                GDriveRead.INPUT_MIME_TYPES,
                                List.of("application/pdf")));

        boolean progressed =
                gDriveRead.execute(new WorkflowModel(), task, mock(WorkflowExecutor.class));

        assertTrue(progressed);
        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals("folder-123", service.lastRequest.getFolderId());
        assertEquals("{\"refresh_token\":\"token\"}", service.lastRequest.getOauthTokenJson());
        assertEquals(Integer.valueOf(10), service.lastRequest.getMaxFiles());
        assertEquals(List.of("application/pdf"), service.lastRequest.getMimeTypes());
        assertEquals("folder-123", task.getOutputData().get(GDriveRead.OUTPUT_FOLDER_ID));
        assertSame(response.getFiles(), task.getOutputData().get(GDriveRead.OUTPUT_FILES));
        assertEquals(1, task.getOutputData().get(GDriveRead.OUTPUT_COUNT));
    }

    @Test
    public void executeAcceptsLegacyDriveFolderIdInput() {
        RecordingGDriveIntegrationService service =
                new RecordingGDriveIntegrationService(
                        new GDriveLoadResponse("legacy", List.of()), null);
        GDriveRead gDriveRead = new GDriveRead(service);

        TaskModel task =
                taskWithInput(
                        Map.of(
                                GDriveRead.INPUT_LEGACY_FOLDER_ID,
                                "legacy",
                                GDriveRead.INPUT_OAUTH_TOKEN_JSON,
                                "{\"refresh_token\":\"token\"}"));

        gDriveRead.execute(new WorkflowModel(), task, mock(WorkflowExecutor.class));

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals("legacy", service.lastRequest.getFolderId());
    }

    @Test
    public void executeMissingRequiredInputFailsWithTerminalError() {
        GDriveRead gDriveRead = new GDriveRead(new RecordingGDriveIntegrationService(null, null));

        TaskModel task = taskWithInput(Map.of(GDriveRead.INPUT_FOLDER_ID, "folder-123"));

        gDriveRead.execute(new WorkflowModel(), task, mock(WorkflowExecutor.class));

        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, task.getStatus());
        assertEquals("oauthTokenJson input is required", task.getReasonForIncompletion());
        assertEquals(
                "oauthTokenJson input is required",
                task.getOutputData().get(GDriveRead.OUTPUT_ERROR));
    }

    @Test
    public void executeIntegrationFailureIsRetriableFailure() {
        GDriveRead gDriveRead =
                new GDriveRead(
                        new RecordingGDriveIntegrationService(
                                null, new GDriveIntegrationException("Drive request failed")));

        TaskModel task =
                taskWithInput(
                        Map.of(
                                GDriveRead.INPUT_FOLDER_ID,
                                "folder-123",
                                GDriveRead.INPUT_OAUTH_TOKEN_JSON,
                                "{\"refresh_token\":\"token\"}"));

        gDriveRead.execute(new WorkflowModel(), task, mock(WorkflowExecutor.class));

        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertEquals("Drive request failed", task.getReasonForIncompletion());
        assertEquals("Drive request failed", task.getOutputData().get(GDriveRead.OUTPUT_ERROR));
    }

    private TaskModel taskWithInput(Map<String, Object> input) {
        TaskModel taskModel = new TaskModel();
        taskModel.setInputData(input);
        return taskModel;
    }

    private static class RecordingGDriveIntegrationService extends GDriveIntegrationService {

        private final GDriveLoadResponse response;
        private final GDriveIntegrationException exception;
        private GDriveLoadRequest lastRequest;

        RecordingGDriveIntegrationService(
                GDriveLoadResponse response, GDriveIntegrationException exception) {
            this.response = response;
            this.exception = exception;
        }

        @Override
        public GDriveLoadResponse loadFolder(GDriveLoadRequest request) {
            lastRequest = request;
            if (exception != null) {
                throw exception;
            }
            return response;
        }
    }
}
