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
package org.conductoross.conductor.ai.vectordb;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.netflix.conductor.sdk.workflow.executor.task.TaskContext;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class VectorDBProvider {

    private final Map<String, VectorDB> vectorDBs = new ConcurrentHashMap<>();

    public VectorDBProvider(List<VectorDBConfig<VectorDB>> configs) {
        for (VectorDBConfig<VectorDB> config : configs) {
            VectorDB vectorDB = config.get();
            vectorDBs.put(vectorDB.getType(), vectorDB);
        }
    }

    public VectorDB get(String integrationName, TaskContext taskContext) {
        return vectorDBs.get(integrationName);
    }
}
