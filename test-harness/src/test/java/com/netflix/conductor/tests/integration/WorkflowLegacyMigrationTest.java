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
import com.netflix.conductor.core.orchestration.ExecutionDAOFacade;
import com.netflix.conductor.core.utils.IDGenerator;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.tests.utils.TestRunner;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.Assert.fail;

@RunWith(TestRunner.class)
public class WorkflowLegacyMigrationTest extends AbstractWorkflowServiceTest {

    private static final String WORKFLOW_SCENARIOS_PATH_PREFIX = "/integration/scenarios/legacy/";
    private static final String WORKFLOW_SCENARIO_EXTENSION = ".json";
    private static final String WORKFLOW_INSTANCE_ID_PLACEHOLDER = "WORKFLOW_INSTANCE_ID";

    @Inject
    private ExecutionDAO executionDAO;

    @Inject
    private ObjectMapper objectMapper;

    @Inject
    private Configuration configuration;

    @Inject
    ExecutionDAOFacade executionDAOFacade;

    @Override
    public String startOrLoadWorkflowExecution(String snapshotResourceName, String workflowName,
                                               int version, String correlationId, Map<String, Object> input,
                                               String event, Map<String, String> taskToDomain) {
        Workflow workflow = null;
        try {
            workflow = loadWorkflowSnapshot(getWorkflowResourcePath(snapshotResourceName));
        } catch (Exception e) {
            fail("Error loading workflow scenario " + snapshotResourceName);
        }

        final String workflowId = workflow.getWorkflowId();

        workflow.setCorrelationId(correlationId);
        workflow.setInput(input);
        workflow.setEvent(event);
        workflow.setTaskToDomain(taskToDomain);
        workflow.setVersion(version);

        workflow.getTasks().forEach(task -> {
            task.setTaskId(IDGenerator.generate());
            task.setWorkflowInstanceId(workflowId);
            task.setCorrelationId(correlationId);
        });

        executionDAOFacade.createWorkflow(workflow);
        executionDAOFacade.createTasks(workflow.getTasks());

        /*
         * Apart from loading a workflow snapshot,
         * in order to represent a workflow on the system, we need to populate the
         * respective queues related to tasks in progress or decisions.
         */
        workflow.getTasks().forEach(task -> {
            workflowExecutor.addTaskToQueue(task);
            queueDAO.push(WorkflowExecutor.DECIDER_QUEUE, workflowId, configuration.getSweepFrequency());
        });

        return workflow.getWorkflowId();
    }

    private Workflow loadWorkflowSnapshot(String resourcePath) throws Exception {

        String content = Resources.toString(WorkflowLegacyMigrationTest.class.getResource(resourcePath), StandardCharsets.UTF_8);
        String workflowId = IDGenerator.generate();
        content = content.replace(WORKFLOW_INSTANCE_ID_PLACEHOLDER, workflowId);

        Workflow workflow = objectMapper.readValue(content, Workflow.class);
        workflow.setWorkflowId(workflowId);

        return workflow;
    }

    private String getWorkflowResourcePath(String workflowName) {
        return WORKFLOW_SCENARIOS_PATH_PREFIX + workflowName + WORKFLOW_SCENARIO_EXTENSION;
    }

    @Ignore
    @Test
    @Override
    /*
     * This scenario cannot be recreated loading a workflow snapshot.
     * ForkJoins are also tested on testForkJoin()
     */
    public void testForkJoinNestedWithSubWorkflow() {
    }

    @Ignore
    @Test
    @Override
    public void testTerminateTaskWithFailedStatus() {
    }

    @Ignore
    @Test
    @Override
    public void testTerminateTaskWithCompletedStatus() {
    }

    @Ignore
    @Test
    @Override
    public void testTerminateMultiLevelWorkflow() {
    }

    @Ignore
    @Test
    @Override
    public void testForkJoinWithOptionalSubworkflows() {
    }

    @Ignore
    @Test
    @Override
    public void testTerminateTaskInASubworkflow() {
    }
}
