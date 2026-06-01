/*
 * Copyright 2025 Conductor Authors.
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
package com.netflix.conductor.model;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.springframework.beans.BeanUtils;

import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest.TaskRateLimitOverride;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.Workflow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Verifies that task-level rate-limit overrides survive the model <-> DTO conversion that backs
 * workflow suspend/resume. The persistence layer stores the {@link Workflow} DTO produced by {@link
 * WorkflowModel#toWorkflow()}; if the field is missing from the DTO, overrides are silently dropped
 * when the workflow is rehydrated.
 */
public class WorkflowModelRateLimitOverridesTest {

    private static final String TASK_REF = "send_email";
    private static final String TASK_DEF = "email_task";

    private WorkflowModel newModelWithOverrides() {
        WorkflowDef def = new WorkflowDef();
        def.setName("wf_rate_limit_test");
        def.setVersion(1);

        WorkflowModel model = new WorkflowModel();
        model.setWorkflowId("wf-1");
        model.setWorkflowDefinition(def);
        model.setStatus(WorkflowModel.Status.RUNNING);

        Map<String, TaskRateLimitOverride> overrides = new HashMap<>();
        TaskRateLimitOverride byRef = new TaskRateLimitOverride();
        byRef.setRateLimitPerFrequency(10);
        byRef.setRateLimitFrequencyInSeconds(60);
        overrides.put(TASK_REF, byRef);

        TaskRateLimitOverride byDef = new TaskRateLimitOverride();
        byDef.setRateLimitPerFrequency(5);
        overrides.put(TASK_DEF, byDef);

        model.setTaskRateLimitOverrides(overrides);
        return model;
    }

    @Test
    public void overridesSurviveToWorkflowDtoConversion() {
        WorkflowModel model = newModelWithOverrides();

        Workflow dto = model.toWorkflow();

        assertNotNull(dto.getTaskRateLimitOverrides());
        assertEquals(2, dto.getTaskRateLimitOverrides().size());

        TaskRateLimitOverride refOverride = dto.getTaskRateLimitOverrides().get(TASK_REF);
        assertNotNull(refOverride);
        assertEquals(Integer.valueOf(10), refOverride.getRateLimitPerFrequency());
        assertEquals(Integer.valueOf(60), refOverride.getRateLimitFrequencyInSeconds());

        TaskRateLimitOverride defOverride = dto.getTaskRateLimitOverrides().get(TASK_DEF);
        assertNotNull(defOverride);
        assertEquals(Integer.valueOf(5), defOverride.getRateLimitPerFrequency());
        // Frequency-in-seconds was never set on the second override; must remain null.
        assertEquals(null, defOverride.getRateLimitFrequencyInSeconds());
    }

    @Test
    public void overridesSurviveDtoToModelRoundtrip() {
        // Simulates the suspend/resume path: WorkflowModel is converted to a Workflow DTO,
        // persisted, then read back and copied into a fresh WorkflowModel via BeanUtils.
        WorkflowModel original = newModelWithOverrides();
        Workflow stored = original.toWorkflow();

        WorkflowModel restored = new WorkflowModel();
        BeanUtils.copyProperties(stored, restored);

        assertEquals(2, restored.getTaskRateLimitOverrides().size());
        assertEquals(
                Integer.valueOf(10),
                restored.getTaskRateLimitOverrides().get(TASK_REF).getRateLimitPerFrequency());
        assertEquals(
                Integer.valueOf(60),
                restored.getTaskRateLimitOverrides()
                        .get(TASK_REF)
                        .getRateLimitFrequencyInSeconds());
        assertEquals(
                Integer.valueOf(5),
                restored.getTaskRateLimitOverrides().get(TASK_DEF).getRateLimitPerFrequency());
    }

    @Test
    public void emptyOverridesMapIsCarriedAsEmpty() {
        WorkflowModel model = new WorkflowModel();
        model.setWorkflowId("wf-2");
        WorkflowDef def = new WorkflowDef();
        def.setName("wf");
        def.setVersion(1);
        model.setWorkflowDefinition(def);
        model.setStatus(WorkflowModel.Status.RUNNING);

        Workflow dto = model.toWorkflow();

        assertNotNull(dto.getTaskRateLimitOverrides());
        assertEquals(0, dto.getTaskRateLimitOverrides().size());
    }
}
