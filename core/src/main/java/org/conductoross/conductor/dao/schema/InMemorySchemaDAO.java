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

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.SchemaDef;

/**
 * In-memory {@link SchemaDAO}, the fallback used when the configured metadata backend ships no
 * schema storage of its own.
 *
 * <p>It keeps the registry working — and inline-schema validation is unaffected either way — but
 * everything it holds is lost on restart, and nothing is shared between servers. A deployment that
 * relies on the registry belongs on a backend that implements {@link SchemaDAO} (PostgreSQL, MySQL,
 * SQLite, Redis).
 *
 * <p>Doubles as the test double for {@link SchemaDAO}: the service and the DAO are two halves of
 * one feature, and a mock would let the service's tests assert on calls instead of on stored state.
 */
@Component
@ConditionalOnMissingBean(SchemaDAO.class)
public class InMemorySchemaDAO implements SchemaDAO {

    private final Map<String, SchemaDef> stored = new ConcurrentHashMap<>();

    private static String key(String name, Integer version) {
        return name + "/" + version;
    }

    @Override
    public void save(SchemaDef schemaDef) {
        stored.put(key(schemaDef.getName(), schemaDef.getVersion()), schemaDef);
    }

    @Override
    public SchemaDef findByNameAndVersion(String name, Integer version) {
        Objects.requireNonNull(version, "Schema version cannot be null");
        return stored.get(key(name, version));
    }

    @Override
    public SchemaDef findLatestVersionByName(String name) {
        return stored.values().stream()
                .filter(def -> def.getName().equals(name))
                .max(Comparator.comparingInt(SchemaDef::getVersion))
                .orElse(null);
    }

    @Override
    public List<SchemaDef> getAll() {
        return stored.values().stream()
                .sorted(
                        Comparator.comparing(SchemaDef::getName)
                                .thenComparingInt(SchemaDef::getVersion))
                .toList();
    }

    @Override
    public int deleteByNameAndVersion(String name, Integer version) {
        Objects.requireNonNull(version, "Schema version cannot be null");
        return stored.remove(key(name, version)) == null ? 0 : 1;
    }

    @Override
    public int deleteAllByName(String name) {
        // Counts what it removed rather than the change in size: this map is written concurrently,
        // so a size taken before and after would fold in a neighbour's write.
        int removed = 0;
        for (var entries = stored.values().iterator(); entries.hasNext(); ) {
            if (entries.next().getName().equals(name)) {
                entries.remove();
                removed++;
            }
        }
        return removed;
    }

    @Override
    public int deleteAllByNames(List<String> names) {
        if (names == null || names.isEmpty()) {
            return 0;
        }
        int removed = 0;
        for (String name : names) {
            removed += deleteAllByName(name);
        }
        return removed;
    }

    @Override
    public List<SchemaDef> findAllVersionsByName(String name) {
        return stored.values().stream()
                .filter(def -> def.getName().equals(name))
                .sorted(Comparator.comparingInt(SchemaDef::getVersion).reversed())
                .toList();
    }

    @Override
    public List<SchemaDef> getAllShortenedSchemas() {
        return getAll().stream()
                .map(def -> nameAndVersion(def.getName(), def.getVersion()))
                .toList();
    }

    private static SchemaDef nameAndVersion(String name, int version) {
        SchemaDef schema = new SchemaDef();
        schema.setName(name);
        schema.setVersion(version);
        return schema;
    }
}
