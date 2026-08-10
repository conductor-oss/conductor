/*
 * Copyright 2022 Conductor Authors.
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

import java.util.UUID;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class IDGeneratorTest {

    @Test
    public void testGenerateSubWorkflowIdIsStableForTaskAttempt() {
        IDGenerator idGenerator = new IDGenerator();

        String first = idGenerator.generateSubWorkflowId("parent", "task", 0);
        String second = idGenerator.generateSubWorkflowId("parent", "task", 0);
        String retried = idGenerator.generateSubWorkflowId("parent", "task", 1);

        assertEquals(first, second);
        assertNotEquals(first, retried);
        UUID.fromString(first);
        UUID.fromString(retried);
    }
}
