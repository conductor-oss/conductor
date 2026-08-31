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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;
import org.conductoross.conductor.dao.schema.SchemaDAO;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import com.netflix.conductor.common.metadata.SchemaDef;
import com.netflix.conductor.core.exception.ConflictException;
import com.netflix.conductor.core.exception.NotFoundException;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Default {@link SchemaService}.
 *
 * <p>{@link SchemaDAO} is a required constructor dependency and no implementation is registered by
 * default, so a server on a backend without one fails at startup instead of accepting schema writes
 * it cannot store.
 */
@Service
@EnableConfigurationProperties(SchemaCacheProperties.class)
public class SchemaServiceImpl implements SchemaService {

    /**
     * How many times version allocation re-reads the current maximum after losing the insert. Each
     * failure means some other writer took the version this caller was aiming for, so a caller can
     * only lose once per concurrent writer; the bound exists to turn a pathological write storm
     * into a reported conflict rather than an unbounded loop.
     */
    private static final int MAX_VERSION_ALLOCATION_ATTEMPTS = 10;

    private static final String LATEST = "latest";

    private final SchemaDAO schemaDAO;

    /**
     * Null unless a time-to-live is configured, which it is not by default. Entries are the
     * instances the DAO deserialized, shared between readers rather than copied — as the metadata
     * DAOs' definition caches are — so a caller must treat a schema it reads as read-only.
     */
    private final Cache<String, SchemaDef> cache;

    public SchemaServiceImpl(SchemaDAO schemaDAO, SchemaCacheProperties cacheProperties) {
        this.schemaDAO = schemaDAO;
        this.cache =
                cacheProperties.isEnabled()
                        ? Caffeine.newBuilder()
                                .maximumSize(cacheProperties.getMaxSize())
                                .expireAfterWrite(cacheProperties.getTtl())
                                .build()
                        : null;
    }

    /**
     * Saves each schema in turn. Not atomic across the list: a failure part-way leaves the schemas
     * already saved in place, and reports which one failed.
     */
    @Override
    public List<SchemaDef> saveSchemas(List<SchemaDef> schemas, boolean newVersion) {
        if (schemas == null || schemas.isEmpty()) {
            return List.of();
        }
        List<SchemaDef> saved = new ArrayList<>(schemas.size());
        for (SchemaDef schema : schemas) {
            saved.add(saveSchema(schema, newVersion));
        }
        return saved;
    }

    /**
     * Mutates and returns the schema it was given — the version it landed at and its audit
     * timestamps are stamped onto that instance, as the metadata services do. Callers that need
     * their argument left alone should pass a copy.
     */
    @Override
    public SchemaDef saveSchema(SchemaDef schema, boolean newVersion) {
        if (schema == null) {
            throw new IllegalArgumentException("Schema cannot be null");
        }
        if (StringUtils.isBlank(schema.getName())) {
            throw new IllegalArgumentException("Schema name cannot be blank");
        }

        SchemaDef stored = newVersion ? insertAtNextVersion(schema) : upsert(schema);
        invalidate(stored.getName(), stored.getVersion());
        return stored;
    }

    private SchemaDef upsert(SchemaDef schema) {
        if (schema.getVersion() < 1) {
            schema.setVersion(1);
        }
        // An in-place save keeps the version's original creation time and stamps the update. The
        // read is what tells the two apart: the same call creates a version or corrects one.
        Optional<SchemaDef> existing = schemaDAO.getSchema(schema.getName(), schema.getVersion());
        if (existing.isPresent()) {
            schema.setCreateTime(existing.get().getCreateTime());
            schema.setUpdateTime(System.currentTimeMillis());
        } else {
            stampCreation(schema);
        }
        schemaDAO.saveSchema(schema);
        return schema;
    }

    /**
     * OSS Conductor has no authenticated principal, so the created-by and updated-by fields stay
     * unset; the object mapper omits null properties, so they simply do not appear.
     */
    private static void stampCreation(SchemaDef schema) {
        schema.setCreateTime(System.currentTimeMillis());
        schema.setUpdateTime(null);
    }

    /**
     * Allocates the next version by reading the current maximum and letting the unique constraint
     * on (name, version) decide the race. A rejected insert means another writer took the version
     * first, so the maximum is re-read and the insert retried; nothing is overwritten either way.
     */
    private SchemaDef insertAtNextVersion(SchemaDef schema) {
        for (int attempt = 0; attempt < MAX_VERSION_ALLOCATION_ATTEMPTS; attempt++) {
            int highest =
                    schemaDAO
                            .getLatestSchema(schema.getName())
                            .map(SchemaDef::getVersion)
                            .orElse(0);
            schema.setVersion(highest + 1);
            stampCreation(schema);
            if (schemaDAO.createSchemaIfAbsent(schema)) {
                return schema;
            }
        }
        throw new ConflictException(
                "Unable to allocate a new version for schema %s after %d attempts; another writer keeps claiming it",
                schema.getName(), MAX_VERSION_ALLOCATION_ATTEMPTS);
    }

    @Override
    public SchemaDef getSchema(String name) {
        requireName(name);
        return cached(key(name, LATEST), () -> schemaDAO.getLatestSchema(name))
                .orElseThrow(() -> new NotFoundException("No such schema found by name %s", name));
    }

    @Override
    public SchemaDef getSchema(String name, int version) {
        requireName(name);
        return cached(key(name, String.valueOf(version)), () -> schemaDAO.getSchema(name, version))
                .orElseThrow(
                        () ->
                                new NotFoundException(
                                        "No such schema found by name %s and version %d",
                                        name, version));
    }

    /**
     * Always read from the backend. Caching a whole listing under one key would go stale on every
     * write to any schema, and the per-key entries cannot answer "what exists".
     */
    @Override
    public List<SchemaDef> getAllSchemas() {
        return schemaDAO.getAllSchemas();
    }

    @Override
    public void deleteSchema(String name) {
        requireName(name);
        schemaDAO.deleteSchemaByName(name);
        invalidateName(name);
    }

    @Override
    public void deleteSchema(String name, int version) {
        requireName(name);
        schemaDAO.deleteSchema(name, version);
        invalidate(name, version);
    }

    private static void requireName(String name) {
        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("Schema name cannot be blank");
        }
    }

    private static String key(String name, String version) {
        return name + "/" + version;
    }

    /**
     * Reads through the cache when one is configured. A miss is never cached: a schema that does
     * not exist yet is the one most likely to be created moments later.
     */
    private Optional<SchemaDef> cached(String key, Supplier<Optional<SchemaDef>> loader) {
        if (cache == null) {
            return loader.get();
        }
        return Optional.ofNullable(cache.get(key, ignored -> loader.get().orElse(null)));
    }

    /** Drops one version and the name's latest pointer, which that version may have been. */
    private void invalidate(String name, int version) {
        if (cache == null) {
            return;
        }
        cache.invalidate(key(name, String.valueOf(version)));
        cache.invalidate(key(name, LATEST));
    }

    /**
     * Drops every entry for a name by prefix. The separator keeps {@code order} from matching
     * {@code orders}, but a name containing a slash can still match a neighbour's keys — which
     * costs a cache miss and nothing else, so the match is deliberately left broad rather than
     * exact.
     */
    private void invalidateName(String name) {
        if (cache == null) {
            return;
        }
        String prefix = name + "/";
        cache.asMap().keySet().removeIf(key -> key.startsWith(prefix));
    }
}
