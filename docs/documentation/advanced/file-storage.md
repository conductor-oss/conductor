---
description: "File Storage — first-class support for binary file payloads in Conductor workflows, with pluggable backends (local, S3, Azure Blob, GCS)."
---
# File Storage

## Context

The file-storage feature lets workflows carry binary file payloads (images, video, archives, model artefacts) without stuffing them into JSON inputs and outputs. Files are uploaded directly to a configured backend via short-lived presigned URLs and tracked in Conductor by a metadata record. Workflow tasks pass a small `FileHandle`/`FileHandler` reference instead of the bytes themselves.

This is distinct from [External Payload Storage](externalpayloadstorage.md), which transparently offloads oversized JSON workflow/task payloads. File storage is for *user file content* that the workflow knowingly produces or consumes.

## Feature flag

The entire feature — REST endpoints, service beans, DAOs — is gated by a single property:

```properties
conductor.file-storage.enabled=true
```

When `false` (the default) nothing registers and `/api/files/*` returns `404`.

## Common properties

`conductor.file-storage.*`:

| Property | Description | default value |
| --- | --- | --- |
| conductor.file-storage.enabled | Master flag for the file-storage feature. | `false` |
| conductor.file-storage.type | Backend selector: `local`, `s3`, `azure-blob`, `gcs`. | `local` |
| conductor.file-storage.signed-url-expiration | TTL for presigned upload/download URLs. Spring `Duration`; bare numbers are seconds. | `60s` |

## Backends

### Local (`type=local`)

Server-local filesystem. Intended for development and single-node deployments only — does not scale horizontally and does not support multipart upload.

| Property | Description | default value |
| --- | --- | --- |
| conductor.file-storage.local.directory | Directory where files are written. | `${java.io.tmpdir}/conductor/files-uploaded` |

### Amazon S3 (`type=s3`)

Uses the default AWS credential provider chain (environment, instance profile, etc.).

| Property | Description | default value |
| --- | --- | --- |
| conductor.file-storage.s3.bucket-name | S3 bucket where files are stored. | |
| conductor.file-storage.s3.region | AWS region for the bucket. | `us-east-1` |

### Azure Blob (`type=azure-blob`)

| Property | Description | default value |
| --- | --- | --- |
| conductor.file-storage.azure-blob.container-name | Azure Blob container where files are stored. | |
| conductor.file-storage.azure-blob.connection-string | Account connection string. Required for SAS-signed URLs. | |

### Google Cloud Storage (`type=gcs`)

| Property | Description | default value |
| --- | --- | --- |
| conductor.file-storage.gcs.bucket-name | GCS bucket where files are stored. | |
| conductor.file-storage.gcs.project-id | GCP project that owns the bucket. | |
| conductor.file-storage.gcs.credentials-file | Path to a service-account JSON key file. Falls back to application default credentials if unset. | |

### Bring Your Own Storage (BYOS)

Implement `org.conductoross.conductor.core.storage.FileStorage` and register it as a Spring bean. The default service uses the bean that matches `conductor.file-storage.type`.

## Persistence

File metadata lives in the `file_metadata` table — `fileId`, `fileName`, `contentType`, `storagePath`, `uploadStatus`, `workflowId`, `taskId`, timestamps, plus storage-reported `contentHash` and `contentSize` after upload completes.

Schemas are created by Flyway:

| Backend | Migration |
| --- | --- |
| Postgres | `V15__file_metadata.sql` |
| MySQL | `V9__file_metadata.sql` |
| SQLite | `V3__file_metadata.sql` |

Redis and Cassandra metadata DAOs are also provided.

## Storage layout

Objects are written to `conductor/<workflowId>/<fileId>` inside the configured bucket / container / directory. Layout is the same across backends.

## Access scope

Download URLs are *workflow-family scoped*: the caller must supply a `workflowId` that resolves to the same workflow family (self, ancestors, or descendants in the sub-workflow tree) as the file's `workflowId`. Mismatches return `403 Forbidden`. There is no per-file size cap in this iteration.

## Worker usage

Workers consume and produce files via the Java SDK's `FileHandler` type. See the [File handling](../clientsdks/java-sdk.md#file-handling) section of the Java SDK page and the [Media Transcoder](https://github.com/conductor-oss/file-storage-java-sdk/tree/main/examples/file-storage/media-transcoder) example.

For direct REST access (non-Java callers, custom clients), see the [File API reference](../api/files.md).
