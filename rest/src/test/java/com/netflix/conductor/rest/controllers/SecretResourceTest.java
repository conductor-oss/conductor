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

import com.netflix.conductor.dao.SecretsDAO;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class SecretResourceTest {

    private SecretsDAO dao;
    private SecretResource resource;

    @Before
    public void setUp() {
        dao = mock(SecretsDAO.class);
        resource = new SecretResource(dao);
    }

    @Test
    public void testListNamesOnly() {
        when(dao.listSecretNames()).thenReturn(List.of("DB_PASSWORD"));
        List<String> names = resource.listSecretNames();
        assertEquals(1, names.size());
        assertEquals("DB_PASSWORD", names.get(0));
    }
}
