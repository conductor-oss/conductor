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

import java.util.List;
import java.util.Map;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;

/** Internal, observable workflow that submits one completed root execution to OCG. */
final class OcgMemoryCaptureWorkflow {
    static final String NAME = "ocg_memory_capture";

    private OcgMemoryCaptureWorkflow() {}

    static WorkflowDef definition() {
        WorkflowTask capture = new WorkflowTask();
        capture.setName(OcgMemoryCaptureTask.TASK_TYPE);
        capture.setType(OcgMemoryCaptureTask.TASK_TYPE);
        capture.setTaskReferenceName("submit_execution_memory");
        capture.setInputParameters(
                Map.of("sourceExecutionId", "${workflow.input.sourceExecutionId}"));

        WorkflowDef definition = new WorkflowDef();
        definition.setName(NAME);
        definition.setVersion(1);
        definition.setSchemaVersion(2);
        definition.setInputParameters(List.of("sourceExecutionId"));
        definition.setTasks(List.of(capture));
        return definition;
    }
}
