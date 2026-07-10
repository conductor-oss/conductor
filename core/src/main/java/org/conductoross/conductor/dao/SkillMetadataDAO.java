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

import java.util.List;
import java.util.Optional;

/**
 * Persistence for AgentSpan skill <b>metadata</b>: the per-version manifest plus a per-skill
 * "latest" pointer. Skill package <b>bytes</b> are stored separately via {@link SkillPackageDAO}.
 *
 * <p>This DAO is the Conductor-native backing store for AgentSpan's {@code
 * dev.agentspan.runtime.spi.SkillMetadataDAO} SPI; an adapter in the {@code conductor-ai} module
 * bridges the two so skills persist through whichever Conductor backend is configured (Postgres,
 * MySQL, SQLite, Redis). It deliberately stores the manifest as an opaque JSON document ({@code
 * detailJson}) so the persistence layer carries no dependency on AgentSpan model types.
 *
 * <p>All operations are scoped by {@code ownerId}. Authorization (whether the caller may read a
 * given skill) is the caller's concern, not this DAO's. Implemented per supported DB.
 */
public interface SkillMetadataDAO {

    /**
     * Inserts or replaces the metadata for a single skill version (keyed by {@code ownerId + name +
     * version}). When {@code makeLatest} is {@code true}, this version becomes the skill's recorded
     * latest and any previously-latest version for the same {@code (ownerId, name)} is cleared.
     *
     * @param detailJson the serialized skill manifest/detail document
     * @param createdAt epoch milliseconds the version was first created (may be {@code null})
     * @param updatedAt epoch milliseconds of this write (may be {@code null})
     */
    void save(
            String ownerId,
            String name,
            String version,
            boolean makeLatest,
            String detailJson,
            Long createdAt,
            Long updatedAt);

    /** Exact-version lookup; returns the stored {@code detailJson} or empty when absent. */
    Optional<String> find(String ownerId, String name, String version);

    /** The recorded latest version string for a skill, if any. */
    Optional<String> latestVersion(String ownerId, String name);

    /** The stored {@code detailJson} for every recorded version of a single skill (unordered). */
    List<String> listVersions(String ownerId, String name);

    /**
     * The stored {@code detailJson} for an owner's skills. When {@code allVersions} is {@code
     * false}, returns only each skill's latest version; when {@code true}, returns every version of
     * every skill.
     */
    List<String> list(String ownerId, boolean allVersions);

    /** Removes a single version and recomputes the skill's latest pointer when needed. */
    void delete(String ownerId, String name, String version);
}
