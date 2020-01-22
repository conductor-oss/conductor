package com.netflix.conductor.storage;

import com.azure.core.exception.UnexpectedLengthException;
import com.azure.core.util.Context;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import com.azure.storage.blob.specialized.BlockBlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.common.Utility;
import com.azure.storage.common.implementation.credentials.SasTokenCredential;
import com.netflix.conductor.azureblob.AzureBlobConfiguration;
import com.netflix.conductor.common.run.ExternalStorageLocation;
import com.netflix.conductor.common.utils.ExternalPayloadStorage;
import com.netflix.conductor.core.execution.ApplicationException;
import com.netflix.conductor.core.utils.IDGenerator;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * An implementation of {@link ExternalPayloadStorage} using Azure Blob for storing large JSON payload data.
 *
 * @see <a href="https://github.com/Azure/azure-sdk-for-java">Azure Java SDK</a>
 */
@Singleton
public class AzureBlobPayloadStorage implements ExternalPayloadStorage {
    private static final Logger logger = LoggerFactory.getLogger(AzureBlobPayloadStorage.class);
    private static final String CONTENT_TYPE = "application/json";

    private final String workflowInputPath;
    private final String workflowOutputPath;
    private final String taskInputPath;
    private final String taskOutputPath;

    private final BlobContainerClient blobContainerClient;
    private final int expirationSec;
    private final SasTokenCredential sasTokenCredential;

    @Inject
    public AzureBlobPayloadStorage(AzureBlobConfiguration config) {
        workflowInputPath = config.getWorkflowInputPath();
        workflowOutputPath = config.getWorkflowOutputPath();
        taskInputPath = config.getTaskInputPath();
        taskOutputPath = config.getTaskOutputPath();
        expirationSec = config.getSignedUrlExpirationSeconds();
        String connectionString = config.getConnectionString();
        String containerName = config.getContainerName();
        String endpoint = config.getEndpoint();
        String sasToken = config.getSasToken();

        BlobContainerClientBuilder blobContainerClientBuilder = new BlobContainerClientBuilder();
        if (connectionString != null) {
            blobContainerClientBuilder.connectionString(connectionString);
            sasTokenCredential = null;
        } else if (endpoint != null) {
            blobContainerClientBuilder.endpoint(endpoint);
            if (sasToken != null) {
                sasTokenCredential = SasTokenCredential.fromSasTokenString(sasToken);
                blobContainerClientBuilder.sasToken(sasTokenCredential.getSasToken());
            } else {
                sasTokenCredential = null;
            }
        } else {
            String msg = "Missing property "
                    + config.CONNECTION_STRING_PROPERTY_NAME
                    + " OR "
                    + config.ENDPOINT_PROPERTY_NAME;
            logger.error(msg);
            throw new ApplicationException(ApplicationException.Code.BACKEND_ERROR, msg);
        }
        blobContainerClient = blobContainerClientBuilder
                .containerName(containerName)
                .buildClient();
    }

