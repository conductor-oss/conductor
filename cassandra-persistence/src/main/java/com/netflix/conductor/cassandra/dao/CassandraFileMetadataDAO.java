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
package com.netflix.conductor.cassandra.dao;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.conductoross.conductor.dao.FileMetadataDAO;
import org.conductoross.conductor.model.FileModel;
import org.conductoross.conductor.model.file.FileUploadStatus;

import com.netflix.conductor.cassandra.config.CassandraProperties;

import com.datastax.driver.core.*;
import tools.jackson.databind.ObjectMapper;

import static com.netflix.conductor.cassandra.util.Constants.TABLE_FILE_METADATA;
import static com.netflix.conductor.cassandra.util.Constants.TABLE_FILE_METADATA_BY_TASK;
import static com.netflix.conductor.cassandra.util.Constants.TABLE_FILE_METADATA_BY_WORKFLOW;

public class CassandraFileMetadataDAO extends CassandraBaseDAO implements FileMetadataDAO {

    private final PreparedStatement insertStmt;
    private final PreparedStatement insertByWorkflowStmt;
    private final PreparedStatement insertByTaskStmt;
    private final PreparedStatement selectByIdStmt;
    private final PreparedStatement selectIdsByWorkflowStmt;
    private final PreparedStatement selectIdsByTaskStmt;

    private final Session session;
    private final ConsistencyLevel readConsistency;
    private final ConsistencyLevel writeConsistency;

    public CassandraFileMetadataDAO(
            Session session, ObjectMapper objectMapper, CassandraProperties properties) {
        super(session, objectMapper, properties);
        this.session = session;
        this.readConsistency = properties.getReadConsistencyLevel();
        this.writeConsistency = properties.getWriteConsistencyLevel();

        insertStmt =
                session.prepare(
                                "INSERT INTO "
                                        + TABLE_FILE_METADATA
                                        + " (file_id, workflow_id, task_id, json_data)"
                                        + " VALUES (?, ?, ?, ?)")
                        .setConsistencyLevel(writeConsistency);

        insertByWorkflowStmt =
                session.prepare(
                                "INSERT INTO "
                                        + TABLE_FILE_METADATA_BY_WORKFLOW
                                        + " (workflow_id, file_id) VALUES (?, ?)")
                        .setConsistencyLevel(writeConsistency);

        insertByTaskStmt =
                session.prepare(
                                "INSERT INTO "
                                        + TABLE_FILE_METADATA_BY_TASK
                                        + " (task_id, file_id) VALUES (?, ?)")
                        .setConsistencyLevel(writeConsistency);

        selectByIdStmt =
                session.prepare(
                                "SELECT json_data FROM "
                                        + TABLE_FILE_METADATA
                                        + " WHERE file_id = ?")
                        .setConsistencyLevel(readConsistency);

        selectIdsByWorkflowStmt =
                session.prepare(
                                "SELECT file_id FROM "
                                        + TABLE_FILE_METADATA_BY_WORKFLOW
                                        + " WHERE workflow_id = ?")
                        .setConsistencyLevel(readConsistency);

        selectIdsByTaskStmt =
                session.prepare(
                                "SELECT file_id FROM "
                                        + TABLE_FILE_METADATA_BY_TASK
                                        + " WHERE task_id = ?")
                        .setConsistencyLevel(readConsistency);
    }

    @Override
    public void createFileMetadata(FileModel fileModel) {
        session.execute(
                insertStmt.bind(
                        fileModel.getFileId(),
                        fileModel.getWorkflowId(),
                        fileModel.getTaskId(),
                        toJson(fileModel)));
        if (fileModel.getWorkflowId() != null) {
            session.execute(
                    insertByWorkflowStmt.bind(fileModel.getWorkflowId(), fileModel.getFileId()));
        }
        if (fileModel.getTaskId() != null) {
            session.execute(insertByTaskStmt.bind(fileModel.getTaskId(), fileModel.getFileId()));
        }
    }

    @Override
    public FileModel getFileMetadata(String fileId) {
        ResultSet rs = session.execute(selectByIdStmt.bind(fileId));
        Row row = rs.one();
        if (row == null) return null;
        return readValue(row.getString("json_data"), FileModel.class);
    }

    @Override
    public void updateUploadStatus(String fileId, FileUploadStatus status) {
        FileModel model = getFileMetadata(fileId);
        if (model == null) {
            return;
        }
        model.setUploadStatus(status);
        model.setUpdatedAt(Instant.now());
        session.execute(
                insertStmt.bind(fileId, model.getWorkflowId(), model.getTaskId(), toJson(model)));
    }

    @Override
    public void updateUploadComplete(
            String fileId, FileUploadStatus status, String contentHash, long contentSize) {
        FileModel model = getFileMetadata(fileId);
        if (model == null) {
            return;
        }
        model.setUploadStatus(status);
        model.setStorageContentHash(contentHash);
        model.setStorageContentSize(contentSize);
        model.setUpdatedAt(Instant.now());
        session.execute(
                insertStmt.bind(fileId, model.getWorkflowId(), model.getTaskId(), toJson(model)));
    }

    @Override
    public List<FileModel> getFilesByWorkflowId(String workflowId) {
        return getFileModels(session.execute(selectIdsByWorkflowStmt.bind(workflowId)));
    }

    @Override
    public List<FileModel> getFilesByTaskId(String taskId) {
        return getFileModels(session.execute(selectIdsByTaskStmt.bind(taskId)));
    }

    /**
     * The index tables hold only {@code file_id}, so each id is resolved against the main table. A
     * row present in an index but missing from the main table is skipped rather than failing the
     * whole lookup.
     */
    private List<FileModel> getFileModels(ResultSet rs) {
        List<FileModel> list = new ArrayList<>();
        for (Row row : rs) {
            FileModel model = getFileMetadata(row.getString("file_id"));
            if (model != null) {
                list.add(model);
            }
        }
        return list;
    }
}
