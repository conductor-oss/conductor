---
description: "File Storage — workflow-scoped binary payloads backed by local storage, S3, Azure Blob, or GCS."
---

# File Storage

## Context

File storage lets workflows carry images, video, archives, model artifacts, and other binary payloads without embedding bytes in JSON. Workflow inputs and outputs carry only an opaque string such as `conductor://file/<id>`.

Conductor owns the metadata and authorization decision. File bytes move directly between the SDK and a configured storage backend through short-lived signed URLs. This is distinct from [External Payload Storage](externalpayloadstorage.md), which transparently offloads oversized workflow and task JSON.

## Feature flag

```properties
conductor.file-storage.enabled=true
```

When `false` (the default), file-storage services and REST endpoints are not registered.

## Common properties

| Property | Description | Default |
|---|---|---|
| `conductor.file-storage.enabled` | Enables file storage. | `false` |
| `conductor.file-storage.type` | `local`, `s3`, `azure-blob`, or `gcs`. | `local` |
| `conductor.file-storage.signed-url-expiration` | Signed upload/download URL TTL as a Spring `Duration`. Bare numbers are seconds. | `60s` |

## Backends

### Local (`type=local`)

Server-local filesystem for development and single-node deployments. It supports only `file:` URLs and single-request transfers.

| Property | Description | Default |
|---|---|---|
| `conductor.file-storage.local.directory` | Directory containing file objects. | `${java.io.tmpdir}/conductor/files-uploaded` |

### Amazon S3 (`type=s3`)

Uses the default AWS credential provider chain. The Java SDK supports single-request and multipart transfers. Multipart completion requires every part's S3 `ETag`; failed sessions are aborted on a best-effort basis.

| Property | Description | Default |
|---|---|---|
| `conductor.file-storage.s3.bucket-name` | Destination bucket. | — |
| `conductor.file-storage.s3.region` | Bucket region. | `us-east-1` |

### Azure Blob (`type=azure-blob`)

The Java SDK sends `x-ms-blob-type: BlockBlob` for whole-file uploads. Multipart uses deterministic Base64 block IDs and commits the ordered block list. Uncommitted blocks expire, so abort is a server-side no-op.

| Property | Description | Default |
|---|---|---|
| `conductor.file-storage.azure-blob.container-name` | Destination container. | — |
| `conductor.file-storage.azure-blob.connection-string` | Account connection string used to create SAS URLs. | — |

### Google Cloud Storage (`type=gcs`)

The Java SDK intentionally uses single-request signed PUT uploads. GCS resumable upload is not exposed as multipart until the resumable protocol is implemented end to end.

| Property | Description | Default |
|---|---|---|
| `conductor.file-storage.gcs.bucket-name` | Destination bucket. | — |
| `conductor.file-storage.gcs.project-id` | Project that owns the bucket. | — |
| `conductor.file-storage.gcs.credentials-file` | Service-account JSON path; application default credentials are used when unset. | — |

### Bring Your Own Storage

Implement `org.conductoross.conductor.core.storage.FileStorage` and register a Spring bean for the configured storage type. This is a server extension point. Java SDK signed-transfer adapters are internal and are not a public application extension point.

## Persistence and layout

File metadata stores the ID, original filename, content type, storage path, upload status, workflow/task ownership, timestamps, and the storage-reported hash and size after completion.

| Database | Migration |
|---|---|
| PostgreSQL | `V15__file_metadata.sql` |
| MySQL | `V9__file_metadata.sql` |
| SQLite | `V3__file_metadata.sql` |

Redis and Cassandra metadata DAOs are also available. Objects use the same logical layout across providers:

```text
conductor/<workflowId>/<fileId>
```

## Authorization model

Each file has one owning workflow. Authorization is intentionally asymmetric:

| Operation | Access rule |
|---|---|
| Create | The supplied workflow becomes the owner. |
| Refresh upload URL, complete upload, initiate/upload/complete/abort multipart | Exact owner only. |
| Metadata and download | Owner's workflow family: self, ancestors, and descendants. |

This permits a parent and sub-workflow to exchange a handle while preventing either from mutating another execution's in-progress upload.

## Java worker usage

Workers inject `FileClient`, accept and return handle strings, and call upload or download explicitly. The task runner does not scan worker inputs/outputs for file objects and does not upload automatically.

```java
public final class ResizeWorker {
    private final FileClient files;

    public ResizeWorker(FileClient files) {
        this.files = files;
    }

    @WorkerTask("resize_image")
    public @OutputParam("image") String resize(
            @InputParam("image") String inputHandle,
            @WorkflowInstanceIdInputParam String workflowId) throws IOException {
        Path input = Files.createTempFile("image-", ".bin");
        Path output = Files.createTempFile("resized-", ".png");
        try {
            files.download(workflowId, inputHandle, input);
            resize(input, output);
            return files.upload(
                    workflowId,
                    output,
                    new FileUploadOptions().setContentType("image/png"));
        } finally {
            Files.deleteIfExists(input);
            Files.deleteIfExists(output);
        }
    }
}
```

See [Java SDK file handling](../clientsdks/java-sdk.md#file-handling) for every public upload/download form and the [File API](../api/files.md) for direct REST access.

## Transfer behavior

`FileClient` owns orchestration: request validation, file-record creation, retry policy, signed-URL refresh, automatic multipart selection, completion reconciliation, and cleanup. Internal provider adapters perform one signed transfer attempt.

- Streams are buffered to a repeatable temporary file before the server record is created. A stream upload requires a filename, never closes the caller's stream, and removes the temporary file.
- Files larger than the configured threshold use multipart only for S3 and Azure. GCS, local, and unknown HTTP(S) storage types use one request.
- Downloads write to a unique sibling temporary file and atomically replace the destination only after a complete response.
- Signed URL requests use a separate raw HTTP client with redirects disabled. Conductor authentication, cookies, and API interceptors are not forwarded.
- Retries refresh signed URLs and stop when the thread is interrupted. Signed URLs are redacted from errors.

The detailed component and lifecycle rationale is in [File Storage Design](../../design/file-storage.md).

## Migration from smart file objects

The current contract replaces `FileHandler`, `ManagedFileHandler`, `LocalFileHandler`, `FileUploader`, and `WorkflowFileClient` with explicit `FileClient` calls and raw handle strings.

Old output:

```json
{"fileHandleId":"conductor://file/abc","fileName":"report.pdf","contentType":"application/pdf"}
```

Current output:

```json
"conductor://file/abc"
```

Mixed worker versions therefore produce incompatible workflow data shapes. Drain running workflows or coordinate the server and worker rollout before switching formats.
