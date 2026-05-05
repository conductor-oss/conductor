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
package org.conductoross.conductor.postgres.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.conductoross.conductor.dao.FileMetadataDAO;
import org.conductoross.conductor.model.FileModel;
import org.conductoross.conductor.model.file.FileUploadStatus;
import org.conductoross.conductor.model.file.StorageType;
import org.springframework.retry.support.RetryTemplate;

import com.netflix.conductor.postgres.dao.PostgresBaseDAO;

import com.fasterxml.jackson.databind.ObjectMapper;

/** PostgreSQL {@link FileMetadataDAO} — table {@code file_metadata}. */
public class PostgresFileMetadataDAO extends PostgresBaseDAO implements FileMetadataDAO {

    private static final String INSERT_FILE =
            "INSERT INTO file_metadata (file_id, file_name, content_type, "
                    + "storage_type, storage_path, upload_status, workflow_id, task_id, "
                    + "created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String SELECT_BY_ID = "SELECT * FROM file_metadata WHERE file_id = ?";

    private static final String UPDATE_STATUS =
            "UPDATE file_metadata SET upload_status = ?, updated_at = CURRENT_TIMESTAMP "
                    + "WHERE file_id = ?";

    private static final String UPDATE_UPLOAD_COMPLETE =
            "UPDATE file_metadata SET upload_status = ?, storage_content_hash = ?, "
                    + "storage_content_size = ?, updated_at = CURRENT_TIMESTAMP "
                    + "WHERE file_id = ?";

    private static final String SELECT_BY_WORKFLOW =
            "SELECT * FROM file_metadata WHERE workflow_id = ?";

    private static final String SELECT_BY_TASK = "SELECT * FROM file_metadata WHERE task_id = ?";

    public PostgresFileMetadataDAO(
            RetryTemplate retryTemplate, ObjectMapper objectMapper, DataSource dataSource) {
        super(retryTemplate, objectMapper, dataSource);
    }

    @Override
    public void createFileMetadata(FileModel fileModel) {
        executeWithTransaction(
                INSERT_FILE,
                q ->
                        q.addParameter(fileModel.getFileId())
                                .addParameter(fileModel.getFileName())
                                .addParameter(fileModel.getContentType())
                                .addParameter(fileModel.getStorageType().name())
                                .addParameter(fileModel.getStoragePath())
                                .addParameter(fileModel.getUploadStatus().name())
                                .addParameter(fileModel.getWorkflowId())
                                .addParameter(fileModel.getTaskId())
                                .addParameter(Timestamp.from(fileModel.getCreatedAt()))
                                .addParameter(Timestamp.from(fileModel.getUpdatedAt()))
                                .executeUpdate());
    }

    @Override
    public FileModel getFileMetadata(String fileId) {
        return queryWithTransaction(
                SELECT_BY_ID,
                q ->
                        q.addParameter(fileId)
                                .executeAndFetch(
                                        rs -> {
                                            if (!rs.next()) return null;
                                            return toFileModel(rs);
                                        }));
    }

    @Override
    public void updateUploadStatus(String fileId, FileUploadStatus status) {
        executeWithTransaction(
                UPDATE_STATUS,
                q -> q.addParameter(status.name()).addParameter(fileId).executeUpdate());
    }

    @Override
    public void updateUploadComplete(
            String fileId, FileUploadStatus status, String contentHash, long contentSize) {
        executeWithTransaction(
                UPDATE_UPLOAD_COMPLETE,
                q ->
                        q.addParameter(status.name())
                                .addParameter(contentHash)
                                .addParameter(contentSize)
                                .addParameter(fileId)
                                .executeUpdate());
    }

    @Override
    public List<FileModel> getFilesByWorkflowId(String workflowId) {
        return queryWithTransaction(
                SELECT_BY_WORKFLOW,
                q -> q.addParameter(workflowId).executeAndFetch(this::toFileModelList));
    }

    @Override
    public List<FileModel> getFilesByTaskId(String taskId) {
        return queryWithTransaction(
                SELECT_BY_TASK, q -> q.addParameter(taskId).executeAndFetch(this::toFileModelList));
    }

    private List<FileModel> toFileModelList(ResultSet rs) throws SQLException {
        List<FileModel> list = new ArrayList<>();
        while (rs.next()) {
            list.add(toFileModel(rs));
        }
        return list;
    }

    private FileModel toFileModel(ResultSet rs) throws SQLException {
        FileModel model = new FileModel();
        model.setFileId(rs.getString("file_id"));
        model.setFileName(rs.getString("file_name"));
        model.setContentType(rs.getString("content_type"));
        model.setStorageContentHash(rs.getString("storage_content_hash"));
        long scs = rs.getLong("storage_content_size");
        model.setStorageContentSize(rs.wasNull() ? 0 : scs);
        model.setStorageType(StorageType.valueOf(rs.getString("storage_type")));
        model.setStoragePath(rs.getString("storage_path"));
        model.setUploadStatus(FileUploadStatus.valueOf(rs.getString("upload_status")));
        model.setWorkflowId(rs.getString("workflow_id"));
        model.setTaskId(rs.getString("task_id"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) model.setCreatedAt(createdAt.toInstant());
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) model.setUpdatedAt(updatedAt.toInstant());
        return model;
    }
}
