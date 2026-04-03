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
package org.conductoross.conductor.ai.vectordb.postgres;

import org.conductoross.conductor.ai.vectordb.VectorDBConfig;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostgresConfig implements VectorDBConfig<PostgresVectorDB> {

    private String datasourceURL;

    private String user;

    private String password;

    private Integer connectionPoolSize = 5;

    private Integer dimensions = 256;

    private String indexingMethod = "hnsw";

    private String distanceMetric = "l2";

    private Integer invertedListCount = 100;

    private String tablePrefix;

    @Override
    public PostgresVectorDB get() {
        throw new UnsupportedOperationException("Use get(String name) instead");
    }

    public PostgresVectorDB get(String name) {
        return new PostgresVectorDB(name, this);
    }
}
