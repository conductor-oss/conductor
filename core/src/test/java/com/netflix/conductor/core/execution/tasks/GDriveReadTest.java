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

import org.conductoross.conductor.common.integrations.gdrive.GDriveConnection;
import org.conductoross.conductor.common.integrations.gdrive.GDriveFile;
import org.conductoross.conductor.common.integrations.gdrive.GDriveIntegrationException;
import org.conductoross.conductor.common.integrations.gdrive.GDriveIntegrationService;
import org.conductoross.conductor.common.integrations.gdrive.GDriveLoadRequest;
import org.conductoross.conductor.common.integrations.gdrive.GDriveLoadResponse;
import org.conductoross.conductor.core.dao.InMemoryGDriveConnectionDAO;
import org.junit.Test;

import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
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
        InMemoryGDriveConnectionDAO gDriveConnectionDAO =
                daoWithConnection("gdrive-prod", "{\"refresh_token\":\"token\"}");
        GDriveRead gDriveRead = new GDriveRead(service, gDriveConnectionDAO);

        TaskModel task =
                taskWithInput(
                        Map.of(
                                GDriveRead.INPUT_CONNECTION_ID,
                                "gdrive-prod",
                                GDriveRead.INPUT_FOLDER_ID,
                                "folder-123",
                                GDriveRead.INPUT_MAX_FILES,
                                "10",
                                GDriveRead.INPUT_MIME_TYPES,
                                List.of("application/pdf")));

        boolean progressed =
                gDriveRead.execute(new WorkflowModel(), task, mock(WorkflowExecutor.class));

        assertTrue(progressed);
        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals("gdrive-prod", service.lastRequest.getConnectionId());
        assertEquals("folder-123", service.lastRequest.getFolderId());
        assertEquals("{\"refresh_token\":\"token\"}", service.lastRequest.getOauthTokenJson());
        assertEquals(Integer.valueOf(10), service.lastRequest.getMaxFiles());
        assertEquals(List.of("application/pdf"), service.lastRequest.getMimeTypes());
        assertEquals("gdrive-prod", task.getOutputData().get(GDriveRead.OUTPUT_CONNECTION_ID));
        assertEquals("folder-123", task.getOutputData().get(GDriveRead.OUTPUT_FOLDER_ID));
        assertEquals(List.of("folder-123"), task.getOutputData().get(GDriveRead.OUTPUT_FOLDER_IDS));
        assertEquals(List.of(), task.getOutputData().get(GDriveRead.OUTPUT_FILE_IDS));
        assertSame(response.getFiles(), task.getOutputData().get(GDriveRead.OUTPUT_FILES));
        assertEquals(1, task.getOutputData().get(GDriveRead.OUTPUT_COUNT));
    }

    @Test
    public void executeAcceptsFolderAndFileListInputs() {
        RecordingGDriveIntegrationService service =
                new RecordingGDriveIntegrationService(
                        new GDriveLoadResponse(
                                List.of("folder-1", "folder-2"),
                                List.of("file-1", "file-2"),
                                List.of()),
                        null);
        GDriveRead gDriveRead =
                new GDriveRead(
                        service, daoWithConnection("gdrive-prod", "{\"refresh_token\":\"token\"}"));

        TaskModel task =
                taskWithInput(
                        Map.of(
                                GDriveRead.INPUT_CONNECTION_ID,
                                "gdrive-prod",
                                GDriveRead.INPUT_FOLDER_IDS,
                                "folder-1\nfolder-2",
                                GDriveRead.INPUT_FILE_IDS,
                                List.of("file-1", "file-2")));

        gDriveRead.execute(new WorkflowModel(), task, mock(WorkflowExecutor.class));

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals(List.of("folder-1", "folder-2"), service.lastRequest.getFolderIds());
        assertEquals(List.of("file-1", "file-2"), service.lastRequest.getFileIds());
        assertEquals(
                List.of("folder-1", "folder-2"),
                task.getOutputData().get(GDriveRead.OUTPUT_FOLDER_IDS));
        assertEquals(
                List.of("file-1", "file-2"), task.getOutputData().get(GDriveRead.OUTPUT_FILE_IDS));
    }

    @Test
    public void executeAllowsAccountWideLoadWithConnectionOnly() {
        RecordingGDriveIntegrationService service =
                new RecordingGDriveIntegrationService(
                        new GDriveLoadResponse(List.of(), List.of(), List.of()), null);
        GDriveRead gDriveRead =
                new GDriveRead(
                        service, daoWithConnection("gdrive-prod", "{\"refresh_token\":\"token\"}"));

        TaskModel task = taskWithInput(Map.of(GDriveRead.INPUT_CONNECTION_ID, "gdrive-prod"));

        gDriveRead.execute(new WorkflowModel(), task, mock(WorkflowExecutor.class));

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertNull(service.lastRequest.getFolderId());
        assertEquals(List.of(), service.lastRequest.getFolderIds());
        assertEquals(List.of(), service.lastRequest.getFileIds());
        assertEquals(List.of(), task.getOutputData().get(GDriveRead.OUTPUT_FOLDER_IDS));
        assertEquals(List.of(), task.getOutputData().get(GDriveRead.OUTPUT_FILE_IDS));
    }

    @Test
    public void executeUsesLatestStoredConnectionWhenConnectionIdIsMissing() {
        RecordingGDriveIntegrationService service =
                new RecordingGDriveIntegrationService(
                        new GDriveLoadResponse("folder-123", List.of()), null);
        GDriveRead gDriveRead =
                new GDriveRead(
                        service, daoWithConnection("gdrive-prod", "{\"refresh_token\":\"token\"}"));

        TaskModel task = taskWithInput(Map.of(GDriveRead.INPUT_FOLDER_ID, "folder-123"));

        gDriveRead.execute(new WorkflowModel(), task, mock(WorkflowExecutor.class));

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals("gdrive-prod", service.lastRequest.getConnectionId());
        assertEquals("{\"refresh_token\":\"token\"}", service.lastRequest.getOauthTokenJson());
        assertEquals("gdrive-prod", task.getOutputData().get(GDriveRead.OUTPUT_CONNECTION_ID));
    }

    @Test
    public void executeUsesWorkflowConnectionIdWhenTaskInputIsUnresolved() {
        RecordingGDriveIntegrationService service =
                new RecordingGDriveIntegrationService(
                        new GDriveLoadResponse(List.of(), List.of(), List.of()), null);
        GDriveRead gDriveRead =
                new GDriveRead(
                        service,
                        daoWithConnection(
                                "gdrive-856b8377-c9fe-43c3-a7b1-b282c729ec81",
                                "{\"refresh_token\":\"token\"}"));
        WorkflowModel workflow = new WorkflowModel();
        workflow.setInput(
                Map.of(
                        GDriveRead.INPUT_LEGACY_WORKFLOW_CONNECTION_ID,
                        "gdrive-856b8377-c9fe-43c3-a7b1-b282c729ec81"));
        TaskModel task =
                taskWithInput(
                        Map.of(
                                GDriveRead.INPUT_CONNECTION_ID,
                                "${workflow.input.gdriveConnectionId}"));

        gDriveRead.execute(workflow, task, mock(WorkflowExecutor.class));

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals(
                "gdrive-856b8377-c9fe-43c3-a7b1-b282c729ec81",
                service.lastRequest.getConnectionId());
    }

    @Test
    public void executeDoesNotUseUnresolvedConnectionIdAsDaoLookupKey() {
        GDriveRead gDriveRead =
                new GDriveRead(
                        new RecordingGDriveIntegrationService(null, null),
                        new InMemoryGDriveConnectionDAO());
        TaskModel task =
                taskWithInput(
                        Map.of(
                                GDriveRead.INPUT_CONNECTION_ID,
                                "${workflow.input.gdriveConnectionId}"));

        gDriveRead.execute(new WorkflowModel(), task, mock(WorkflowExecutor.class));

        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, task.getStatus());
        assertEquals(
                "connectionId input is required or create a Google Drive connection in the UI",
                task.getReasonForIncompletion());
    }

    @Test
    public void executeAcceptsJsonArrayStringListInputs() {
        RecordingGDriveIntegrationService service =
                new RecordingGDriveIntegrationService(
                        new GDriveLoadResponse(
                                List.of("folder-1", "folder-2"),
                                List.of("file-1", "file-2"),
                                List.of()),
                        null);
        GDriveRead gDriveRead =
                new GDriveRead(
                        service, daoWithConnection("gdrive-prod", "{\"refresh_token\":\"token\"}"));

        TaskModel task =
                taskWithInput(
                        Map.of(
                                GDriveRead.INPUT_CONNECTION_ID,
                                "gdrive-prod",
                                GDriveRead.INPUT_FOLDER_IDS,
                                "[\"folder-1\", \"folder-2\"]",
                                GDriveRead.INPUT_FILE_IDS,
                                "[\"file-1\", \"file-2\"]"));

        gDriveRead.execute(new WorkflowModel(), task, mock(WorkflowExecutor.class));

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals(List.of("folder-1", "folder-2"), service.lastRequest.getFolderIds());
        assertEquals(List.of("file-1", "file-2"), service.lastRequest.getFileIds());
    }

    @Test
    public void executeAcceptsLegacyDriveFolderAndFileListInputs() {
        RecordingGDriveIntegrationService service =
                new RecordingGDriveIntegrationService(
                        new GDriveLoadResponse(
                                List.of("folder-legacy"), List.of("file-legacy"), List.of()),
                        null);
        GDriveRead gDriveRead =
                new GDriveRead(
                        service, daoWithConnection("gdrive-prod", "{\"refresh_token\":\"token\"}"));

        TaskModel task =
                taskWithInput(
                        Map.of(
                                GDriveRead.INPUT_CONNECTION_ID,
                                "gdrive-prod",
                                GDriveRead.INPUT_LEGACY_FOLDER_IDS,
                                List.of("folder-legacy"),
                                GDriveRead.INPUT_LEGACY_FILE_IDS,
                                List.of("file-legacy")));

        gDriveRead.execute(new WorkflowModel(), task, mock(WorkflowExecutor.class));

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals(List.of("folder-legacy"), service.lastRequest.getFolderIds());
        assertEquals(List.of("file-legacy"), service.lastRequest.getFileIds());
    }

    @Test
    public void executeIgnoresUnresolvedOptionalListExpressions() {
        RecordingGDriveIntegrationService service =
                new RecordingGDriveIntegrationService(
                        new GDriveLoadResponse(List.of(), List.of(), List.of()), null);
        GDriveRead gDriveRead =
                new GDriveRead(
                        service, daoWithConnection("gdrive-prod", "{\"refresh_token\":\"token\"}"));

        TaskModel task =
                taskWithInput(
                        Map.of(
                                GDriveRead.INPUT_CONNECTION_ID,
                                "gdrive-prod",
                                GDriveRead.INPUT_FOLDER_IDS,
                                "${workflow.input.folderIds}",
                                GDriveRead.INPUT_FILE_IDS,
                                "${workflow.input.fileIds}"));

        gDriveRead.execute(new WorkflowModel(), task, mock(WorkflowExecutor.class));

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals(List.of(), service.lastRequest.getFolderIds());
        assertEquals(List.of(), service.lastRequest.getFileIds());
    }

    @Test
    public void executeAcceptsLegacyDriveFolderIdInput() {
        RecordingGDriveIntegrationService service =
                new RecordingGDriveIntegrationService(
                        new GDriveLoadResponse("legacy", List.of()), null);
        GDriveRead gDriveRead =
                new GDriveRead(
                        service, daoWithConnection("gdrive-prod", "{\"refresh_token\":\"token\"}"));

        TaskModel task =
                taskWithInput(
                        Map.of(
                                GDriveRead.INPUT_CONNECTION_ID,
                                "gdrive-prod",
                                GDriveRead.INPUT_LEGACY_FOLDER_ID,
                                "legacy"));

        gDriveRead.execute(new WorkflowModel(), task, mock(WorkflowExecutor.class));

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals("legacy", service.lastRequest.getFolderId());
    }

    @Test
    public void executeAcceptsLegacyOAuthTokenJsonInput() {
        RecordingGDriveIntegrationService service =
                new RecordingGDriveIntegrationService(
                        new GDriveLoadResponse("folder-123", List.of()), null);
        GDriveRead gDriveRead = new GDriveRead(service, new InMemoryGDriveConnectionDAO());

        TaskModel task =
                taskWithInput(
                        Map.of(
                                GDriveRead.INPUT_FOLDER_ID,
                                "folder-123",
                                GDriveRead.INPUT_OAUTH_TOKEN_JSON,
                                "{\"refresh_token\":\"token\"}"));

        gDriveRead.execute(new WorkflowModel(), task, mock(WorkflowExecutor.class));

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals("{\"refresh_token\":\"token\"}", service.lastRequest.getOauthTokenJson());
    }

    @Test
    public void executeMissingRequiredInputFailsWithTerminalError() {
        GDriveRead gDriveRead =
                new GDriveRead(
                        new RecordingGDriveIntegrationService(null, null),
                        new InMemoryGDriveConnectionDAO());

        TaskModel task = taskWithInput(Map.of(GDriveRead.INPUT_FOLDER_ID, "folder-123"));

        gDriveRead.execute(new WorkflowModel(), task, mock(WorkflowExecutor.class));

        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, task.getStatus());
        assertEquals(
                "connectionId input is required or create a Google Drive connection in the UI",
                task.getReasonForIncompletion());
        assertEquals(
                "connectionId input is required or create a Google Drive connection in the UI",
                task.getOutputData().get(GDriveRead.OUTPUT_ERROR));
    }

    @Test
    public void executeUnknownConnectionFailsWithTerminalError() {
        GDriveRead gDriveRead =
                new GDriveRead(
                        new RecordingGDriveIntegrationService(null, null),
                        new InMemoryGDriveConnectionDAO());

        TaskModel task =
                taskWithInput(
                        Map.of(
                                GDriveRead.INPUT_CONNECTION_ID,
                                "missing",
                                GDriveRead.INPUT_FOLDER_ID,
                                "folder-123"));

        gDriveRead.execute(new WorkflowModel(), task, mock(WorkflowExecutor.class));

        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, task.getStatus());
        assertEquals(
                "No Google Drive connection found for connectionId missing",
                task.getReasonForIncompletion());
    }

    @Test
    public void executeIntegrationFailureIsRetriableFailure() {
        GDriveRead gDriveRead =
                new GDriveRead(
                        new RecordingGDriveIntegrationService(
                                null, new GDriveIntegrationException("Drive request failed")),
                        daoWithConnection("gdrive-prod", "{\"refresh_token\":\"token\"}"));

        TaskModel task =
                taskWithInput(
                        Map.of(
                                GDriveRead.INPUT_CONNECTION_ID,
                                "gdrive-prod",
                                GDriveRead.INPUT_FOLDER_ID,
                                "folder-123"));

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

    private InMemoryGDriveConnectionDAO daoWithConnection(
            String connectionId, String oauthTokenJson) {
        InMemoryGDriveConnectionDAO gDriveConnectionDAO = new InMemoryGDriveConnectionDAO();
        gDriveConnectionDAO.saveConnection(new GDriveConnection(connectionId, oauthTokenJson));
        return gDriveConnectionDAO;
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
