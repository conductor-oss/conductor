/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.conductor.es7.dao.index;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.run.SearchResult;
import org.junit.Test;
import org.springframework.test.context.TestPropertySource;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@TestPropertySource(properties = "conductor.elasticsearch.indexBatchSize=2")
public class TestElasticSearchRestDAOV7Batch extends ElasticSearchRestDaoBaseTest {

    @Test
    public void indexTaskWithBatchSizeTwo() {
        String correlationId = "some-correlation-id";

        Task task = new Task();
        task.setTaskId("some-task-id");
        task.setWorkflowInstanceId("some-workflow-instance-id");
        task.setTaskType("some-task-type");
        task.setStatus(Task.Status.FAILED);
        task.setInputData(new HashMap<String, Object>() {{
            put("input_key", "input_value");
        }});
        task.setCorrelationId(correlationId);
        task.setTaskDefName("some-task-def-name");
        task.setReasonForIncompletion("some-failure-reason");

        indexDAO.indexTask(task);
        indexDAO.indexTask(task);

        await()
                .atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    SearchResult<String> result = indexDAO
                            .searchTasks("correlationId='" + correlationId + "'", "*", 0, 10000, null);

                    assertTrue("should return 1 or more search results", result.getResults().size() > 0);
                    assertEquals("taskId should match the indexed task", "some-task-id", result.getResults().get(0));
                });

    }
}
