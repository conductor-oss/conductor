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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.conductoross.conductor.common.JsonSchemaValidator;
import org.conductoross.conductor.core.exception.SchemaValidationException;
import org.conductoross.conductor.dao.schema.SchemaDAO;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.SchemaDef;
import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.metrics.Monitors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.networknt.schema.JsonSchemaException;
import com.networknt.schema.ValidationMessage;
import lombok.extern.slf4j.Slf4j;

/**
 * Schema registry: versioning, lookup, and removal of {@link SchemaDef}s backed by {@link
 * SchemaDAO}.
 *
 * <p>Lookups return {@code null} for absent schemas rather than throwing; turning a miss into a
 * {@code 404} is the controller's job.
 */
@Slf4j
@Service
@EnableConfigurationProperties(SchemaCacheProperties.class)
public class SchemaService {

    private static final String LATEST = "latest";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapperProvider().getObjectMapper();

    private final SchemaDAO schemaDAO;

    /** Null when caching is not configured (the default). */
    private final Cache<String, SchemaDef> cache;

    private final JsonSchemaValidator jsonSchemaValidator;

    public SchemaService(
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
        log.info(
                "Schema registry storing through {}, read cache {}",
                schemaDAO.getClass().getSimpleName(),
                cache == null ? "disabled" : "enabled, ttl " + cacheProperties.getTtl());
    }

    /** Mutates and returns the given schema, stamping the version and audit timestamps onto it. */
    public SchemaDef saveSchema(SchemaDef dto, boolean incrementVersion) {
        if (dto == null) {
            throw new IllegalArgumentException("Schema cannot be null");
        }
        if (StringUtils.isBlank(dto.getName())) {
            throw new IllegalArgumentException("Schema name cannot be blank");
        }

        SchemaDef stored = incrementVersion ? insertAtNextVersion(dto) : upsert(dto);
        invalidate(stored.getName(), stored.getVersion());
        return stored;
    }

    private SchemaDef upsert(SchemaDef schema) {
        if (schema.getVersion() < 1) {
            schema.setVersion(1);
        }
        Optional<SchemaDef> existing = lookup(schema.getName(), schema.getVersion());
        if (existing.isPresent()) {
            schema.setCreateTime(existing.get().getCreateTime());
            schema.setUpdateTime(System.currentTimeMillis());
        } else {
            stampCreation(schema);
        }
        schemaDAO.save(schema);
        return schema;
    }

    /** OSS Conductor has no authenticated principal, so created-by/updated-by stay null. */
    private static void stampCreation(SchemaDef schema) {
        schema.setCreateTime(System.currentTimeMillis());
        schema.setUpdateTime(null);
    }

    /**
     * Allocates the next version. Two concurrent saves for the same name can land on the same
     * version number; the later write wins.
     */
    private SchemaDef insertAtNextVersion(SchemaDef schema) {
        int highest = lookupLatest(schema.getName()).map(SchemaDef::getVersion).orElse(0);
        schema.setVersion(highest + 1);
        stampCreation(schema);
        schemaDAO.save(schema);
        return schema;
    }

    /** {@code null} rather than an exception when absent; see {@link SchemaService}. */
    public SchemaDef getSchemaByNameWithLatestVersion(String name) {
        requireName(name);
        return cached(key(name, LATEST), () -> lookupLatest(name)).orElse(null);
    }

    /** {@code null} rather than an exception when absent; see {@link SchemaService}. */
    public SchemaDef getSchemaByNameAndVersion(String name, int version) {
        requireName(name);
        return cached(key(name, String.valueOf(version)), () -> lookup(name, version)).orElse(null);
    }

    public List<SchemaDef> getAllSchemas() {
        return schemaDAO.getAll();
    }

    /** Straight through to the backend's projection, which reads no schema bodies. */
    public List<SchemaDef> getAllShortenedSchemas() {
        return schemaDAO.getAllShortenedSchemas();
    }

    public List<SchemaDef> getSchemas(String name, Integer version) {
        if (name == null) {
            return getAllSchemas();
        }
        if (version == null) {
            return getSchemasByName(name);
        }
        SchemaDef schema = getSchemaByNameAndVersion(name, version);
        if (schema == null) {
            throw new NotFoundException(
                    "No such schema found by name %s and version %d", name, version);
        }
        return List.of(schema);
    }

    /** Returns every version registered under {@code name}, highest version first. */
    public List<SchemaDef> getSchemasByName(String name) {
        requireName(name);
        return schemaDAO.findAllVersionsByName(name);
    }

    /** Removing a name that is not registered is reported rather than passed over as a no-op. */
    public void deleteSchemaByName(String name) {
        requireName(name);
        if (getSchemasByName(name).isEmpty()) {
            throw new NotFoundException("No schema found by name %s", name);
        }
        schemaDAO.deleteAllByName(name);
        invalidateName(name);
    }

    public void deleteSchemasByNamesBatch(List<String> names) {
        if (names == null || names.isEmpty()) {
            log.debug("No schema names provided for batch delete");
            return;
        }
        int deleted = schemaDAO.deleteAllByNames(names);
        names.forEach(this::invalidateName);
        log.info("Batch deleted {} schema versions across {} names", deleted, names.size());
    }

