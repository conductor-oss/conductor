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
package com.netflix.conductor.dao;

import java.util.List;

import com.netflix.conductor.common.metadata.tasks.PollData;

/** An abstraction to enable different PollData store implementations */
public interface PollDataDAO {

    /**
     * Updates the {@link PollData} information with the most recently polled data for a task queue.
     *
     * @param taskDefName name of the task as specified in the task definition
     * @param domain domain in which this task is being polled from
     * @param workerId the identifier of the worker polling for this task
     */
    void updateLastPollData(String taskDefName, String domain, String workerId);

    /**
     * Retrieve the {@link PollData} for the given task in the given domain.
     *
     * @param taskDefName name of the task as specified in the task definition
     * @param domain domain for which {@link PollData} is being requested
     * @return the {@link PollData} for the given task queue in the specified domain
     */
    PollData getPollData(String taskDefName, String domain);

    /**
     * Retrieve the {@link PollData} for the given task across all domains.
     *
     * @param taskDefName name of the task as specified in the task definition
     * @return the {@link PollData} for the given task queue in all domains
     */
    List<PollData> getPollData(String taskDefName);

    /**
     * Retrieve the {@link PollData} for all task types
     *
     * @return the {@link PollData} for all task types
     */
    default List<PollData> getAllPollData() {
        throw new UnsupportedOperationException(
                "The selected PollDataDAO ("
                        + this.getClass().getSimpleName()
                        + ") does not implement the getAllPollData() method");
    }
}
