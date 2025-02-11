package com.netflix.conductor.sqlite.dao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.metadata.tasks.PollData;
import com.netflix.conductor.dao.PollDataDAO;
import com.netflix.conductor.sqlite.config.SqliteProperties;
import org.springframework.retry.support.RetryTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class SqlitePollDataDAO extends SqliteBaseDAO implements PollDataDAO  {

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
//      //  this.cacheValidityPeriod = properties.getPollDataCacheValidityPeriod().toMillis();
//        this.useReadCache = cacheValidityPeriod > 0;
//        if (this.useReadCache) {
//            logger.info("Using Postgres pollData read cache");
//        }
    }

    @Override
    public void updateLastPollData(String taskDefName, String domain, String workerId) {

    }

    @Override
    public PollData getPollData(String taskDefName, String domain) {
        return null;
    }

    @Override
    public List<PollData> getPollData(String taskDefName) {
        return List.of();
    }
}
