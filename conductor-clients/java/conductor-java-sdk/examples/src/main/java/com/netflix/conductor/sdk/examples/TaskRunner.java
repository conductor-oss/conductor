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
package com.netflix.conductor.sdk.examples;

import java.util.*;

import com.netflix.conductor.client.automator.TaskRunnerConfigurer;
import com.netflix.conductor.client.http.ConductorClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.worker.Worker;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;

import io.orkes.conductor.sdk.examples.util.ClientUtil;


/**
 * Task worker example. Shows how you can create a worker that polls and executes a SIMPLE task in
 * the conductor workflow.
 */
public class TaskRunner {
    public static void main(String[] args) {
        ConductorClient client = ClientUtil.getClient();
        TaskClient taskClient = new TaskClient(client);

        // Add a list with Worker implementations
        List<Worker> workers = Arrays.asList(new TaskWorker(), new LongRunningTaskWorker());

        TaskRunnerConfigurer.Builder builder =
                new TaskRunnerConfigurer.Builder(taskClient, workers);

        // Map of task type to domain when polling for task from specific domains
        Map<String, String> taskToDomains = new HashMap<>();

        // No. of threads for each task type.  Used to configure taskType specific thread count for
        // execution
        Map<String, Integer> taskThreadCount = new HashMap<>();

        TaskRunnerConfigurer taskRunner =
                builder.withThreadCount(
                                10) // Default thread count if not specified in taskThreadCount
                        .withTaskToDomain(taskToDomains)
                        .withTaskThreadCount(taskThreadCount)
                        .withSleepWhenRetry(
                                500) // Time in millis to sleep when retrying for a task update.
                        // Default is 500ms
                        // .withTaskPollTimeout(100) // Poll timeout for long-poll.  Default is
                        // 100ms
                        .withWorkerNamePrefix(
                                "worker-") // Thread name prefix for the task worker executor.
                        // Useful for logging
                        .withUpdateRetryCount(
                                3) // No. of times to retry if task update fails.  defaults to 3
                        .build();

        // Start Polling for tasks and execute them
        taskRunner.init();

        // Optionally, use the shutdown method to stop polling
        //taskRunner.shutdown();
    }

    /** Example Task Worker */
    private static class TaskWorker implements Worker {

        private static final String TASK_NAME = "simple_task";

        @Override
        public String getTaskDefName() {
            return TASK_NAME;
        }

        @Override
        public TaskResult execute(Task task) {
            // execute method is called once the task is scheduled in the workflow
            // The method implements the business logic for the task.

            // Conductor provides at-least once guarantees
            // The task can be rescheduled in (rare) case of transient failures on Network
            // To handle such cases, the implementation should be idempotent

            // BUSINESS LOGIC GOES HERE

            // Create a TaskResult object to hold the result of the execution and status
            TaskResult result = new TaskResult(task);

            // Capture the output of the task execution as output
            result.getOutputData().put("key", "value");
            result.getOutputData().put("amount", 123.45);

            // The values can be a map or an array as well
            result.getOutputData().put("key2", new HashMap<>());

            // Set the status COMPLETED.
            result.setStatus(TaskResult.Status.COMPLETED);

            return result;
        }
    }

    /**
     * An example worker that is a long-running and periodically gets polled to check the status of
     * a long-running task. A use case for such worker is when the actual work is happening via
     * separate process that could take longer to execute (anywhere from minutes to days or months),
     * e.g. database backup, running a background job that could take hours. In such cases, such
     * tasks are polled every so often (e.g. hourly or daily) to check the status and remains in
     * progress for much longer without holding up threads.
     */
    private static class LongRunningTaskWorker implements Worker {

        private static final String TASK_NAME = "long_running_task";

        @Override
        public String getTaskDefName() {
            return TASK_NAME;
        }

        @Override
        public TaskResult execute(Task task) {
            // 1. Create a TaskResult object to hold the result of the execution and status
            TaskResult result = new TaskResult(task);

            // Check the status of the long-running process
            boolean isCompleted = isJobCompleted();
            if (isCompleted) {
                // Let's complete the task
                // Set the status COMPLETED.
                result.setStatus(TaskResult.Status.COMPLETED);
            } else {
                // still in progress, let's check back in an hour
                result.setCallbackAfterSeconds(60 * 60);
            }
            return result;
        }

        private boolean isJobCompleted() {
            return true;
        }
    }
}
