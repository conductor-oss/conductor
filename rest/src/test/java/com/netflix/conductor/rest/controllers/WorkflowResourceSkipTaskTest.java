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

import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.netflix.conductor.common.config.ObjectMapperBuilderConfiguration;
import com.netflix.conductor.common.metadata.workflow.SkipTaskRequest;
import com.netflix.conductor.service.WorkflowService;
import com.netflix.conductor.service.WorkflowTestService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The skip-task request body as clients send it: a JSON {@code SkipTaskRequest}. Without
 * {@code @RequestBody} the body was ignored and the skipped task got no input or output.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = WorkflowResourceSkipTaskTest.TestConfig.class)
@AutoConfigureMockMvc
public class WorkflowResourceSkipTaskTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private WorkflowService workflowService;

    /** The mock itself: the bean is wrapped in a validating proxy, which matchers would trip. */
    private WorkflowService service() {
        return AopTestUtils.getUltimateTargetObject(workflowService);
    }

    @Before
    public void setUp() {
        reset(service());
    }

    @Test
    public void readsTheInputAndOutputFromTheJsonBody() throws Exception {
        mockMvc.perform(
                        put("/api/workflow/wf-1/skiptask/validate_ref")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"taskInput\":{\"in\":1},"
                                                + "\"taskOutput\":{\"skipped\":true}}"))
                .andExpect(status().isOk());

        ArgumentCaptor<SkipTaskRequest> request = ArgumentCaptor.forClass(SkipTaskRequest.class);
        verify(service()).skipTaskFromWorkflow(eq("wf-1"), eq("validate_ref"), request.capture());
        assertEquals(Map.of("in", 1), request.getValue().getTaskInput());
        assertEquals(Map.of("skipped", true), request.getValue().getTaskOutput());
    }

    @Test
    public void aRequestWithoutABodyStillSkips() throws Exception {
        mockMvc.perform(put("/api/workflow/wf-1/skiptask/validate_ref")).andExpect(status().isOk());

        ArgumentCaptor<SkipTaskRequest> request = ArgumentCaptor.forClass(SkipTaskRequest.class);
        verify(service()).skipTaskFromWorkflow(eq("wf-1"), eq("validate_ref"), request.capture());
        assertNull(request.getValue());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    // not ApplicationExceptionMapper: loading it here would fix its static logger before
    // ApplicationExceptionMapperTest mocks it in the same JVM
    @Import({WorkflowResource.class, ObjectMapperBuilderConfiguration.class})
    static class TestConfig {

        @Bean
        public WorkflowService workflowService() {
            return mock(WorkflowService.class);
        }

        @Bean
        public WorkflowTestService workflowTestService() {
            return mock(WorkflowTestService.class);
        }
    }
}
