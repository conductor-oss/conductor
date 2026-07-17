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
package com.netflix.conductor.rest.controllers;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.netflix.conductor.common.metadata.EnvironmentVariable;
import com.netflix.conductor.dao.EnvironmentDAO;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class EnvironmentResourceTest {

    private EnvironmentDAO dao;
    private EnvironmentResource resource;

    @Before
    public void setUp() {
        dao = mock(EnvironmentDAO.class);
        resource = new EnvironmentResource(dao);
    }

    @Test
    public void testList() {
        when(dao.getAll()).thenReturn(List.of(EnvironmentVariable.of("REGION", "us-east-1")));
        List<EnvironmentVariable> all = resource.getAll();
        assertEquals(1, all.size());
        assertEquals("REGION", all.get(0).getName());
    }

    @Test
    public void testGet() {
        when(dao.getEnvVariable("REGION")).thenReturn("us-east-1");
        assertEquals("us-east-1", resource.get("REGION"));
    }
}
