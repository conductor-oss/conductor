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

import org.conductoross.conductor.ai.vectordb.VectorDBConfig;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuration for a SQLite-backed vector database instance using the <a
 * href="https://github.com/asg017/sqlite-vec">sqlite-vec</a> extension.
 *
 * <p>sqlite-vec ships as a native loadable SQLite extension ({@code vec0}). It is not published as
 * a Maven artifact and has no JVM binding, so the operator must make the compiled extension
 * available on the host and point {@link #extensionPath} at it (the file name without its platform
 * suffix, e.g. {@code /opt/sqlite-vec/vec0}). When {@code extensionPath} is blank the extension is
 * loaded by the bare name {@code vec0}, which requires it to be resolvable on the default library
 * search path.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SqliteConfig implements VectorDBConfig<SqliteVectorDB> {

    /**
     * Path to the SQLite database file, e.g. {@code /var/lib/conductor/vectordb.db}. Use {@code
     * :memory:} for an ephemeral in-memory database (pool size is forced to 1 in that case).
     */
    private String dbPath;

    /**
     * Path to the sqlite-vec loadable extension, without the platform suffix (e.g. {@code
     * /opt/sqlite-vec/vec0}). When blank, the extension is loaded by the bare name {@code vec0}.
     */
    private String extensionPath;

    private Integer connectionPoolSize = 5;

    private Integer dimensions = 256;

    /**
     * Distance metric for the vector column: {@code l2} (default), {@code cosine} or {@code l1}.
     */
    private String distanceMetric = "l2";

    private String tablePrefix;

    @Override
    public SqliteVectorDB get() {
        throw new UnsupportedOperationException("Use get(String name) instead");
    }

    public SqliteVectorDB get(String name) {
        return new SqliteVectorDB(name, this);
    }
}
