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
package org.conductoross.conductor.controllers;

import org.conductoross.conductor.core.storage.FileStorageService;
import org.conductoross.conductor.model.file.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;

import static com.netflix.conductor.rest.config.RequestMappingConstants.FILES;

/**
 * REST controller for the file-storage feature. Gated by {@code conductor.file-storage.enabled}.
 * Path variables carry the bare {@code fileId}; request/response bodies carry the prefixed {@code
 * fileHandleId} via their DTO fields.
 */
@RestController
@RequestMapping(FILES)
@ConditionalOnProperty(name = "conductor.file-storage.enabled", havingValue = "true")
public class FileResource {

    private final FileStorageService fileStorageService;

    public FileResource(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a file record and get upload URL")
    public FileUploadResponse createFile(@Valid @RequestBody FileUploadRequest request) {
        return fileStorageService.createFile(request);
    }

    @GetMapping("/{fileId}/upload-url")
    @Operation(summary = "Get presigned upload URL")
    public FileUploadUrlResponse getUploadUrl(@PathVariable("fileId") String fileId) {
        return fileStorageService.getUploadUrl(fileId);
    }

    @PostMapping("/{fileId}/upload-complete")
    @Operation(summary = "Confirm file upload completion")
    public FileUploadCompleteResponse confirmUpload(@PathVariable("fileId") String fileId) {
        return fileStorageService.confirmUpload(fileId);
    }

    @GetMapping("/{workflowId}/{fileId}/download-url")
    @Operation(summary = "Get presigned download URL")
    public FileDownloadUrlResponse getDownloadUrl(
            @PathVariable("workflowId") String workflowId, @PathVariable("fileId") String fileId) {
        return fileStorageService.getDownloadUrl(fileId, workflowId);
    }

    @GetMapping("/{fileId}")
    @Operation(summary = "Get file metadata")
    public FileHandle getFileMetadata(@PathVariable("fileId") String fileId) {
        return fileStorageService.getFileMetadata(fileId);
    }

    @PostMapping("/{fileId}/multipart")
    @Operation(summary = "Initiate multipart upload")
    public MultipartInitResponse initiateMultipartUpload(@PathVariable("fileId") String fileId) {
        return fileStorageService.initiateMultipartUpload(fileId);
    }

    @GetMapping("/{fileId}/multipart/{uploadId}/part/{partNumber}")
    @Operation(summary = "Get presigned URL for a multipart part (S3 only)")
    public FileUploadUrlResponse getPartUploadUrl(
            @PathVariable("fileId") String fileId,
            @PathVariable("uploadId") String uploadId,
            @PathVariable("partNumber") int partNumber) {
        return fileStorageService.getPartUploadUrl(fileId, uploadId, partNumber);
    }

    @PostMapping("/{fileId}/multipart/{uploadId}/complete")
    @Operation(summary = "Complete multipart upload")
    public FileUploadCompleteResponse completeMultipartUpload(
            @PathVariable("fileId") String fileId,
            @PathVariable("uploadId") String uploadId,
            @RequestBody MultipartCompleteRequest request) {
        return fileStorageService.completeMultipartUpload(fileId, uploadId, request.getPartETags());
    }
}
