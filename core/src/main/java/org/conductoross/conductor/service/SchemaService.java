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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.conductoross.conductor.common.JsonSchemaValidator;
import org.conductoross.conductor.core.exception.SchemaValidationException;
import org.conductoross.conductor.dao.schema.InMemorySchemaDAO;
import org.conductoross.conductor.dao.schema.SchemaDAO;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.SchemaDef;
import com.netflix.conductor.core.exception.NotFoundException;

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
 *
 * <p>Not every metadata backend implements a {@link SchemaDAO}. One that does not falls back to
 * {@link InMemorySchemaDAO}, so the registry works everywhere — but its contents are lost on
 * restart and not shared between servers, so a deployment relying on the registry belongs on a
 * backend that implements storage.
 */
@Slf4j
@Service
@EnableConfigurationProperties(SchemaCacheProperties.class)
public class SchemaService {

    private static final String LATEST = "latest";

    /**
     * Server-injected keys removed from a payload before it is checked.
     *
     * <p>These are not part of any caller's contract, so a schema declaring {@code
     * additionalProperties: false} must not fail on them. Nothing in this server injects one today;
     * the set exists so that a deployment which does — Conductor's commercial build puts {@code
     * _createdBy} on an event task's input — validates the same payload the caller sent.
     */
    private static final Set<String> INTERNAL_FIELDS = Set.of("_createdBy");

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
                schemaDAO,
                cache == null ? "disabled" : "enabled, ttl " + cacheProperties.getTtl());
    }

    /**
     * Registers {@code dto} and returns what was stored.
     *
     * <p>The branching mirrors Conductor's commercial build so this service can stand in for it:
     * the store is probed at the name and version given, and what happens next depends on whether
     * something is already there.
     *
     * <ul>
     *   <li>Nothing there — stored at the version given, verbatim. Note this ignores {@code
     *       incrementVersion}: naming a version nothing occupies registers that version, it does
     *       not allocate the next one.
     *   <li>Something there, {@code incrementVersion} false — the registered entry's document is
     *       replaced in place. Its {@code type} is left as registered, so an in-place save cannot
     *       change a schema's type.
     *   <li>Something there, {@code incrementVersion} true — stored one past the highest version
     *       registered under the name.
     * </ul>
     *
     * <p>Two deviations from that build, both deliberate:
     *
     * <ul>
     *   <li>A version below 1 is raised to 1. That build has no such coercion because it relies on
     *       {@link SchemaDef}'s version field having defaulted to 1; it defaults to 0 here, where 0
     *       on a reference means "latest", so without this a save naming no version would register
     *       version 0 — a version no reference can pin and the docs say is 1.
     *   <li>{@code externalRef} is carried through. That build's save drops it, which loses a field
     *       the caller sent and contradicts {@link SchemaDAO}'s round-trip contract.
     * </ul>
     *
     * <p>Created-by and updated-by stay null: there is no authenticated principal here.
     */
    public SchemaDef saveSchema(SchemaDef dto, boolean incrementVersion) {
        if (dto == null) {
            throw new IllegalArgumentException("Schema cannot be null");
        }
        if (StringUtils.isBlank(dto.getName())) {
            throw new IllegalArgumentException("Schema name cannot be blank");
        }

        if (dto.getVersion() < 1) {
            dto.setVersion(1);
        }

        long now = System.currentTimeMillis();
        SchemaDef registered = schemaDAO.findByNameAndVersion(dto.getName(), dto.getVersion());
        SchemaDef stored;

        if (registered == null) {
            stored = copyOf(dto, dto.getVersion());
            stored.setCreateTime(now);
            stored.setUpdateTime(now);
        } else if (!incrementVersion) {
            stored = registered;
            stored.setData(dto.getData());
            stored.setUpdateTime(now);
        } else {
            // findAllVersionsByName is documented highest-first, so its head is the highest
            // version; findLatestVersionByName answers the same question in one round trip.
            stored = copyOf(dto, lookupLatest(dto.getName()).getVersion() + 1);
            stored.setCreateTime(now);
            stored.setUpdateTime(now);
        }

        schemaDAO.save(stored);
        invalidate(stored.getName(), stored.getVersion());
        return stored;
    }

    /** A fresh definition at {@code version}, carrying every field the caller sent. */
    private static SchemaDef copyOf(SchemaDef dto, int version) {
        SchemaDef copy = new SchemaDef();
        copy.setName(dto.getName());
        copy.setVersion(version);
        copy.setType(dto.getType());
        copy.setData(dto.getData());
        copy.setExternalRef(dto.getExternalRef());
        return copy;
    }

    /** {@code null} rather than an exception when absent; see {@link SchemaService}. */
    public SchemaDef getSchemaByNameWithLatestVersion(String name) {
        requireName(name);
        return cached(key(name, LATEST), () -> lookupLatest(name));
    }

    /** {@code null} rather than an exception when absent; see {@link SchemaService}. */
    public SchemaDef getSchemaByNameAndVersion(String name, int version) {
        requireName(name);
        return cached(key(name, String.valueOf(version)), () -> lookup(name, version));
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

    private SchemaDef lookup(String name, int version) {
        return schemaDAO.findByNameAndVersion(name, version);
    }

    private SchemaDef lookupLatest(String name) {
        return schemaDAO.findLatestVersionByName(name);
    }

    /**
     * Reads through the cache when one is configured. A miss is never cached: a schema that does
     * not exist yet is the one most likely to be created moments later.
     */
    private SchemaDef cached(String key, Supplier<SchemaDef> loader) {
        if (cache == null) {
            return loader.get();
        }
        return cache.get(key, ignored -> loader.get());
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
        if (schema == null) {
            return; // nothing attached, nothing to check
        }
        SchemaDef resolved = resolve(schema);
        if (resolved == null) {
            return; // unresolvable reference — miss already counted in resolve()
        }

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
            failures = jsonSchemaValidator.validate(schemaContent, withoutInternalFields(data));
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
                    // The resolved version, not the requested one: a reference asking for the
                    // latest carries version 0, and naming that in the failure would tell the
                    // reader nothing about which document rejected their payload.
                    "Schema %s validation failed %s",
                    resolved.getName() + ":" + resolved.getVersion(),
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
    private SchemaDef resolve(SchemaDef schema) {
        // An empty map is a valid JSON Schema (permits everything), so presence of data — not
        // non-emptiness — is what makes a schema inline.
        if (schema.getData() != null) {
            return schema;
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
        SchemaDef registered =
                version < 1
                        ? cached(key(name, LATEST), () -> lookupLatest(name))
                        : cached(key(name, String.valueOf(version)), () -> lookup(name, version));
        return registered;
    }

    /**
     * The payload as its caller sent it. Copies only when there is something to remove, so the
     * common case does not allocate, and never mutates the map it was handed.
     */
    private static Map<String, Object> withoutInternalFields(Map<String, Object> data) {
        if (data == null) {
            return Map.of();
        }
        if (INTERNAL_FIELDS.stream().noneMatch(data::containsKey)) {
            return data;
        }
        Map<String, Object> stripped = new HashMap<>(data);
        stripped.keySet().removeAll(INTERNAL_FIELDS);
        return stripped;
    }

    private static String describe(JsonSchemaException e) {
        String messages = String.valueOf(e.getValidationMessages());
        return StringUtils.isNotBlank(e.getMessage()) ? e.getMessage() : messages;
    }
}
