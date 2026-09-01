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
package org.conductoross.conductor.service;

import java.util.List;
import java.util.Map;

import com.netflix.conductor.common.metadata.SchemaDef;

/**
 * The schema registry. Owns versioning, lookup and removal of {@link SchemaDef}s on top of {@link
 * org.conductoross.conductor.dao.schema.SchemaDAO}.
 *
 * <p>This is a service of its own rather than a set of methods on {@code MetadataService} because
 * the callers that resolve a schema need only this, and should not take a dependency on the whole
 * metadata surface to perform one lookup.
 *
 * <p>This method set is a fixed contract. Adding, removing or renaming one is a breaking change for
 * every implementation, so treat the shape here as settled and put new behaviour behind an existing
 * method or in {@link org.conductoross.conductor.controllers.SchemaResource}, which is what adapts
 * this surface to the published endpoints.
 *
 * <p>The lookups report a schema that is not registered as {@code null} rather than by throwing.
 * Turning an absent schema into a {@code 404} belongs to the controller, so that an implementation
 * of this interface decides what exists and the transport decides what a caller sees.
 */
public interface SchemaService {

    /** Returns every version of every registered schema. */
    List<SchemaDef> getAllSchemas();

    /**
     * Returns a name and a version for every registered schema and nothing else — no type, no
     * document. This is what a picker lists, and it reads only the two indexed columns, so opening
     * a dropdown does not pull every schema body off the backend.
     */
    List<SchemaDef> getAllShortenedSchemas();

    /**
     * Dispatches on what was asked for: every schema when {@code name} is null, every version under
     * that name when only {@code version} is null, otherwise the single version as a one-element
     * list.
     *
     * @throws com.netflix.conductor.core.exception.NotFoundException when both are given and that
     *     version is not registered.
     */
    List<SchemaDef> getSchemas(String name, Integer version);

    /** Returns every version registered under {@code name}, highest version first. */
    List<SchemaDef> getSchemasByName(String name);

    /** Returns one version, or {@code null} when it is not registered. */
    SchemaDef getSchemaByNameAndVersion(String name, int version);

    /**
     * Returns the highest-versioned schema registered under {@code name}, or {@code null} when no
     * version is.
     */
    SchemaDef getSchemaByNameWithLatestVersion(String name);

    /**
     * Stores one schema and returns what was stored.
     *
     * <p>A schema arriving without a version is stored at version 1, which is the default {@link
     * SchemaDef}'s own builder declares; there is no version 0.
     *
     * @param incrementVersion when {@code true}, the schema is stored at one past the highest
     *     version currently registered under its name, leaving definitions pinned to older versions
     *     untouched. When {@code false}, it overwrites whatever is stored at the version it
     *     carries.
     */
    SchemaDef saveSchema(SchemaDef dto, boolean incrementVersion);

    /**
     * Removes every version registered under {@code name}.
     *
     * @throws com.netflix.conductor.core.exception.NotFoundException when the name is not
     *     registered. Unlike {@link #deleteSchemasByNamesBatch}, a delete naming one schema reports
     *     that it removed nothing rather than succeeding silently.
     */
    void deleteSchemaByName(String name);

    /**
     * Removes every version of every name given, in one backend call. A null or empty list removes
     * nothing, and a name in the list that is not registered contributes nothing rather than
     * failing the batch — unlike {@link #deleteSchemaByName}, which names one schema and reports
     * when it is not there.
     */
    void deleteSchemasByNamesBatch(List<String> names);

    /**
     * Removes one version, leaving the rest of the name's history in place.
     *
     * @throws com.netflix.conductor.core.exception.NotFoundException when that version is not
     *     registered.
     */
    void deleteSchemaByNameAndVersion(String name, Integer version);

    /**
     * Checks {@code data} against {@code schema}, and does nothing when it conforms.
     *
     * <p>This is the only place a payload is checked against a schema. The engine's enforcement
     * hooks and the AI layer both call it, so the rules below hold identically wherever a schema is
     * enforced:
     *
     * <ul>
     *   <li>An inline schema — one carrying {@code data} — is used as it stands.
     *   <li>Otherwise the schema is a reference, and is resolved from the registry by its name and
     *       version. A version below 1 means no version was asked for and resolves the latest —
     *       note that {@link SchemaDef} defaults its version to 1, so a definition wanting the
     *       latest has to say {@code 0} outright.
     *   <li>{@code externalRef} is never dereferenced. A schema that carries one instead of a
     *       document is refused outright rather than looked up by its name.
     * </ul>
     *
     * <p>Two cases leave the payload unvalidated rather than failing it, because in neither is
     * there a document to check against: a reference the registry does not hold — counted as a
     * registry miss — and a registered document that cannot be read or that the validator cannot
     * use, which is logged. Both are definition errors, and neither is the payload's fault.
     *
     * @throws org.conductoross.conductor.core.exception.SchemaValidationException when the data
     *     does not conform, or when the schema cannot be enforced and saying so is the only honest
     *     answer: it carries an external reference, it carries no type, or its type is one this
     *     server does not validate.
     */
    void validate(SchemaDef schema, Map<String, Object> data);
}
