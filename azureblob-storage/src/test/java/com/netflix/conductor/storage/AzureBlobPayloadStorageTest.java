package com.netflix.conductor.storage;

import com.netflix.conductor.common.run.ExternalStorageLocation;
import com.netflix.conductor.common.utils.ExternalPayloadStorage;
import com.netflix.conductor.config.InvalidTestConfiguration;
import com.netflix.conductor.config.TestConfiguration;
import com.netflix.conductor.core.execution.ApplicationException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AzureBlobPayloadStorageTest {
    private final TestConfiguration testConfiguration = new TestConfiguration();
    private final String path = "somewhere";

    /**
     * Dummy credentials
     * Azure SDK doesn't work with Azurite since it cleans parameters
     */
    private final String azuriteConnectionString = "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;BlobEndpoint=http://127.0.0.1:10000/devstoreaccount1;EndpointSuffix=localhost";
    private final String azuriteEndpoint = "http://127.0.0.1:10000/";

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void testNoStorageAccount() {
        expectedException.expect(ApplicationException.class);
        new AzureBlobPayloadStorage(new InvalidTestConfiguration());
    }

    @Test
    public void testUseConnectionString() {
        testConfiguration.setConnectionString(azuriteConnectionString);
        new AzureBlobPayloadStorage(testConfiguration);
    }

    @Test
    public void testUseEndpoint() {
        testConfiguration.setConnectionString(null);
        testConfiguration.setEndpoint(azuriteEndpoint);
        new AzureBlobPayloadStorage(testConfiguration);
    }

    @Test
    public void testGetLocationFixedPath() {
        testConfiguration.setConnectionString(azuriteConnectionString);
        AzureBlobPayloadStorage azureBlobPayloadStorage = new AzureBlobPayloadStorage(testConfiguration);
        ExternalStorageLocation externalStorageLocation = azureBlobPayloadStorage.getLocation(ExternalPayloadStorage.Operation.READ, ExternalPayloadStorage.PayloadType.WORKFLOW_INPUT, path);
        assertNotNull(externalStorageLocation);
        assertEquals(path, externalStorageLocation.getPath());
        assertNotNull(externalStorageLocation.getUri());
    }


    private void testGetLocation(AzureBlobPayloadStorage azureBlobPayloadStorage, ExternalPayloadStorage.Operation operation, ExternalPayloadStorage.PayloadType payloadType, String expectedPath) {
        ExternalStorageLocation externalStorageLocation = azureBlobPayloadStorage.getLocation(operation, payloadType, null);
        assertNotNull(externalStorageLocation);
        assertNotNull(externalStorageLocation.getPath());
        assertTrue(externalStorageLocation.getPath().startsWith(expectedPath));
        assertNotNull(externalStorageLocation.getUri());
        assertTrue(externalStorageLocation.getUri().contains(expectedPath));
    }

    @Test
    public void testGetAllLocations() {
        testConfiguration.setConnectionString(azuriteConnectionString);
        AzureBlobPayloadStorage azureBlobPayloadStorage = new AzureBlobPayloadStorage(testConfiguration);

        testGetLocation(azureBlobPayloadStorage, ExternalPayloadStorage.Operation.READ, ExternalPayloadStorage.PayloadType.WORKFLOW_INPUT, testConfiguration.getWorkflowInputPath());
        testGetLocation(azureBlobPayloadStorage, ExternalPayloadStorage.Operation.READ, ExternalPayloadStorage.PayloadType.WORKFLOW_OUTPUT, testConfiguration.getWorkflowOutputPath());
        testGetLocation(azureBlobPayloadStorage, ExternalPayloadStorage.Operation.READ, ExternalPayloadStorage.PayloadType.TASK_INPUT, testConfiguration.getTaskInputPath());
        testGetLocation(azureBlobPayloadStorage, ExternalPayloadStorage.Operation.READ, ExternalPayloadStorage.PayloadType.TASK_OUTPUT, testConfiguration.getTaskOutputPath());

        testGetLocation(azureBlobPayloadStorage, ExternalPayloadStorage.Operation.WRITE, ExternalPayloadStorage.PayloadType.WORKFLOW_INPUT, testConfiguration.getWorkflowInputPath());
        testGetLocation(azureBlobPayloadStorage, ExternalPayloadStorage.Operation.WRITE, ExternalPayloadStorage.PayloadType.WORKFLOW_OUTPUT, testConfiguration.getWorkflowOutputPath());
        testGetLocation(azureBlobPayloadStorage, ExternalPayloadStorage.Operation.WRITE, ExternalPayloadStorage.PayloadType.TASK_INPUT, testConfiguration.getTaskInputPath());
        testGetLocation(azureBlobPayloadStorage, ExternalPayloadStorage.Operation.WRITE, ExternalPayloadStorage.PayloadType.TASK_OUTPUT, testConfiguration.getTaskOutputPath());
    }
}
