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
 * <p>Nothing satisfies this dependency by default. A server on a backend with no implementation
 * fails to start rather than accepting schema writes it cannot store.
 */
public interface SchemaDAO {

    /**
     * Inserts the schema, or overwrites the one already stored at the same {@code name} and {@code
     * version}. This is the update-in-place path; {@link #createSchemaIfAbsent} is the path that
     * allocates a new version.
     */
    void save(SchemaDef schemaDef);

    /**
     * Inserts the schema only when nothing is stored at its {@code name} and {@code version},
     * returning {@code false} when the unique constraint rejects it.
     *
     * <p>This is the concurrency-safe half of version allocation: two callers that read the same
     * current maximum version both attempt the same insert, and exactly one of them is told it
     * lost. Implementations must decide this in the database rather than by reading first, so the
     * check and the insert cannot be interleaved.
     */
    boolean createSchemaIfAbsent(SchemaDef schemaDef);

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

    /**
     * Returns every version stored under {@code name}, highest version first, or an empty list when
     * there is none.
     *
     * <p>Descending, unlike {@link #getAll()}: the first element is the latest version.
     */
    List<SchemaDef> findAllVersionsByName(String name);

    /**
     * Returns every stored schema carrying only its name and version, so a caller that needs to
     * know what exists does not transfer every schema document to find out.
     *
     * <p>The returned definitions are deliberately partial: no type, no data, no external
     * reference. Ordered by name and then version, as {@link #getAll()} is.
     */
    List<SchemaDef> getAllShortenedSchemas();

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
     * Removes every version of every name given, returning how many were removed. An empty or null
     * list removes nothing and returns zero.
     */
    int deleteAllByNames(List<String> names);
}
