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
package com.netflix.conductor.test.integration.grpc.mysql;

import org.junit.Before;
import org.junit.runner.RunWith;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.conductor.client.grpc.EventClient;
import com.netflix.conductor.client.grpc.MetadataClient;
import com.netflix.conductor.client.grpc.TaskClient;
import com.netflix.conductor.client.grpc.WorkflowClient;
import com.netflix.conductor.test.integration.grpc.AbstractGrpcEndToEndTest;

@RunWith(SpringRunner.class)
@TestPropertySource(
        properties = {
            "conductor.db.type=mysql",
            "conductor.grpc-server.port=8094",
            "spring.datasource.url=jdbc:tc:mysql:///conductor", // "tc" prefix starts the MySql
            // container
            "spring.datasource.username=root",
            "spring.datasource.password=root",
            "spring.datasource.hikari.maximum-pool-size=8",
            "spring.datasource.hikari.minimum-idle=300000"
        })
public class MySQLGrpcEndToEndTest extends AbstractGrpcEndToEndTest {

    @Before
    public void init() {
        taskClient = new TaskClient("localhost", 8094);
        workflowClient = new WorkflowClient("localhost", 8094);
        metadataClient = new MetadataClient("localhost", 8094);
        eventClient = new EventClient("localhost", 8094);
    }
}
