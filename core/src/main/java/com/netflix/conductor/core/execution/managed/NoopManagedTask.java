/*
 *  Copyright 2022 Netflix, Inc.
 *  <p>
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *  <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  <p>
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 *  an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *  specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.core.execution.managed;

import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component(NoopManagedTask.NOOP_MANAGED_TASK)
@ConditionalOnProperty(
        value = "conductor.managed-task-type.noop.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class NoopManagedTask extends ManagedTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(NoopManagedTask.class);
    public static final String NOOP_MANAGED_TASK = "NOOP";

    public NoopManagedTask(WorkflowExecutor workflowExecutor) {
        super(workflowExecutor);
    }

    @Override
    protected String getTaskType() {
        return NOOP_MANAGED_TASK;
    }

    @Override
    protected void invoke(WorkflowModel workflow, TaskModel task) {
        LOGGER.info(
                "Noop managed task : {} invoked in workflow: {}",
                task.getTaskId(),
                workflow.getWorkflowId());
    }

    @Override
    protected TaskResult callback() {
        LOGGER.info("Noop managed task callback");
        return null;
    }
}
