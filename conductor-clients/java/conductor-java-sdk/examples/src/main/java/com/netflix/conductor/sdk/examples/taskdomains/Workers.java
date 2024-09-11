/*
 * Copyright 2023 Conductor Authors.
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
package com.netflix.conductor.sdk.examples.taskdomains;

import com.netflix.conductor.client.worker.Worker;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.sdk.workflow.task.WorkerTask;


/**
 * This program demonstrates different mechanisms for setting task domains during worker initialization
 * <p>
 * 1. Using task specific property in application.properties
 * 2. Using task agnostic property in application.properties
 * 3. Passing taskToDomains as constructor parameter when using TaskRunner
 * 4. Passing domain argument when using worker annotator
 * <p>
 * Sample workflow can be created using the definition task_domain_wf.json in the resources folder
 * <p>
 * Example Task to Domain Mapping for workflow creation when property, conductor.worker.all.domain, is set
 * {
 * "task-domain-property-simple-task": "test-domain-prop",
 * "task-domain-all-simple-task": "test-domain-common",
 * "task-domain-runner-simple-task": "test-domain-common",
 * "task-domain-annotated-simple-task": "test-domain-common"
 * }
 * <p>
 * Example Task to Domain Mapping for workflow creation when property, conductor.worker.all.domain, is not set
 * {
 * "task-domain-property-simple-task": "test-domain-prop",
 * "task-domain-runner-simple-task": "test-domain-runner",
 * "task-domain-annotated-simple-task": "test-domain-annotated",
 * }
 */
public class Workers {

    @WorkerTask(value = "task-domain-property-simple-task", pollingInterval = 200)
    public TaskResult sendPropertyTaskDomain(Task task) {
        // test-domain-prop should be picked up as Task Domain
        TaskResult result = new TaskResult(task);

        result.getOutputData().put("key", "value2");
        result.getOutputData().put("amount", 167.12);
        result.setStatus(TaskResult.Status.COMPLETED);

        return result;
    }

    @WorkerTask(value = "task-domain-all-simple-task", pollingInterval = 200)
    public TaskResult sendAllTaskDomain(Task task) {
        // test-domain-common should be picked up as Task Domain
        TaskResult result = new TaskResult(task);
        result.getOutputData().put("key", "value3");
        result.getOutputData().put("amount", 400);
        result.setStatus(TaskResult.Status.COMPLETED);

        return result;
    }

    @WorkerTask(value = "task-domain-annotated-simple-task", domain = "test-domain-annotated", pollingInterval = 200)
    public TaskResult sendAnnotatedTaskDomain(Task task) {
        // test-domain-common should be picked up as Task Domain if conductor.worker.all.domain is populated
        // For test-domain-annotated to be picked up, conductor.worker.all.domain shouldn't be populated
        TaskResult result = new TaskResult(task);

        result.getOutputData().put("key", "value");
        result.getOutputData().put("amount", 123.45);
        result.setStatus(TaskResult.Status.COMPLETED);

        return result;
    }

    static class TaskWorker implements Worker {

        @Override
        public String getTaskDefName() {
            return "task-domain-runner-simple-task";
        }

        public TaskResult execute(Task task) {
            TaskResult result = new TaskResult(task);

            result.getOutputData().put("key2", "value2");
            result.getOutputData().put("amount", 145);
            result.setStatus(TaskResult.Status.COMPLETED);

            return result;
        }
    }
}