    /** Removing a version that is not registered is reported, as removing a whole name is. */
    public void deleteSchemaByNameAndVersion(String name, Integer version) {
        requireName(name);
        Objects.requireNonNull(version, "Schema version cannot be null");
        if (getSchemaByNameAndVersion(name, version) == null) {
            throw new NotFoundException("No schema found by name %s and version %d", name, version);
        }
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

    private Optional<SchemaDef> lookup(String name, int version) {
        return Optional.ofNullable(schemaDAO.findByNameAndVersion(name, version));
    }

    private Optional<SchemaDef> lookupLatest(String name) {
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

    private void invalidateName(String name) {
        if (cache == null) {
            return;
        }
        String prefix = name + "/";
        cache.asMap().keySet().removeIf(key -> key.startsWith(prefix));
    }

    /**
     * Checks {@code data} against {@code schema}. Inline schemas are used directly; named schemas
     * are resolved from the registry. An unresolvable reference leaves the payload unchecked (a
     * miss is counted); a bad schema document is logged and also left unchecked.
     *
     * @throws SchemaValidationException when data does not conform, or when the schema carries an
     *     external reference, has no type, or has an unsupported type.
     */
    public void validate(SchemaDef schema, Map<String, Object> data) {
        long start = System.currentTimeMillis();
        try {
            doValidate(schema, data);
        } catch (SchemaValidationException e) {
            // Central point for all callers, so metrics are recorded here rather than at each site.
            Monitors.recordSchemaValidationFailure(schema.getName());
            throw e;
        } finally {
            Monitors.recordSchemaValidationTime(System.currentTimeMillis() - start);
        }
    }

    private void doValidate(SchemaDef schema, Map<String, Object> data) {
        if (schema == null) {
            throw new IllegalArgumentException("Schema cannot be null");
        }

        Optional<SchemaDef> registered = resolve(schema);
        if (registered.isEmpty()) {
            return; // unresolvable reference — miss already counted in resolve()
        }
        SchemaDef resolved = registered.get();

        // TaskDef schema fields have no cascading validation, so a typeless schema reaches here.
        if (resolved.getType() == null) {
            throw new SchemaValidationException(
                    "Schema %s version %d has no type, so nothing can be validated against it",
                    resolved.getName(), resolved.getVersion());
        }
        if (resolved.getType() != SchemaDef.Type.JSON) {
            throw new SchemaValidationException("Unsupported schema type %s", resolved.getType());
        }

        String schemaContent;
        try {
            schemaContent = OBJECT_MAPPER.writeValueAsString(resolved.getData());
        } catch (JsonProcessingException e) {
            // Bad schema data — log it and skip validation rather than failing the payload.
            log.error(
                    "Error parsing the json schema {} version {}: {}",
                    resolved.getName(),
                    resolved.getVersion(),
                    e.getMessage(),
                    e);
            return;
        }

        Set<ValidationMessage> failures;
        try {
            failures = jsonSchemaValidator.validate(schemaContent, data == null ? Map.of() : data);
        } catch (JsonSchemaException e) {
            // Bad schema document — skip validation. networknt getMessage() is often empty,
            // so log the validation messages instead.
            log.error(
                    "Bad or unsupported schema {} version {}: {}",
                    resolved.getName(),
                    resolved.getVersion(),
                    describe(e),
                    e);
            return;
        }

        if (failures != null && !failures.isEmpty()) {
            throw new SchemaValidationException(
                    "Schema validation failed %s",
                    failures.stream()
                            .map(ValidationMessage::getMessage)
                            .collect(Collectors.joining(", ")));
        }
    }

    /**
     * Returns the schema to validate against. Inline schemas (carrying {@code data}) are returned
     * as-is; others are looked up by name and version. Version {@code < 1} resolves the latest.
     * Returns empty when the registry has no matching entry. {@code externalRef} is refused.
     */
    private Optional<SchemaDef> resolve(SchemaDef schema) {
        // An empty map is a valid JSON Schema (permits everything), so presence of data — not
        // non-emptiness — is what makes a schema inline.
        if (schema.getData() != null) {
            return Optional.of(schema);
        }
        if (schema.getExternalRef() != null) {
            throw new SchemaValidationException(
                    "external schema references are not yet supported %s", schema.getExternalRef());
        }
        if (StringUtils.isBlank(schema.getName())) {
            throw new SchemaValidationException(
                    "A schema was attached with neither an inline document nor a name to resolve it by");
        }
        String name = schema.getName();
        int version = schema.getVersion();
        Optional<SchemaDef> registered =
                version < 1
                        ? cached(key(name, LATEST), () -> lookupLatest(name))
                        : cached(key(name, String.valueOf(version)), () -> lookup(name, version));
        if (registered.isEmpty()) {
            log.debug(
                    "A definition references schema {} version {}, which is not registered",
                    name,
                    version);
            Monitors.recordSchemaRegistryMiss(name);
        }
        return registered;
    }

    private static String describe(JsonSchemaException e) {
        String messages = String.valueOf(e.getValidationMessages());
        return StringUtils.isNotBlank(e.getMessage()) ? e.getMessage() : messages;
    }
}
