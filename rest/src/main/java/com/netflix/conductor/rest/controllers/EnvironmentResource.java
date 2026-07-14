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

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.netflix.conductor.common.metadata.EnvironmentVariable;
import com.netflix.conductor.dao.EnvironmentDAO;

import io.swagger.v3.oas.annotations.Operation;

import static com.netflix.conductor.rest.config.RequestMappingConstants.ENVIRONMENT;

@RestController
@RequestMapping(value = ENVIRONMENT, produces = MediaType.APPLICATION_JSON_VALUE)
public class EnvironmentResource {

    private final EnvironmentDAO environmentDAO;

    public EnvironmentResource(EnvironmentDAO environmentDAO) {
        this.environmentDAO = environmentDAO;
    }

    @GetMapping
    @Operation(summary = "List all environment variables")
    public List<EnvironmentVariable> getAll() {
        return environmentDAO.getAll();
    }

    @GetMapping(value = "/{key}", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "Get the value of an environment variable")
    public String get(@PathVariable("key") String key) {
        return environmentDAO.getEnvVariable(key);
    }
}
