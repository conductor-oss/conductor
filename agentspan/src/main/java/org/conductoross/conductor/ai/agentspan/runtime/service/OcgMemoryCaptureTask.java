/*
 * Copyright 2026 Conductor Authors.
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
package org.conductoross.conductor.ai.agentspan.runtime.service;

import java.util.Map;

import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

/** Submits a source execution through the existing redacted OCG capture path. */
public class OcgMemoryCaptureTask extends WorkflowSystemTask {
    static final String TASK_TYPE = "OCG_MEMORY_CAPTURE";

    private final ExecutionDAO executionDAO;
    private final OcgAgentRunExporter exporter;
    private final OcgClient ocgClient;

    OcgMemoryCaptureTask(
            ExecutionDAO executionDAO, OcgAgentRunExporter exporter, OcgClient ocgClient) {
        super(TASK_TYPE);
        this.executionDAO = executionDAO;
        this.exporter = exporter;
        this.ocgClient = ocgClient;
    }

    @Override
    public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
        String sourceExecutionId = String.valueOf(task.getInputData().get("sourceExecutionId"));
        WorkflowModel source = executionDAO.getWorkflow(sourceExecutionId, true);
        if (source == null) {
            task.setReasonForIncompletion("Source execution was not found");
            task.setStatus(TaskModel.Status.FAILED);
            return;
        }
        try {
            OcgAgentRunCapture capture = exporter.capture(source);
            if (capture == null)
                throw new IllegalStateException("Source execution has no OCG memory configuration");
            ocgClient.captureAgentRun(capture.config(), capture.payload());
            task.setOutputData(Map.of("sourceExecutionId", sourceExecutionId));
            task.setStatus(TaskModel.Status.COMPLETED);
        } catch (Exception e) {
            task.setReasonForIncompletion("Unable to submit execution memory");
            task.setStatus(TaskModel.Status.FAILED);
        }
    }
}
