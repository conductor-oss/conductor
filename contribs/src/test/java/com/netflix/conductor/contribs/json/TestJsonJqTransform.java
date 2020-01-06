package com.netflix.conductor.contribs.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.utils.JsonMapperProvider;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class TestJsonJqTransform {

    private ObjectMapper objectMapper = new JsonMapperProvider().get();

    @Test
    public void dataShouldBeCorrectlySelected() {
        final JsonJqTransform t = new JsonJqTransform(objectMapper);
        final Workflow w = new Workflow();
        final Task task = new Task();
        final Map<String, Object> inputData = new HashMap<>();
        inputData.put("queryExpression", ".inputJson.key[0]");
        final Map<String, Object> inputJson = new HashMap<>();
        inputJson.put("key", Collections.singletonList("VALUE"));
        inputData.put("inputJson", inputJson);
        task.setInputData(inputData);
        task.setOutputData(new HashMap<>());

        t.start(w, task, null);

        Assert.assertNull(task.getOutputData().get("error"));
        Assert.assertEquals("\"VALUE\"", task.getOutputData().get("result").toString());
        Assert.assertEquals("[\"VALUE\"]", task.getOutputData().get("resultList").toString());
    }

    @Test
    public void simpleErrorShouldBeDisplayed() {
        final JsonJqTransform t = new JsonJqTransform(objectMapper);
        final Workflow w = new Workflow();
        final Task task = new Task();
        final Map<String, Object> inputData = new HashMap<>();
        inputData.put("queryExpression", "{");
        task.setInputData(inputData);
        task.setOutputData(new HashMap<>());

        t.start(w, task, null);

        Assert.assertTrue(((String)task.getOutputData().get("error")).startsWith("Encountered \"<EOF>\" at line 1, column 1."));
    }

    @Test
    public void nestedExceptionsWithNACausesShouldBeDisregarded() {
        final JsonJqTransform t = new JsonJqTransform(objectMapper);
        final Workflow w = new Workflow();
        final Task task = new Task();
        final Map<String, Object> inputData = new HashMap<>();
        inputData.put("queryExpression", "{officeID: (.inputJson.OIDs | unique)[], requestedIndicatorList: .inputJson.requestedindicatorList}");
        final Map<String, Object> inputJson = new HashMap<>();
        inputJson.put("OIDs", Collections.singletonList("VALUE"));
        final Map<String, Object> indicatorList = new HashMap<>();
        indicatorList.put("indicator", "AFA");
        indicatorList.put("value", false);
        inputJson.put("requestedindicatorList", Collections.singletonList(indicatorList));
        inputData.put("inputJson", inputJson);
        task.setInputData(inputData);
        task.setOutputData(new HashMap<>());

        t.start(w, task, null);

        Assert.assertTrue(((String)task.getOutputData().get("error")).startsWith("Encountered \" \"[\" \"[ \"\" at line 1"));
    }
}
