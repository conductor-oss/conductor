# External Payload Storage

!!!warning
    The external payload storage is currently only implemented to be used to by the Java client. Client libraries in other languages need to be modified to enable this.  
    Contributions are welcomed.

## Context
Conductor can be configured to enforce barriers on the size of workflow and task payloads for both input and output.  
These barriers can be used as safeguards to prevent the usage of conductor as a data persistence system and to reduce the pressure on its datastore.

## Barriers
Conductor typically applies two kinds of barriers:

* Soft Barrier
* Hard Barrier


#### Soft Barrier

The soft barrier is used to alleviate pressure on the conductor datastore. In some special workflow use-cases, the size of the payload is warranted enough to be stored as part of the workflow execution.  
In such cases, conductor externalizes the storage of such payloads to S3 and uploads/downloads to/from S3 as needed during the execution. This process is completely transparent to the user/worker process.  


#### Hard Barrier
The hard barriers are enforced to safeguard the conductor backend from the pressure of having to persist and deal with voluminous data which is not essential for workflow execution.
In such cases, conductor will reject such payloads and will terminate/fail the workflow execution with the reasonForIncompletion set to an appropriate error message detailing the payload size.

## Usage

### Barriers setup

Set the following properties to the desired values in the JVM system properties:

| Property | Description | default value |
| -- | -- | -- |
| conductor.app.workflowInputPayloadSizeThreshold | Soft barrier for workflow input payload in KB | 5120 |
| conductor.app.maxWorkflowInputPayloadSizeThreshold | Hard barrier for workflow input payload in KB | 10240 |
| conductor.app.workflowOutputPayloadSizeThreshold | Soft barrier for workflow output payload in KB | 5120 |
| conductor.app.maxWorkflowOutputPayloadSizeThreshold | Hard barrier for workflow output payload in KB | 10240 |
| conductor.app.taskInputPayloadSizeThreshold | Soft barrier for task input payload in KB | 3072 |
| conductor.app.maxTaskInputPayloadSizeThreshold | Hard barrier for task input payload in KB | 10240 |
| conductor.app.taskOutputPayloadSizeThreshold | Soft barrier for task output payload in KB | 3072 |
| conductor.app.maxTaskOutputPayloadSizeThreshold | Hard barrier for task output payload in KB | 10240 |

### Amazon S3

