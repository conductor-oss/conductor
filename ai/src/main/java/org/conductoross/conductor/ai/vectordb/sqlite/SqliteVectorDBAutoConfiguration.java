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
package org.conductoross.conductor.ai.vectordb.sqlite;

import org.apache.commons.lang3.StringUtils;
import org.conductoross.conductor.ai.vectordb.VectorDB;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;

/**
 * Registers a ready-to-use SQLite vector database instance when both the AI integration and the
 * SQLite persistence backend are enabled ({@code conductor.integrations.ai.enabled=true} and {@code
 * conductor.db.type=sqlite}). This makes sqlite-vec the zero-configuration vector store for the
 * all-in-one SQLite deployment: the native {@code vec0} extension is bundled in the jar (see {@link
 * SqliteVecExtensions}), so no external vector database is required.
 *
 * <p>The instance is exposed under the name {@code default} and merged into the {@code
 * VectorDBProvider} alongside any explicitly configured {@code conductor.vectordb.instances}. An
 * explicit instance named {@code default} takes precedence. All defaults below can be overridden
 * via {@code conductor.vectordb.sqlite-default.*}.
 */
@Configuration
@ConditionalOnProperty(name = "conductor.integrations.ai.enabled", havingValue = "true")
@Slf4j
public class SqliteVectorDBAutoConfiguration {

    @Bean
    @ConditionalOnProperty(name = "conductor.db.type", havingValue = "sqlite")
    @ConditionalOnMissingBean(name = "defaultSqliteVectorDB")
    public VectorDB defaultSqliteVectorDB(
            @Value("${conductor.vectordb.sqlite-default.name:default}") String name,
            @Value("${conductor.vectordb.sqlite-default.db-path:}") String dbPath,
            @Value("${spring.datasource.url:}") String datasourceUrl,
            @Value("${conductor.vectordb.sqlite-default.dimensions:256}") int dimensions,
            @Value("${conductor.vectordb.sqlite-default.distance-metric:cosine}")
                    String distanceMetric,
            @Value("${conductor.vectordb.sqlite-default.extension-path:}") String extensionPath) {

        SqliteConfig config = new SqliteConfig();
        config.setDbPath(resolveDbPath(dbPath, datasourceUrl));
        config.setDimensions(dimensions);
        config.setDistanceMetric(distanceMetric);
        // Blank extensionPath lets SqliteVectorDB fall back to the bundled vec0 binary.
        config.setExtensionPath(StringUtils.trimToNull(extensionPath));

        log.info(
                "Registering default SQLite vector DB instance '{}' (dbPath={}, dimensions={}, distanceMetric={})",
                name,
                config.getDbPath(),
                dimensions,
                distanceMetric);
        return config.get(name);
    }

    /**
     * Picks a SQLite file for the default vector store. Uses the explicit value when set, otherwise
     * co-locates a {@code *_vectordb.db} file next to the persistence database, falling back to a
     * file in the working directory.
     */
    static String resolveDbPath(String configured, String datasourceUrl) {
        if (StringUtils.isNotBlank(configured)) {
            return configured;
        }
        if (StringUtils.isNotBlank(datasourceUrl) && datasourceUrl.startsWith("jdbc:sqlite:")) {
            String path = datasourceUrl.substring("jdbc:sqlite:".length()).trim();
            if (StringUtils.isNotBlank(path) && !path.contains(":memory:")) {
                int dot = path.lastIndexOf('.');
                int sep = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
                String base = dot > sep ? path.substring(0, dot) : path;
                return base + "_vectordb.db";
            }
        }
        return "conductor_vectordb.db";
    }
}
