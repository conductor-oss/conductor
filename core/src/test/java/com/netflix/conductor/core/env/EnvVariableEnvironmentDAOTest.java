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
package com.netflix.conductor.core.env;

import java.util.List;

import org.junit.After;
import org.junit.Test;

import com.netflix.conductor.common.metadata.EnvironmentVariable;

import static org.junit.Assert.*;

public class EnvVariableEnvironmentDAOTest {

    private final EnvVariableEnvironmentDAO dao = new EnvVariableEnvironmentDAO("CONDUCTOR_ENV_");

    @After
    public void cleanup() {
        System.clearProperty("CONDUCTOR_ENV_REGION");
        System.clearProperty("CONDUCTOR_ENV_ZONE");
    }

    @Test
    public void testGetReadsPrefixedProperty() {
        System.setProperty("CONDUCTOR_ENV_REGION", "us-east-1");
        assertEquals("us-east-1", dao.getEnvVariable("REGION"));
    }

    @Test
    public void testGetMissingReturnsNull() {
        assertNull(dao.getEnvVariable("DOES_NOT_EXIST"));
    }

    @Test
    public void testGetAllStripsPrefix() {
        System.setProperty("CONDUCTOR_ENV_REGION", "us-east-1");
        System.setProperty("CONDUCTOR_ENV_ZONE", "a");
        List<EnvironmentVariable> all = dao.getAll();
        assertTrue(
                all.stream()
                        .anyMatch(
                                e ->
                                        e.getName().equals("REGION")
                                                && e.getValue().equals("us-east-1")));
        assertTrue(
                all.stream().anyMatch(e -> e.getName().equals("ZONE") && e.getValue().equals("a")));
        assertTrue(all.stream().noneMatch(e -> e.getName().startsWith("CONDUCTOR_ENV_")));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testSetThrows() {
        dao.setEnvVariable("REGION", "x");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testDeleteThrows() {
        dao.delete("REGION");
    }
}
