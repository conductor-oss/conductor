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

import org.conductoross.conductor.common.integrations.gdrive.GDriveIntegrationService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class IntegrationsResourceTest {

    private MockMvc mockMvc;

    @Before
    public void before() {
        this.mockMvc =
                MockMvcBuilders.standaloneSetup(
                                new IntegrationsResource(new GDriveIntegrationService()))
                        .build();
    }

    @Test
    public void testGoogleDriveLoadIsMappedUnderApiPrefix() throws Exception {
        this.mockMvc
                .perform(
                        MockMvcRequestBuilders.post("/api/integrations/gdrive/load")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
