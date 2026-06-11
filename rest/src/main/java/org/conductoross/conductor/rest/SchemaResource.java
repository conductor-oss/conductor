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
package org.conductoross.conductor.rest;

import java.util.List;

import org.conductoross.conductor.service.SchemaService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.netflix.conductor.common.metadata.SchemaDef;

import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping("/api/schema")
public class SchemaResource {

    private final SchemaService schemaService;

    public SchemaResource(SchemaService schemaService) {
        this.schemaService = schemaService;
    }

    @PostMapping
    @Operation(summary = "Create or update a schema definition")
    public void save(
            @RequestBody SchemaDef schemaDef,
            @RequestParam(value = "newVersion", defaultValue = "false") boolean newVersion) {
        schemaService.save(schemaDef, newVersion);
    }

    @GetMapping("/{name}")
    @Operation(summary = "Retrieves the latest version of a schema definition")
    public SchemaDef getSchemaByNameWithLatestVersion(@PathVariable("name") String name) {
        return schemaService.getSchemaByNameWithLatestVersion(name);
    }

    @GetMapping("/{name}/{version}")
    @Operation(summary = "Retrieves a schema definition by name and version")
    public SchemaDef getSchemaByNameAndVersion(
            @PathVariable("name") String name, @PathVariable("version") Integer version) {
        return schemaService.getSchemaByNameAndVersion(name, version);
    }

    @GetMapping
    @Operation(summary = "Retrieves all schema definitions")
    public List<SchemaDef> getAllSchemas(
            @RequestParam(value = "short", defaultValue = "false") boolean shortResult) {
        return schemaService.getAllSchemas(shortResult);
    }

    @DeleteMapping("/{name}")
    @Operation(summary = "Deletes all versions of a schema definition")
    public void deleteSchemaByName(@PathVariable("name") String name) {
        schemaService.deleteSchemaByName(name);
    }

    @DeleteMapping("/{name}/{version}")
    @Operation(summary = "Deletes a schema definition by name and version")
    public void deleteSchemaByNameAndVersion(
            @PathVariable("name") String name, @PathVariable("version") Integer version) {
        schemaService.deleteSchemaByNameAndVersion(name, version);
    }
}
