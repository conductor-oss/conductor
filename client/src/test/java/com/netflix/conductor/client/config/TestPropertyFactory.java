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
package com.netflix.conductor.client.config;

import org.junit.Test;

import com.netflix.conductor.client.worker.Worker;
import com.netflix.conductor.common.metadata.tasks.TaskResult;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TestPropertyFactory {

    @Test
    public void testIdentity() {
        Worker worker = Worker.create("Test2", TaskResult::new);
        assertNotNull(worker.getIdentity());
        boolean paused = worker.paused();
        assertFalse("Paused? " + paused, paused);
    }

    @Test
    public void test() {

        int val = PropertyFactory.getInteger("workerB", "pollingInterval", 100);
        assertEquals("got: " + val, 2, val);
        assertEquals(
                100, PropertyFactory.getInteger("workerB", "propWithoutValue", 100).intValue());

        assertFalse(
                PropertyFactory.getBoolean(
                        "workerB", "paused", true)); // Global value set to 'false'
        assertTrue(
                PropertyFactory.getBoolean(
                        "workerA", "paused", false)); // WorkerA value set to 'true'

        assertEquals(
                42,
                PropertyFactory.getInteger("workerA", "batchSize", 42)
                        .intValue()); // No global value set, so will return the default value
        // supplied
        assertEquals(
                84,
                PropertyFactory.getInteger("workerB", "batchSize", 42)
                        .intValue()); // WorkerB's value set to 84

        assertEquals("domainA", PropertyFactory.getString("workerA", "domain", null));
        assertEquals("domainB", PropertyFactory.getString("workerB", "domain", null));
        assertNull(PropertyFactory.getString("workerC", "domain", null)); // Non Existent
    }

    @Test
    public void testProperty() {
        Worker worker = Worker.create("Test", TaskResult::new);
        boolean paused = worker.paused();
        assertTrue("Paused? " + paused, paused);
    }
}
