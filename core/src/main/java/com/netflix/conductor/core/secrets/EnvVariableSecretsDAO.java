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
package com.netflix.conductor.core.secrets;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.netflix.conductor.core.env.EnvVarLookup;
import com.netflix.conductor.dao.SecretsDAO;

@Component
@ConditionalOnProperty(name = "conductor.secrets.type", havingValue = "env", matchIfMissing = true)
public class EnvVariableSecretsDAO implements SecretsDAO {

    private final String prefix;

    public EnvVariableSecretsDAO(
            @Value("${conductor.secrets.env.prefix:CONDUCTOR_SECRET_}") String prefix) {
        this.prefix = prefix;
    }

    @Override
    public String getSecret(String name) {
        return EnvVarLookup.lookup(prefix, name);
    }

    @Override
    public boolean secretExists(String name) {
        return getSecret(name) != null;
    }

    @Override
    public List<String> listSecretNames() {
        return new ArrayList<>(EnvVarLookup.allWithPrefix(prefix).keySet());
    }

    @Override
    public void putSecret(String name, String value) {
        throw new UnsupportedOperationException("env-backed secrets are read-only");
    }

    @Override
    public void deleteSecret(String name) {
        throw new UnsupportedOperationException("env-backed secrets are read-only");
    }
}
