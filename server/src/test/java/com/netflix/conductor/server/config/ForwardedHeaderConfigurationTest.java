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
package com.netflix.conductor.server.config;

import org.junit.Before;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

public class ForwardedHeaderConfigurationTest {

    private org.springframework.test.web.servlet.MockMvc mockMvc;

    @Before
    public void setUp() {
        ForwardedHeaderConfiguration configuration = new ForwardedHeaderConfiguration();
        mockMvc =
                standaloneSetup(new OriginController())
                        .addFilters(configuration.forwardedHeaderFilter().getFilter())
                        .build();
    }

    @Test
    public void forwardedHeadersProducePublicOrigin() throws Exception {
        mockMvc.perform(
                        get("/origin")
                                .with(
                                        request -> {
                                            request.setScheme("http");
                                            request.setServerName("conductor.internal");
                                            request.setServerPort(8080);
                                            return request;
                                        })
                                .header("X-Forwarded-Proto", "https")
                                .header("X-Forwarded-Host", "conductor.example.com")
                                .header("X-Forwarded-Port", "8443"))
                .andExpect(status().isOk())
                .andExpect(content().string("https://conductor.example.com:8443"));
    }

    @Test
    public void requestsWithoutForwardedHeadersRetainOriginalOrigin() throws Exception {
        mockMvc.perform(
                        get("/origin")
                                .with(
                                        request -> {
                                            request.setScheme("http");
                                            request.setServerName("conductor.internal");
                                            request.setServerPort(8080);
                                            return request;
                                        }))
                .andExpect(status().isOk())
                .andExpect(content().string("http://conductor.internal:8080"));
    }

    @Controller
    static class OriginController {

        @GetMapping(value = "/origin", produces = MediaType.TEXT_PLAIN_VALUE)
        @ResponseBody
        public String origin() {
            return ServletUriComponentsBuilder.fromCurrentContextPath().toUriString();
        }
    }
}
