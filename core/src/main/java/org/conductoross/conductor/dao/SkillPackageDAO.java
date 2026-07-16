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

/**
 * Persistence for AgentSpan skill package <b>bytes</b> (the zipped {@code SKILL.md} package),
 * addressed by an opaque {@code handle}. Skill <b>metadata</b> is stored separately via {@link
 * SkillMetadataDAO}.
 *
 * <p>This DAO is the Conductor-native backing store for AgentSpan's {@code
 * dev.agentspan.runtime.spi.SkillPackageStore} SPI; an adapter in the {@code conductor-ai} module
 * bridges the two. Implementations store the bytes through whichever Conductor backend is
 * configured (Postgres, MySQL, SQLite, Redis). Bytes are persisted Base64-encoded so the value
 * binds uniformly across all backends without per-dialect binary handling.
 */
public interface SkillPackageDAO {

    /** Stores (or overwrites) the package bytes for {@code handle}. */
    void put(String handle, byte[] data);

    /** Returns the package bytes for {@code handle}, or {@code null} when not found. */
    byte[] get(String handle);

    /** Returns {@code true} when a package exists for {@code handle}. */
    boolean exists(String handle);

    /** Deletes the package for {@code handle}. No-op when absent. */
    void delete(String handle);
}
