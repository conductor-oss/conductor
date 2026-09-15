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
package com.netflix.conductor.rest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Guards the springdoc-openapi / Spring Framework pairing.
 *
 * <p>springdoc reflects over the {@code @RestControllerAdvice} beans in the context while building
 * the OpenAPI document. Versions of springdoc built against Spring Framework 6.0 call {@code new
 * ControllerAdviceBean(Object)}, a constructor removed in Spring Framework 6.2 — so an incompatible
 * pairing compiles, links, and starts cleanly, and only fails when {@code /api-docs} is first
 * requested. Nothing short of a real request for the document detects it, which is why this test
 * loads a context and asks for the document rather than asserting on versions.
 *
 * <p>The context is assembled explicitly rather than by component scan: scanning {@code
 * com.netflix.conductor.rest.controllers} would drag in every resource and its service
 * dependencies, none of which this test needs. The advice below is a local stand-in for the
 * production ones for the same reason — springdoc walks whatever advice beans are present, so any
 * one reproduces the failure.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(
        classes = OpenApiDocsTest.TestConfig.class,
        // mirrors server/src/main/resources/application.properties
        properties = "springdoc.api-docs.path=/api-docs")
@AutoConfigureMockMvc
public class OpenApiDocsTest {

    @Autowired private MockMvc mockMvc;

    @Test
    public void servesTheOpenApiDocument() throws Exception {
        mockMvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.paths['/probe']").exists());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({ProbeExceptionHandler.class, ProbeController.class})
    static class TestConfig {}

    @RestController
    static class ProbeController {

        @GetMapping("/probe")
        public String probe() {
            return "ok";
        }
    }

    /** Stands in for the production advices; springdoc only needs one to be present. */
    @RestControllerAdvice
    static class ProbeExceptionHandler {

        @ExceptionHandler(IllegalStateException.class)
        public ResponseEntity<String> handle(IllegalStateException e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }
}
