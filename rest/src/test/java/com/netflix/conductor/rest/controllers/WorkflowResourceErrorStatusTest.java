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
package com.netflix.conductor.rest.controllers;

import org.junit.Before;
import org.junit.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.netflix.conductor.core.exception.ConflictException;
import com.netflix.conductor.service.WorkflowService;
import com.netflix.conductor.service.WorkflowTestService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that client-caused state errors on resume/skip-task surface as 4xx through {@link
 * ApplicationExceptionMapper}, not 500.
 */
public class WorkflowResourceErrorStatusTest {

    private WorkflowService workflowService;
    private MockMvc mockMvc;

    @Before
    public void before() {
        workflowService = mock(WorkflowService.class);
        WorkflowResource resource =
                new WorkflowResource(workflowService, mock(WorkflowTestService.class));
        mockMvc =
                MockMvcBuilders.standaloneSetup(resource)
                        .setControllerAdvice(new ApplicationExceptionMapper())
                        .build();
    }

    @Test
    public void resumeOfNonPausedWorkflowReturns409() throws Exception {
        doThrow(
                        new ConflictException(
                                "The workflow wf1 is not PAUSED so cannot resume. Current status is COMPLETED"))
                .when(workflowService)
                .resumeWorkflow("wf1");

        mockMvc.perform(MockMvcRequestBuilders.put("/api/workflow/wf1/resume"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.retryable").value(false))
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "The workflow wf1 is not PAUSED so cannot resume. Current status is COMPLETED"));
    }

    @Test
    public void skipTaskOnNonRunningWorkflowReturns409() throws Exception {
        doThrow(new ConflictException("The workflow wf1 is not running"))
                .when(workflowService)
                .skipTaskFromWorkflow(eq("wf1"), eq("t1"), any());

        mockMvc.perform(MockMvcRequestBuilders.put("/api/workflow/wf1/skiptask/t1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    public void skipTaskWithUnknownTaskRefReturns400() throws Exception {
        doThrow(new IllegalArgumentException("The task referenced by nope does not exist"))
                .when(workflowService)
                .skipTaskFromWorkflow(eq("wf1"), eq("nope"), any());

        mockMvc.perform(MockMvcRequestBuilders.put("/api/workflow/wf1/skiptask/nope"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
