/*
 * Copyright 2022 Conductor Authors.
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
package com.netflix.conductor.core.index;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.netflix.conductor.common.metadata.tasks.PollData;
import com.netflix.conductor.dao.PollDataDAO;

/** In-memory implementation of {@link PollDataDAO} which keeps the poll data locally in memory */
public class MemoryPollDataDAO implements PollDataDAO {

    private ConcurrentHashMap<String, ConcurrentHashMap<String, PollData>> pollData =
            new ConcurrentHashMap<String, ConcurrentHashMap<String, PollData>>();

    @Override
    public void updateLastPollData(String taskDefName, String domain, String workerId) {
        ConcurrentHashMap<String, PollData> domainPollData = pollData.get(taskDefName);
        if (domainPollData == null) {
            domainPollData = new ConcurrentHashMap<String, PollData>();
            pollData.put(taskDefName, domainPollData);
        }
        String domainKey = domain == null ? "DEFAULT" : domain;
        domainPollData.put(
                domainKey, new PollData(taskDefName, domain, workerId, System.currentTimeMillis()));
    }

    @Override
    public PollData getPollData(String taskDefName, String domain) {
        ConcurrentHashMap<String, PollData> domainPollData = pollData.get(taskDefName);
        if (domainPollData == null) {
            return null;
        }
        return domainPollData.get(domain == null ? "DEFAULT" : domain);
    }

    @Override
    public List<PollData> getPollData(String taskDefName) {
        ConcurrentHashMap<String, PollData> domainPollData = pollData.get(taskDefName);
        if (domainPollData == null) {
            return new ArrayList<PollData>();
        }
        return new ArrayList<PollData>(domainPollData.values());
    }

    @Override
    public List<PollData> getAllPollData() {
        return pollData.values().stream().map(m -> m.values()).collect(Collectors.toList()).stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }
}
