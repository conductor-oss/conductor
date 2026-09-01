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
package org.conductoross.conductor.controllers;

import java.util.List;

import org.conductoross.conductor.service.SchemaService;
import org.springframework.http.MediaType;
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

import static com.netflix.conductor.rest.config.RequestMappingConstants.SCHEMA;

/**
 * REST controller for the schema registry.
 *
 * <p>Unauthenticated, as the rest of the OSS API is. Audit fields are never populated because there
 * is no authenticated principal to populate them from, and the object mapper omits nulls, so {@code
 * createdBy} and {@code updatedBy} are simply absent from responses.
 *
 * <p>Method names are the operation ids the SDK generators read off the API description, so
 * renaming one renames a method in every generated client.
 *
 * <p>{@code save} returns no body. That is what the published contract says, and the six shipped
 * schema clients all declare the call {@code void}; returning the stored definitions here would put
 * a response type in an SDK generated from this server that an SDK generated from the commercial
 * one does not have. A caller that needs the version a {@code newVersion=true} save landed on reads
 * it back with {@link #getSchemaByNameWithLatestVersion}.
 */
@RestController
@RequestMapping(value = SCHEMA, produces = MediaType.APPLICATION_JSON_VALUE)
public class SchemaResource {

    private final SchemaService schemaService;

    public SchemaResource(SchemaService schemaService) {
        this.schemaService = schemaService;
    }

    /**
     * The body is a list. Three of the six shipped schema clients post a bare object instead, which
     * is accepted because the server sets {@code
     * spring.jackson.deserialization.accept-single-value-as-array}. Taking a bare {@code SchemaDef}
     * here instead would reject the other three.
     *
     * <p>Deliberately not {@code @Valid}. {@link SchemaDef} declares {@code @NotNull} on its type,
     * but a definition may already carry a schema with no type — the schema fields on task and
     * workflow definitions have no cascading-validation annotation, so such a schema is registrable
     * today — and rejecting it here would refuse a payload the rest of the server accepts. A
     * missing name is still rejected, by the service, as a 400.
     */
    @PostMapping
    @Operation(summary = "Save schema")
    public void save(
            @RequestBody List<SchemaDef> schemas,
            @RequestParam(value = "newVersion", defaultValue = "false") boolean newVersion) {
        schemaService.saveSchemas(schemas, newVersion);
    }

    @GetMapping
    @Operation(summary = "Get all schemas")
    public List<SchemaDef> getAllSchemas(
            @RequestParam(value = "short", defaultValue = "false") boolean shortForm) {
        List<SchemaDef> schemas = schemaService.getAllSchemas();
        return shortForm ? schemas.stream().map(SchemaResource::nameAndVersion).toList() : schemas;
    }

    @GetMapping("/{name}")
    @Operation(summary = "Get schema by name with latest version")
    public SchemaDef getSchemaByNameWithLatestVersion(@PathVariable("name") String name) {
        return schemaService.getSchema(name);
    }

    @GetMapping("/{name}/{version}")
    @Operation(summary = "Get schema by name and version")
    public SchemaDef getSchemaByNameAndVersion(
            @PathVariable("name") String name, @PathVariable("version") int version) {
        return schemaService.getSchema(name, version);
    }

    @DeleteMapping("/{name}")
    @Operation(summary = "Delete all versions of schema by name")
    public void deleteSchemaByName(@PathVariable("name") String name) {
        schemaService.deleteSchema(name);
    }

    @DeleteMapping("/{name}/{version}")
    @Operation(summary = "Delete a version of schema by name")
    public void deleteSchemaByNameAndVersion(
            @PathVariable("name") String name, @PathVariable("version") int version) {
        schemaService.deleteSchema(name, version);
    }

    /**
     * The shortened listing a picker asks for: enough to identify a schema, none of its body.
     * Copied onto a fresh instance rather than blanked on the stored one, which the service may be
     * handing out from its cache.
     *
     * <p>The timestamps are left unset and therefore serialize as {@code 0}, because {@link
     * com.netflix.conductor.common.metadata.Auditable#getCreateTime()} substitutes zero for null.
     * Carrying the real ones would put more than a name and a version in a listing whose whole
     * purpose is to carry less.
     */
    private static SchemaDef nameAndVersion(SchemaDef schema) {
        SchemaDef summary = new SchemaDef();
        summary.setName(schema.getName());
        summary.setVersion(schema.getVersion());
        return summary;
    }
}
