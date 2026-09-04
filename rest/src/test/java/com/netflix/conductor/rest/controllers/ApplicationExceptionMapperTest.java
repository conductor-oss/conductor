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

import org.conductoross.conductor.core.exception.SchemaValidationException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import com.netflix.conductor.core.exception.ConflictException;
import com.netflix.conductor.core.exception.NotFoundException;
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
        // logger is a static mock reused across tests; clear its invocation history
        // so per-test verifications (e.g. never().error()) are order-independent.
        clearInvocations(logger);

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

    @Test
    public void testClientErrorsLoggedAtWarn() throws Exception {
        // Client (4xx) errors are logged at WARN, not ERROR, across the mapped
        // exception types (for example ConflictException -> 409,
        // NotFoundException -> 404).
        assertLoggedAtWarn(new ConflictException("resource already exists"), status().isConflict());
        assertLoggedAtWarn(new NotFoundException("resource not found"), status().isNotFound());
    }

    @Test
    public void testSchemaValidationMapsTo400() throws Exception {
        // A payload that does not match its definition's schema is the caller's to fix; a 500
        // would tell an SDK to retry something that can never succeed.
        assertLoggedAtWarn(
                new SchemaValidationException("Workflow order input: required property 'name'"),
                status().isBadRequest());
    }

    /**
     * The same, with both advices registered as the server registers them.
     * SchemaValidationException is a {@code jakarta.validation.ValidationException}, so
     * ValidationExceptionMapper — at HIGHEST_PRECEDENCE — handles it, not the status map above. Its
     * non-constraint-violation branch answers 500, so without an explicit case for this type a bad
     * payload would come back as a server fault. The test above registers only one advice and would
     * not notice.
     */
    @Test
    public void testSchemaValidationMapsTo400WithBothAdvicesRegistered() throws Exception {
        MockMvc withBothAdvices =
                MockMvcBuilders.standaloneSetup(this.queueAdminResource)
                        .setControllerAdvice(
                                new ValidationExceptionMapper(), new ApplicationExceptionMapper())
                        .build();

        doThrow(new SchemaValidationException("Workflow order input: required property 'name'"))
                .when(this.queueAdminResource)
                .update(any(), any(), any(), any());

        withBothAdvices
                .perform(
                        MockMvcRequestBuilders.post(
                                        "/api/queue/update/workflowId/taskRefName/{status}",
                                        TaskModel.Status.SKIPPED)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        new ObjectMapper()
                                                .writeValueAsString(Collections.emptyMap())))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testMethodNotSupportedMapsTo405() throws Exception {
        // an unsupported HTTP method on an existing path must map to 405 (RFC 7231),
        // not the default 500, so SDK GET-then-PUT-on-405 fallbacks keep working.
        assertLoggedAtWarn(
                new HttpRequestMethodNotSupportedException("GET"), status().isMethodNotAllowed());
    }

    private void assertLoggedAtWarn(Exception exception, ResultMatcher expectedStatus)
            throws Exception {
        // logger is a static mock reused across assertions; start each one clean.
        clearInvocations(logger);
        doThrow(exception).when(this.queueAdminResource).update(any(), any(), any(), any());

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
                .andExpect(expectedStatus);
        // client (4xx) errors must be logged at WARN, not ERROR
        verify(logger)
                .warn(
                        "Error {} url: '{}'",
                        exception.getClass().getSimpleName(),
                        "/api/queue/update/workflowId/taskRefName/SKIPPED",
                        exception);
        verify(logger, never()).error(any(), any(), any(), any());
        verifyNoMoreInteractions(logger);
    }
}
