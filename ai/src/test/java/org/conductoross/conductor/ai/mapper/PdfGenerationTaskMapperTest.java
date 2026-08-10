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
package org.conductoross.conductor.ai.mapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.conductoross.conductor.ai.tasks.mapper.PdfGenerationTaskMapper;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.execution.mapper.TaskMapperContext;
import com.netflix.conductor.core.utils.IDGenerator;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static org.junit.jupiter.api.Assertions.*;

public class PdfGenerationTaskMapperTest {

    @Test
    public void testTaskMapperReturnsCorrectTaskType() {
        PdfGenerationTaskMapper mapper = new PdfGenerationTaskMapper();
        assertEquals("GENERATE_PDF", mapper.getTaskType());
    }

    @Test
    public void testTaskMapperCreatesScheduledTask() {
        String taskType = "GENERATE_PDF";
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setName("pdf_generation_task");
        workflowTask.setType(taskType);
        String taskId = new IDGenerator().generate();

        WorkflowModel workflow = new WorkflowModel();
        WorkflowDef workflowDef = new WorkflowDef();
        workflow.setWorkflowDefinition(workflowDef);

        Map<String, Object> taskInputs = new HashMap<>();
        taskInputs.put("markdown", "# Test Document\n\nHello World");
        taskInputs.put("pageSize", "A4");

        TaskMapperContext taskMapperContext =
                getTaskMapperContext(workflow, workflowTask, taskId, taskInputs);

        PdfGenerationTaskMapper mapper = new PdfGenerationTaskMapper();
        List<TaskModel> mappedTasks = mapper.getMappedTasks(taskMapperContext);

        assertEquals(1, mappedTasks.size());
        assertEquals(taskType, mappedTasks.get(0).getTaskType());
        assertEquals(TaskModel.Status.SCHEDULED, mappedTasks.get(0).getStatus());
    }

    @Test
    public void testTaskMapperPreservesInputParameters() {
        String taskType = "GENERATE_PDF";
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setName("pdf_task");
        workflowTask.setType(taskType);
        String taskId = new IDGenerator().generate();

        WorkflowModel workflow = new WorkflowModel();
        WorkflowDef workflowDef = new WorkflowDef();
        workflow.setWorkflowDefinition(workflowDef);

        Map<String, Object> taskInputs = new HashMap<>();
        taskInputs.put("markdown", "# Report");
        taskInputs.put("pageSize", "LETTER");
        taskInputs.put("theme", "compact");
        taskInputs.put("baseFontSize", 12f);

        TaskMapperContext taskMapperContext =
                getTaskMapperContext(workflow, workflowTask, taskId, taskInputs);

        PdfGenerationTaskMapper mapper = new PdfGenerationTaskMapper();
        List<TaskModel> mappedTasks = mapper.getMappedTasks(taskMapperContext);

        assertEquals(1, mappedTasks.size());
        Map<String, Object> inputData = mappedTasks.get(0).getInputData();
        assertEquals("# Report", inputData.get("markdown"));
        assertEquals("LETTER", inputData.get("pageSize"));
        assertEquals("compact", inputData.get("theme"));
    }

    @Test
    public void testTaskMapperWithEmptyInputs() {
        String taskType = "GENERATE_PDF";
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setName("pdf_task");
        workflowTask.setType(taskType);
        String taskId = new IDGenerator().generate();

        WorkflowModel workflow = new WorkflowModel();
        WorkflowDef workflowDef = new WorkflowDef();
        workflow.setWorkflowDefinition(workflowDef);

        TaskMapperContext taskMapperContext =
                getTaskMapperContext(workflow, workflowTask, taskId, null);

        PdfGenerationTaskMapper mapper = new PdfGenerationTaskMapper();
        List<TaskModel> mappedTasks = mapper.getMappedTasks(taskMapperContext);

        assertEquals(1, mappedTasks.size());
        assertEquals(TaskModel.Status.SCHEDULED, mappedTasks.get(0).getStatus());
    }

    protected TaskMapperContext getTaskMapperContext(
            WorkflowModel workflowModel,
            WorkflowTask workflowTask,
            String taskId,
            Map<String, Object> inputs) {
        return TaskMapperContext.newBuilder()
                .withWorkflowModel(workflowModel)
                .withTaskDefinition(new TaskDef())
                .withWorkflowTask(workflowTask)
                .withTaskInput(inputs != null ? inputs : new HashMap<>())
                .withRetryCount(0)
                .withTaskId(taskId)
                .build();
    }
}
