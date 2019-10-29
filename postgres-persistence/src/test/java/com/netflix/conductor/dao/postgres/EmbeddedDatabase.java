/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.dao.postgres;

import com.opentable.db.postgres.embedded.EmbeddedPostgres;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;

import static com.netflix.conductor.dao.postgres.PostgresDAOTestUtil.createDb;

public enum EmbeddedDatabase {
  INSTANCE;

  private final DataSource ds;
  private final Logger logger = LoggerFactory.getLogger(EmbeddedDatabase.class);

  public DataSource getDataSource() {
    return ds;
  }

  private DataSource startEmbeddedDatabase() {
    try {
			DataSource ds = EmbeddedPostgres.builder().setPort(54320).start().getPostgresDatabase();
			createDb(ds,"conductor");
			return ds;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  EmbeddedDatabase() {
		logger.info("Starting embedded database");
		ds = startEmbeddedDatabase();
  }

}
