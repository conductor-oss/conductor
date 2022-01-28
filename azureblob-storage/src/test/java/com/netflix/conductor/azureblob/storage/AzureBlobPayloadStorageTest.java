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
package com.netflix.conductor.azureblob.storage;

import java.time.Duration;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.netflix.conductor.azureblob.config.AzureBlobProperties;
import com.netflix.conductor.common.run.ExternalStorageLocation;
import com.netflix.conductor.common.utils.ExternalPayloadStorage;
import com.netflix.conductor.core.exception.ApplicationException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AzureBlobPayloadStorageTest {

    private AzureBlobProperties properties;

    @Before
    public void setUp() {
        properties = mock(AzureBlobProperties.class);
        when(properties.getConnectionString()).thenReturn(null);
        when(properties.getContainerName()).thenReturn("conductor-payloads");
        when(properties.getEndpoint()).thenReturn(null);
        when(properties.getSasToken()).thenReturn(null);
        when(properties.getSignedUrlExpirationDuration()).thenReturn(Duration.ofSeconds(5));
        when(properties.getWorkflowInputPath()).thenReturn("workflow/input/");
        when(properties.getWorkflowOutputPath()).thenReturn("workflow/output/");
        when(properties.getTaskInputPath()).thenReturn("task/input");
        when(properties.getTaskOutputPath()).thenReturn("task/output/");
    }

    /** Dummy credentials Azure SDK doesn't work with Azurite since it cleans parameters */
    private final String azuriteConnectionString =
            "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;BlobEndpoint=http://127.0.0.1:10000/devstoreaccount1;EndpointSuffix=localhost";

    @Rule public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void testNoStorageAccount() {
        expectedException.expect(ApplicationException.class);
        new AzureBlobPayloadStorage(properties);
    }

    @Test
    public void testUseConnectionString() {
        when(properties.getConnectionString()).thenReturn(azuriteConnectionString);
        new AzureBlobPayloadStorage(properties);
    }

    @Test
    public void testUseEndpoint() {
        String azuriteEndpoint = "http://127.0.0.1:10000/";
        when(properties.getEndpoint()).thenReturn(azuriteEndpoint);
        new AzureBlobPayloadStorage(properties);
    }

    @Test
    public void testGetLocationFixedPath() {
        when(properties.getConnectionString()).thenReturn(azuriteConnectionString);
        AzureBlobPayloadStorage azureBlobPayloadStorage = new AzureBlobPayloadStorage(properties);
        String path = "somewhere";
        ExternalStorageLocation externalStorageLocation =
                azureBlobPayloadStorage.getLocation(
                        ExternalPayloadStorage.Operation.READ,
                        ExternalPayloadStorage.PayloadType.WORKFLOW_INPUT,
                        path);
        assertNotNull(externalStorageLocation);
        assertEquals(path, externalStorageLocation.getPath());
        assertNotNull(externalStorageLocation.getUri());
    }

    private void testGetLocation(
            AzureBlobPayloadStorage azureBlobPayloadStorage,
            ExternalPayloadStorage.Operation operation,
            ExternalPayloadStorage.PayloadType payloadType,
            String expectedPath) {
        ExternalStorageLocation externalStorageLocation =
                azureBlobPayloadStorage.getLocation(operation, payloadType, null);
        assertNotNull(externalStorageLocation);
        assertNotNull(externalStorageLocation.getPath());
        assertTrue(externalStorageLocation.getPath().startsWith(expectedPath));
        assertNotNull(externalStorageLocation.getUri());
        assertTrue(externalStorageLocation.getUri().contains(expectedPath));
    }

    @Test
    public void testGetAllLocations() {
        when(properties.getConnectionString()).thenReturn(azuriteConnectionString);
        AzureBlobPayloadStorage azureBlobPayloadStorage = new AzureBlobPayloadStorage(properties);

        testGetLocation(
                azureBlobPayloadStorage,
                ExternalPayloadStorage.Operation.READ,
                ExternalPayloadStorage.PayloadType.WORKFLOW_INPUT,
                properties.getWorkflowInputPath());
        testGetLocation(
                azureBlobPayloadStorage,
                ExternalPayloadStorage.Operation.READ,
                ExternalPayloadStorage.PayloadType.WORKFLOW_OUTPUT,
                properties.getWorkflowOutputPath());
        testGetLocation(
                azureBlobPayloadStorage,
                ExternalPayloadStorage.Operation.READ,
                ExternalPayloadStorage.PayloadType.TASK_INPUT,
                properties.getTaskInputPath());
        testGetLocation(
                azureBlobPayloadStorage,
                ExternalPayloadStorage.Operation.READ,
                ExternalPayloadStorage.PayloadType.TASK_OUTPUT,
                properties.getTaskOutputPath());

        testGetLocation(
                azureBlobPayloadStorage,
                ExternalPayloadStorage.Operation.WRITE,
                ExternalPayloadStorage.PayloadType.WORKFLOW_INPUT,
                properties.getWorkflowInputPath());
        testGetLocation(
                azureBlobPayloadStorage,
                ExternalPayloadStorage.Operation.WRITE,
                ExternalPayloadStorage.PayloadType.WORKFLOW_OUTPUT,
                properties.getWorkflowOutputPath());
        testGetLocation(
                azureBlobPayloadStorage,
                ExternalPayloadStorage.Operation.WRITE,
                ExternalPayloadStorage.PayloadType.TASK_INPUT,
                properties.getTaskInputPath());
        testGetLocation(
                azureBlobPayloadStorage,
                ExternalPayloadStorage.Operation.WRITE,
                ExternalPayloadStorage.PayloadType.TASK_OUTPUT,
                properties.getTaskOutputPath());
    }
}