Conductor provides an implementation of [Amazon S3](https://aws.amazon.com/s3/) used to externalize large payload storage.  
Set the following property in the JVM system properties:
```
conductor.external-payload-storage.type=S3
```

!!!note
    This [implementation](https://github.com/Netflix/conductor/blob/master/core/src/main/java/com/netflix/conductor/core/utils/S3PayloadStorage.java#L44-L45) assumes that S3 access is configured on the instance.

Set the following properties to the desired values in the JVM system properties:

| Property | Description | default value |
| --- | --- | --- |
| conductor.external-payload-storage.s3.bucketName | S3 bucket where the payloads will be stored | |
| conductor.external-payload-storage.s3.signedUrlExpirationDuration | The expiration time in seconds of the signed url for the payload | 5 |

The payloads will be stored in the bucket configured above in a `UUID.json` file at locations determined by the type of the payload. See [here](https://github.com/Netflix/conductor/blob/master/core/src/main/java/com/netflix/conductor/core/utils/S3PayloadStorage.java#L149-L167) for information about how the object key is determined.

### Azure Blob Storage

ProductLive provides an implementation of [Azure Blob Storage](https://azure.microsoft.com/services/storage/blobs/) used to externalize large payload storage.  

To build conductor with azure blob feature read the [README.md](https://github.com/Netflix/conductor/blob/master/azureblob-storage/README.md) in `azureblob-storage` module 

!!!note
    This implementation assumes that you have an [Azure Blob Storage account's connection string or SAS Token](https://github.com/Azure/azure-sdk-for-java/blob/master/sdk/storage/azure-storage-blob/README.md).
    If you want signed url to expired you must specify a Connection String. 

Set the following properties to the desired values in the JVM system properties:

| Property | Description | default value |
| --- | --- | --- |
| workflow.external.payload.storage.azure_blob.connection_string | Azure Blob Storage connection string. Required to sign Url. | |
| workflow.external.payload.storage.azure_blob.endpoint | Azure Blob Storage endpoint. Optional if connection_string is set. | |
| workflow.external.payload.storage.azure_blob.sas_token | Azure Blob Storage SAS Token. Must have permissions `Read` and `Write` on Resource `Object` on Service `Blob`. Optional if connection_string is set. | |
| workflow.external.payload.storage.azure_blob.container_name | Azure Blob Storage container where the payloads will be stored | `conductor-payloads` |
| workflow.external.payload.storage.azure_blob.signedurlexpirationseconds | The expiration time in seconds of the signed url for the payload | 5 |
| workflow.external.payload.storage.azure_blob.workflow_input_path | Path prefix where workflows input will be stored with an random UUID filename | workflow/input/ |
| workflow.external.payload.storage.azure_blob.workflow_output_path | Path prefix where workflows output will be stored with an random UUID filename | workflow/output/ |
| workflow.external.payload.storage.azure_blob.task_input_path | Path prefix where tasks input will be stored with an random UUID filename | task/input/ |
| workflow.external.payload.storage.azure_blob.task_output_path | Path prefix where tasks output will be stored with an random UUID filename | task/output/ |

The payloads will be stored as done in [Amazon S3](https://github.com/Netflix/conductor/blob/master/core/src/main/java/com/netflix/conductor/core/utils/S3PayloadStorage.java#L149-L167).

### PostgreSQL Storage

Frinx provides an implementation of [PostgreSQL Storage](https://www.postgresql.org/) used to externalize large payload storage.

!!!note
This implementation assumes that you have an [PostgreSQL database server with all required credentials](https://jdbc.postgresql.org/documentation/94/connect.html).

Set the following properties to your application.properties:

| Property                                                    | Description                                                                                                                                                                              | default value                         |
|-------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------|
| conductor.external-payload-storage.postgres.conductor-url   | URL, that can be used to pull the json configurations, that will be downloaded from PostgreSQL to the conductor server. For example: for local development it is `http://localhost:8080` | `""`                                  |
| conductor.external-payload-storage.postgres.url             | PostgreSQL database connection URL. Required to connect to database.                                                                                                                     |                                       |
| conductor.external-payload-storage.postgres.username        | Username for connecting to PostgreSQL database. Required to connect to database.                                                                                                         |                                       |
| conductor.external-payload-storage.postgres.password        | Password for connecting to PostgreSQL database. Required to connect to database.                                                                                                         |                                       |
| conductor.external-payload-storage.postgres.table-name      | The PostgreSQL schema and table name where the payloads will be stored                                                                                                                   | `external.external_payload`           |
| conductor.external-payload-storage.postgres.max-data-rows   | Maximum count of data rows in PostgreSQL database. After overcoming this limit, the oldest data will be deleted.                                                                         | Long.MAX_VALUE (9223372036854775807L) |
| conductor.external-payload-storage.postgres.max-data-days   | Maximum count of days of data age in PostgreSQL database. After overcoming limit, the oldest data will be deleted.                                                                       | 0                                     |
| conductor.external-payload-storage.postgres.max-data-months | Maximum count of months of data age in PostgreSQL database. After overcoming limit, the oldest data will be deleted.                                                                     | 0                                     |
| conductor.external-payload-storage.postgres.max-data-years  | Maximum count of years of data age in PostgreSQL database. After overcoming limit, the oldest data will be deleted.                                                                      | 1                                     |

The maximum date age for fields in the database will be: `years + months + days`  
The payloads will be stored in PostgreSQL database with key (externalPayloadPath) `UUID.json` and you can generate
URI for this data using `external-postgres-payload-resource` rest controller.   
To make this URI work correctly, you must correctly set the conductor-url property.
