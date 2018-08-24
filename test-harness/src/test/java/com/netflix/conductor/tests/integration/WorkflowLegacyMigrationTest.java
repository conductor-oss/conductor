/**
 * Copyright 2016 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 *
 */
package com.netflix.conductor.tests.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.utils.IDGenerator;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.tests.utils.TestRunner;
import org.apache.commons.io.Charsets;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.inject.Inject;
import java.util.Map;

import static org.junit.Assert.fail;

@RunWith(TestRunner.class)
public class WorkflowLegacyMigrationTest extends AbstractWorkflowServiceTest {

    private static final String WORKFLOW_SCENARIOS_PATH_PREFIX = "/integration/scenarios/";
    private static final String WORKFLOW_SCENARIO_EXTENSION = ".json";

    @Inject
    private ExecutionDAO executionDAO;

    @Inject
    private ObjectMapper objectMapper;

    @Inject
    private Configuration configuration;

    @Before
    public void init() throws Exception {
        super.init();
    }

    private Workflow loadWorkflow(String resourcePath) throws Exception {

        String content = Resources.toString(WorkflowLegacyMigrationTest.class.getResource(resourcePath), Charsets.UTF_8);
        String workflowId = IDGenerator.generate();
        content = content.replace("WORKFLOW_INSTANCE_ID", workflowId);

        Workflow workflow = objectMapper.readValue(content, Workflow.class);
        workflow.setWorkflowId(workflowId);

        return workflow;
    }

    @Override
    String startOrLoadWorkflowExecution(String snapshotResourceName, String workflowName, int version, String correlationId, Map<String, Object> input, String event, Map<String, String> taskToDomain) {
        Workflow workflow = null;
        try {
            workflow = loadWorkflow(getWorkflowResourcePath(snapshotResourceName));
        } catch (Exception e) {
            fail("Error loading workflow scenario " + snapshotResourceName);
        }

        final String workflowId = workflow.getWorkflowId();

        workflow.setCorrelationId(correlationId);
        workflow.setInput(input);
        workflow.setEvent(event);
        workflow.setTaskToDomain(taskToDomain);
        workflow.setVersion(version);

        workflow.getTasks().stream().forEach(task -> {
            task.setTaskId(IDGenerator.generate());
            task.setWorkflowInstanceId(workflowId);
            task.setCorrelationId(correlationId);
        });

        executionDAO.createTasks(workflow.getTasks());
        executionDAO.createWorkflow(workflow);
        workflow.getTasks().stream().forEach(task -> {
            workflowExecutor.addTaskToQueue(task);
            queueDAO.push(WorkflowExecutor.DECIDER_QUEUE, workflowId, configuration.getSweepFrequency());
        });

        return workflow.getWorkflowId();
    }

    private String getWorkflowResourcePath(String workflowName) {
        return WORKFLOW_SCENARIOS_PATH_PREFIX + workflowName + WORKFLOW_SCENARIO_EXTENSION;
    }

    @Ignore
    @Test
    @Override
    public void testForkJoinNestedWithSubWorkflow() {
    }
}
