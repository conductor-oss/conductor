/*
 * Copyright 2022 Netflix, Inc.
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
package com.netflix.conductor.core.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.netflix.conductor.annotations.VisibleForTesting;
import com.netflix.conductor.common.run.ExternalStorageLocation;
import com.netflix.conductor.common.utils.ExternalPayloadStorage;
import com.netflix.conductor.common.utils.ExternalPayloadStorage.PayloadType;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.exception.NonTransientException;
import com.netflix.conductor.core.exception.TerminateWorkflowException;
import com.netflix.conductor.core.exception.TransientException;
import com.netflix.conductor.metrics.Monitors;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Provides utility functions to upload and download payloads to {@link ExternalPayloadStorage} */
@Component
public class ExternalPayloadStorageUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExternalPayloadStorageUtils.class);

    private final ExternalPayloadStorage externalPayloadStorage;
    private final ConductorProperties properties;
    private final ObjectMapper objectMapper;

    public ExternalPayloadStorageUtils(
            ExternalPayloadStorage externalPayloadStorage,
            ConductorProperties properties,
            ObjectMapper objectMapper) {
        this.externalPayloadStorage = externalPayloadStorage;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Download the payload from the given path.
     *
     * @param path the relative path of the payload in the {@link ExternalPayloadStorage}
     * @return the payload object
     * @throws NonTransientException in case of JSON parsing errors or download errors
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> downloadPayload(String path) {
        try (InputStream inputStream = externalPayloadStorage.download(path)) {
            return objectMapper.readValue(
                    IOUtils.toString(inputStream, StandardCharsets.UTF_8), Map.class);
        } catch (TransientException te) {
            throw te;
        } catch (Exception e) {
            LOGGER.error("Unable to download payload from external storage path: {}", path, e);
            throw new NonTransientException(
                    "Unable to download payload from external storage path: " + path, e);
        }
    }

    /**
     * Verify the payload size and upload to external storage if necessary.
     *
     * @param entity the task or workflow for which the payload is to be verified and uploaded
     * @param payloadType the {@link PayloadType} of the payload
     * @param <T> {@link TaskModel} or {@link WorkflowModel}
     * @throws NonTransientException in case of JSON parsing errors or upload errors
     * @throws TerminateWorkflowException if the payload size is bigger than permissible limit as
     *     per {@link ConductorProperties}
     */
    public <T> void verifyAndUpload(T entity, PayloadType payloadType) {
        if (!shouldUpload(entity, payloadType)) return;

        long threshold = 0L;
        long maxThreshold = 0L;
        Map<String, Object> payload = new HashMap<>();
        String workflowId = "";
        switch (payloadType) {
            case TASK_INPUT:
                threshold = properties.getTaskInputPayloadSizeThreshold().toKilobytes();
                maxThreshold = properties.getMaxTaskInputPayloadSizeThreshold().toKilobytes();
                payload = ((TaskModel) entity).getInputData();
                workflowId = ((TaskModel) entity).getWorkflowInstanceId();
                break;
            case TASK_OUTPUT:
                threshold = properties.getTaskOutputPayloadSizeThreshold().toKilobytes();
                maxThreshold = properties.getMaxTaskOutputPayloadSizeThreshold().toKilobytes();
                payload = ((TaskModel) entity).getOutputData();
                workflowId = ((TaskModel) entity).getWorkflowInstanceId();
                break;
            case WORKFLOW_INPUT:
                threshold = properties.getWorkflowInputPayloadSizeThreshold().toKilobytes();
                maxThreshold = properties.getMaxWorkflowInputPayloadSizeThreshold().toKilobytes();
                payload = ((WorkflowModel) entity).getInput();
                workflowId = ((WorkflowModel) entity).getWorkflowId();
                break;
            case WORKFLOW_OUTPUT:
                threshold = properties.getWorkflowOutputPayloadSizeThreshold().toKilobytes();
                maxThreshold = properties.getMaxWorkflowOutputPayloadSizeThreshold().toKilobytes();
                payload = ((WorkflowModel) entity).getOutput();
                workflowId = ((WorkflowModel) entity).getWorkflowId();
                break;
        }

        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            objectMapper.writeValue(byteArrayOutputStream, payload);
            byte[] payloadBytes = byteArrayOutputStream.toByteArray();
            long payloadSize = payloadBytes.length;

            final long maxThresholdInBytes = maxThreshold * 1024;
            if (payloadSize > maxThresholdInBytes) {
                if (entity instanceof TaskModel) {
                    String errorMsg =
                            String.format(
                                    "The payload size: %d of task: %s in workflow: %s  is greater than the permissible limit: %d bytes",
                                    payloadSize,
                                    ((TaskModel) entity).getTaskId(),
                                    ((TaskModel) entity).getWorkflowInstanceId(),
                                    maxThresholdInBytes);
                    failTask(((TaskModel) entity), payloadType, errorMsg);
                } else {
                    String errorMsg =
                            String.format(
                                    "The payload size: %d of workflow: %s is greater than the permissible limit: %d bytes",
                                    payloadSize,
                                    ((WorkflowModel) entity).getWorkflowId(),
                                    maxThresholdInBytes);
                    failWorkflow(((WorkflowModel) entity), payloadType, errorMsg);
                }
            } else if (payloadSize > threshold * 1024) {
                String externalInputPayloadStoragePath, externalOutputPayloadStoragePath;
                switch (payloadType) {
                    case TASK_INPUT:
                        externalInputPayloadStoragePath =
                                uploadHelper(payloadBytes, payloadSize, PayloadType.TASK_INPUT);
                        ((TaskModel) entity).externalizeInput(externalInputPayloadStoragePath);
                        Monitors.recordExternalPayloadStorageUsage(
                                ((TaskModel) entity).getTaskDefName(),
                                ExternalPayloadStorage.Operation.WRITE.toString(),
                                PayloadType.TASK_INPUT.toString());
                        break;
                    case TASK_OUTPUT:
                        externalOutputPayloadStoragePath =
                                uploadHelper(payloadBytes, payloadSize, PayloadType.TASK_OUTPUT);
                        ((TaskModel) entity).externalizeOutput(externalOutputPayloadStoragePath);
                        Monitors.recordExternalPayloadStorageUsage(
                                ((TaskModel) entity).getTaskDefName(),
                                ExternalPayloadStorage.Operation.WRITE.toString(),
                                PayloadType.TASK_OUTPUT.toString());
                        break;
                    case WORKFLOW_INPUT:
                        externalInputPayloadStoragePath =
                                uploadHelper(payloadBytes, payloadSize, PayloadType.WORKFLOW_INPUT);
                        ((WorkflowModel) entity).externalizeInput(externalInputPayloadStoragePath);
                        Monitors.recordExternalPayloadStorageUsage(
                                ((WorkflowModel) entity).getWorkflowName(),
                                ExternalPayloadStorage.Operation.WRITE.toString(),
                                PayloadType.WORKFLOW_INPUT.toString());
                        break;
                    case WORKFLOW_OUTPUT:
                        externalOutputPayloadStoragePath =
                                uploadHelper(
                                        payloadBytes, payloadSize, PayloadType.WORKFLOW_OUTPUT);
                        ((WorkflowModel) entity)
                                .externalizeOutput(externalOutputPayloadStoragePath);
                        Monitors.recordExternalPayloadStorageUsage(
                                ((WorkflowModel) entity).getWorkflowName(),
                                ExternalPayloadStorage.Operation.WRITE.toString(),
                                PayloadType.WORKFLOW_OUTPUT.toString());
                        break;
                }
            }
        } catch (TransientException | TerminateWorkflowException te) {
            throw te;
        } catch (Exception e) {
            LOGGER.error(
                    "Unable to upload payload to external storage for workflow: {}", workflowId, e);
            throw new NonTransientException(
                    "Unable to upload payload to external storage for workflow: " + workflowId, e);
        }
    }

    @VisibleForTesting
    String uploadHelper(
            byte[] payloadBytes, long payloadSize, ExternalPayloadStorage.PayloadType payloadType) {
        ExternalStorageLocation location =
                externalPayloadStorage.getLocation(
                        ExternalPayloadStorage.Operation.WRITE, payloadType, "", payloadBytes);
        externalPayloadStorage.upload(
                location.getPath(), new ByteArrayInputStream(payloadBytes), payloadSize);
        return location.getPath();
    }

    @VisibleForTesting
    void failTask(TaskModel task, PayloadType payloadType, String errorMsg) {
        LOGGER.error(errorMsg);
        task.setReasonForIncompletion(errorMsg);
        task.setStatus(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR);
        if (payloadType == PayloadType.TASK_INPUT) {
            task.setInputData(new HashMap<>());
        } else {
            task.setOutputData(new HashMap<>());
        }
    }

    @VisibleForTesting
    void failWorkflow(WorkflowModel workflow, PayloadType payloadType, String errorMsg) {
        LOGGER.error(errorMsg);
        if (payloadType == PayloadType.WORKFLOW_INPUT) {
            workflow.setInput(new HashMap<>());
        } else {
            workflow.setOutput(new HashMap<>());
        }
        throw new TerminateWorkflowException(errorMsg);
    }

    @VisibleForTesting
    <T> boolean shouldUpload(T entity, PayloadType payloadType) {
        if (entity instanceof TaskModel) {
            TaskModel taskModel = (TaskModel) entity;
            if (payloadType == PayloadType.TASK_INPUT) {
                return !taskModel.getRawInputData().isEmpty();
            } else {
                return !taskModel.getRawOutputData().isEmpty();
            }
        } else {
            WorkflowModel workflowModel = (WorkflowModel) entity;
            if (payloadType == PayloadType.WORKFLOW_INPUT) {
                return !workflowModel.getRawInput().isEmpty();
            } else {
                return !workflowModel.getRawOutput().isEmpty();
            }
        }
    }
}
