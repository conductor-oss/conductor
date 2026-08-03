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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.conductoross.conductor.controllers.FileResource;
import org.conductoross.conductor.core.exception.FileStorageException;
import org.conductoross.conductor.core.storage.FileContent;
import org.conductoross.conductor.core.storage.FileStorageService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.netflix.conductor.core.exception.AccessForbiddenException;
import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.rest.controllers.ApplicationExceptionMapper;

import static org.junit.Assert.assertArrayEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class FileResourceTest {

    private static final String FILE_ID = "abc";
    private static final String WORKFLOW_ID = "wf-1";
    private static final String CONTENT_PATH = "/api/files/content/" + WORKFLOW_ID + "/" + FILE_ID;

    private FileStorageService fileStorageService;
    private MockMvc mockMvc;

    @Before
    public void setUp() {
        fileStorageService = mock(FileStorageService.class);
        mockMvc =
                MockMvcBuilders.standaloneSetup(new FileResource(fileStorageService))
                        .setControllerAdvice(new ApplicationExceptionMapper())
                        .build();
    }

    @Test
    public void streamsRawUploadContentToService() throws Exception {
        byte[] expected = "upload bytes".getBytes(StandardCharsets.UTF_8);
        ArgumentCaptor<InputStream> contentCaptor = ArgumentCaptor.forClass(InputStream.class);

        mockMvc.perform(MockMvcRequestBuilders.put(CONTENT_PATH).content(expected))
                .andExpect(status().isNoContent());

        verify(fileStorageService)
                .uploadContent(eq(WORKFLOW_ID), eq(FILE_ID), contentCaptor.capture());
        assertArrayEquals(expected, contentCaptor.getValue().readAllBytes());
    }

    @Test
    public void streamsDownloadWithRecordedContentTypeAndLength() throws Exception {
        byte[] expected = "download bytes".getBytes(StandardCharsets.UTF_8);
        when(fileStorageService.downloadContent(WORKFLOW_ID, FILE_ID))
                .thenReturn(
                        new FileContent(
                                new ByteArrayInputStream(expected),
                                MediaType.TEXT_PLAIN_VALUE,
                                expected.length));

        MvcResult result =
                mockMvc.perform(MockMvcRequestBuilders.get(CONTENT_PATH))
                        .andExpect(request().asyncStarted())
                        .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.TEXT_PLAIN))
                .andExpect(header().longValue("Content-Length", expected.length))
                .andExpect(content().bytes(expected));
    }

    @Test
    public void rejectsUploadForNonOwningWorkflow() throws Exception {
        doThrow(new AccessForbiddenException("not the file owner"))
                .when(fileStorageService)
                .uploadContent(eq("other-workflow"), eq(FILE_ID), any());

        mockMvc.perform(
                        MockMvcRequestBuilders.put("/api/files/content/other-workflow/" + FILE_ID)
                                .content("bytes"))
                .andExpect(status().isForbidden());
    }

    @Test
    public void returnsNotFoundForUnknownFile() throws Exception {
        doThrow(new NotFoundException("file not found"))
                .when(fileStorageService)
                .uploadContent(eq(WORKFLOW_ID), eq(FILE_ID), any());

        mockMvc.perform(MockMvcRequestBuilders.put(CONTENT_PATH).content("bytes"))
                .andExpect(status().isNotFound());
    }

    @Test
    public void rejectsDownloadBeforeUploadCompletes() throws Exception {
        when(fileStorageService.downloadContent(WORKFLOW_ID, FILE_ID))
                .thenThrow(new IllegalArgumentException("File not yet uploaded"));

        mockMvc.perform(MockMvcRequestBuilders.get(CONTENT_PATH))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void mapsStreamingUploadLimitToPayloadTooLarge() throws Exception {
        doAnswer(
                        invocation -> {
                            invocation.getArgument(2, InputStream.class).readAllBytes();
                            throw new FileStorageException("File exceeds configured maximum size");
                        })
                .when(fileStorageService)
                .uploadContent(
                        org.mockito.ArgumentMatchers.eq(WORKFLOW_ID),
                        org.mockito.ArgumentMatchers.eq(FILE_ID),
                        org.mockito.ArgumentMatchers.any());

        mockMvc.perform(MockMvcRequestBuilders.put(CONTENT_PATH).content("too large"))
                .andExpect(status().isPayloadTooLarge());

        verify(fileStorageService).uploadContent(eq(WORKFLOW_ID), eq(FILE_ID), any());
        verifyNoMoreInteractions(fileStorageService);
    }

    @Test
    public void returnsNotFoundForMissingDownload() throws Exception {
        when(fileStorageService.downloadContent(WORKFLOW_ID, FILE_ID))
                .thenThrow(new NotFoundException("file not found"));

        mockMvc.perform(MockMvcRequestBuilders.get(CONTENT_PATH)).andExpect(status().isNotFound());
    }
}
