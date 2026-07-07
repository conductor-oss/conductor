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
package com.netflix.conductor.core.secrets;

import java.util.Arrays;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.netflix.conductor.dao.EnvironmentDAO;
import com.netflix.conductor.dao.SecretsDAO;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class RuntimeMetadataResolverTest {

    @Mock private SecretsDAO secretsDAO;
    @Mock private EnvironmentDAO environmentDAO;

    private RuntimeMetadataResolver resolver;

    @Before
    public void setup() {
        resolver = new RuntimeMetadataResolver(secretsDAO, environmentDAO);
    }

    @Test
    public void testSecretsStoreHit() {
        when(secretsDAO.getSecret("A")).thenReturn("sA");

        Map<String, String> result = resolver.resolve(Arrays.asList("A"));

        assertEquals("sA", result.get("A"));
        verify(secretsDAO).getSecret("A");
        // environmentDAO should not be consulted for A when secret is found
        verify(environmentDAO, never()).getEnvVariable("A");
    }

    @Test
    public void testEnvironmentFallback() {
        when(secretsDAO.getSecret("B")).thenReturn(null);
        when(environmentDAO.getEnvVariable("B")).thenReturn("eB");

        Map<String, String> result = resolver.resolve(Arrays.asList("B"));

        assertEquals("eB", result.get("B"));
        verify(secretsDAO).getSecret("B");
        verify(environmentDAO).getEnvVariable("B");
    }

    @Test
    public void testSecretsPreferredOverEnvironment() {
        when(secretsDAO.getSecret("C")).thenReturn("sC");

        Map<String, String> result = resolver.resolve(Arrays.asList("C"));

        assertEquals("sC", result.get("C"));
        // environmentDAO should NOT be consulted when secret is found
        verify(environmentDAO, never()).getEnvVariable("C");
    }

    @Test
    public void testMissingNameOmitted() {
        when(secretsDAO.getSecret("MISSING")).thenReturn(null);
        when(environmentDAO.getEnvVariable("MISSING")).thenReturn(null);

        Map<String, String> result = resolver.resolve(Arrays.asList("MISSING"));

        assertFalse(result.containsKey("MISSING"));
        assertEquals(0, result.size());
    }

    @Test
    public void testNullListReturnsEmptyMap() {
        Map<String, String> result = resolver.resolve(null);

        assertEquals(0, result.size());
        verify(secretsDAO, never()).getSecret(anyString());
        verify(environmentDAO, never()).getEnvVariable(anyString());
    }

    @Test
    public void testEmptyListReturnsEmptyMap() {
        Map<String, String> result = resolver.resolve(Arrays.asList());

        assertEquals(0, result.size());
        verify(secretsDAO, never()).getSecret(anyString());
        verify(environmentDAO, never()).getEnvVariable(anyString());
    }

    @Test
    public void testMultipleNamesWithMixedResults() {
        when(secretsDAO.getSecret("A")).thenReturn("sA");
        when(secretsDAO.getSecret("B")).thenReturn(null);
        when(environmentDAO.getEnvVariable("B")).thenReturn("eB");
        when(secretsDAO.getSecret("C")).thenReturn(null);
        when(environmentDAO.getEnvVariable("C")).thenReturn(null);

        Map<String, String> result = resolver.resolve(Arrays.asList("A", "B", "C"));

        assertEquals("sA", result.get("A"));
        assertEquals("eB", result.get("B"));
        assertFalse(result.containsKey("C"));
        assertEquals(2, result.size());
    }
}
