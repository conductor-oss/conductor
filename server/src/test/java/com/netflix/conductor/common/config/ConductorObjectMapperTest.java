/*
 * Copyright 2021 Netflix, Inc.
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
package com.netflix.conductor.common.config;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.Workflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Any;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests the customized {@link ObjectMapper} that is used by {@link com.netflix.conductor.Conductor}
 * application.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@RunWith(SpringRunner.class)
@TestPropertySource(properties = "conductor.queue.type=")
public class ConductorObjectMapperTest {

    @Autowired ObjectMapper objectMapper;

    @Test
    public void testSimpleMapping() throws IOException {
        assertTrue(objectMapper.canSerialize(Any.class));

        Struct struct1 =
                Struct.newBuilder()
                        .putFields(
                                "some-key", Value.newBuilder().setStringValue("some-value").build())
                        .build();

        Any source = Any.pack(struct1);

        StringWriter buf = new StringWriter();
        objectMapper.writer().writeValue(buf, source);

        Any dest = objectMapper.reader().forType(Any.class).readValue(buf.toString());
        assertEquals(source.getTypeUrl(), dest.getTypeUrl());

        Struct struct2 = dest.unpack(Struct.class);
        assertTrue(struct2.containsFields("some-key"));
        assertEquals(
                struct1.getFieldsOrThrow("some-key").getStringValue(),
                struct2.getFieldsOrThrow("some-key").getStringValue());
    }

    @Test
    public void testNullOnWrite() throws JsonProcessingException {
        Map<String, Object> data = new HashMap<>();
        data.put("someKey", null);
        data.put("someId", "abc123");
        String result = objectMapper.writeValueAsString(data);
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

        String workflowPayload = objectMapper.writeValueAsString(workflow);
        Workflow workflow1 = objectMapper.readValue(workflowPayload, Workflow.class);

        assertTrue(workflow1.getOutput().containsKey("someKey"));
        assertNull(workflow1.getOutput().get("someKey"));
        assertNotNull(workflow1.getInput());
    }
}
