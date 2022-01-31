/*
 * Copyright 2022 Netflix, Inc.
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
package com.netflix.conductor.contribs.tasks.json;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class JsonJqTransformTest {

    private final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();

    @Test
    public void dataShouldBeCorrectlySelected() {
        final JsonJqTransform jsonJqTransform = new JsonJqTransform(objectMapper);
        final WorkflowModel workflow = new WorkflowModel();
        final TaskModel task = new TaskModel();
        final Map<String, Object> inputData = new HashMap<>();
        inputData.put("queryExpression", ".inputJson.key[0]");
        final Map<String, Object> inputJson = new HashMap<>();
        inputJson.put("key", Collections.singletonList("VALUE"));
        inputData.put("inputJson", inputJson);
        task.setInputData(inputData);
        task.setOutputData(new HashMap<>());

        jsonJqTransform.start(workflow, task, null);

        assertNull(task.getOutputData().get("error"));
        assertEquals("\"VALUE\"", task.getOutputData().get("result").toString());
        assertEquals("[\"VALUE\"]", task.getOutputData().get("resultList").toString());
    }

    @Test
    public void simpleErrorShouldBeDisplayed() {
        final JsonJqTransform jsonJqTransform = new JsonJqTransform(objectMapper);
        final WorkflowModel workflow = new WorkflowModel();
        final TaskModel task = new TaskModel();
        final Map<String, Object> inputData = new HashMap<>();
        inputData.put("queryExpression", "{");
        task.setInputData(inputData);
        task.setOutputData(new HashMap<>());

        jsonJqTransform.start(workflow, task, null);

        assertTrue(
                ((String) task.getOutputData().get("error"))
                        .startsWith("Encountered \"<EOF>\" at line 1, column 1."));
    }

    @Test
    public void nestedExceptionsWithNACausesShouldBeDisregarded() {
        final JsonJqTransform jsonJqTransform = new JsonJqTransform(objectMapper);
        final WorkflowModel workflow = new WorkflowModel();
        final TaskModel task = new TaskModel();
        final Map<String, Object> inputData = new HashMap<>();
        inputData.put(
                "queryExpression",
                "{officeID: (.inputJson.OIDs | unique)[], requestedIndicatorList: .inputJson.requestedindicatorList}");
        final Map<String, Object> inputJson = new HashMap<>();
        inputJson.put("OIDs", Collections.singletonList("VALUE"));
        final Map<String, Object> indicatorList = new HashMap<>();
        indicatorList.put("indicator", "AFA");
        indicatorList.put("value", false);
        inputJson.put("requestedindicatorList", Collections.singletonList(indicatorList));
        inputData.put("inputJson", inputJson);
        task.setInputData(inputData);
        task.setOutputData(new HashMap<>());

        jsonJqTransform.start(workflow, task, null);

        assertTrue(
                ((String) task.getOutputData().get("error"))
                        .startsWith("Encountered \" \"[\" \"[ \"\" at line 1"));
    }
}
