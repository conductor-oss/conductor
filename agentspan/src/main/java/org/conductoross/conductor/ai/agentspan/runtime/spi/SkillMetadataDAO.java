/*
 * Copyright 2025 Conductor Authors.
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
package org.conductoross.conductor.ai.agentspan.runtime.spi;

import java.util.List;
import java.util.Optional;

import org.conductoross.conductor.common.metadata.agent.SkillDetail;

/**
 * Persistence SPI for skill <b>metadata</b> (manifest, versions, and the per-skill "latest"
 * pointer). Skill package <b>bytes</b> are stored separately via {@link SkillPackageStore}.
 *
 * <p>The standalone server ships a filesystem-backed implementation (zero-config, single node); an
 * embedding host (e.g. orkes-conductor) supplies a durable/HA implementation (e.g. Postgres) so
 * skill listings are consistent across nodes.
 *
 * <p>Skills are global: there is no per-caller ownership or scoping.
 */
public interface SkillMetadataDAO {

    /**
     * Persist a skill version's metadata (create or overwrite).
     *
     * @param detail metadata to store, keyed by {@code name + version}
     * @param makeLatest when {@code true}, mark this version as the skill's latest
     */
    void save(SkillDetail detail, boolean makeLatest);

    /** Exact-version lookup. */
    Optional<SkillDetail> find(String name, String version);

    /** The recorded latest version string for a skill, if any. */
    Optional<String> latestVersion(String name);

    /** All recorded versions of a single skill (unordered). */
    List<SkillDetail> listVersions(String name);

    /**
     * All registered skills. When {@code allVersions} is {@code false}, returns only each skill's
     * latest version; when {@code true}, returns every version of every skill.
     */
    List<SkillDetail> list(boolean allVersions);

    /** Remove a single version and recompute the skill's latest pointer if needed. */
    void delete(String name, String version);
}
