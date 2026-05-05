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
import com.fasterxml.jackson.databind.ObjectMapper;

public class CassandraFileMetadataDAO extends CassandraBaseDAO implements FileMetadataDAO {

    private static final String TABLE_FILE_METADATA = "file_metadata";

    private final PreparedStatement insertStmt;
    private final PreparedStatement selectByIdStmt;
    private final PreparedStatement selectByWorkflowStmt;
    private final PreparedStatement selectByTaskStmt;

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
                                        + " (file_id, json_data) VALUES (?, ?)")
                        .setConsistencyLevel(writeConsistency);

        selectByIdStmt =
                session.prepare(
                                "SELECT json_data FROM "
                                        + TABLE_FILE_METADATA
                                        + " WHERE file_id = ?")
                        .setConsistencyLevel(readConsistency);

        selectByWorkflowStmt =
                session.prepare(
                                "SELECT json_data FROM "
                                        + TABLE_FILE_METADATA
                                        + " WHERE workflow_id = ?")
                        .setConsistencyLevel(readConsistency);

        selectByTaskStmt =
                session.prepare(
                                "SELECT json_data FROM "
                                        + TABLE_FILE_METADATA
                                        + " WHERE task_id = ?")
                        .setConsistencyLevel(readConsistency);
    }

    @Override
    public void createFileMetadata(FileModel fileModel) {
        session.execute(insertStmt.bind(fileModel.getFileId(), toJson(fileModel)));
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
        session.execute(insertStmt.bind(fileId, toJson(model)));
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
        session.execute(insertStmt.bind(fileId, toJson(model)));
    }

    @Override
    public List<FileModel> getFilesByWorkflowId(String workflowId) {
        ResultSet rs = session.execute(selectByWorkflowStmt.bind(workflowId));
        return toFileModelList(rs);
    }

    @Override
    public List<FileModel> getFilesByTaskId(String taskId) {
        ResultSet rs = session.execute(selectByTaskStmt.bind(taskId));
        return toFileModelList(rs);
    }

    private List<FileModel> toFileModelList(ResultSet rs) {
        List<FileModel> list = new ArrayList<>();
        for (Row row : rs) {
            list.add(readValue(row.getString("json_data"), FileModel.class));
        }
        return list;
    }
}
