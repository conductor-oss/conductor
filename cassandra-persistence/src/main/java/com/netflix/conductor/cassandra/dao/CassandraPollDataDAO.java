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
package com.netflix.conductor.cassandra.dao;

import java.util.List;

import com.netflix.conductor.common.metadata.tasks.PollData;
import com.netflix.conductor.dao.PollDataDAO;

/**
 * This is a dummy implementation and this feature is not implemented for Cassandra backed
 * Conductor.
 */
public class CassandraPollDataDAO implements PollDataDAO {

    @Override
    public void updateLastPollData(String taskDefName, String domain, String workerId) {
        throw new UnsupportedOperationException(
                "This method is not implemented in CassandraPollDataDAO. Please use ExecutionDAOFacade instead.");
    }

    @Override
    public PollData getPollData(String taskDefName, String domain) {
        throw new UnsupportedOperationException(
                "This method is not implemented in CassandraPollDataDAO. Please use ExecutionDAOFacade instead.");
    }

    @Override
    public List<PollData> getPollData(String taskDefName) {
        throw new UnsupportedOperationException(
                "This method is not implemented in CassandraPollDataDAO. Please use ExecutionDAOFacade instead.");
    }
}
