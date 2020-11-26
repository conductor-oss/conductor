/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.conductor.test.integration.http;

import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.dao.IndexDAO;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.springframework.beans.factory.annotation.Autowired;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class HttpEndToEndTest extends AbstractHttpEndToEndTest {

    @Autowired
    private IndexDAO indexDAO;

    @BeforeClass
    public static void setup() {

        container.start();

        String httpHostAddress = container.getHttpHostAddress();
        System.setProperty("workflow.elasticsearch.url", "http://" + httpHostAddress);
    }

    @AfterClass
    public static void cleanup() {
        container.stop();
    }

    @Before
    public void init() throws Exception {
        indexDAO.setup();

        apiRoot = String.format("http://localhost:%d/api/", port);

        taskClient = new TaskClient();
        taskClient.setRootURI(apiRoot);

        workflowClient = new WorkflowClient();
        workflowClient.setRootURI(apiRoot);

        metadataClient = new MetadataClient();
        metadataClient.setRootURI(apiRoot);
    }
}
