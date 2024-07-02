/*
 * Copyright 2021 Conductor Authors.
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
package com.netflix.conductor.common.workflow;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.conductor.common.config.TestObjectMapperConfiguration;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.SubWorkflowParams;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import static org.junit.Assert.assertEquals;

@ContextConfiguration(classes = {TestObjectMapperConfiguration.class})
@RunWith(SpringRunner.class)
public class SubWorkflowParamsTest {

    @Autowired private ObjectMapper objectMapper;

    @Test
    public void testWorkflowSetTaskToDomain() {
        SubWorkflowParams subWorkflowParams = new SubWorkflowParams();
        Map<String, String> taskToDomain = new HashMap<>();
        taskToDomain.put("unit", "test");
        subWorkflowParams.setTaskToDomain(taskToDomain);
        assertEquals(taskToDomain, subWorkflowParams.getTaskToDomain());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetWorkflowDefinition() {
        SubWorkflowParams subWorkflowParams = new SubWorkflowParams();
        subWorkflowParams.setName("dummy-name");
        subWorkflowParams.setWorkflowDefinition(new Object());
    }

    @Test
    public void testGetWorkflowDef() {
        SubWorkflowParams subWorkflowParams = new SubWorkflowParams();
        subWorkflowParams.setName("dummy-name");
        WorkflowDef def = new WorkflowDef();
        def.setName("test_workflow");
        def.setVersion(1);
        WorkflowTask task = new WorkflowTask();
        task.setName("test_task");
        task.setTaskReferenceName("t1");
        def.getTasks().add(task);
        subWorkflowParams.setWorkflowDefinition(def);
        assertEquals(def, subWorkflowParams.getWorkflowDefinition());
    }

    @Test
    public void testWorkflowDefJson() throws Exception {
        SubWorkflowParams subWorkflowParams = new SubWorkflowParams();
        subWorkflowParams.setName("dummy-name");
        WorkflowDef def = new WorkflowDef();
        def.setName("test_workflow");
        def.setVersion(1);
        WorkflowTask task = new WorkflowTask();
        task.setName("test_task");
        task.setTaskReferenceName("t1");
        def.getTasks().add(task);
        subWorkflowParams.setWorkflowDefinition(def);

        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        objectMapper.enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY);
        objectMapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

        String serializedParams =
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(subWorkflowParams);
        SubWorkflowParams deserializedParams =
                objectMapper.readValue(serializedParams, SubWorkflowParams.class);
        var x = (WorkflowDef) deserializedParams.getWorkflowDefinition();
        assertEquals(def, x);

        var taskName = "taskName";
        var subWorkflowName = "subwf";
        TaskDef taskDef = new TaskDef(taskName);
        taskDef.setRetryCount(0);
        taskDef.setOwnerEmail("test@orkes.io");

        WorkflowTask inline = new WorkflowTask();
        inline.setTaskReferenceName(taskName);
        inline.setName(taskName);
        inline.setTaskDefinition(taskDef);
        inline.setWorkflowTaskType(TaskType.SIMPLE);
        inline.setInputParameters(Map.of("evaluatorType", "graaljs", "expression", "true;"));

        WorkflowDef subworkflowDef = new WorkflowDef();
        subworkflowDef.setName(subWorkflowName);
        subworkflowDef.setOwnerEmail("test@orkes.io");
        subworkflowDef.setInputParameters(Arrays.asList("value", "inlineValue"));
        subworkflowDef.setDescription("Sub Workflow to test retry");
        subworkflowDef.setTimeoutSeconds(600);
        subworkflowDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        subworkflowDef.setTasks(Arrays.asList(inline));

        // autowired
        var serializedSubWorkflowDef1 = objectMapper.writeValueAsString(subworkflowDef);
        var deserializedSubWorkflowDef1 =
                objectMapper.readValue(serializedSubWorkflowDef1, WorkflowDef.class);
        assertEquals(deserializedSubWorkflowDef1, subworkflowDef);
        // default
        ObjectMapper mapper = new ObjectMapper();
        var serializedSubWorkflowDef2 = mapper.writeValueAsString(subworkflowDef);
        var deserializedSubWorkflowDef2 =
                mapper.readValue(serializedSubWorkflowDef2, WorkflowDef.class);
        assertEquals(deserializedSubWorkflowDef2, subworkflowDef);
    }
}
