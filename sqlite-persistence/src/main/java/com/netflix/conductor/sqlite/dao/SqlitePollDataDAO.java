/*
 * Copyright 2025 Conductor Authors.
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
package com.netflix.conductor.sqlite.dao;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import javax.sql.DataSource;

import org.springframework.retry.support.RetryTemplate;

import com.netflix.conductor.common.metadata.tasks.PollData;
import com.netflix.conductor.dao.PollDataDAO;
import com.netflix.conductor.sqlite.config.SqliteProperties;

import com.fasterxml.jackson.databind.ObjectMapper;

public class SqlitePollDataDAO extends SqliteBaseDAO implements PollDataDAO {

    private ConcurrentHashMap<String, ConcurrentHashMap<String, PollData>> pollDataCache =
            new ConcurrentHashMap<>();

    private long pollDataFlushInterval;

    private long cacheValidityPeriod;

    private long lastFlushTime = 0;

    private boolean useReadCache;

    public SqlitePollDataDAO(
            RetryTemplate retryTemplate,
            ObjectMapper objectMapper,
            DataSource dataSource,
            SqliteProperties properties) {
        super(retryTemplate, objectMapper, dataSource);
        //        //this.pollDataFlushInterval = properties.getPollDataFlushInterval().toMillis();
        //        if (this.pollDataFlushInterval > 0) {
        //            logger.info("Using Postgres pollData write cache");
        //        }
        //      //  this.cacheValidityPeriod =
        // properties.getPollDataCacheValidityPeriod().toMillis();
        //        this.useReadCache = cacheValidityPeriod > 0;
        //        if (this.useReadCache) {
        //            logger.info("Using Postgres pollData read cache");
        //        }
    }

    @Override
    public void updateLastPollData(String taskDefName, String domain, String workerId) {}

    @Override
    public PollData getPollData(String taskDefName, String domain) {
        return null;
    }

    @Override
    public List<PollData> getPollData(String taskDefName) {
        return List.of();
    }
}
