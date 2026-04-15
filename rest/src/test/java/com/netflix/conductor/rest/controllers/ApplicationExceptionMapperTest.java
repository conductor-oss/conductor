/*
 * Copyright 2024 Conductor Authors.
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
package com.netflix.conductor.rest.controllers;

import java.util.Collections;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.netflix.conductor.model.TaskModel;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class ApplicationExceptionMapperTest {

    private QueueAdminResource queueAdminResource;

    private MockMvc mockMvc;

    private static MockedStatic<LoggerFactory> mockLoggerFactory;
    private static final Logger logger = mock(Logger.class);

    @Before
    public void before() {
        mockLoggerFactory = Mockito.mockStatic(LoggerFactory.class);
        when(LoggerFactory.getLogger(ApplicationExceptionMapper.class)).thenReturn(logger);

        this.queueAdminResource = mock(QueueAdminResource.class);
        this.mockMvc =
                MockMvcBuilders.standaloneSetup(this.queueAdminResource)
                        .setControllerAdvice(new ApplicationExceptionMapper())
                        .build();
    }

    @After
    public void after() {
        mockLoggerFactory.close();
    }

    @Test
    public void testException() throws Exception {
        var exception = new Exception();
        // pick a method that raises a generic exception
        doThrow(exception).when(this.queueAdminResource).update(any(), any(), any(), any());

        // verify we do send an error response
        this.mockMvc
                .perform(
                        MockMvcRequestBuilders.post(
                                        "/api/queue/update/workflowId/taskRefName/{status}",
                                        TaskModel.Status.SKIPPED)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        new ObjectMapper()
                                                .writeValueAsString(Collections.emptyMap())))
                .andDo(print())
                .andExpect(status().is5xxServerError());
        // verify the error was logged
        verify(logger)
                .error(
                        "Error {} url: '{}'",
                        "Exception",
                        "/api/queue/update/workflowId/taskRefName/SKIPPED",
                        exception);
        verifyNoMoreInteractions(logger);
    }
}
