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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.netflix.conductor.common.metadata.SchemaDef;
import com.netflix.conductor.core.exception.NotFoundException;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

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
 * a response type in an SDK generated from this server that the published contract does not
 * declare. A caller that needs the version a {@code newVersion=true} save landed on reads it back
 * with {@link #getSchemaByNameWithLatestVersion}.
 */
@RestController
@RequestMapping(value = SCHEMA, produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Validated
public class SchemaResource {

    private final SchemaService schemaService;

    /**
     * The body is a list. Three of the six shipped schema clients post a bare object instead, which
     * is accepted because the server sets {@code
     * spring.jackson.deserialization.accept-single-value-as-array}. Taking a bare {@code SchemaDef}
     * here instead would reject the other three.
     *
     * <p>{@code @Valid} here validates the list, not its elements — cascading into them would need
     * {@code List<@Valid SchemaDef>} — so {@link SchemaDef}'s {@code @NotNull} on {@code type} is
     * not enforced by it, and a schema with no type is stored. That is the behaviour to keep: the
     * schema fields on task and workflow definitions carry no cascading validation either, so
     * refusing a type-less schema only here would make the two paths disagree. {@code
     * SchemaService.validate} reports it when such a schema is used. A missing name is still
     * rejected, by the service, as a 400.
     */
    @PostMapping
    @Operation(summary = "Save schema")
    public void save(
            @Valid @RequestBody List<SchemaDef> schemas,
            @RequestParam(value = "newVersion", defaultValue = "false") boolean newVersion) {
        if (schemas == null) {
            return;
        }
        // Saved one at a time, because the service stores one at a time. Unwinding the list here
        // rather than adding a bulk method to the service keeps that method off an interface where
        // only this caller would use it. Not atomic across the list: a failure part-way leaves the
        // schemas already saved in place.
        for (SchemaDef schema : schemas) {
            schemaService.saveSchema(schema, newVersion);
        }
    }

    @GetMapping
    @Operation(summary = "Get all schemas")
    public List<SchemaDef> listAllSchemas(
            @RequestParam(value = "short", defaultValue = "false") boolean shortened) {
        return shortened ? schemaService.getAllShortenedSchemas() : schemaService.getAllSchemas();
    }

    @GetMapping("/{name}")
    @Operation(summary = "Get schema by name with latest version")
    public SchemaDef getSchemaByNameWithLatestVersion(@PathVariable("name") String name) {
        return found(
                schemaService.getSchemaByNameWithLatestVersion(name),
                "No such schema found by name %s".formatted(name));
    }

    /** A {@code null} version asks for no particular one, and reads the latest. */
    @GetMapping("/{name}/{version}")
    @Operation(summary = "Get schema by name and version")
    public SchemaDef getSchemaByNameAndVersion(
            @PathVariable("name") String name, @PathVariable("version") Integer version) {
        if (version == null) {
            return getSchemaByNameWithLatestVersion(name);
        }
        return found(
                schemaService.getSchemaByNameAndVersion(name, version),
                "No such schema found by name %s and version %d".formatted(name, version));
    }

    @DeleteMapping("/{name}")
    @Operation(summary = "Delete all versions of schema by name")
    public void deleteSchemaByName(@PathVariable("name") String name) {
        schemaService.deleteSchemaByName(name);
    }

    /**
     * A {@code null} version names no particular one, and removes the latest — not the whole
     * history, which is what {@link #deleteSchemaByName} is for.
     */
    @DeleteMapping("/{name}/{version}")
    @Operation(summary = "Delete a version of schema by name")
    public void deleteSchemaByNameAndVersion(
            @PathVariable("name") String name, @PathVariable("version") Integer version) {
        Integer target =
                version != null
                        ? version
                        : found(
                                        schemaService.getSchemaByNameWithLatestVersion(name),
                                        "No such schema found by name %s".formatted(name))
                                .getVersion();
        schemaService.deleteSchemaByNameAndVersion(name, target);
    }

    /**
     * Turns an unregistered schema into a {@code 404}. {@link SchemaService} reports one as {@code
     * null} rather than by throwing, so this is the one place the status is decided.
     */
    private static SchemaDef found(SchemaDef schema, String message) {
        if (schema == null) {
            throw new NotFoundException(message);
        }
        return schema;
    }
}
