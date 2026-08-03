# File Storage

## Context

File storage lets workflows exchange opaque file handles without exposing a storage provider's
bucket, object path, or credentials. The workflow-visible value is always:

```text
conductor://file/<id>
```

The file API stores metadata and enforces workflow ownership. The configured backend stores the
bytes.

## What a working deployment needs

1. A file-metadata DAO supported by the configured persistence implementation.
2. File storage enabled with a configured backend.
3. Workers that use the matching Java SDK `FileClient` configuration.
4. For the `conductor` backend, a directory available to every Conductor server node that can
   receive a content request.

## Feature flag

File storage is disabled by default. Enable it together with a backend:

```properties
conductor.file-storage.enabled=true
conductor.file-storage.type=conductor
```

## Choosing a backend

| Backend | `conductor.file-storage.type` | Transfer path | Multipart |
|---|---|---|---|
| Conductor-managed filesystem | `conductor` | HTTP `PUT`/`GET` through Conductor | Not supported |
| Amazon S3 | `s3` | Direct, provider-signed URLs | Supported |
| Azure Blob Storage | `azure-blob` | Direct, provider-signed URLs | Supported |
| Google Cloud Storage | `gcs` | Direct, provider-signed URLs | Not supported |

Use `conductor` for Conductor-managed filesystem storage.

## Common properties

| Property | Description |
|---|---|
| `conductor.file-storage.enabled` | Enables the file API and storage configuration. |
| `conductor.file-storage.type` | Selects `conductor`, `s3`, `azure-blob`, or `gcs`. |
| `conductor.file-storage.signed-url-expiration` | Lifetime used when the API issues an upload or download URL. |
| `conductor.file-storage.multipart-threshold` | Size at which clients choose multipart for a backend that supports it. |

## Conductor-managed storage (`type=conductor`)

The Conductor-managed backend stores objects in a server-side filesystem and returns HTTP URLs
served by Conductor. A client does **not** receive a `file:` URL and does not need direct access to
the storage directory.

| Property | Description | Default |
|---|---|---|
| `conductor.file-storage.conductor.directory` | Directory containing uploaded file objects. | `${java.io.tmpdir}/conductor/files-uploaded` |
| `conductor.file-storage.conductor.base-url` | Optional public Conductor origin used to issue content URLs when the inbound request origin is not suitable. | Derived from the inbound request |
| `conductor.file-storage.conductor.max-size` | Maximum accepted upload size, in bytes. Oversized uploads are rejected and the partial object is removed. | `104857600` (100 MiB) |
| `conductor.file-storage.conductor.signing.enabled` | Requires signed content URLs. | `false` |
| `conductor.file-storage.conductor.signing.keys[n].id` | Identifier included in signed URLs. | — |
| `conductor.file-storage.conductor.signing.keys[n].secret` | HMAC secret for the key. | — |

The content URL origin comes from the forwarded or inbound request origin unless `base-url` is
configured. Set `base-url` to the externally reachable origin when Conductor is behind a proxy or
load balancer that does not preserve the public scheme, host, and port. It must be an HTTP(S)
origin; Conductor appends the raw content path.

!!! warning "Multi-node deployments require shared storage"
    Every Conductor server node that can serve file content must mount the same
    `conductor.file-storage.conductor.directory`. Use shared storage such as NFS, EFS, or a
    `ReadWriteMany` volume. A node-local directory can accept an upload on one node and then fail
    a later download routed to another node.

### HTTP transfer through Conductor

For this backend, upload and download URLs point back to Conductor:

```text
PUT /api/files/content/{workflowId}/{fileId}
GET /api/files/content/{workflowId}/{fileId}
```

