/**
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
/**
 *
 */
package com.netflix.conductor.tests.integration;

import com.google.inject.Guice;
import com.google.inject.Injector;

import com.netflix.conductor.bootstrap.BootstrapModule;
import com.netflix.conductor.bootstrap.ModulesProvider;
import com.netflix.conductor.client.grpc.MetadataClient;
import com.netflix.conductor.client.grpc.TaskClient;
import com.netflix.conductor.client.grpc.WorkflowClient;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.Task.Status;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskDef.TimeoutPolicy;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask.Type;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.Workflow.WorkflowStatus;
import com.netflix.conductor.common.run.WorkflowSummary;
import com.netflix.conductor.elasticsearch.ElasticSearchConfiguration;
import com.netflix.conductor.elasticsearch.EmbeddedElasticSearch;
import com.netflix.conductor.elasticsearch.EmbeddedElasticSearchProvider;
import com.netflix.conductor.grpc.server.GRPCServer;
import com.netflix.conductor.grpc.server.GRPCServerConfiguration;
import com.netflix.conductor.grpc.server.GRPCServerProvider;
import com.netflix.conductor.tests.utils.TestEnvironment;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Viren
 *
 */
public class End2EndGrpcTests {
    private static TaskClient tc;
    private static WorkflowClient wc;
    private static MetadataClient mc;
    private static EmbeddedElasticSearch search;

    @BeforeClass
    public static void setup() throws Exception {
        TestEnvironment.setup();
        System.setProperty(GRPCServerConfiguration.ENABLED_PROPERTY_NAME, "true");
        System.setProperty(ElasticSearchConfiguration.EMBEDDED_PORT_PROPERTY_NAME, "9202");
        System.setProperty(ElasticSearchConfiguration.ELASTIC_SEARCH_URL_PROPERTY_NAME, "localhost:9302");

        Injector bootInjector = Guice.createInjector(new BootstrapModule());
        Injector serverInjector = Guice.createInjector(bootInjector.getInstance(ModulesProvider.class).get());

        search = serverInjector.getInstance(EmbeddedElasticSearchProvider.class).get().get();
        search.start();

        Optional<GRPCServer> server = serverInjector.getInstance(GRPCServerProvider.class).get();
        assertTrue("failed to instantiate GRPCServer", server.isPresent());
        server.get().start();

        tc = new TaskClient("localhost", 8090);
        wc = new WorkflowClient("localhost", 8090);
        mc = new MetadataClient("localhost", 8090);
    }

    @AfterClass
    public static void teardown() throws Exception {
        TestEnvironment.teardown();
        search.stop();
    }

