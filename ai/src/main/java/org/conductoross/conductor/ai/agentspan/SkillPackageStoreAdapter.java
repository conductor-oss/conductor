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

import org.conductoross.conductor.dao.SkillPackageDAO;

import dev.agentspan.runtime.spi.SkillPackageStore;
import dev.agentspan.runtime.spi.StoredSkillPackage;

/**
 * Bridges AgentSpan's {@link SkillPackageStore} SPI onto Conductor's backend-agnostic {@link
 * SkillPackageDAO}, so skill package bytes persist through whichever Conductor backend is
 * configured (Postgres, MySQL, SQLite, Redis).
 *
 * <p>The opaque handle is content-addressed ({@code conductor-db://<checksum>}); identical package
 * bytes therefore deduplicate to one stored row.
 */
public class SkillPackageStoreAdapter implements SkillPackageStore {

    static final String STORAGE_TYPE = "conductor-db";
    private static final String HANDLE_PREFIX = "conductor-db://";

    private final SkillPackageDAO delegate;

    public SkillPackageStoreAdapter(SkillPackageDAO delegate) {
        this.delegate = delegate;
    }

    @Override
    public String storageType() {
        return STORAGE_TYPE;
    }

    @Override
    public StoredSkillPackage store(String name, String version, String checksum, byte[] bytes) {
        String handle = HANDLE_PREFIX + checksum;
        delegate.put(handle, bytes);
        return new StoredSkillPackage(handle, STORAGE_TYPE, bytes.length);
    }

    @Override
    public byte[] read(String handle) {
        byte[] bytes = delegate.get(handle);
        if (bytes == null) {
            throw new IllegalArgumentException("Skill package not found: " + handle);
        }
        return bytes;
    }

    @Override
    public boolean exists(String handle) {
        return delegate.exists(handle);
    }

    @Override
    public void delete(String handle) {
        delegate.delete(handle);
    }
}