The client sends and receives the raw file body over HTTP. Conductor streams the content to or from
the configured filesystem, so clients require access to the Conductor HTTP endpoint rather than to
the underlying filesystem. See [File API](../api/files.md#conductor-managed-raw-content) for the
exact request and response contract.

### Optional URL signing and key rotation

Signing is disabled by default. When enabled, content URLs include `op`, `exp`, `kid`, and `sig`
query parameters. They are bearer credentials: do not log them or include them in exception
messages.

```properties
conductor.file-storage.conductor.signing.enabled=true
conductor.file-storage.conductor.signing.keys[0].id=2026-07
conductor.file-storage.conductor.signing.keys[0].secret=${CONDUCTOR_FILE_SIGNING_KEY_CURRENT}
conductor.file-storage.conductor.signing.keys[1].id=2026-04
conductor.file-storage.conductor.signing.keys[1].secret=${CONDUCTOR_FILE_SIGNING_KEY_PREVIOUS}
```

The first key signs newly issued URLs. All configured keys verify existing URLs, which supports
rotation: add the new key first, keep the previous key until URLs signed with it have expired, then
remove the previous key. Each key ID must be unique and signing requires at least one key.

## Object-store backends

S3, Azure Blob Storage, and GCS retain their provider-specific configuration and direct transfer
behavior. Conductor creates and authorizes the file record, then the client transfers bytes to the
provider URL. Only S3 and Azure Blob Storage support multipart uploads through the file API.

## Configuration examples

### Docker Compose

```properties
# config-postgres.properties
conductor.file-storage.enabled=true
conductor.file-storage.type=conductor
conductor.file-storage.conductor.directory=/data/conductor-files
conductor.file-storage.conductor.base-url=http://conductor:8080
conductor.file-storage.conductor.max-size=104857600
```

Mount `/data/conductor-files` into the server container. For more than one server container, mount
the same shared filesystem at that path in every container.

### Kubernetes with a shared volume

```yaml
env:
  - name: conductor.file-storage.enabled
    value: "true"
  - name: conductor.file-storage.type
    value: conductor
  - name: conductor.file-storage.conductor.directory
    value: /var/lib/conductor/files
  - name: conductor.file-storage.conductor.base-url
    value: https://conductor.example.com
volumeMounts:
  - name: conductor-files
    mountPath: /var/lib/conductor/files
volumes:
  - name: conductor-files
    persistentVolumeClaim:
      claimName: conductor-files-rwx
```

The persistent volume must support simultaneous access by every Conductor server replica.

## Verifying the configuration

Create a file record, upload bytes to the returned URL, and then confirm the upload:

```shell
curl -sS -X POST http://localhost:8080/api/files \
  -H 'Content-Type: application/json' \
  -d '{"workflowId":"wf-docs-demo","fileName":"report.pdf","contentType":"application/pdf"}'

curl -X PUT --data-binary @report.pdf \
  'http://localhost:8080/api/files/content/wf-docs-demo/<file-id>'

curl -sS -X POST \
  'http://localhost:8080/api/files/wf-docs-demo/<file-id>/upload-complete'
```

For the `conductor` backend, a successful raw upload returns `204 No Content`. The completion
request records the actual size and content hash, then moves the file to `UPLOADED`.

## Authorization model

Each file has one owning workflow. Authorization is intentionally asymmetric:

| Operation | Access rule |
|---|---|
| Create | The supplied workflow becomes the owner. |
| Upload content, refresh upload URL, complete upload, and multipart mutations | Exact owner only. |
| Metadata and download content | Owner's workflow family: self, ancestors, and descendants. |

This permits a parent and sub-workflow to exchange a handle while preventing either from mutating
another execution's in-progress upload.

## Java worker usage

Workers inject `FileClient`, accept and return handle strings, and call upload or download
explicitly. The task runner does not scan worker inputs or outputs for file objects and does not
upload automatically.

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

See [Java SDK file handling](../clientsdks/java-sdk.md#file-handling) for public upload and
download forms, and the [File API](../api/files.md) for direct REST access.

## Transfer behavior

`FileClient` owns orchestration: request validation, file-record creation, retry policy, URL
refresh, completion reconciliation, and cleanup. Internal transfer adapters perform one transfer
attempt each.

- Stream uploads are buffered to a repeatable temporary file before a server record is created.
- The `conductor` backend always uses one proxied HTTP request and is limited by `max-size`.
- S3 and Azure Blob Storage can use multipart uploads; GCS and `conductor` use a single request.
- Downloads write to a unique sibling temporary file and atomically replace the destination only
  after a complete response.
- Content URLs are redacted from errors. When signing is enabled, URLs are bearer credentials.

The detailed component and lifecycle rationale is in [File Storage Design](../../design/file-storage.md).

## Migration from smart file objects

The current contract replaces `FileHandler`, `ManagedFileHandler`, `FileUploader`, and
`WorkflowFileClient` with explicit `FileClient` calls and raw handle strings.

Old output:

```json
{"fileHandleId":"conductor://file/abc","fileName":"report.pdf","contentType":"application/pdf"}
```

Current output:

```json
"conductor://file/abc"
```

Mixed worker versions therefore produce incompatible workflow data shapes. Drain running workflows
or coordinate the server and worker rollout before switching formats.
