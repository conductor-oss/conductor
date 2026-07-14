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
package org.conductoross.conductor.ai.agentspan;

import java.util.List;
import java.util.Optional;

import org.conductoross.conductor.ai.agentspan.runtime.model.skill.SkillDetail;
import org.conductoross.conductor.ai.agentspan.runtime.spi.SkillMetadataDAO;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Bridges AgentSpan's {@link SkillMetadataDAO} SPI onto Conductor's backend-agnostic {@link
 * org.conductoross.conductor.dao.SkillMetadataDAO}. The {@link SkillDetail} manifest is serialized
 * to JSON for storage so the persistence layer carries no dependency on AgentSpan model types; it
 * is rehydrated on read.
 *
 * <p>OSS Conductor is single-tenant, so skills share one global namespace and neither side of the
 * adapter is owner-scoped.
 */
public class SkillMetadataDaoAdapter implements SkillMetadataDAO {

    private final org.conductoross.conductor.dao.SkillMetadataDAO delegate;
    private final ObjectMapper objectMapper;

    public SkillMetadataDaoAdapter(
            org.conductoross.conductor.dao.SkillMetadataDAO delegate, ObjectMapper objectMapper) {
        this.delegate = delegate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(SkillDetail detail, boolean makeLatest) {
        delegate.save(
                detail.getName(),
                detail.getVersion(),
                makeLatest,
                toJson(detail),
                detail.getCreatedAt(),
                detail.getUpdatedAt());
    }

    @Override
    public Optional<SkillDetail> find(String name, String version) {
        return delegate.find(name, version).map(this::fromJson);
    }

    @Override
    public Optional<String> latestVersion(String name) {
        return delegate.latestVersion(name);
    }

    @Override
    public List<SkillDetail> listVersions(String name) {
        return delegate.listVersions(name).stream().map(this::fromJson).toList();
    }

    @Override
    public List<SkillDetail> list(boolean allVersions) {
        return delegate.list(allVersions).stream().map(this::fromJson).toList();
    }

    @Override
    public void delete(String name, String version) {
        delegate.delete(name, version);
    }

    private String toJson(SkillDetail detail) {
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize skill metadata", e);
        }
    }

    private SkillDetail fromJson(String json) {
        try {
            return objectMapper.readValue(json, SkillDetail.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize skill metadata", e);
        }
    }
}
