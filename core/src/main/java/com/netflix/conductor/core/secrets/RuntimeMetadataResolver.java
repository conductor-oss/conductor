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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.conductoross.conductor.dao.SecretsDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.netflix.conductor.dao.EnvironmentDAO;

@Component
public class RuntimeMetadataResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(RuntimeMetadataResolver.class);

    private final SecretsDAO secretsDAO;
    private final EnvironmentDAO environmentDAO;

    public RuntimeMetadataResolver(SecretsDAO secretsDAO, EnvironmentDAO environmentDAO) {
        this.secretsDAO = secretsDAO;
        this.environmentDAO = environmentDAO;
    }

    /** Resolve each declared name (SecretsDAO first, EnvironmentDAO fallback); omit misses. */
    public Map<String, String> resolve(List<String> names) {
        Map<String, String> out = new LinkedHashMap<>();
        if (names == null) {
            return out;
        }
        for (String name : names) {
            String value = secretsDAO.getSecret(name);
            if (value == null) {
                value = environmentDAO.getEnvVariable(name);
            }
            if (value != null) {
                out.put(name, value);
            } else {
                LOGGER.warn(
                        "Declared secret/env '{}' not found in secrets store or environment", name);
            }
        }
        return out;
    }
}
