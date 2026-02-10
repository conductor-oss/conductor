/*
 * Copyright 2023 Conductor Authors.
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
package org.conductoross.conductor.os3.dao.index;

import java.time.Instant;

import org.apache.hc.core5.http.HttpHost;
import org.conductoross.conductor.os3.config.OpenSearchProperties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opensearch.client.RestClient;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.springframework.retry.support.RetryTemplate;

import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.WorkflowSummary;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class QuickV3Test {

    private RestClient restClient;
    private OpenSearchRestDAO dao;
    private ObjectMapper objectMapper;

    @Before
    public void setup() throws Exception {
        objectMapper = new ObjectMapper();
        restClient = RestClient.builder(new HttpHost("http", "localhost", 9202)).build();
        RestClientTransport transport =
                new RestClientTransport(restClient, new JacksonJsonpMapper(objectMapper));
        OpenSearchClient client = new OpenSearchClient(transport);

        OpenSearchProperties props = new OpenSearchProperties();
        props.setUrl("http://localhost:9202");
        props.setIndexPrefix("conductor_v3_test");

        dao = new OpenSearchRestDAO(restClient, client, new RetryTemplate(), props, objectMapper);
        dao.setup();
    }

    @After
    public void tearDown() throws Exception {
        if (restClient != null) {
            restClient.close();
        }
    }

    @Test
    public void testBasicWorkflowOperations() throws Exception {
        // Create a test workflow
        WorkflowSummary workflow = new WorkflowSummary();
        workflow.setWorkflowId("test-workflow-" + System.currentTimeMillis());
        workflow.setWorkflowType("test_workflow");
        workflow.setVersion(1);
        workflow.setStatus(Workflow.WorkflowStatus.RUNNING);
        workflow.setStartTime(Instant.now().toString());

        // Index it
        dao.indexWorkflow(workflow);
        System.out.println("✓ Indexed workflow: " + workflow.getWorkflowId());

        // Give OpenSearch time to index
        Thread.sleep(1500);

        // Search for it
        var result =
                dao.searchWorkflows(
                        "workflowId='" + workflow.getWorkflowId() + "'", "*", 0, 10, null);
        System.out.println("✓ Search found " + result.getTotalHits() + " workflows");

        assertTrue("Should find the workflow", result.getTotalHits() > 0);
        assertEquals("Should find exactly 1 workflow", 1, result.getTotalHits());

        // Remove it
        dao.removeWorkflow(workflow.getWorkflowId());
        System.out.println("✓ Removed workflow");

        System.out.println("✅ TEST PASSED - os-persistence-v3 works with OpenSearch 3.0.0!");
    }
}
