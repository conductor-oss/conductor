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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.conductoross.conductor.common.JsonSchemaValidator;
import org.conductoross.conductor.core.exception.SchemaValidationException;
import org.conductoross.conductor.dao.schema.SchemaDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.SchemaDef;
import com.netflix.conductor.core.exception.ConflictException;
import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.metrics.Monitors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.networknt.schema.JsonSchemaException;
import com.networknt.schema.ValidationMessage;

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

    private static final Logger LOGGER = LoggerFactory.getLogger(SchemaServiceImpl.class);

    /**
     * How many times version allocation re-reads the current maximum after losing the insert. Each
     * failure means some other writer took the version this caller was aiming for, so a caller can
     * only lose once per concurrent writer; the bound exists to turn a pathological write storm
     * into a reported conflict rather than an unbounded loop.
     */
    private static final int MAX_VERSION_ALLOCATION_ATTEMPTS = 10;

    private static final String LATEST = "latest";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapperProvider().getObjectMapper();

    private final SchemaDAO schemaDAO;

    /**
     * Null unless a time-to-live is configured, which it is not by default. Entries are the
     * instances the DAO deserialized, shared between readers rather than copied — as the metadata
     * DAOs' definition caches are — so a caller must treat a schema it reads as read-only.
     */
    private final Cache<String, SchemaDef> cache;

    private final JsonSchemaValidator jsonSchemaValidator;

    public SchemaServiceImpl(
            SchemaDAO schemaDAO,
            SchemaCacheProperties cacheProperties,
            JsonSchemaValidator jsonSchemaValidator) {
        this.schemaDAO = schemaDAO;
        this.jsonSchemaValidator = jsonSchemaValidator;
        this.cache =
                cacheProperties.isEnabled()
                        ? Caffeine.newBuilder()
                                .maximumSize(cacheProperties.getMaxSize())
                                .expireAfterWrite(cacheProperties.getTtl())
                                .build()
                        : null;
        // Which backend the registry bound to, and whether reads are cached: the two facts that
        // decide where a schema went and how stale a read can be, answered without a request.
        LOGGER.info(
                "Schema registry storing through {}, read cache {}",
                schemaDAO.getClass().getSimpleName(),
                cache == null ? "disabled" : "enabled, ttl " + cacheProperties.getTtl());
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
        Optional<SchemaDef> existing = findByNameAndVersion(schema.getName(), schema.getVersion());
        if (existing.isPresent()) {
            schema.setCreateTime(existing.get().getCreateTime());
            schema.setUpdateTime(System.currentTimeMillis());
        } else {
            stampCreation(schema);
        }
        schemaDAO.save(schema);
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
                    findLatestVersionByName(schema.getName()).map(SchemaDef::getVersion).orElse(0);
            schema.setVersion(highest + 1);
            stampCreation(schema);
            if (schemaDAO.createSchemaIfAbsent(schema)) {
                return schema;
            }
            LOGGER.debug(
                    "Lost the race for schema {} version {}, re-reading the current maximum",
                    schema.getName(),
                    schema.getVersion());
        }
        LOGGER.warn(
                "Gave up allocating a new version for schema {} after {} attempts; writes to this"
                        + " name are contending",
                schema.getName(),
                MAX_VERSION_ALLOCATION_ATTEMPTS);
        Monitors.recordSchemaVersionAllocationConflict(schema.getName());
        throw new ConflictException(
                "Unable to allocate a new version for schema %s after %d attempts; another writer keeps claiming it",
                schema.getName(), MAX_VERSION_ALLOCATION_ATTEMPTS);
    }

    @Override
    public SchemaDef getSchema(String name) {
        requireName(name);
        return cached(key(name, LATEST), () -> findLatestVersionByName(name))
                .orElseThrow(() -> new NotFoundException("No such schema found by name %s", name));
    }

    @Override
    public SchemaDef getSchema(String name, int version) {
        requireName(name);
        return cached(key(name, String.valueOf(version)), () -> findByNameAndVersion(name, version))
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
        return schemaDAO.getAll();
    }

    @Override
    public void deleteSchema(String name) {
        requireName(name);
        schemaDAO.deleteAllByName(name);
        invalidateName(name);
    }

    @Override
    public void deleteSchema(String name, int version) {
        requireName(name);
        schemaDAO.deleteByNameAndVersion(name, version);
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
     * The two nullable reads on {@link SchemaDAO}, wrapped once each. Every lookup in this class
     * goes through one of them, so an absent schema becomes {@link Optional#empty()} here and null
     * never travels further in.
     */
    private Optional<SchemaDef> findByNameAndVersion(String name, int version) {
        return Optional.ofNullable(schemaDAO.findByNameAndVersion(name, version));
    }

    private Optional<SchemaDef> findLatestVersionByName(String name) {
        return Optional.ofNullable(schemaDAO.findLatestVersionByName(name));
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

    @Override
    public void validate(SchemaDef schema, Map<String, Object> data) {
        if (schema == null) {
            throw new IllegalArgumentException("Schema cannot be null");
        }

        SchemaDef resolved = resolve(schema);

        // The schema fields on a Task Definition carry no cascading-validation annotation, so a
        // schema with no type is accepted at registration and only discovered here. That is a
        // definition error and is reported as one, rather than passed over.
        if (resolved.getType() == null) {
            throw new SchemaValidationException(
                    "Schema %s version %d has no type, so nothing can be validated against it",
                    resolved.getName(), resolved.getVersion());
        }
        if (resolved.getType() != SchemaDef.Type.JSON) {
            throw new SchemaValidationException(
                    "Schema %s version %d is of type %s; this server validates JSON schemas only",
                    resolved.getName(), resolved.getVersion(), resolved.getType());
        }

        String schemaContent;
        try {
            schemaContent = OBJECT_MAPPER.writeValueAsString(resolved.getData());
        } catch (JsonProcessingException e) {
            throw new SchemaValidationException(
                    "Schema %s version %d could not be read: %s",
                    resolved.getName(), resolved.getVersion(), e.getMessage());
        }

        Set<ValidationMessage> failures;
        try {
            failures = jsonSchemaValidator.validate(schemaContent, data == null ? Map.of() : data);
        } catch (JsonSchemaException e) {
            // networknt reports an unusable schema document through an exception whose own
            // getMessage() is frequently empty, so the messages it carries are what get reported.
            throw new SchemaValidationException(
                    "Schema %s version %d is not a usable JSON schema: %s",
                    resolved.getName(), resolved.getVersion(), describe(e));
        }

        if (failures != null && !failures.isEmpty()) {
            throw new SchemaValidationException(
                    "Schema validation failed for %s version %d: %s",
                    resolved.getName(),
                    resolved.getVersion(),
                    failures.stream()
                            .map(ValidationMessage::getMessage)
                            .collect(Collectors.joining(", ")));
        }
    }

    /**
     * Returns the schema whose {@code data} is the document to validate against: the argument when
     * it inlines one, otherwise the registered version it names.
     *
     * <p>{@code externalRef} is deliberately not a third step. It is stored and returned unchanged,
     * and nothing dereferences it, so a schema carrying only an external reference is unresolvable
     * and is reported as a missing registration rather than silently skipped.
     */
    private SchemaDef resolve(SchemaDef schema) {
        // An empty document is a legal JSON Schema that permits anything, so `data` being present
        // — not being non-empty — is what makes a schema inline.
        if (schema.getData() != null) {
            return schema;
        }
        if (StringUtils.isBlank(schema.getName())) {
            throw new SchemaValidationException(
                    "A schema was attached with neither an inline document nor a name to resolve it by");
        }
        String name = schema.getName();
        int version = schema.getVersion();
        // Through the cache: under enforcement this runs on every scheduled task, which is the
        // read the cache exists for.
        Optional<SchemaDef> registered =
                version < 1
                        ? cached(key(name, LATEST), () -> findLatestVersionByName(name))
                        : cached(
                                key(name, String.valueOf(version)),
                                () -> findByNameAndVersion(name, version));
        if (registered.isEmpty()) {
            // A definition references a schema this server does not hold. The caller is told, but
            // the operator who has to register it never sees that response.
            LOGGER.warn(
                    "A definition references schema {} version {}, which is not registered",
                    name,
                    version);
            Monitors.recordSchemaRegistryMiss(name, version);
        }
        return registered.orElseThrow(
                () ->
                        new SchemaValidationException(
                                "No schema registered as %s version %d, referenced by a definition",
                                schema.getName(), schema.getVersion()));
    }

    private static String describe(JsonSchemaException e) {
        String messages = String.valueOf(e.getValidationMessages());
        return StringUtils.isNotBlank(e.getMessage()) ? e.getMessage() : messages;
    }
}
