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
package org.conductoross.conductor.core.storage;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.conductoross.conductor.dao.FileMetadataDAO;
import org.conductoross.conductor.model.FileModel;
import org.conductoross.conductor.model.file.FileUploadStatus;

public class StubFileMetadataDAO implements FileMetadataDAO {

    private final Map<String, FileModel> store = new ConcurrentHashMap<>();

    @Override
    public void createFileMetadata(FileModel fileModel) {
        store.put(fileModel.getFileId(), fileModel);
    }

    @Override
    public FileModel getFileMetadata(String fileId) {
        return store.get(fileId);
    }

    @Override
    public void updateUploadStatus(String fileId, FileUploadStatus status) {
        FileModel model = store.get(fileId);
        if (model != null) {
            model.setUploadStatus(status);
            model.setUpdatedAt(Instant.now());
        }
    }

    @Override
    public void updateUploadComplete(
            String fileId, FileUploadStatus status, String contentHash, long contentSize) {
        FileModel model = store.get(fileId);
        if (model != null) {
            model.setUploadStatus(status);
            model.setStorageContentHash(contentHash);
            model.setStorageContentSize(contentSize);
            model.setUpdatedAt(Instant.now());
        }
    }

    @Override
    public List<FileModel> getFilesByWorkflowId(String workflowId) {
        return store.values().stream()
                .filter(m -> workflowId.equals(m.getWorkflowId()))
                .collect(Collectors.toList());
    }

    @Override
    public List<FileModel> getFilesByTaskId(String taskId) {
        return store.values().stream()
                .filter(m -> taskId.equals(m.getTaskId()))
                .collect(Collectors.toList());
    }
}
