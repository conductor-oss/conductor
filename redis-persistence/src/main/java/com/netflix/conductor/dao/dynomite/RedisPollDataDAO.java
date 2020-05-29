/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.conductor.dao.dynomite;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.netflix.conductor.common.metadata.tasks.PollData;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.dao.PollDataDAO;
import com.netflix.conductor.dyno.DynoProxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import org.apache.commons.lang3.StringUtils;

public class RedisPollDataDAO extends BaseDynoDAO implements PollDataDAO {

    private final static String POLL_DATA = "POLL_DATA";

    @Inject
    public RedisPollDataDAO(DynoProxy dynoClient, ObjectMapper objectMapper, Configuration config) {
        super(dynoClient, objectMapper, config);
    }

    @Override
    public void updateLastPollData(String taskDefName, String domain, String workerId) {
        Preconditions.checkNotNull(taskDefName, "taskDefName name cannot be null");
        PollData pollData = new PollData(taskDefName, domain, workerId, System.currentTimeMillis());

        String key = nsKey(POLL_DATA, pollData.getQueueName());
        String field = (domain == null) ? "DEFAULT" : domain;

        String payload = toJson(pollData);
        recordRedisDaoRequests("updatePollData");
        recordRedisDaoPayloadSize("updatePollData", payload.length(), "n/a", "n/a");
        dynoClient.hset(key, field, payload);
    }

    @Override
    public PollData getPollData(String taskDefName, String domain) {
        Preconditions.checkNotNull(taskDefName, "taskDefName name cannot be null");

        String key = nsKey(POLL_DATA, taskDefName);
        String field = (domain == null) ? "DEFAULT" : domain;

        String pollDataJsonString = dynoClient.hget(key, field);
        recordRedisDaoRequests("getPollData");
        recordRedisDaoPayloadSize("getPollData", StringUtils.length(pollDataJsonString), "n/a", "n/a");

        PollData pollData = null;
        if (StringUtils.isNotBlank(pollDataJsonString)) {
            pollData = readValue(pollDataJsonString, PollData.class);
        }
        return pollData;
    }

    @Override
    public List<PollData> getPollData(String taskDefName) {
        Preconditions.checkNotNull(taskDefName, "taskDefName name cannot be null");

        String key = nsKey(POLL_DATA, taskDefName);

        Map<String, String> pMapdata = dynoClient.hgetAll(key);
        List<PollData> pollData = new ArrayList<>();
        if (pMapdata != null) {
            pMapdata.values().forEach(pollDataJsonString -> {
                pollData.add(readValue(pollDataJsonString, PollData.class));
                recordRedisDaoRequests("getPollData");
                recordRedisDaoPayloadSize("getPollData", pollDataJsonString.length(), "n/a", "n/a");
            });
        }
        return pollData;
    }
}