    @Test
    public void testAll() throws Exception {
        assertNotNull(tc);
        List<TaskDef> defs = new LinkedList<>();
        for (int i = 0; i < 5; i++) {
            TaskDef def = new TaskDef("t" + i, "task " + i);
            def.setTimeoutPolicy(TimeoutPolicy.RETRY);
            defs.add(def);
        }
        mc.registerTaskDefs(defs);

        for (int i = 0; i < 5; i++) {
            final String taskName = "t" + i;
            TaskDef def = mc.getTaskDef(taskName);
            assertNotNull(def);
            assertEquals(taskName, def.getName());
        }

        WorkflowDef def = new WorkflowDef();
        def.setName("test");
        WorkflowTask t0 = new WorkflowTask();
        t0.setName("t0");
        t0.setWorkflowTaskType(Type.SIMPLE);
        t0.setTaskReferenceName("t0");

        WorkflowTask t1 = new WorkflowTask();
        t1.setName("t1");
        t1.setWorkflowTaskType(Type.SIMPLE);
        t1.setTaskReferenceName("t1");


        def.getTasks().add(t0);
        def.getTasks().add(t1);

        mc.registerWorkflowDef(def);
        WorkflowDef foundd = mc.getWorkflowDef(def.getName(), null);
        assertNotNull(foundd);
        assertEquals(def.getName(), foundd.getName());
        assertEquals(def.getVersion(), foundd.getVersion());

        String correlationId = "test_corr_id";
        StartWorkflowRequest startWf = new StartWorkflowRequest();
        startWf.setName(def.getName());
        startWf.setCorrelationId(correlationId);

        String workflowId = wc.startWorkflow(startWf);
        assertNotNull(workflowId);
        System.out.println("Started workflow id=" + workflowId);

        Workflow wf = wc.getWorkflow(workflowId, false);
        assertEquals(0, wf.getTasks().size());
        assertEquals(workflowId, wf.getWorkflowId());

        wf = wc.getWorkflow(workflowId, true);
        assertNotNull(wf);
        assertEquals(WorkflowStatus.RUNNING, wf.getStatus());
        assertEquals(1, wf.getTasks().size());
        assertEquals(t0.getTaskReferenceName(), wf.getTasks().get(0).getReferenceTaskName());
        assertEquals(workflowId, wf.getWorkflowId());

        List<String> runningIds = wc.getRunningWorkflow(def.getName(), def.getVersion());
        assertNotNull(runningIds);
        assertEquals(1, runningIds.size());
        assertEquals(workflowId, runningIds.get(0));

        List<Task> polled = tc.batchPollTasksByTaskType("non existing task", "test", 1, 100);
        assertNotNull(polled);
        assertEquals(0, polled.size());

        polled = tc.batchPollTasksByTaskType(t0.getName(), "test", 1, 100);
        assertNotNull(polled);
        assertEquals(1, polled.size());
        assertEquals(t0.getName(), polled.get(0).getTaskDefName());
        Task task = polled.get(0);

        Boolean acked = tc.ack(task.getTaskId(), "test");
        assertNotNull(acked);
        assertTrue(acked);

        task.getOutputData().put("key1", "value1");
        task.setStatus(Status.COMPLETED);
        tc.updateTask(new TaskResult(task));

        polled = tc.batchPollTasksByTaskType(t0.getName(), "test", 1, 100);
        assertNotNull(polled);
        assertTrue(polled.toString(), polled.isEmpty());

        wf = wc.getWorkflow(workflowId, true);
        assertNotNull(wf);
        assertEquals(WorkflowStatus.RUNNING, wf.getStatus());
        assertEquals(2, wf.getTasks().size());
        assertEquals(t0.getTaskReferenceName(), wf.getTasks().get(0).getReferenceTaskName());
        assertEquals(t1.getTaskReferenceName(), wf.getTasks().get(1).getReferenceTaskName());
        assertEquals(Status.COMPLETED, wf.getTasks().get(0).getStatus());
        assertEquals(Status.SCHEDULED, wf.getTasks().get(1).getStatus());

        Task taskById = tc.getTaskDetails(task.getTaskId());
        assertNotNull(taskById);
        assertEquals(task.getTaskId(), taskById.getTaskId());


        List<Task> getTasks = tc.getPendingTasksByType(t0.getName(), null, 1);
        assertNotNull(getTasks);
        assertEquals(0, getTasks.size());        //getTasks only gives pending tasks


        getTasks = tc.getPendingTasksByType(t1.getName(), null, 1);
        assertNotNull(getTasks);
        assertEquals(1, getTasks.size());


        Task pending = tc.getPendingTaskForWorkflow(workflowId, t1.getTaskReferenceName());
        assertNotNull(pending);
        assertEquals(t1.getTaskReferenceName(), pending.getReferenceTaskName());
        assertEquals(workflowId, pending.getWorkflowInstanceId());

        Thread.sleep(1000);
        SearchResult<WorkflowSummary> searchResult = wc.search("workflowType='" + def.getName() + "'");
        assertNotNull(searchResult);
        assertEquals(1, searchResult.getTotalHits());

        wc.terminateWorkflow(workflowId, "terminate reason");
        wf = wc.getWorkflow(workflowId, true);
        assertNotNull(wf);
        assertEquals(WorkflowStatus.TERMINATED, wf.getStatus());

        wc.restart(workflowId);
        wf = wc.getWorkflow(workflowId, true);
        assertNotNull(wf);
        assertEquals(WorkflowStatus.RUNNING, wf.getStatus());
        assertEquals(1, wf.getTasks().size());
    }
}
