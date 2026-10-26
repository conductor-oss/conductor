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
package org.conductoross.conductor.ai.agentspan.runtime.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.conductoross.conductor.dao.SecretsDAO;
import org.conductoross.conductor.model.secret.CredentialMeta;

/**
 * Real secret store over a map, counting reads. The count is what makes a credential cache
 * observable: a cache hit performs no read at all.
 */
class InMemorySecretsDAO implements SecretsDAO {

    private final Map<String, String> values = new ConcurrentHashMap<>();
    final AtomicInteger reads = new AtomicInteger();

    void put(String name, String value) {
        values.put(name, value);
    }

    void remove(String name) {
        values.remove(name);
    }

    @Override
    public String getSecret(String name) {
        reads.incrementAndGet();
        return values.get(name);
    }

    @Override
    public boolean secretExists(String name) {
        return values.containsKey(name);
    }

    @Override
    public List<String> listSecretNames() {
        return List.copyOf(values.keySet());
    }

    @Override
    public void putSecret(String name, String value) {
        values.put(name, value);
    }

    @Override
    public void deleteSecret(String name) {
        values.remove(name);
    }

    @Override
    public List<CredentialMeta> listWithMeta() {
        return List.of();
    }
}
