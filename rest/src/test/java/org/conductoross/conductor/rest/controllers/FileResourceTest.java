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
package org.conductoross.conductor.rest.controllers;

import java.util.List;

import org.conductoross.conductor.controllers.FileResource;
import org.conductoross.conductor.core.storage.FileStorageService;
import org.conductoross.conductor.model.file.*;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class FileResourceTest {

    private static final String FILE_ID = "abc";
    private static final String FILE_HANDLE_ID = FileIdToFileHandleIdConverter.PREFIX + FILE_ID;

    private FileStorageService fileStorageService;
    private FileResource fileResource;

    @Before
    public void setUp() {
        fileStorageService = mock(FileStorageService.class);
        fileResource = new FileResource(fileStorageService);
    }

    @Test
    public void testCreateFile() {
        FileUploadRequest request = new FileUploadRequest();
        request.setFileName("test.pdf");
        FileUploadResponse expected = new FileUploadResponse();
        expected.setFileHandleId(FILE_HANDLE_ID);
        when(fileStorageService.createFile(request)).thenReturn(expected);

        FileUploadResponse result = fileResource.createFile(request);
        assertEquals(FILE_HANDLE_ID, result.getFileHandleId());
        verify(fileStorageService).createFile(request);
    }

    @Test
    public void testGetUploadUrl() {
        FileUploadUrlResponse expected = new FileUploadUrlResponse();
        expected.setFileHandleId(FILE_HANDLE_ID);
        expected.setUploadUrl("https://s3/url");
        when(fileStorageService.getUploadUrl(FILE_ID)).thenReturn(expected);

        FileUploadUrlResponse result = fileResource.getUploadUrl(FILE_ID);
        assertEquals("https://s3/url", result.getUploadUrl());
    }

    @Test
    public void testConfirmUpload() {
        FileUploadCompleteResponse expected = new FileUploadCompleteResponse();
        expected.setUploadStatus(FileUploadStatus.UPLOADED);
        when(fileStorageService.confirmUpload(FILE_ID)).thenReturn(expected);

        FileUploadCompleteResponse result = fileResource.confirmUpload(FILE_ID);
        assertEquals(FileUploadStatus.UPLOADED, result.getUploadStatus());
    }

    @Test
    public void testGetDownloadUrl() {
        FileDownloadUrlResponse expected = new FileDownloadUrlResponse();
        expected.setDownloadUrl("https://s3/download");
        when(fileStorageService.getDownloadUrl(FILE_ID, "wf-1")).thenReturn(expected);

        FileDownloadUrlResponse result = fileResource.getDownloadUrl("wf-1", FILE_ID);
        assertEquals("https://s3/download", result.getDownloadUrl());
    }

    @Test
    public void testGetFileMetadata() {
        FileHandle expected = new FileHandle();
        expected.setFileHandleId(FILE_HANDLE_ID);
        expected.setFileName("test.pdf");
        when(fileStorageService.getFileMetadata(FILE_ID)).thenReturn(expected);

        FileHandle result = fileResource.getFileMetadata(FILE_ID);
        assertEquals("test.pdf", result.getFileName());
    }

    @Test
    public void testInitiateMultipartUpload() {
        MultipartInitResponse expected = new MultipartInitResponse();
        expected.setUploadId("mp-123");
        when(fileStorageService.initiateMultipartUpload(FILE_ID)).thenReturn(expected);

        MultipartInitResponse result = fileResource.initiateMultipartUpload(FILE_ID);
        assertEquals("mp-123", result.getUploadId());
    }

    @Test
    public void testGetPartUploadUrl() {
        FileUploadUrlResponse expected = new FileUploadUrlResponse();
        expected.setUploadUrl("https://s3/part/1");
        when(fileStorageService.getPartUploadUrl(FILE_ID, "mp-123", 1)).thenReturn(expected);

        FileUploadUrlResponse result = fileResource.getPartUploadUrl(FILE_ID, "mp-123", 1);
        assertEquals("https://s3/part/1", result.getUploadUrl());
    }

    @Test
    public void testCompleteMultipartUpload() {
        MultipartCompleteRequest request = new MultipartCompleteRequest();
        request.setPartETags(List.of("etag1", "etag2"));
        FileUploadCompleteResponse expected = new FileUploadCompleteResponse();
        expected.setUploadStatus(FileUploadStatus.UPLOADED);
        when(fileStorageService.completeMultipartUpload(
                        FILE_ID, "mp-123", List.of("etag1", "etag2")))
                .thenReturn(expected);

        FileUploadCompleteResponse result =
                fileResource.completeMultipartUpload(FILE_ID, "mp-123", request);
        assertEquals(FileUploadStatus.UPLOADED, result.getUploadStatus());
    }
}
