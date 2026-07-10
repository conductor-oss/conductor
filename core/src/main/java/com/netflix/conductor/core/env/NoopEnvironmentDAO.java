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
package com.netflix.conductor.core.env;

import java.util.Collections;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.EnvironmentVariable;
import com.netflix.conductor.dao.EnvironmentDAO;

@Component
@ConditionalOnProperty(name = "conductor.environment.type", havingValue = "noop")
public class NoopEnvironmentDAO implements EnvironmentDAO {

    @Override
    public String getEnvVariable(String key) {
        return null;
    }

    @Override
    public void setEnvVariable(String key, String value) {
        throw new UnsupportedOperationException("environment variables are disabled");
    }

    @Override
    public void delete(String key) {
        throw new UnsupportedOperationException("environment variables are disabled");
    }

    @Override
    public List<EnvironmentVariable> getAll() {
        return Collections.emptyList();
    }
}
