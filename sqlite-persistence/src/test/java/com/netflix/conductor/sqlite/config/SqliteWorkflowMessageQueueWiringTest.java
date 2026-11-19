/*
 * Copyright 2026 Conductor Authors.
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
package com.netflix.conductor.sqlite.config;

import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.conductor.common.config.TestObjectMapperConfiguration;
import com.netflix.conductor.core.config.WorkflowMessageQueueConfiguration;
import com.netflix.conductor.core.dao.JdbcWorkflowMessageQueueDAO;
import com.netflix.conductor.dao.WorkflowMessageQueueDAO;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Which workflow message queue store exists, with the feature disabled (the default) and enabled.
 */
@RunWith(Enclosed.class)
public class SqliteWorkflowMessageQueueWiringTest {

    @ContextConfiguration(
            classes = {
                TestObjectMapperConfiguration.class,
                SqliteConfiguration.class,
                FlywayAutoConfiguration.class,
                WorkflowMessageQueueConfiguration.class
            })
    @RunWith(SpringRunner.class)
    @TestPropertySource(properties = "spring.flyway.clean-disabled=false")
    @SpringBootTest
    public static class Disabled {

        @Autowired private ObjectProvider<WorkflowMessageQueueDAO> dao;

        @Test
        public void noStoreIsCreatedByDefault() {
            assertNull(dao.getIfAvailable());
        }
    }

    @ContextConfiguration(
            classes = {
                TestObjectMapperConfiguration.class,
                SqliteConfiguration.class,
                FlywayAutoConfiguration.class,
                WorkflowMessageQueueConfiguration.class
            })
    @RunWith(SpringRunner.class)
    @TestPropertySource(
            properties = {
                "spring.flyway.clean-disabled=false",
                "conductor.workflow-message-queue.enabled=true"
            })
    @SpringBootTest
    public static class Enabled {

        @Autowired private WorkflowMessageQueueDAO dao;

        @Test
        public void theDurableStoreIsTheOneInjected() {
            assertTrue(dao instanceof JdbcWorkflowMessageQueueDAO);
        }
    }
}
