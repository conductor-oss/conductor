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
package com.netflix.conductor.test.integration.grpc.postgres;

import com.netflix.conductor.client.grpc.MetadataClient;
import com.netflix.conductor.client.grpc.TaskClient;
import com.netflix.conductor.client.grpc.WorkflowClient;
import com.netflix.conductor.test.integration.grpc.AbstractGrpcEndToEndTest;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
@RunWith(SpringRunner.class)
@TestPropertySource(properties = {
    "db=postgres",
    "conductor.grpc.server.port=8098",
    "jdbc.url=jdbc:tc:postgresql:///conductor", // "tc" prefix starts the Postgres container
    "jdbc.username=postgres",
    "jdbc.password=postgres",
    "conductor.postgres.connection.pool.size.min=8",
    "conductor.postgres.connection.pool.size.max=8",
    "conductor.postgres.connection.pool.idle.min=300000",
    "spring.flyway.enabled=false"
})
public class PostgresGrpcEndToEndTest extends AbstractGrpcEndToEndTest {

    @Before
    public void init() throws Exception {
        taskClient = new TaskClient("localhost", 8098);
        workflowClient = new WorkflowClient("localhost", 8098);
        metadataClient = new MetadataClient("localhost", 8098);
    }
}
