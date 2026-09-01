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

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.conductoross.conductor.dao.schema.SchemaDAO;

import com.netflix.conductor.common.metadata.SchemaDef;

/**
 * Test double for {@link SchemaDAO}. Deliberately hand-written rather than mocked: the service and
 * the DAO are two halves of one feature, and a mock would let the service's tests assert on calls
 * instead of on stored state.
 *
 * <p>Never registered as a bean — an in-memory registry that accepts writes and loses them on
 * restart is the failure this feature exists to avoid.
 */
public class InMemorySchemaDAO implements SchemaDAO {

    private final Map<String, SchemaDef> stored = new ConcurrentHashMap<>();

    /**
     * Counts every attempted conditional insert, so a test can see how often allocation retried.
     */
    final AtomicInteger createAttempts = new AtomicInteger();

    /** Rows to insert behind the service's back, one per conditional insert, simulating a race. */
    private final java.util.Deque<SchemaDef> racers = new java.util.ArrayDeque<>();

    void queueRacer(SchemaDef def) {
        racers.add(def);
    }

    private static String key(String name, Integer version) {
        return name + "/" + version;
    }

    @Override
    public void save(SchemaDef schemaDef) {
        stored.put(key(schemaDef.getName(), schemaDef.getVersion()), schemaDef);
    }

    @Override
    public boolean createSchemaIfAbsent(SchemaDef schemaDef) {
        createAttempts.incrementAndGet();
        SchemaDef racer = racers.poll();
        if (racer != null) {
            stored.put(key(racer.getName(), racer.getVersion()), racer);
        }
        return stored.putIfAbsent(key(schemaDef.getName(), schemaDef.getVersion()), schemaDef)
                == null;
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
    public List<SchemaDef> findAllVersionsByName(String name) {
        return stored.values().stream()
                .filter(def -> def.getName().equals(name))
                .sorted(Comparator.comparingInt(SchemaDef::getVersion).reversed())
                .toList();
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
    public List<SchemaDef> getAllShortenedSchemas() {
        return getAll().stream()
                .map(
                        schema -> {
                            SchemaDef summary = new SchemaDef();
                            summary.setName(schema.getName());
                            summary.setVersion(schema.getVersion());
                            return summary;
                        })
                .toList();
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
}
