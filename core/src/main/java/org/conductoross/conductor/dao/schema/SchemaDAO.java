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
package org.conductoross.conductor.dao.schema;

import java.util.List;

import com.netflix.conductor.common.metadata.SchemaDef;

/**
 * Persistence for {@link SchemaDef}, the schema registry's storage seam. Implemented per supported
 * metadata backend (PostgreSQL, MySQL, SQLite, Redis).
 *
 * <p>A schema is addressed by {@code name} and {@code version}; the pair is unique. The payload is
 * stored whole, so every field of {@link SchemaDef} — including {@code externalRef}, which nothing
 * resolves — round-trips unchanged.
 *
 * <p>No method takes a tenant identifier: OSS Conductor is single-tenant.
 *
 * <p>A backend that implements none of this still gets a registry: {@link InMemorySchemaDAO} is
 * wired in as the default, so the server starts and the registry works, at the cost of losing its
 * contents on restart.
 */
public interface SchemaDAO {

    /**
     * Upserts the schema, or overwrites the one already stored at the same {@code name} and {@code
     * version}.
     */
    void save(SchemaDef schemaDef);

    /**
     * Returns the schema at {@code name} and {@code version}, or {@code null} when there is none.
     *
     * @param version must not be null
     */
    SchemaDef findByNameAndVersion(String name, Integer version);

    /**
     * Returns the highest-versioned schema stored under {@code name}, or {@code null} when there is
     * none.
     */
    SchemaDef findLatestVersionByName(String name);

    /** Returns every version of every schema, ordered by name and then version. */
    List<SchemaDef> getAll();

    /**
     * Removes one version, returning how many were removed — one, or zero when it was already
     * absent.
     *
     * @param version must not be null
     */
    int deleteByNameAndVersion(String name, Integer version);

    /**
     * Removes every version stored under {@code name}, returning how many were removed — zero when
     * the name is unknown.
     */
    int deleteAllByName(String name);

    /**
     * Bulk delete operation
     *
     * @param names names of the schemas to delete
     * @return no. of schemas deleted
     */
    int deleteAllByNames(List<String> names);

    /**
     * @param name name of the schema
     * @return returns all the versions
     */
    List<SchemaDef> findAllVersionsByName(String name);

    /**
     * @return List of schema name and versions without entire schema definitions
     */
    List<SchemaDef> getAllShortenedSchemas();
}
