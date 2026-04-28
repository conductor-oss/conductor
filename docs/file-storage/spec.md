# Spec: File Storage for Conductor OSS

Add file storage support to Conductor OSS — server and SDKs — so developers can pass files between workflows and tasks
with zero knowledge of the underlying storage backend.

---

# Table of Contents

1. [Principles & Constraints](#principles--constraints)
    - [Developer Experience](#developer-experience)
    - [File Lifecycle](#file-lifecycle)
    - [Execution](#execution)
    - [Presigned URLs](#presigned-urls)
    - [RBAC](#rbac)
    - [Configuration](#configuration)
    - [Implementation](#implementation)
2. [File ID Convention](#file-id-convention)
3. [Server](#server)
    - [REST API](#rest-api)
        - [Create File](#create-file)
        - [Get Upload URL](#get-upload-url)
        - [Confirm Upload](#confirm-upload)
        - [Get Download URL](#get-download-url)
        - [Get File Metadata](#get-file-metadata)
        - [Initiate Multipart Upload](#initiate-multipart-upload)
        - [Get Part Upload URL (S3 only)](#get-part-upload-url-s3-only)
        - [Complete Multipart Upload](#complete-multipart-upload)
        - [Error Responses](#error-responses)
    - [DTOs](#dtos)
        - [StorageType](#storagetype)
        - [FileUploadStatus](#fileuploadstatus)
        - [FileHandle](#filehandle)
        - [FileUploadRequest](#fileuploadrequest)
        - [FileUploadResponse](#fileuploadresponse)
        - [FileUploadUrlResponse](#fileuploadurlresponse)
        - [FileDownloadUrlResponse](#filedownloadurlresponse)
        - [FileUploadCompleteResponse](#fileuploadcompleteresponse)
        - [MultipartInitResponse](#multipartinitresponse)
    - [Interfaces](#interfaces)
        - [FileStorage](#filestorage)
    - [Service](#service)
        - [FileStorageService](#filestorageservice)
    - [DAO](#dao)
        - [FileMetadataDAO](#filemetadatadao)
        - [Implementations](#implementations)
        - [Audit & Tracking](#audit--tracking)
        - [FileModel](#filemodel)
    - [Database Schema](#database-schema)
        - [Migration / Schema Setup](#migration--schema-setup)
    - [Storage Backends](#storage-backends)
        - [Local File Storage (default)](#local-file-storage-default)
        - [AWS S3](#aws-s3)
        - [Azure Blob Storage](#azure-blob-storage)
        - [Google Cloud Storage](#google-cloud-storage)
        - [Bring Your Own Storage (BYOS)](#bring-your-own-storage-byos)
    - [Configuration](#configuration-1)
    - [Module Placement (Server)](#module-placement-server)
4. [Java SDK](#java-sdk)
    - [Public Interfaces](#public-interfaces)
        - [FileHandler](#filehandler)
        - [FileUploader](#fileuploader)
    - [SDK-Internal Classes](#sdk-internal-classes)
        - [FileClient](#fileclient)
        - [LocalFileHandler](#localfilehandler)
        - [ManagedFileHandler](#managedfilehandler)
        - [FileStorageBackend](#filestoragebackend)
        - [FileDownloadStatus](#filedownloadstatus)
    - [Worker Integration](#worker-integration)
        - [Annotation-Based (zero config)](#annotation-based-zero-config)
        - [Imperative](#imperative)
    - [Upload Strategies](#upload-strategies)
        - [Explicit Upload](#explicit-upload)
        - [Automatic Upload](#automatic-upload)
    - [Configuration (SDK)](#configuration-sdk)
    - [Module Placement (SDK)](#module-placement-sdk)
5. [File Operations](#file-operations)
    - [Upload Flow](#upload-flow)
    - [Download Flow](#download-flow)
    - [Presigned URLs](#presigned-urls-1)
    - [Multipart Upload & Download](#multipart-upload--download)
    - [Failure & Retry](#failure--retry)
        - [Upload Failure](#upload-failure)
        - [Download Failure](#download-failure)
    - [Status Tracking](#status-tracking)
        - [Upload Status (server + worker)](#upload-status-server--worker)
        - [Download Status (worker only)](#download-status-worker-only)
6. [Use Cases](#use-cases)
    - [1. Order Invoice, Packing Slip & Shipping Label Generation](#1-order-invoice-packing-slip--shipping-label-generation)
    - [2. AI-Powered Knowledge Base Builder (RAG Pipeline)](#2-ai-powered-knowledge-base-builder-rag-pipeline)
    - [3. Multi-Format Media Transcoding & Publishing](#3-multi-format-media-transcoding--publishing)
7. [What-If Scenarios](#what-if-scenarios)
8. [Documentation Placement](#documentation-placement)
9. [Developer Experience Showcase](#developer-experience-showcase)
10. [Testing Strategy](#testing-strategy)
    - [Server](#server-1)
    - [Java SDK](#java-sdk-1)
    - [Stubs](#stubs)
    - [Verification](#verification)
11. [Other SDKs](#other-sdks)

---

# Principles & Constraints

From [idea.md](idea.md) — these are non-negotiable.

## Developer Experience

- Storage backend totally abstracted — developer never interacts with backend-specific APIs
- Temporary file cleanup and local file availability abstracted — not the developer's concern

## File Lifecycle

- We will **never** clean up temporary files on local disk — whether created or downloaded
- Existing files must **not** be overwritten
- Files must **not** be deleted after workflow execution ends
- No TTL or deletion policy for storage backend files right now
    - Retention policies (archival, TTL-based deletion) configurable per customer/cluster/tenant in the future

## Execution

- Other workflows must **not** wait for a file upload to complete
    - A workflow will wait **only** if it requires the uploaded file as input
- A `conductor://file/uuid` resolves to a **single `ManagedFileHandler`** per worker node — no cross-worker reuse
- Once downloaded, the file is cached locally and reusable via the same `FileHandler` within the same worker node

## Presigned URLs

- Created by Conductor server only — never by SDK
- Short-lived — generated fresh on each request, no caching

## RBAC

- RBAC is **enterprise edition only** — OSS does not implement RBAC
- File ownership model (used by enterprise RBAC):
    1. Each file belongs to a **workflow ID** (when part of workflow input) or a **task ID** (when part of task output)
    2. Workflow and task both have well-known RBAC models — once ownership is established, the standard enterprise RBAC
       works out of the box
- No new RBAC model needed for files

## Configuration

- Precedence: code defaults → config file → env var
- Env vars should be **limited** — only when necessary

## Feature Flag

- File storage is behind a server-side feature flag — **disabled by default**
- `conductor.file-storage.enabled=false` — entire feature gated on this property
- When disabled:
    - No `FileStorage`, `FileStorageService`, `FileMetadataDAO` beans created
    - `FileResource` controller not registered — all `/api/files` endpoints return 404
    - Zero overhead on existing functionality
- When enabled: requires `conductor.file-storage.type` to select backend (local, s3, azure-blob, gcs)
- Implementation: `@ConditionalOnProperty(name = "conductor.file-storage.enabled", havingValue = "true")`
  on `FileStorageServiceImpl` and `FileResource`

## Implementation

- Create **worktree** for codebases before implementation
    - Server: `file-storage-conductor` — branch `feature/file-storage-wip`
    - SDK: `file-storage-java-sdk` — branch `feature/file-storage-wip`
- Follow existing coding standards in the codebase
- No magic variables — all values must be named constants or configuration

---

# File ID Convention

- Format: `conductor://file/<uuid>` — e.g. `conductor://file/d6a4e5f7-8b9c-4a1d-b2e3-f4a5b6c7d8e9`
- Flows through existing `inputData`/`outputData` as plain strings
- No changes to `TaskDef`, `Task`, `WorkflowDef`, or any existing **serialized** model
    - `Task` gets a `@JsonIgnore private transient FileClient` field — not serialized, not a model change
- Server generates UUIDs — no collision risk
- SDK detects `conductor://file/` prefix, wraps as `FileHandler`
- Old SDKs see plain string — no breakage

---

# Server

## REST API

Base path: `/api/files`

### Create File

```
POST /api/files
Content-Type: application/json

Request:
{
    "fileName": "report.pdf",
    "contentType": "application/pdf",
    "fileSize": 1048576,
    "workflowId": "wf-uuid",        // optional — set when file is workflow input
    "taskId": "task-uuid"            // optional — set when file is task output
}

Response: 201 Created
{
    "fileId": "conductor://file/d6a4e5f7-...",
    "fileName": "report.pdf",
    "contentType": "application/pdf",
    "fileSize": 1048576,
    "storageType": "S3",
    "uploadStatus": "UPLOADING",
    "uploadUrl": "https://s3.../presigned-put-url",
    "createdAt": "2026-04-14T..."
}
```

### Get Upload URL

```
GET /api/files/{fileId}/upload-url

Response: 200 OK
{
    "fileId": "conductor://file/...",
    "uploadUrl": "https://s3.../presigned-put-url",
    "expiresAt": "2026-04-14T..."
}
```

Generates a fresh presigned URL. Used on retry when the original URL from `POST /api/files` has expired.

### Confirm Upload

```
POST /api/files/{fileId}/upload-complete

Response: 200 OK
{
    "fileId": "conductor://file/...",
    "uploadStatus": "UPLOADED",
    "contentHash": "d41d8cd98f00b204e9800998ecf8427e"
}
```

Server retrieves content hash from storage backend (S3 ETag, Azure Content-MD5, GCS md5Hash/crc32c) and
persists it. No hash computation by SDK or server — read from what the storage provider already has.
Local backend: hash is null.

### Get Download URL

```
GET /api/files/{fileId}/download-url

Response: 200 OK
{
    "fileId": "conductor://file/...",
    "downloadUrl": "https://s3.../presigned-get-url",
    "expiresAt": "2026-04-14T..."
}
```

### Get File Metadata

```
GET /api/files/{fileId}

Response: 200 OK
{
    "fileId": "conductor://file/...",
    "fileName": "report.pdf",
    "contentType": "application/pdf",
    "fileSize": 1048576,
    "contentHash": "d41d8cd98f00b204e9800998ecf8427e",
    "storageType": "S3",
    "uploadStatus": "UPLOADED",
    "workflowId": "wf-uuid",
    "taskId": "task-uuid",
    "createdAt": "2026-04-14T...",
    "updatedAt": "2026-04-14T..."
}
```

Note: `storagePath` is server-internal — not exposed in API responses.

### Initiate Multipart Upload

```
POST /api/files/{fileId}/multipart

Response: 200 OK
{
    "fileId": "conductor://file/...",
    "uploadId": "backend-specific-upload-id",
    "partSize": 5242880
}
```

Server determines part size based on file size and backend constraints.

### Get Part Upload URL (S3 only)

```
GET /api/files/{fileId}/multipart/{uploadId}/part/{partNumber}

Response: 200 OK
{
    "fileId": "conductor://file/...",
    "uploadUrl": "https://s3.../presigned-part-url",
    "expiresAt": "2026-04-14T..."
}
```

Response uses `FileUploadUrlResponse` DTO (same as Get Upload URL).

Only called when `MultipartInitResponse.uploadUrl` is null (S3). GCS and Azure use the resumable URL from
initiate for all parts — the SDK skips this endpoint entirely based on `getStorageType()`.

### Complete Multipart Upload

```
POST /api/files/{fileId}/multipart/{uploadId}/complete
Content-Type: application/json

Request:
{
    "partETags": ["etag1", "etag2", "etag3"]
}

Response: 200 OK
{
    "fileId": "conductor://file/...",
    "uploadStatus": "UPLOADED",
    "contentHash": "d41d8cd98f00b204e9800998ecf8427e"
}
```

Server finalizes multipart upload on storage, then verifies via `getStorageFileInfo()` — same as single-part
`upload-complete`. Reads content hash and actual size from storage provider, persists both.

### Error Responses

Reuses existing Conductor exception hierarchy (`com.netflix.conductor.core.exception`). Errors flow through
the existing `ApplicationExceptionMapper` (`@RestControllerAdvice`) → `ErrorResponse` DTO.

| Status | Exception | Condition |
|--------|-----------|-----------|
| 400 | `IllegalArgumentException` | Invalid request, bad file ID format, upload not complete |
| 404 | `NotFoundException` | File ID not found |
| 409 | `ConflictException` | File already uploaded (duplicate upload-complete) |
| 413 | `FileTooLargeException` (new, extends `NonTransientException`) | File size exceeds configured maximum |
| 500 | `NonTransientException` | Storage backend error |

## DTOs

All DTOs in `common` module: `com.netflix.conductor.common.run`

Follows existing common module convention: `@ProtoMessage`/`@ProtoField` annotations on DTOs, `@ProtoEnum` on
enums, manual getters/setters, `equals()`/`hashCode()`, `toString()`.

### StorageType

Shared enum — used by both server and SDK.

```java
public enum StorageType { S3, AZURE_BLOB, GCS, LOCAL }
```

### FileUploadStatus

Shared enum — used by both server and SDK.

```java
public enum FileUploadStatus {
    PENDING,
    UPLOADING,
    UPLOADED,
    FAILED
}
```

### FileHandle

Server-to-client DTO for file metadata. Does **not** expose `storagePath` — that is a server-internal detail.

```java
public class FileHandle {
    String fileId;              // conductor://file/uuid
    String fileName;
    String contentType;
    long fileSize;
    String contentHash;         // raw value from storage provider — null until upload confirmed
    StorageType storageType;
    FileUploadStatus uploadStatus;
    String workflowId;          // nullable — set for workflow input files
    String taskId;              // nullable — set for task output files
    Instant createdAt;
    Instant updatedAt;
}
```

### FileUploadRequest

```java
public class FileUploadRequest {
    String fileName;
    String contentType;
    long fileSize;
    String workflowId;         // optional
    String taskId;             // optional
}
```

### FileUploadResponse

```java
public class FileUploadResponse {
    String fileId;
    String fileName;
    String contentType;
    long fileSize;
    StorageType storageType;
    FileUploadStatus uploadStatus;
    String uploadUrl;           // presigned URL for upload (null for local backend)
    Instant uploadUrlExpiresAt; // when uploadUrl expires (null for local backend)
    Instant createdAt;
}
```

### FileUploadUrlResponse

Note: `expiresAt` is null for local backend (direct file API, no expiration).

```java
public class FileUploadUrlResponse {
    String fileId;
    String uploadUrl;
    Instant expiresAt;          // null for local backend
}
```

### FileDownloadUrlResponse

Note: `expiresAt` is null for local backend (direct file API, no expiration).

```java
public class FileDownloadUrlResponse {
    String fileId;
    String downloadUrl;
    Instant expiresAt;          // null for local backend
}
```

### FileUploadCompleteResponse

```java
public class FileUploadCompleteResponse {
    String fileId;
    FileUploadStatus uploadStatus;
    String contentHash;         // from storage provider — null for local backend
}
```

### MultipartInitResponse

```java
public class MultipartInitResponse {
    String fileId;
    String uploadId;            // backend-specific multipart upload ID
    String uploadUrl;           // resumable URL (GCS/Azure) — null for S3 (uses per-part URLs)
    long partSize;              // recommended part size in bytes
}
```

- **S3**: `uploadUrl` is null — SDK must request per-part presigned URLs via the per-part endpoint
- **GCS**: `uploadUrl` is a resumable session URI — SDK reuses for all parts with byte-range headers
- **Azure**: `uploadUrl` is a SAS-token URL — SDK reuses for all parts with block IDs

### MultipartCompleteRequest

```java
public class MultipartCompleteRequest {
    List<String> partETags;
}
```

## Interfaces

### FileStorage

Server-side storage abstraction. One implementation per backend.

Location: `core` module — `com.netflix.conductor.core.storage`

```java
public interface FileStorage {

    StorageType getStorageType();

    /** Generate a presigned upload URL (or equivalent) for the given storage path. */
    String generateUploadUrl(String storagePath, Duration expiration);

    /** Generate a presigned download URL (or equivalent) for the given storage path. */
    String generateDownloadUrl(String storagePath, Duration expiration);

    /**
     * Verify upload and return storage metadata in a single call to the storage backend.
     * Returns null if file does not exist at the given path.
     * Fields: exists, contentHash (raw from provider, null for local), contentSize (bytes).
     */
    StorageFileInfo getStorageFileInfo(String storagePath);

    // --- Multipart support ---

    /** Initiate a multipart upload. Returns a backend-specific upload ID. */
    String initiateMultipartUpload(String storagePath);

    /** Generate a presigned URL for uploading a single part. */
    String generatePartUploadUrl(String storagePath, String uploadId, int partNumber, Duration expiration);

    /** Complete a multipart upload after all parts have been uploaded. */
    void completeMultipartUpload(String storagePath, String uploadId, List<String> partETags);
}
```

`StorageFileInfo` — server-internal value object returned by `getStorageFileInfo()`:

```java
public class StorageFileInfo {
    boolean exists;
    String contentHash;     // raw from storage provider — null for local backend
    long contentSize;       // actual bytes on storage
}
```

Location: `core` module — `com.netflix.conductor.core.storage`

No `deleteFile()` — files must not be deleted after workflow execution ends (see Principles & Constraints).

Follows the existing backend module pattern from `ExternalPayloadStorage` — per-backend modules with
`@Configuration` + `@ConditionalOnProperty` + `@ConfigurationProperties`. (`ExternalPayloadStorage` itself is in
`common`; `FileStorage` is in `core` because only the server needs it — the SDK uses `FileStorageBackend` instead.)
**BYOS** supported — any custom implementation of this interface can be plugged in.

## Service

### FileStorageService

Location: `core` module — `com.netflix.conductor.core.storage`

Orchestrates file metadata DAO + storage backend. Injected into `FileResource` controller.

```java
@Validated
public interface FileStorageService {

    FileUploadResponse createFile(FileUploadRequest request);

    FileUploadUrlResponse getUploadUrl(String fileId);

    FileUploadCompleteResponse confirmUpload(String fileId);

    FileDownloadUrlResponse getDownloadUrl(String fileId);

    FileHandle getFileMetadata(String fileId);

    // Multipart
    MultipartInitResponse initiateMultipartUpload(String fileId);

    FileUploadUrlResponse getPartUploadUrl(String fileId, String uploadId, int partNumber);

    FileUploadCompleteResponse completeMultipartUpload(String fileId, String uploadId, List<String> partETags);
}
```

Implementation: `FileStorageServiceImpl` in `core` module.

- Validates file size against configured maximum
- Generates storage path: `files/<uuid>/<fileName>`
- Delegates presigned URL generation to `FileStorage`
- Generates fresh presigned URLs on each request — no caching
- On `confirmUpload()`: single call to `FileStorage.getStorageFileInfo()` — verifies file exists, reads
  content hash + actual size from storage provider in one round-trip. Persists both on `FileModel`
  (`storageContentHash`, `storageContentSize`)
- Persists file metadata via `FileMetadataDAO`

## DAO

### FileMetadataDAO

Location: `core` module — `com.netflix.conductor.dao`

Follows existing DAO pattern (`ExecutionDAO`, `MetadataDAO`, etc.).

```java
public interface FileMetadataDAO {

    void createFileMetadata(FileModel fileModel);

    FileModel getFileMetadata(String fileId);

    void updateUploadStatus(String fileId, FileUploadStatus status);

    void updateUploadComplete(String fileId, FileUploadStatus status, String contentHash,
            long contentSize);

    List<FileModel> getFilesByWorkflowId(String workflowId);

    List<FileModel> getFilesByTaskId(String taskId);
}
```

### Implementations

Must implement for **all 5 persistence backends**. Each follows its module's existing conditional activation
and base class pattern. All DAO beans **additionally** require
`@ConditionalOnProperty(name = "conductor.file-storage.enabled", havingValue = "true")` so no DAO beans are
created when the feature is disabled.

| Backend | Module | Activation | Base Class | Bean Registration |
|---------|--------|-----------|------------|-------------------|
| Postgres | `postgres-persistence` | `@ConditionalOnProperty(name = "conductor.db.type", havingValue = "postgres")` + file-storage enabled | Extends `PostgresBaseDAO` | `@Bean` + `@DependsOn({"flywayForPrimaryDb"})` in `PostgresConfiguration` |
| MySQL | `mysql-persistence` | `@ConditionalOnProperty(name = "conductor.db.type", havingValue = "mysql")` + file-storage enabled | Extends `MySQLBaseDAO` | `@Bean` + `@DependsOn({"flyway", "flywayInitializer"})` in `MySQLConfiguration` |
| SQLite | `sqlite-persistence` | `@ConditionalOnProperty(name = "conductor.db.type", havingValue = "sqlite")` + file-storage enabled | Delegate DAO extending `SqliteBaseDAO` | `@Bean` + `@DependsOn({"flywayForPrimaryDb"})` in `SqliteConfiguration` |
| Redis | `redis-persistence` | `@Conditional(AnyRedisCondition.class)` + file-storage enabled | Extends `BaseDynoDAO` | `@Component` on DAO class directly |
| Cassandra | `cassandra-persistence` | `@ConditionalOnProperty(name = "conductor.db.type", havingValue = "cassandra")` + file-storage enabled | Extends `CassandraBaseDAO` | `@Bean` in `CassandraConfiguration` |

**Storage patterns per backend type:**

- **Relational (Postgres, MySQL, SQLite):** SQL table with `json_data` column, `queryWithTransaction()`,
  `RetryTemplate` + `ObjectMapper` + `DataSource` injection, Flyway migration
- **Redis:** Hash (`hset`/`hget`) keyed by file ID, JSON string values, key namespace via `nsKey()`
- **Cassandra:** Dedicated table with prepared statements, JSON string column, consistency level config

**SQLite note:** follows facade/delegate pattern — `SqliteFileMetadataDAO` delegates to a sub-DAO extending
`SqliteBaseDAO` (same as `SqliteMetadataDAO` delegates to `SqliteTaskMetadataDAO`, `SqliteWorkflowMetadataDAO`).

**Stubs:** `StubFileMetadataDAO` — in-memory `ConcurrentHashMap` for testing.

### Audit & Tracking

- **Background audit workflow** — detect orphaned files with PENDING/UPLOADING status that were never completed.
  Consider a background process that scans for stale uploads and marks them FAILED.
- **Storage usage tracking** — should be able to identify how much storage is used per workflow (and per customer if
  multi-tenancy is supported). Query by `workflow_id` in `file_metadata` table.

### FileModel

Location: `core` module — `com.netflix.conductor.model`

```java
public class FileModel {
    String fileId;
    String fileName;
    String contentType;
    long fileSize; // in bytes, set at creation time
    
    // - "hash code" — set on upload-complete from S3/Azure/GCS
    // - do not compute this value, get it from the server
    // local storage sets it to null
    String storageContentHash;         
    long storageContentSize;         // in bytes — set on upload-complete
    StorageType storageType;    
    String storagePath;         // server-internal — not exposed in API
    FileUploadStatus uploadStatus;
    String workflowId;
    String taskId;
    Instant createdAt;
    Instant updatedAt;
}
```

## Database Schema

New table: `file_metadata`

```sql
CREATE TABLE file_metadata (
    file_id         VARCHAR(255) NOT NULL PRIMARY KEY,
    file_name       VARCHAR(1024) NOT NULL,
    content_type    VARCHAR(255) NOT NULL,
    file_size       BIGINT NOT NULL,
    storage_content_hash VARCHAR(255),
    storage_content_size BIGINT,
    storage_type    VARCHAR(50) NOT NULL,
    storage_path    VARCHAR(2048) NOT NULL,
    upload_status   VARCHAR(50) NOT NULL DEFAULT 'UPLOADING',
    workflow_id     VARCHAR(255),
    task_id         VARCHAR(255),
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_file_metadata_workflow_id ON file_metadata (workflow_id);
CREATE INDEX idx_file_metadata_task_id ON file_metadata (task_id);
CREATE INDEX idx_file_metadata_upload_status ON file_metadata (upload_status);
```

### Migration / Schema Setup

**Flyway migrations (relational):**

- Postgres: `postgres-persistence/src/main/resources/db/migration_postgres/V15__file_metadata.sql`
- MySQL: `mysql-persistence/src/main/resources/db/migration/V9__file_metadata.sql`
- SQLite: `sqlite-persistence/src/main/resources/db/migration_sqlite/V<next>__file_metadata.sql`

Follows existing Flyway naming convention — next version number in each module.

**Redis:** No migration. Schema is implicit in DAO code — hash keys created on first write.

**Cassandra:** Table creation via CQL in DAO initialization or setup script — follows existing Cassandra
module pattern.

## Storage Backends

Each backend implements `FileStorage` interface. Follows existing `ExternalPayloadStorage` pattern:
`@Configuration` + `@ConditionalOnProperty` + `@ConfigurationProperties`. All backend beans **additionally**
require `@ConditionalOnProperty(name = "conductor.file-storage.enabled", havingValue = "true")` — no storage
beans created when feature is disabled.

### Local File Storage (default)

- No presigned URLs — server returns the local storage path (e.g. `files/<uuid>/<fileName>`)
- SDK's `LocalFileStorageBackend` reads/writes directly to the configured local directory — server never
  receives file content
- Files stored on local disk under configured directory (shared between server and SDK on same machine)
- Default out-of-box — no infra needed to get started
- No extra endpoints needed — SDK handles file transfer via local filesystem

### AWS S3

- Presigned URLs via `S3Presigner` (existing AWS SDK v2 dependency in `awss3-storage`)
- Shares module with `S3PayloadStorage` — reuses `S3Client` and `S3Presigner` beans
- Config prefix: `conductor.file-storage.s3`

### Azure Blob Storage

- SAS tokens for upload/download
- Shares module with `AzureBlobPayloadStorage`
- Config prefix: `conductor.file-storage.azure-blob`

### Google Cloud Storage

- Signed URLs for upload/download
- New module: `gcs-storage`
- Config prefix: `conductor.file-storage.gcs`

### Bring Your Own Storage (BYOS)

- Any custom `FileStorage` implementation can be plugged in
- Implement `FileStorage` interface, register as Spring bean
- Corresponding SDK-side `FileStorageBackend` implementation required
- No code changes to Conductor — interface-based design enables this out of the box

## Configuration

Config prefix: `conductor.file-storage`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `conductor.file-storage.enabled` | boolean | `false` | Feature flag — entire file storage feature gated on this |
| `conductor.file-storage.type` | String | `local` | Storage backend: `local`, `s3`, `azure-blob`, `gcs` |
| `conductor.file-storage.max-file-size` | DataSize | `5GB` | Max file size |
| `conductor.file-storage.signed-url-expiration` | Duration | `60s` | Presigned URL TTL |
| `conductor.file-storage.local.directory` | String | `./conductor-files` | Local storage root directory |
| `conductor.file-storage.s3.bucket-name` | String | — | S3 bucket |
| `conductor.file-storage.s3.region` | String | `us-east-1` | AWS region |
| `conductor.file-storage.azure-blob.container-name` | String | — | Azure container |
| `conductor.file-storage.azure-blob.connection-string` | String | — | Azure connection string |
| `conductor.file-storage.gcs.bucket-name` | String | — | GCS bucket |
| `conductor.file-storage.gcs.project-id` | String | — | GCP project ID |

Precedence: code defaults → config file → env var.

## Module Placement (Server)

### `common` module — `com.netflix.conductor.common`

```
common/src/main/java/com/netflix/conductor/common/
    run/
        FileHandle.java
        FileUploadRequest.java
        FileUploadResponse.java
        FileUploadUrlResponse.java
        FileDownloadUrlResponse.java
        FileUploadCompleteResponse.java
        MultipartInitResponse.java
        MultipartCompleteRequest.java
        StorageType.java                    ← enum (shared server + SDK)
        FileUploadStatus.java               ← enum (shared server + SDK)
```

### `core` module — `com.netflix.conductor`

```
core/src/main/java/com/netflix/conductor/
    core/storage/
        FileStorage.java                    ← interface
        StorageFileInfo.java                ← value object returned by FileStorage.getStorageFileInfo()
        FileStorageService.java             ← interface
        FileStorageServiceImpl.java
        FileStorageProperties.java          ← @ConfigurationProperties
        converter/
            FileModelConverter.java         ← FileUploadRequest→FileModel, FileModel→FileHandle, FileModel→FileUploadResponse
    dao/
        FileMetadataDAO.java                ← interface
    model/
        FileModel.java
```

### `rest` module

```
rest/src/main/java/com/netflix/conductor/rest/controllers/
    FileResource.java                       ← @RestController
```

### `awss3-storage` module (existing)

```
awss3-storage/src/main/java/com/netflix/conductor/s3/
    config/
        S3FileStorageConfiguration.java
        S3FileStorageProperties.java
    storage/
        S3FileStorage.java                  ← implements FileStorage
```

### `azureblob-storage` module (existing)

```
azureblob-storage/src/main/java/com/netflix/conductor/azureblob/
    config/
        AzureBlobFileStorageConfiguration.java
        AzureBlobFileStorageProperties.java
    storage/
        AzureBlobFileStorage.java           ← implements FileStorage
```

### `gcs-storage` module (new)

```
gcs-storage/src/main/java/com/netflix/conductor/gcs/
    config/
        GcsFileStorageConfiguration.java
        GcsFileStorageProperties.java
    storage/
        GcsFileStorage.java                 ← implements FileStorage
```

### `local-file-storage` module (new)

```
local-file-storage/src/main/java/com/netflix/conductor/local/
    config/
        LocalFileStorageConfiguration.java
        LocalFileStorageProperties.java
    storage/
        LocalFileStorage.java               ← implements FileStorage
```

### `postgres-persistence` module (existing)

```
postgres-persistence/src/main/java/com/netflix/conductor/postgres/dao/
    PostgresFileMetadataDAO.java            ← extends PostgresBaseDAO, implements FileMetadataDAO
postgres-persistence/src/main/resources/db/migration_postgres/
    V15__file_metadata.sql
```

Bean: add to `PostgresConfiguration` with `@DependsOn({"flywayForPrimaryDb"})`

### `mysql-persistence` module (existing)

```
mysql-persistence/src/main/java/com/netflix/conductor/mysql/dao/
    MySQLFileMetadataDAO.java               ← extends MySQLBaseDAO, implements FileMetadataDAO
mysql-persistence/src/main/resources/db/migration/
    V9__file_metadata.sql
```

Bean: add to `MySQLConfiguration` with `@DependsOn({"flyway", "flywayInitializer"})`

### `sqlite-persistence` module (existing)

```
sqlite-persistence/src/main/java/com/netflix/conductor/sqlite/dao/
    SqliteFileMetadataDAO.java              ← facade, implements FileMetadataDAO
    file/
        SqliteFileMetadataSubDAO.java       ← extends SqliteBaseDAO (actual implementation)
sqlite-persistence/src/main/resources/db/migration_sqlite/
    V<next>__file_metadata.sql
```

Bean: add to `SqliteConfiguration` with `@DependsOn({"flywayForPrimaryDb"})`

### `redis-persistence` module (existing)

```
redis-persistence/src/main/java/com/netflix/conductor/redis/dao/
    RedisFileMetadataDAO.java               ← @Component, @Conditional(AnyRedisCondition.class),
                                               extends BaseDynoDAO, implements FileMetadataDAO
```

No migration — hash keys created on first write.

### `cassandra-persistence` module (existing)

```
cassandra-persistence/src/main/java/com/netflix/conductor/cassandra/dao/
    CassandraFileMetadataDAO.java           ← extends CassandraBaseDAO, implements FileMetadataDAO
```

Bean: add to `CassandraConfiguration`

---

# Java SDK

## Public Interfaces

### FileHandler

Developer-facing file reference. Developer sees only this interface.

Location: `conductor-client` — `com.netflix.conductor.sdk.file`

```java
public interface FileHandler {

    String getFileId();         // returns conductor://file/uuid

    InputStream getInputStream();

    String getFileName();

    String getContentType();

    long getFileSize();

    static FileHandler fromLocalFile(Path path);

    static FileHandler fromLocalFile(Path path, String contentType);
}
```

- `getInputStream()` — lazy download on first call; returns new `InputStream` from cached local file on subsequent
  calls; blocks if download is in progress (lock); applies retry; throws `FileStorageException` on failure
- `fromLocalFile()` — wraps a local file for upload; no network call; no file ID assigned yet

`FileStorageException` — SDK-side unchecked exception for all file storage errors (download failure, storage
mismatch, missing file, etc.). In `com.netflix.conductor.sdk.file`.

### FileUploader

Developer-facing upload API. Returned by `FileClient`.

Location: `conductor-client` — `com.netflix.conductor.sdk.file`

```java
public interface FileUploader {

    FileHandler upload(InputStream inputStream);

    FileHandler upload(InputStream inputStream, String contentType);

    FileHandler upload(Path localFile);

    FileHandler upload(Path localFile, String contentType);
}
```

Default content type: `application/octet-stream`.

## SDK-Internal Classes

### FileClient

Implements `FileUploader`. Composes `ConductorClient` for server communication and `FileStorageBackend` for actual
transfer. Follows existing client pattern (`TaskClient`, `WorkflowClient`).

Location: `conductor-client` — `com.netflix.conductor.client.http`

```java
public class FileClient implements FileUploader {
    private final ConductorClient client;
    private final FileStorageBackend storageBackend;

    // developer-facing (via FileUploader)
    FileHandler upload(Path localFile);
    FileHandler upload(Path localFile, String contentType);
    FileHandler upload(InputStream inputStream);
    FileHandler upload(InputStream inputStream, String contentType);

    // SDK-internal — used by ManagedFileHandler, not exposed to developer
    void download(String fileId, Path destination);
    FileHandle getMetadata(String fileId);
    StorageType getStorageBackendType();  // delegates to FileStorageBackend.getStorageType()
    String getUploadUrl(String fileId);
    String getDownloadUrl(String fileId);
    void confirmUpload(String fileId);

    // SDK-internal — multipart
    String initiateMultipartUpload(String fileId);
    String getPartUploadUrl(String fileId, String uploadId, int partNumber);
    void completeMultipartUpload(String fileId, String uploadId, List<String> partETags);
}
```

Builder: `FileClient.builder().conductorClient(client).storageBackend(backend).build()`

Fits into `OrkesClients` facade: `orkesClients.getFileClient(storageBackend)` — follows existing pattern for
`getTaskClient()`, `getWorkflowClient()`, etc. Takes `FileStorageBackend` parameter because file operations
require a configured storage backend (unlike other clients that only need `ConductorClient`).

### LocalFileHandler

Package-private. Returned by `FileHandler.fromLocalFile(path)`. Wraps a local file before upload — no file ID, no
network call, no server interaction. Holds `Path` and optional `contentType`. `getFileId()` returns null before upload.
Becomes a `ManagedFileHandler` after upload assigns a `conductor://file/uuid`.

### ManagedFileHandler

SDK-internal `FileHandler` implementation. Package-private.

Location: `conductor-client` — `com.netflix.conductor.sdk.file`

```java
class ManagedFileHandler implements FileHandler {
    String fileId;              // conductor://file/uuid
    String fileName;
    String contentType;
    long fileSize;
    StorageType storageType;
    Path localPath;             // cached local file after download
    FileUploadStatus uploadStatus;
    FileDownloadStatus downloadStatus;
    FileClient fileClient;      // for lazy download, URL management, metadata
    // lock for concurrent download
}
```

`fileName`, `contentType`, `fileSize` populated from server metadata on first access.

### FileStorageBackend

Actual file transfer to/from storage. One implementation per backend.

Location: `conductor-client` — `com.netflix.conductor.sdk.file`

```java
public interface FileStorageBackend {

    StorageType getStorageType();

    void upload(String url, Path localFile);

    void upload(String url, InputStream inputStream, long contentLength);

    void download(String url, Path destination);

    // --- Multipart support ---

    /** Upload a single part to the given presigned URL. Returns the part's ETag. */
    String uploadPart(String url, Path localFile, long offset, long length);
}
```

`getStorageType()` enables the fail-fast mismatch check — SDK compares its backend type against server metadata.
Single backend per client — multiple backends is a future improvement.

Implementations (all in `conductor-client` module, `com.netflix.conductor.sdk.file.storage`):
- `S3FileStorageBackend` — uses presigned URLs with HTTP PUT/GET
- `AzureFileStorageBackend` — uses SAS token URLs
- `GcsFileStorageBackend` — uses signed URLs
- `LocalFileStorageBackend` — uses Conductor server direct file API

### FileDownloadStatus

SDK-side only — not persisted to server.

```java
public enum FileDownloadStatus {
    NOT_STARTED,
    DOWNLOADING,
    DOWNLOADED,
    FAILED
}
```

## Worker Integration

### Annotation-Based (zero config)

```java
@Component
public class MyWorkers {
    @WorkerTask("process_document")
    public FileHandler process(@InputParam("inputFile") FileHandler inputFile) {
        InputStream stream = inputFile.getInputStream();
        // process...
        return FileHandler.fromLocalFile(output);
    }
}
```

Changes to support this:
- `AnnotatedWorker.getInputValue()` — detect `conductor://file/` prefix in `inputData`, wrap as `ManagedFileHandler`
- `AnnotatedWorker.setValue()` — detect `FileHandler` return type, trigger upload, replace with `conductor://file/uuid`
- Spring auto-config creates `FileClient` bean in `ConductorClientAutoConfiguration`

### Imperative

```java
FileClient fileClient = FileClient.builder()
        .conductorClient(conductorClient)
        .storageBackend(new S3FileStorageBackend())
        .build();

Worker worker = new DocumentWorker(fileClient);
```

```java
public class DocumentWorker implements Worker {
    private final FileUploader fileUploader;

    public DocumentWorker(FileUploader fileUploader) {
        this.fileUploader = fileUploader;
    }

    public TaskResult execute(Task task) {
        TaskResult result = new TaskResult(task);
        FileHandler input = task.getInputFileHandler("inputFile");
        InputStream stream = input.getInputStream();
        // process...
        FileHandler output = fileUploader.upload(Path.of("/data/result.pdf"));
        result.getOutputData().put("outputFile", output.getFileId());
        return result;
    }
}
```

- `task.getInputFileHandler("key")` — reads `inputData.get("key")`, detects `conductor://file/` prefix, wraps as
  `ManagedFileHandler`. Throws if value is not a `conductor://file/` reference.
- `Task` holds transient (non-serialized) `FileClient` reference set by `TaskRunner` before `execute()`:
  `@JsonIgnore private transient FileClient fileClient;`

## Upload Strategies

### Explicit Upload

Developer calls `FileUploader.upload()` directly. Gets `FileHandler` back with `conductor://file/uuid`.

Used outside task context (e.g., before starting a workflow) or when developer wants explicit control.

### Automatic Upload

Framework handles on task completion. Developer returns `FileHandler.fromLocalFile(path)` from `@WorkerTask` method
or puts it in `result.getOutputData()`.

Single interception point in `TaskRunner` — after `execute()`, before `updateTask()`:
- Scan `outputData` for `FileHandler` instances
- Upload each via `FileClient`
- Replace with `conductor://file/uuid` string

Changes: `TaskRunner` (scan loop + `FileClient` param), `TaskRunnerConfigurer` (pass `FileClient`),
`TaskRunnerConfigurer.Builder` (`withFileClient()`).

May drop automatic upload after initial implementation — kept isolated for clean removal.

## Configuration (SDK)

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `conductor.file-client.retry-count` | int | `3` | Upload/download retry count |
| `conductor.file-client.multipart-threshold` | long | `104857600` | Bytes above which multipart upload kicks in |
| `conductor.file-client.local-cache-directory` | String | `${java.io.tmpdir}/conductor-files` | Client-side download cache for files fetched from S3/GCS/Azure |

Precedence: code defaults → config file → env var.

## Module Placement (SDK)

### `conductor-client` module

```
conductor-client/src/main/java/com/netflix/conductor/
    sdk/file/
        FileHandler.java                    ← public interface
        FileUploader.java                   ← public interface
        FileStorageBackend.java             ← public interface
        FileDownloadStatus.java             ← enum
        FileStorageException.java           ← unchecked, SDK-side
        ManagedFileHandler.java             ← package-private
        LocalFileHandler.java              ← package-private, from fromLocalFile()
        converter/
            FileHandlerConverter.java       ← FileUploadResponse→ManagedFileHandler, FileHandle→ManagedFileHandler, Path→FileUploadRequest
        storage/
            S3FileStorageBackend.java       ← implements FileStorageBackend
            AzureFileStorageBackend.java
            GcsFileStorageBackend.java
            LocalFileStorageBackend.java
    client/http/
        FileClient.java                     ← implements FileUploader
io/orkes/conductor/client/
    OrkesClients.java                       ← add getFileClient(FileStorageBackend) (modify)
    http/
        OrkesFileClient.java                ← extends FileClient (follows OrkesTaskClient pattern)
```

### `conductor-client-spring` module

```
conductor-client-spring/src/main/java/io/orkes/conductor/client/spring/
    OrkesConductorClientAutoConfiguration.java  ← add FileStorageBackend + FileClient beans (modify)
```

Spring auto-config creates appropriate `FileStorageBackend` bean based on `conductor.file-storage.type` config
property (e.g., `S3FileStorageBackend` when type is `s3`), then creates `FileClient` bean composing both
`ConductorClient` and the selected `FileStorageBackend`. Follows existing Orkes auto-config pattern with
`@ConditionalOnMissingBean` and `@ConditionalOnBean(ApiClient.class)`.

---

# File Operations

## Upload Flow

1. Developer creates `FileHandler.fromLocalFile(path)` — wraps local file, no network call
2. On task completion (automatic) or explicit `FileUploader.upload()`:
    1. SDK calls `POST /api/files` with file metadata → server creates record with status UPLOADING (URL generated
       eagerly), returns `FileUploadResponse` with presigned upload URL and `conductor://file/uuid`
    2. SDK uploads file to storage backend using presigned URL via `FileStorageBackend`
    3. On retry: SDK calls `GET /api/files/{fileId}/upload-url` if URL expired → gets fresh URL
    4. SDK calls `POST /api/files/{fileId}/upload-complete` → server verifies file on storage, reads
       content hash from storage provider (S3 ETag, Azure Content-MD5, GCS md5Hash), persists hash,
       marks UPLOADED
3. SDK returns `FileHandler` with `conductor://file/uuid` to developer

## Download Flow

1. Workflow input passes `conductor://file/uuid` strings — regular string values in `inputData`
2. SDK detects `conductor://file/` prefix, wraps as `ManagedFileHandler`
3. Developer receives `FileHandler` via `@InputParam` or `task.getInputFileHandler("key")`
4. File downloaded only when `fileHandler.getInputStream()` is called:
    1. SDK calls `GET /api/files/{fileId}` — gets metadata including storage type
    2. SDK validates storage type matches configured backend — fail fast on mismatch
    3. SDK calls `GET /api/files/{fileId}/download-url` — gets presigned download URL
    4. SDK downloads file via `FileStorageBackend` to local cache
    5. Returns `InputStream` from cached local file
5. Subsequent calls return new `InputStream` from same cached file — no re-download
6. Concurrent calls block on lock until first download completes

## Presigned URLs

- Created by Conductor server only
- Short-lived — configurable TTL (default 60s)
- New URL created only if previous expired
- Per-backend mechanism:
    - AWS S3: presigned URLs via `S3Presigner`
    - Azure Blob: SAS tokens
    - GCS: signed URLs
    - Local: not needed — direct file API

## Multipart Upload & Download

- Upload uses explicit multipart (server-coordinated parts) for files above a configurable threshold
- Download uses HTTP range requests within `FileStorageBackend.download()` — no server-side multipart
  coordination needed. The SDK handles chunked download as an internal implementation detail.
- Max file size configurable (default 5 GB) — files exceeding limit rejected
- Multipart upload flow:
    1. SDK calls `POST /api/files/{fileId}/multipart` → server initiates multipart on storage backend, returns
       upload ID, part size, and optional resumable URL
    2. Per-part upload — backend-specific:
        - **S3**: SDK calls `GET .../multipart/{uploadId}/part/{partNumber}` per part → gets per-part presigned URL,
          uploads via `FileStorageBackend.uploadPart()` → returns ETag
        - **GCS/Azure**: SDK reuses `uploadUrl` from initiate response for all parts — no per-part server calls.
          SDK knows to skip based on `FileStorageBackend.getStorageType()`
    3. SDK calls `POST .../multipart/{uploadId}/complete` with all part ETags → server finalizes on storage backend
- S3 per-part presigned URLs generated fresh per request
- SDK decides single-part vs multipart based on file size and configured threshold

## Failure & Retry

### Upload Failure

- SDK reuses same `conductor://file/uuid` — does not create new file metadata
- Requests new presigned URL if expired
- Restarts upload from beginning
- Default: 3 retries, configurable

### Download Failure

- SDK retries automatically
- Requests new presigned URL if expired
- Default: 3 retries, configurable

## Status Tracking

### Upload Status (server + worker)

```
UPLOADING → UPLOADED
         → FAILED
```

- `POST /api/files` creates file with status UPLOADING (URL generated eagerly, upload expected immediately)
- `POST .../upload-complete` transitions to UPLOADED
- FAILED on error (retry exhausted, size mismatch, etc.)
- PENDING reserved for future use (batch registration, deferred upload)
- Persisted on server in `file_metadata` table
- Also tracked on worker node via `ManagedFileHandler.uploadStatus`

### Download Status (worker only)

```
NOT_STARTED → DOWNLOADING → DOWNLOADED
                          → FAILED
```

- Worker node only — not persisted to server
- Tracked via `ManagedFileHandler.downloadStatus`

---

# Use Cases

Full mermaid diagrams in [conductor-file-usecases-mermaid.md](conductor-file-usecases-mermaid.md).

## 1. Order Invoice, Packing Slip & Shipping Label Generation

E-commerce order triggers parallel document generation across three workers, each producing a file output. Files are
passed downstream to a bundling task that reads all three.

### Flow

1. Webhook triggers workflow with order ID as input
2. `fetch_order` task — HTTP task fetches order data from order service
3. `calculate_totals` task — INLINE task computes tax, discounts, shipping
4. FORK into 3 parallel branches:
    - `generate_invoice` — worker receives order data via `@InputParam`, generates PDF,
      returns `FileHandler.fromLocalFile(invoicePdf)` → framework auto-uploads → `conductor://file/invoice-uuid`
    - `generate_packing_slip` — same pattern, returns `conductor://file/packslip-uuid`
    - `generate_shipping_label` — calls carrier API, generates label image,
      returns `FileHandler.fromLocalFile(labelPng)` → `conductor://file/label-uuid`
5. JOIN — collects `conductor://file/` IDs from all three branches
6. `bundle_and_distribute` — worker receives three `FileHandler` inputs via `@InputParam`:
    - `invoiceFile.getInputStream()` → reads invoice PDF
    - `packslipFile.getInputStream()` → reads packing slip
    - `labelFile.getInputStream()` → reads label image
    - Bundles, emails invoice to customer, sends slip + label to warehouse printer
7. `update_order_status` — HTTP task marks order as "Ready to Ship"

### File Operations Exercised

- 3 automatic uploads (one per FORK branch)
- 3 lazy downloads (in bundle task)
- `FileHandler` passed between tasks via `conductor://file/uuid` in `outputData` → `inputData`
- Parallel file creation

## 2. AI-Powered Knowledge Base Builder (RAG Pipeline)

Document ingestion pipeline where each document produces multiple derived files. Files are reused across chunking and
embedding stages.

### Flow

1. Trigger: new documents uploaded to storage, workflow receives list of `conductor://file/` IDs as input
2. DO_WHILE loop — process each document:
    1. SWITCH on file type (PDF, DOCX, HTML, other):
        - `extract_text_pdf` — worker receives `FileHandler` via `@InputParam`, calls
          `inputFile.getInputStream()` → extracts text via Tika, returns `FileHandler.fromLocalFile(extractedTxt)`
        - `extract_text_docx` / `extract_text_html` / `extract_text_ocr` — same pattern
    2. `chunk_text` — worker receives extracted text file, reads via `getInputStream()`, chunks into 512-token
       segments with 50-token overlap, writes chunks to JSONL, returns `FileHandler.fromLocalFile(chunksJsonl)`
    3. DYNAMIC_FORK — one task per chunk:
        - `generate_embedding` — worker receives chunk text, calls LLM API, returns embedding vector
    4. JOIN — collects embeddings
    5. `upsert_vectors` — HTTP task upserts to vector DB (Pinecone / Weaviate)
    6. `generate_metadata_index` — worker produces index JSON file, returns `FileHandler`
3. After loop: `write_master_manifest` — aggregates all index files into master manifest,
   returns `FileHandler.fromLocalFile(manifest)`

### File Operations Exercised

- Multiple input file types (PDF, DOCX, HTML)
- Derived file outputs (extracted text → chunks → embeddings)
- File handler chaining: upload output → next task downloads
- DYNAMIC_FORK with file-producing workers
- Explicit upload for pre-workflow document ingestion

## 3. Multi-Format Media Transcoding & Publishing

Large file upload (4K video), parallel processing into multiple output files of different formats and sizes.

### Flow

1. Developer explicitly uploads master video before starting workflow:
   ```java
   FileHandler master = fileUploader.upload(Path.of("/data/master_4k.mov"));
   workflowClient.startWorkflow("transcode", Map.of("masterVideo", master.getFileId()));
   ```
2. `validate_metadata` — INLINE task reads `masterVideo` FileHandler, extracts media metadata
3. FORK into 3 branches:
    - Branch 1 — DYNAMIC_FORK: transcode to multiple resolutions/formats
        - `transcode_variant` — worker receives master via `FileHandler`, calls FFmpeg, returns transcoded file
        - One task each for 1080p MP4, 720p MP4, 480p MP4, 1080p WebM, HLS playlist
    - Branch 2 — thumbnail generation:
        - `extract_keyframes` → `resize_thumbnails` → `generate_poster`
        - Each produces file outputs via `FileHandler`
    - Branch 3 — speech-to-text:
        - `transcribe_audio` → `generate_srt` → `generate_vtt`
        - Subtitle files returned as `FileHandler`
4. JOIN — collects all `conductor://file/` IDs
5. `generate_manifest` — aggregates all file references into manifest JSON
6. `upload_to_cdn` — HTTP task pushes all assets (reads each `FileHandler`)
7. `update_cms` — HTTP task registers URLs in CMS
8. `notify_team` — Slack notification

### File Operations Exercised

- Explicit upload of large file (multipart)
- One input file → many output files
- Parallel file creation via FORK + DYNAMIC_FORK
- File handler passing across multiple workflow stages
- Multiple file downloads in single task (manifest generation)

---

# What-If Scenarios

Answers go in the design doc.

1. **Crash during file upload** — what happens to the `conductor://file/uuid`? Can the SDK resume or must it restart?
2. **Temp file missing from disk** — developer or OS deletes cached file. What happens on next `getInputStream()`?
3. **File never fully uploaded** — server has PENDING/UPLOADING status but upload never completed. How is this detected?
   What does a downstream task see?
4. **File handler reuse on same worker** — same `conductor://file/uuid` used by two tasks on same worker. Does it re-download?
5. **Same file across different workflows** — workflow A uploads file, workflow B references same `conductor://file/uuid`. Is this
   allowed? How does it work?
6. **Presigned URL expires during multipart upload** — mid-upload, the URL expires. What happens?
7. **Storage backend mismatch** — server configured for S3 but SDK configured for local. When is this detected? What
   error does the developer see?
8. **Concurrent uploads to same file ID** — two workers try to upload to the same `conductor://file/uuid`. What happens?
9. **Worker restart mid-download** — worker crashes during file download. On restart, the task is retried. Does it
   re-download from scratch?
10. **File size exceeds limit after partial upload** — file is larger than declared in `FileUploadRequest`. What happens?

---

# Documentation Placement

Conductor docs use MkDocs Material. Existing nav structure in `mkdocs.yml`.

| Document | Location | Description |
|----------|----------|-------------|
| Getting started guide | `docs/documentation/quickstart/file-storage.md` | 2-minute guide using Java SDK |
| API reference | `docs/documentation/api/file-storage.md` | REST endpoint docs |
| Configuration reference | `docs/documentation/advanced/file-storage.md` | Server + backend config |
| Java SDK reference | Update `docs/documentation/sdks/java-sdk.md` | FileHandler, FileUploader usage |
| Python SDK reference | Update `docs/documentation/sdks/python-sdk.md` | Python file handling |
| JavaScript SDK reference | Update `docs/documentation/sdks/javascript-sdk.md` | JS/TS file handling |
| Go SDK reference | Update `docs/documentation/sdks/go-sdk.md` | Go file handling |
| C# SDK reference | Update `docs/documentation/sdks/csharp-sdk.md` | C# file handling |

Update `mkdocs.yml` nav to include new pages.

---

# Developer Experience Showcase

Separate section in the design doc. Not for use case examples — purely DX showcase. Must not overlap with
use case scenarios in [conductor-file-usecases-mermaid.md](conductor-file-usecases-mermaid.md).

## Structure

Two subsections, in order:

1. **Using FileClient** — developer calls `FileUploader.upload()` explicitly. Primary and stable approach.
2. **Automatic Upload** — framework detects `FileHandler` in output, uploads on behalf of developer.
   May be removed after initial implementation — kept isolated for clean removal. **Placed at the end.**

## Example Scenario

Create a simple, independent **media transcode** scenario for all DX examples. Not from the use cases — a
standalone scenario designed purely to showcase the SDK.

## Requirements

- **Imperative flow first**, then annotation-based — imperative shows full wiring, annotation shows zero-config
- Must demonstrate all of the following:
    - Upload before workflow (pre-workflow explicit upload)
    - Single file upload/download
    - Multi file upload/download
    - With FileClient upload (explicit) and without (automatic)
    - Annotation-based vs imperative workers
- Include **workflow definitions** (JSON)
- Include **execution input/output JSON** showing `conductor://file/<uuid>` values flowing through
- **All variants must use the same workflow def and execution input/output** — no gaps between example code,
  workflow defs, and JSON. Imperative and annotation examples produce identical results against the same
  workflow.

---

# Testing Strategy

## Server

| Test Type | Location | Scope |
|-----------|----------|-------|
| Unit tests | Per-module `src/test/` | `FileStorage` impls, `FileStorageServiceImpl`, `FileMetadataDAO` impls, DTOs |
| Integration tests | `test-harness/src/test/groovy/` | `FileStorageSpec.groovy` — upload, download, status lifecycle |
| E2E tests | `e2e/src/test/java/` | Server + worker + MinIO end-to-end |

## Java SDK

| Test Type | Location | Scope |
|-----------|----------|-------|
| Unit tests | `conductor-client/src/test/` | `FileHandler`, `FileClient`, `FileStorageBackend`, `ManagedFileHandler` |
| Integration tests | `tests/src/test/` | Upload/download through real server |

## Stubs

- `StubFileStorage` — in-memory `FileStorage` implementation for server tests
- `StubFileMetadataDAO` — in-memory DAO for server tests
- `StubFileStorageBackend` — in-memory `FileStorageBackend` for SDK tests

## Verification

- **Kubernetes + MinIO is the primary E2E verification mechanism** — implementation is not complete
  until it passes. Full guide: [e2e-guide.md](e2e-guide.md)
- K8s manifests in `deploy/k8s/`: Conductor server + Postgres + MinIO (S3-compatible) + bucket init job
- Run: `./e2e/run_tests-file-storage.sh` — builds image, deploys to K8s, port-forwards, tests, tears down
- E2E scenario: start server → upload file → verify metadata → verify URLs → verify status transitions
- Java SDK first. Other SDKs one at a time after Java is stable

---

# Other SDKs

Implement once Java SDK is stable. Each SDK gets its own design doc.

| SDK | Language-specific notes |
|-----|----------------------|
| Python | `FileHandler` as protocol/ABC, `file_uploader.upload()`, decorator-based workers |
| JavaScript/TypeScript | `FileHandler` as interface/class, Promise-based upload/download |
| Go | `FileHandler` as interface, `FileClient` struct, goroutine-safe download |
| C#/.NET | `IFileHandler` interface, `IFileUploader` interface, async/await pattern |

Ruby and Rust are not in scope.
