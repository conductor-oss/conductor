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
package com.netflix.conductor.es6.dao.index;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.springframework.test.context.TestPropertySource;

import com.netflix.conductor.common.metadata.tasks.Task.Status;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.TaskSummary;

import com.fasterxml.jackson.core.JsonProcessingException;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@TestPropertySource(properties = "conductor.elasticsearch.indexBatchSize=2")
public class TestElasticSearchRestDAOV6Batch extends ElasticSearchRestDaoBaseTest {

    @Test
    public void indexTaskWithBatchSizeTwo() {
        String correlationId = "some-correlation-id";
        TaskSummary taskSummary = new TaskSummary();
        taskSummary.setTaskId("some-task-id");
        taskSummary.setWorkflowId("some-workflow-instance-id");
        taskSummary.setTaskType("some-task-type");
        taskSummary.setStatus(Status.FAILED);
        try {
            taskSummary.setInput(
                    objectMapper.writeValueAsString(
                            new HashMap<String, Object>() {
                                {
                                    put("input_key", "input_value");
                                }
                            }));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        taskSummary.setCorrelationId(correlationId);
        taskSummary.setTaskDefName("some-task-def-name");
        taskSummary.setReasonForIncompletion("some-failure-reason");

        indexDAO.indexTask(taskSummary);
        indexDAO.indexTask(taskSummary);

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            SearchResult<String> result =
                                    indexDAO.searchTasks(
                                            "correlationId='" + correlationId + "'",
                                            "*",
                                            0,
                                            10000,
                                            null);

                            assertTrue(
                                    "should return 1 or more search results",
                                    result.getResults().size() > 0);
                            assertEquals(
                                    "taskId should match the indexed task",
                                    "some-task-id",
                                    result.getResults().get(0));
                        });
    }
}
