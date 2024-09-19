/*
 * Copyright 2022 Netflix, Inc.
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
package com.netflix.conductor.scylla.dao;

import java.util.List;

import com.netflix.conductor.common.metadata.tasks.PollData;
import com.netflix.conductor.dao.PollDataDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a dummy implementation and this feature is not implemented for Cassandra backed
 * Conductor.
 */
public class ScyllaPollDataDAO implements PollDataDAO {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScyllaPollDataDAO.class);

    @Override
    public void updateLastPollData(String taskDefName, String domain, String workerId) {
        // LOGGER.info("Task ScyllaPollDataDAO.updateLastPollData NOT implemented in scylla persistence");
    }

    @Override
    public PollData getPollData(String taskDefName, String domain) {
        // LOGGER.info("Task ScyllaPollDataDAO.getPollData NOT implemented in scylla persistence");
        return null;
    }

    @Override
    public List<PollData> getPollData(String taskDefName) {
        // LOGGER.info("Task ScyllaPollDataDAO.getPollData NOT implemented in scylla persistence");
        return null;
    }
}
