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

import com.netflix.conductor.common.metadata.SchemaDef;

/**
 * The schema registry. Owns versioning, lookup and removal of {@link SchemaDef}s on top of {@link
 * org.conductoross.conductor.dao.schema.SchemaDAO}.
 *
 * <p>This is a service of its own rather than a set of methods on {@code MetadataService} because
 * the callers that resolve a schema need only this, and should not take a dependency on the whole
 * metadata surface to perform one lookup.
 */
public interface SchemaService {

    /**
     * Stores each schema and returns what was stored.
     *
     * <p>A schema arriving without a version is stored at version 1, which is the default {@link
     * SchemaDef}'s own builder declares; there is no version 0.
     *
     * @param newVersion when {@code true}, each schema is stored at one past the highest version
     *     currently registered under its name, leaving definitions pinned to older versions
     *     untouched. When {@code false}, each schema overwrites whatever is stored at the version
     *     it carries.
     */
    List<SchemaDef> saveSchemas(List<SchemaDef> schemas, boolean newVersion);

    /** Stores one schema. See {@link #saveSchemas} for {@code newVersion}. */
    SchemaDef saveSchema(SchemaDef schema, boolean newVersion);

    /**
     * Returns the highest-versioned schema registered under {@code name}.
     *
     * @throws com.netflix.conductor.core.exception.NotFoundException when no version is registered.
     */
    SchemaDef getSchema(String name);

    /**
     * Returns one version.
     *
     * @throws com.netflix.conductor.core.exception.NotFoundException when it is not registered.
     */
    SchemaDef getSchema(String name, int version);

    /** Returns every version of every registered schema. */
    List<SchemaDef> getAllSchemas();

    /** Removes every version registered under {@code name}. */
    void deleteSchema(String name);

    /** Removes one version, leaving the rest of the name's history in place. */
    void deleteSchema(String name, int version);
}
