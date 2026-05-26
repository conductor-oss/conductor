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
package org.conductoross.conductor.dao;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.netflix.conductor.common.metadata.SchemaDef;
import com.netflix.conductor.core.exception.NotFoundException;

public class InMemorySchemaDAO implements SchemaDAO {

    private final Map<String, Map<String, Map<Integer, SchemaDef>>> schemas =
            new ConcurrentHashMap<>();

    @Override
    public void save(String orgId, SchemaDef schemaDef) {
        schemas.computeIfAbsent(orgId, ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(schemaDef.getName(), ignored -> new ConcurrentHashMap<>())
                .put(schemaDef.getVersion(), copy(schemaDef));
    }

    @Override
    public Optional<SchemaDef> getSchemaByNameWithLatestVersion(String orgId, String name) {
        return schemas.getOrDefault(orgId, Map.of()).getOrDefault(name, Map.of()).values().stream()
                .max(Comparator.comparingInt(SchemaDef::getVersion))
                .map(this::copy);
    }

    @Override
    public Optional<SchemaDef> getSchemaByNameAndVersion(String orgId, String name, int version) {
        return Optional.ofNullable(
                        schemas.getOrDefault(orgId, Map.of())
                                .getOrDefault(name, Map.of())
                                .get(version))
                .map(this::copy);
    }

    @Override
    public List<SchemaDef> getAllSchemas(String orgId) {
        List<SchemaDef> result = new ArrayList<>();
        schemas.getOrDefault(orgId, Map.of()).values().forEach(
                byVersion ->
                        byVersion.values().forEach(schema -> result.add(copy(schema))));
        result.sort(
                Comparator.comparing(SchemaDef::getName)
                        .thenComparingInt(SchemaDef::getVersion));
        return result;
    }

    @Override
    public void deleteSchemaByName(String orgId, String name) {
        Map<String, Map<Integer, SchemaDef>> byName = schemas.get(orgId);
        if (byName == null || byName.remove(name) == null) {
            throw new NotFoundException("No such schema found by name: %s", name);
        }
    }

    @Override
    public void deleteSchemaByNameAndVersion(String orgId, String name, int version) {
        Map<Integer, SchemaDef> byVersion = schemas.getOrDefault(orgId, Map.of()).get(name);
        if (byVersion == null || byVersion.remove(version) == null) {
            throw new NotFoundException(
                    "No such schema found by name: %s, version: %d", name, version);
        }
        if (byVersion.isEmpty()) {
            schemas.getOrDefault(orgId, Map.of()).remove(name);
        }
    }

    private SchemaDef copy(SchemaDef source) {
        SchemaDef copy = new SchemaDef();
        copy.setName(source.getName());
        copy.setVersion(source.getVersion());
        copy.setType(source.getType());
        copy.setData(source.getData() == null ? null : new HashMap<>(source.getData()));
        copy.setExternalRef(source.getExternalRef());
        copy.setOwnerApp(source.getOwnerApp());
        copy.setCreateTime(source.getCreateTime());
        copy.setUpdateTime(source.getUpdateTime());
        copy.setCreatedBy(source.getCreatedBy());
        copy.setUpdatedBy(source.getUpdatedBy());
        return copy;
    }

    @Override
    public Optional<Integer> getLatestVersion(String orgId, String name) {
        return schemas.getOrDefault(orgId, Map.of()).getOrDefault(name, Map.of()).keySet().stream()
                .max(Integer::compareTo);
    }
}
