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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

/**
 * The schema registry. Owns versioning, lookup and removal of {@link SchemaDef}s on top of {@link
 * SchemaDAO}.
 *
 * <p>This is a service of its own rather than a set of methods on {@code MetadataService} because
 * the callers that resolve a schema need only this, and should not take a dependency on the whole
 * metadata surface to perform one lookup.
 *
 * <p>A class rather than an interface with one implementation: there is only ever one registry, and
 * the seam that matters — the one worth substituting per backend — is {@link SchemaDAO} below it.
 * The method set is a fixed contract; adding, removing or renaming one is a breaking change for
 * every caller, so put new behaviour behind an existing method or in {@link
 * org.conductoross.conductor.controllers.SchemaResource}, which adapts this surface to the
 * published endpoints.
 *
 * <p>The lookups report a schema that is not registered as {@code null} rather than by throwing.
 * Turning an absent schema into a {@code 404} belongs to the controller, so that this class decides
 * what exists and the transport decides what a caller sees.
 *
 * <p>{@link SchemaDAO} is a required constructor dependency and no implementation is registered by
 * default, so a server on a backend without one fails at startup instead of accepting schema writes
 * it cannot store.
 */
@Service
@EnableConfigurationProperties(SchemaCacheProperties.class)
public class SchemaService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SchemaService.class);

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
        // Which backend the registry bound to, and whether reads are cached: the two facts that
        // decide where a schema went and how stale a read can be, answered without a request.
        LOGGER.info(
                "Schema registry storing through {}, read cache {}",
                schemaDAO.getClass().getSimpleName(),
                cache == null ? "disabled" : "enabled, ttl " + cacheProperties.getTtl());
    }

    /**
     * Mutates and returns the schema it was given — the version it landed at and its audit
     * timestamps are stamped onto that instance, as the metadata services do. Callers that need
     * their argument left alone should pass a copy.
     */
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
        // An in-place save keeps the version's original creation time and stamps the update. The
        // read is what tells the two apart: the same call creates a version or corrects one.
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

    /**
     * OSS Conductor has no authenticated principal, so the created-by and updated-by fields stay
     * unset; the object mapper omits null properties, so they simply do not appear.
     */
    private static void stampCreation(SchemaDef schema) {
        schema.setCreateTime(System.currentTimeMillis());
        schema.setUpdateTime(null);
    }

    /**
     * Allocates the next version by reading the current maximum and saving one past it.
     *
     * <p>The read and the write are separate calls with no conditional insert between them, so two
     * writers registering the same name at once can read the same maximum, land on the same version
     * and have the later save overwrite the earlier one. Concurrent registration of one name is
     * last-writer-wins.
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

    /**
     * Always read from the backend. Caching a whole listing under one key would go stale on every
     * write to any schema, and the per-key entries cannot answer "what exists".
     */
    public List<SchemaDef> getAllSchemas() {
        return schemaDAO.getAll();
    }

    /** Straight through to the backend's projection, which reads no schema bodies. */
    public List<SchemaDef> getAllShortenedSchemas() {
        return schemaDAO.getAllShortenedSchemas();
    }

    /**
     * Dispatches on what was asked for: every schema when {@code name} is null, every version under
     * that name when only {@code version} is null, otherwise the single version as a one-element
     * list.
     *
     * @throws com.netflix.conductor.core.exception.NotFoundException when both are given and that
     *     version is not registered.
     */
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

    /**
     * One backend call for the whole batch. The cache is dropped per name afterwards rather than
     * per version, because the versions removed are not read back to enumerate them.
     */
    public void deleteSchemasByNamesBatch(List<String> names) {
        if (names == null || names.isEmpty()) {
            LOGGER.debug("No schema names provided for batch delete");
            return;
        }
        int deleted = schemaDAO.deleteAllByNames(names);
        names.forEach(this::invalidateName);
        LOGGER.info("Batch deleted {} schema versions across {} names", deleted, names.size());
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

    /**
     * The two nullable reads on {@link SchemaDAO}, wrapped once each. Every lookup in this class
     * goes through one of them, so an absent schema becomes {@link Optional#empty()} here and null
     * never travels further in. Named apart from the DAO methods they wrap, so a reader can see at
     * the call site which of the two they are looking at.
     */
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
    public void validate(SchemaDef schema, Map<String, Object> data) {
        long start = System.currentTimeMillis();
        try {
            doValidate(schema, data);
        } catch (SchemaValidationException e) {
            // Recorded here rather than at the call sites, because this is the one point every
            // caller — the engine's five enforcement points and the AI layer — passes through.
            // Tagged by schema name only: this method is not told which boundary it was called
            // for, and inventing one would be a tag the caller cannot rely on.
            Monitors.recordSchemaValidationFailure(schema.getName());
            throw e;
        } finally {
            // Recorded for rejections too: a run that rejects every payload is still doing the
            // work.
            Monitors.recordSchemaValidationTime(System.currentTimeMillis() - start);
        }
    }

    private void doValidate(SchemaDef schema, Map<String, Object> data) {
        if (schema == null) {
            throw new IllegalArgumentException("Schema cannot be null");
        }

        Optional<SchemaDef> registered = resolve(schema);
        if (registered.isEmpty()) {
            // A reference the registry does not hold is not enforced. The miss is counted where it
            // is discovered, so an unregistered reference reaches an operator as a signal rather
            // than reaching a caller as a failed execution.
            return;
        }
        SchemaDef resolved = registered.get();

        // The schema fields on a Task Definition carry no cascading-validation annotation, so a
        // schema with no type is accepted at registration and only discovered here. That is a
        // definition error and is reported as one, rather than passed over.
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
            // Same reading as an unusable document below: the schema is at fault, not the payload,
            // so it is logged for whoever registered it and nothing is checked.
            LOGGER.error(
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
            // An unusable schema document is a definition error, and no payload can be checked
            // against it: it is logged for whoever registered it and the payload is let through.
            // networknt's own getMessage() is frequently empty here, so the messages it carries
            // are what get logged.
            LOGGER.error(
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
     * Returns the schema whose {@code data} is the document to validate against: the argument when
     * it inlines one, otherwise the registered version it names. Empty when the reference names a
     * version the registry does not hold, which leaves the payload unvalidated.
     *
     * <p>A version below 1 means no version was asked for, and resolves the highest registered
     * version under the name. {@link SchemaDef} defaults its version to 1, so a definition that
     * wants to track the latest has to say {@code 0} outright.
     *
     * <p>{@code externalRef} is checked before the registry, and refused: it is stored and returned
     * unchanged and nothing dereferences it, so a schema carrying one instead of a document says
     * outright that it cannot be enforced rather than being looked up under its name.
     */
    private Optional<SchemaDef> resolve(SchemaDef schema) {
        // An empty document is a legal JSON Schema that permits anything, so `data` being present
        // — not being non-empty — is what makes a schema inline.
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
        // Through the cache: under enforcement this runs on every scheduled task, which is the
        // read the cache exists for.
        Optional<SchemaDef> registered =
                version < 1
                        ? cached(key(name, LATEST), () -> lookupLatest(name))
                        : cached(key(name, String.valueOf(version)), () -> lookup(name, version));
        if (registered.isEmpty()) {
            // Under enforcement this runs for every scheduled task, so one unregistered reference
            // would warn on every execution of it. The counter is the operator's signal that a
            // definition points at nothing, since the payload itself goes through unchecked.
            LOGGER.debug(
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
