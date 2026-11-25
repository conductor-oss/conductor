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
import java.util.Map;

import org.conductoross.conductor.dao.SecretsDAO;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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

    @PutMapping("/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Create or update a secret (value is encrypted at rest, never returned)")
    public void putSecret(@PathVariable String name, @RequestBody Map<String, String> body) {
        String value = body.get("value");
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Request body must contain a non-empty 'value' field");
        }
        secretsDAO.putSecret(name, value);
    }

    @DeleteMapping("/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a secret")
    public void deleteSecret(@PathVariable String name) {
        secretsDAO.deleteSecret(name);
    }
}
