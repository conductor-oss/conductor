/*
 * Copyright 2022 Orkes, Inc.
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
package io.orkes.conductor.client;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.netflix.conductor.client.worker.Worker;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;

import com.google.common.util.concurrent.Uninterruptibles;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LoadTestWorker implements Worker {

    private final String name;
    private final SecureRandom secureRandom = new SecureRandom();

    public LoadTestWorker(String name) {
        this.name = name;
    }

    @Override
    public String getTaskDefName() {
        return name;
    }

    @Override
    public TaskResult execute(Task task) {
        log.info("Executing {} - {}", task.getTaskType(), task.getTaskId());
        TaskResult result = new TaskResult(task);

        Uninterruptibles.sleepUninterruptibly(10_000, TimeUnit.MILLISECONDS);

        result.setStatus(TaskResult.Status.COMPLETED);
        int keyCount = 50;
        int resultCount = Math.max(20, secureRandom.nextInt(keyCount));

        result.getOutputData().put("fixed", "hello");
        result.getOutputData().put("oddEven", "odd" + secureRandom.nextInt(2));
        result.getOutputData().put("thirds", "thirds" + secureRandom.nextInt(3));
        result.getOutputData().put("fourths", "fourths" + secureRandom.nextInt(4));
        result.getOutputData().put("fifths", "fifths" + secureRandom.nextInt(5));
        result.getOutputData().put("tenths", "tenths" + secureRandom.nextInt(10));

        result.getOutputData().put("randomNumber", resultCount);
        result.getOutputData().put("uuid1", UUID.randomUUID().toString());
        result.getOutputData().put("uuid2", UUID.randomUUID().toString());
        result.getOutputData().put("float", secureRandom.nextDouble());

        result.addOutputData("scheduledTime", task.getScheduledTime());
        result.addOutputData("startTime", task.getStartTime());
        log.info("Done executing task {} @the worker", task.getTaskId());
        return result;
    }

    public void onErrorUpdate(Task task) {
        log.info("I just can't update the task {}", task.getTaskId());
    }

    @Override
    public int getPollingInterval() {
        return 10;
    }
}
