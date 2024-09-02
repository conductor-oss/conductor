/*
 * Copyright 2024 Orkes, Inc.
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
package com.netflix.conductor.sdk.examples;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import com.netflix.conductor.client.http.ConductorClient;
import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.common.metadata.tasks.TaskDef;

import io.orkes.conductor.sdk.examples.util.ClientUtil;


public class TaskRegistration {

    public static void main(String[] args) throws ExecutionException, InterruptedException, TimeoutException {
        ConductorClient client = ClientUtil.getClient();
        MetadataClient metadataClient = new MetadataClient(client);

        TaskDef taskDef = new TaskDef();
        taskDef.setName("task_with_retries");
        taskDef.setDescription("Example task definition");
        taskDef.setRetryCount(3);
        taskDef.setRetryLogic(TaskDef.RetryLogic.FIXED);

        //only allow 3 tasks at a time to be in the IN_PROGRESS status
        taskDef.setConcurrentExecLimit(3);

        //timeout the task if not polled within 60 seconds of scheduling
        taskDef.setPollTimeoutSeconds(60);

        //timeout the task if the task does not COMPLETE in 2 minutes
        taskDef.setTimeoutSeconds(120);

        //for the long running tasks, timeout if the task does not get updated in COMPLETED or IN_PROGRESS status in 60 seconds after the last update
        taskDef.setResponseTimeoutSeconds(60);

        //only allow 100 executions in a 10-second window! -- Note, this is complementary to concurrent_exec_limit
        taskDef.setRateLimitPerFrequency(100);
        taskDef.setRateLimitFrequencyInSeconds(10);
        taskDef.setOwnerEmail("exampes@conductor-oss.org");

        metadataClient.registerTaskDefs(List.of(taskDef));
    }
}
