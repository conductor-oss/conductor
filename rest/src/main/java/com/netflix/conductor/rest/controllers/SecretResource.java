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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.netflix.conductor.dao.SecretsDAO;

import io.swagger.v3.oas.annotations.Operation;

import static com.netflix.conductor.rest.config.RequestMappingConstants.SECRETS;

@RestController
@RequestMapping(value = SECRETS, produces = MediaType.APPLICATION_JSON_VALUE)
public class SecretResource {

    private final SecretsDAO secretsDAO;

    public SecretResource(SecretsDAO secretsDAO) {
        this.secretsDAO = secretsDAO;
    }

    @GetMapping
    @Operation(summary = "List secret names (values are never returned)")
    public List<String> listSecretNames() {
        return secretsDAO.listSecretNames();
    }
}
