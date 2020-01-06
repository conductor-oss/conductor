package com.netflix.conductor.core.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Any;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.utils.JsonMapperProvider;
import org.junit.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

public class JsonMapperProviderTest {
    @Test
    public void testSimpleMapping() throws JsonGenerationException, JsonMappingException, IOException {
        ObjectMapper m = new JsonMapperProvider().get();
        assertTrue(m.canSerialize(Any.class));

        Struct struct1 = Struct.newBuilder().putFields(
                "some-key", Value.newBuilder().setStringValue("some-value").build()
        ).build();

        Any source = Any.pack(struct1);

        StringWriter buf = new StringWriter();
        m.writer().writeValue(buf, source);

        Any dest = m.reader().forType(Any.class).readValue(buf.toString());
        assertEquals(source.getTypeUrl(), dest.getTypeUrl());

        Struct struct2 = dest.unpack(Struct.class);
        assertTrue(struct2.containsFields("some-key"));
        assertEquals(
                struct1.getFieldsOrThrow("some-key").getStringValue(),
                struct2.getFieldsOrThrow("some-key").getStringValue()
        );
    }

    @Test
    public void testNullOnWrite() throws JsonProcessingException {
        Map<String, Object> data = new HashMap<>();
        data.put("someKey", null);
        data.put("someId", "abc123");
        ObjectMapper objectMapper = new JsonMapperProvider().get();
        String result = objectMapper.writeValueAsString(data);
        System.out.println(result);
        assertTrue(result.contains("null"));
    }

    @Test
    public void testWorkflowSerDe() throws IOException {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("testDef");
        workflowDef.setVersion(2);

        Workflow workflow = new Workflow();
        workflow.setWorkflowDefinition(workflowDef);
        workflow.setWorkflowId("test-workflow-id");
        workflow.setStatus(Workflow.WorkflowStatus.RUNNING);
        workflow.setStartTime(10L);
        workflow.setInput(null);

        Map<String, Object> data = new HashMap<>();
        data.put("someKey", null);
        data.put("someId", "abc123");
        workflow.setOutput(data);

        ObjectMapper objectMapper = new JsonMapperProvider().get();

        String workflowPayload = objectMapper.writeValueAsString(workflow);

        Workflow workflow1 = objectMapper.readValue(workflowPayload, Workflow.class);

        assertTrue(workflow1.getOutput().containsKey("someKey"));
        assertNull(workflow1.getOutput().get("someKey"));
        assertNotNull(workflow1.getInput());
    }
}
