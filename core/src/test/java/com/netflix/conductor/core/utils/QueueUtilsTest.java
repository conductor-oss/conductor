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
package com.netflix.conductor.core.utils;

import org.junit.Assert;
import org.junit.Test;

public class QueueUtilsTest {

    @Test
    public void queueNameWithTypeAndIsolationGroup() {
        String queueNameGenerated = QueueUtils.getQueueName("tType", null, "isolationGroup", null);
        String queueNameGeneratedOnlyType = QueueUtils.getQueueName("tType", null, null, null);
        String queueNameGeneratedWithAllValues =
                QueueUtils.getQueueName("tType", "domain", "iso", "eN");

        Assert.assertEquals("tType-isolationGroup", queueNameGenerated);
        Assert.assertEquals("tType", queueNameGeneratedOnlyType);
        Assert.assertEquals("domain:tType@eN-iso", queueNameGeneratedWithAllValues);
    }

    @Test
    public void notIsolatedIfSeparatorNotPresent() {
        String notIsolatedQueue = "notIsolated";
        Assert.assertFalse(QueueUtils.isIsolatedQueue(notIsolatedQueue));
    }

    @Test
    public void testGetExecutionNameSpace() {
        String executionNameSpace = QueueUtils.getExecutionNameSpace("domain:queueName@eN-iso");
        Assert.assertEquals(executionNameSpace, "eN");
    }

    @Test
    public void testGetQueueExecutionNameSpaceEmpty() {
        Assert.assertEquals(QueueUtils.getExecutionNameSpace("queueName"), "");
    }

    @Test
    public void testGetQueueExecutionNameSpaceWithIsolationGroup() {
        Assert.assertEquals(
                QueueUtils.getExecutionNameSpace("domain:test@executionNameSpace-isolated"),
                "executionNameSpace");
    }

    @Test
    public void testGetQueueName() {
        Assert.assertEquals(
                "domain:taskType@eN-isolated",
                QueueUtils.getQueueName("taskType", "domain", "isolated", "eN"));
    }

    @Test
    public void testGetTaskType() {
        Assert.assertEquals("taskType", QueueUtils.getTaskType("domain:taskType-isolated"));
    }

    @Test
    public void testGetTaskTypeWithoutDomain() {
        Assert.assertEquals("taskType", QueueUtils.getTaskType("taskType-isolated"));
    }

    @Test
    public void testGetTaskTypeWithoutDomainAndWithoutIsolationGroup() {
        Assert.assertEquals("taskType", QueueUtils.getTaskType("taskType"));
    }

    @Test
    public void testGetTaskTypeWithoutDomainAndWithExecutionNameSpace() {
        Assert.assertEquals("taskType", QueueUtils.getTaskType("taskType@eN"));
    }
}
