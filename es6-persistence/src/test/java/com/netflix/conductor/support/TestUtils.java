package com.netflix.conductor.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.utils.JsonMapperProvider;
import com.netflix.conductor.core.utils.IDGenerator;
import org.apache.commons.io.Charsets;

public class TestUtils {

    private static final String WORKFLOW_SCENARIO_EXTENSION = ".json";
    private static final String WORKFLOW_INSTANCE_ID_PLACEHOLDER = "WORKFLOW_INSTANCE_ID";

    private static ObjectMapper objectMapper = new JsonMapperProvider().get();

    public static Workflow loadWorkflowSnapshot(String resourceFileName) {
        try {
            String content = loadJsonResource(resourceFileName);
            String workflowId = IDGenerator.generate();
            content = content.replace(WORKFLOW_INSTANCE_ID_PLACEHOLDER, workflowId);

            Workflow workflow = objectMapper.readValue(content, Workflow.class);
            workflow.setWorkflowId(workflowId);

            return workflow;
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public static String loadJsonResource(String resourceFileName) {
        try {
            return Resources.toString(TestUtils.class.getResource("/" + resourceFileName + WORKFLOW_SCENARIO_EXTENSION), Charsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}