    /**
     * @param operation   the type of {@link Operation} to be performed
     * @param payloadType the {@link PayloadType} that is being accessed
     * @return a {@link ExternalStorageLocation} object which contains the pre-signed URL and the azure blob name
     *  for the json payload
     */
    @Override
    public ExternalStorageLocation getLocation(Operation operation, PayloadType payloadType, String path) {
        try {
            ExternalStorageLocation externalStorageLocation = new ExternalStorageLocation();

            String objectKey;
            if (StringUtils.isNotBlank(path)) {
                objectKey = path;
            } else {
                objectKey = getObjectKey(payloadType);
            }
            externalStorageLocation.setPath(objectKey);

            BlockBlobClient blockBlobClient = blobContainerClient.getBlobClient(objectKey).getBlockBlobClient();
            String blobUrl = Utility.urlDecode(blockBlobClient.getBlobUrl());

            if (sasTokenCredential != null) {
                blobUrl = blobUrl + "?" + sasTokenCredential.getSasToken();
            } else {
                BlobSasPermission blobSASPermission = new BlobSasPermission();
                if (operation.equals(Operation.READ)) {
                    blobSASPermission.setReadPermission(true);
                } else if (operation.equals(Operation.WRITE)) {
                    blobSASPermission.setWritePermission(true);
                    blobSASPermission.setCreatePermission(true);
                }
                BlobServiceSasSignatureValues blobServiceSasSignatureValues = new BlobServiceSasSignatureValues(
                        OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(expirationSec),
                        blobSASPermission
                );
                blobUrl = blobUrl + "?" + blockBlobClient.generateSas(blobServiceSasSignatureValues);
            }

            externalStorageLocation.setUri(blobUrl);
            return externalStorageLocation;
        } catch (BlobStorageException e) {
            String msg = "Error communicating with Azure";
            logger.error(msg, e);
            throw new ApplicationException(ApplicationException.Code.BACKEND_ERROR, msg, e);
        }
    }

    /**
     * Uploads the payload to the given azure blob name.
     * It is expected that the caller retrieves the blob name
     * using {@link #getLocation(Operation, PayloadType, String)} before making this call.
     *
     * @param path        the name of the blob to be uploaded
     * @param payload     an {@link InputStream} containing the json payload which is to be uploaded
     * @param payloadSize the size of the json payload in bytes
     */
    @Override
    public void upload(String path, InputStream payload, long payloadSize) {
        try {
            BlockBlobClient blockBlobClient = blobContainerClient.getBlobClient(path).getBlockBlobClient();
            BlobHttpHeaders blobHttpHeaders = new BlobHttpHeaders()
                    .setContentType(CONTENT_TYPE);
            blockBlobClient.uploadWithResponse(payload, payloadSize, blobHttpHeaders,
                    null, null, null, null, null, Context.NONE);
        } catch (BlobStorageException | UncheckedIOException | UnexpectedLengthException e) {
            String msg = "Error communicating with Azure";
            logger.error(msg, e);
            throw new ApplicationException(ApplicationException.Code.BACKEND_ERROR, msg, e);
        }
    }

    /**
     * Downloads the payload stored in an azure blob.
     *
     * @param path the path of the blob
     * @return an input stream containing the contents of the object
     * Caller is expected to close the input stream.
     */
    @Override
    public InputStream download(String path) {
        try {
            BlockBlobClient blockBlobClient = blobContainerClient.getBlobClient(path).getBlockBlobClient();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            // Avoid another call to the api to get the blob size
            // ByteArrayOutputStream outputStream = new ByteArrayOutputStream(blockBlobClient.getProperties().value().blobSize());
            blockBlobClient.download(outputStream);
            return new ByteArrayInputStream(outputStream.toByteArray());
        } catch (BlobStorageException | UncheckedIOException | NullPointerException e) {
            String msg = "Error communicating with Azure";
            logger.error(msg, e);
            throw new ApplicationException(ApplicationException.Code.BACKEND_ERROR, msg, e);
        }
    }

    /**
     * Build path on external storage. Copied from S3PayloadStorage.
     *
     * @param payloadType the {@link PayloadType} which will determine the base path of the object
     * @return External Storage path
     */
    private String getObjectKey(PayloadType payloadType) {
        StringBuilder stringBuilder = new StringBuilder();
        switch (payloadType) {
            case WORKFLOW_INPUT:
                stringBuilder.append(workflowInputPath);
                break;
            case WORKFLOW_OUTPUT:
                stringBuilder.append(workflowOutputPath);
                break;
            case TASK_INPUT:
                stringBuilder.append(taskInputPath);
                break;
            case TASK_OUTPUT:
                stringBuilder.append(taskOutputPath);
                break;
        }
        stringBuilder.append(IDGenerator.generate()).append(".json");
        return stringBuilder.toString();
    }
}