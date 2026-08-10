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
package com.netflix.conductor.redis.dao;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.conductoross.conductor.dao.FileMetadataDAO;
import org.conductoross.conductor.model.FileModel;
import org.conductoross.conductor.model.file.FileUploadStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.redis.config.AnyRedisCondition;
import com.netflix.conductor.redis.config.RedisProperties;
import com.netflix.conductor.redis.jedis.JedisProxy;

import com.fasterxml.jackson.databind.ObjectMapper;

@Component
@Conditional(AnyRedisCondition.class)
@ConditionalOnProperty(name = "conductor.file-storage.enabled", havingValue = "true")
public class RedisFileMetadataDAO extends BaseDynoDAO implements FileMetadataDAO {

    private static final String FILE_METADATA = "FILE_METADATA";
    private static final String WORKFLOW_FILES = "WORKFLOW_FILES";
    private static final String TASK_FILES = "TASK_FILES";

    public RedisFileMetadataDAO(
            JedisProxy jedisProxy,
            ObjectMapper objectMapper,
            ConductorProperties conductorProperties,
            RedisProperties properties) {
        super(jedisProxy, objectMapper, conductorProperties, properties);
    }

    @Override
    public void createFileMetadata(FileModel fileModel) {
        String json = toJson(fileModel);
        jedisProxy.hset(nsKey(FILE_METADATA), fileModel.getFileId(), json);
        if (fileModel.getWorkflowId() != null) {
            jedisProxy.sadd(
                    nsKey(WORKFLOW_FILES, fileModel.getWorkflowId()), fileModel.getFileId());
        }
        if (fileModel.getTaskId() != null) {
            jedisProxy.sadd(nsKey(TASK_FILES, fileModel.getTaskId()), fileModel.getFileId());
        }
    }

    @Override
    public FileModel getFileMetadata(String fileId) {
        String json = jedisProxy.hget(nsKey(FILE_METADATA), fileId);
        if (json == null) return null;
        return readValue(json, FileModel.class);
    }

    @Override
    public void updateUploadStatus(String fileId, FileUploadStatus status) {
        FileModel model = getFileMetadata(fileId);
        if (model != null) {
            model.setUploadStatus(status);
            model.setUpdatedAt(Instant.now());
            jedisProxy.hset(nsKey(FILE_METADATA), fileId, toJson(model));
        }
    }

    @Override
    public void updateUploadComplete(
            String fileId, FileUploadStatus status, String contentHash, long contentSize) {
        FileModel model = getFileMetadata(fileId);
        if (model != null) {
            model.setUploadStatus(status);
            model.setStorageContentHash(contentHash);
            model.setStorageContentSize(contentSize);
            model.setUpdatedAt(Instant.now());
            jedisProxy.hset(nsKey(FILE_METADATA), fileId, toJson(model));
        }
    }

    @Override
    public List<FileModel> getFilesByWorkflowId(String workflowId) {
        Set<String> fileIds = jedisProxy.smembers(nsKey(WORKFLOW_FILES, workflowId));
        return getFileModels(fileIds);
    }

    @Override
    public List<FileModel> getFilesByTaskId(String taskId) {
        Set<String> fileIds = jedisProxy.smembers(nsKey(TASK_FILES, taskId));
        return getFileModels(fileIds);
    }

    private List<FileModel> getFileModels(Set<String> fileIds) {
        List<FileModel> result = new ArrayList<>();
        if (fileIds != null) {
            for (String fileId : fileIds) {
                FileModel model = getFileMetadata(fileId);
                if (model != null) {
                    result.add(model);
                }
            }
        }
        return result;
    }
}
