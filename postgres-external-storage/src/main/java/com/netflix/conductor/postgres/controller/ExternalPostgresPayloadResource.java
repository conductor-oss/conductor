/*
 * Copyright 2022 Netflix, Inc.
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
package com.netflix.conductor.postgres.controller;

import java.io.InputStream;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.netflix.conductor.common.utils.ExternalPayloadStorage;

import io.swagger.v3.oas.annotations.Operation;

/**
 * REST controller for pulling payload stream of data by key (externalPayloadPath) from PostgreSQL
 * database
 */
@RestController
@RequestMapping(value = "/api/external/postgres")
@ConditionalOnProperty(name = "conductor.external-payload-storage.type", havingValue = "postgres")
public class ExternalPostgresPayloadResource {

    private final ExternalPayloadStorage postgresService;

    public ExternalPostgresPayloadResource(
            @Qualifier("postgresExternalPayloadStorage") ExternalPayloadStorage postgresService) {
        this.postgresService = postgresService;
    }

    @GetMapping("/{externalPayloadPath}")
    @Operation(
            summary =
                    "Get task or workflow by externalPayloadPath from External PostgreSQL Storage")
    public ResponseEntity<InputStreamResource> getExternalStorageData(
            @PathVariable("externalPayloadPath") String externalPayloadPath) {
        InputStream inputStream = postgresService.download(externalPayloadPath);
        InputStreamResource outputStreamBody = new InputStreamResource(inputStream);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(outputStreamBody);
    }
}
