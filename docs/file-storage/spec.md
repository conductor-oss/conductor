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

- Format: `conductor://file/<fileId>` — e.g. `conductor://file/d6a4e5f7-8b9c-4a1d-b2e3-f4a5b6c7d8e9`. The bare `fileId` is a UUID generated by the server.
- Two names: **`fileId`** is the bare identifier (URL path params, `FileModel`, DAO/service params). **`fileHandleId`** is the prefixed form (`conductor://file/<fileId>`) used in JSON payloads. Conversion happens via `FileIdToFileHandleIdConverter` (server) / `FileHandler` static helpers (SDK).
- Flows through existing `inputData` / `outputData` as either a plain `fileHandleId` string or a `{fileHandleId, contentType, fileName}` JSON object (Jackson serdes on `FileHandler`).
- No changes to `TaskDef`, `Task`, `WorkflowDef`, or any existing **serialized** model.
    - `Task` gets a `@JsonIgnore private transient WorkflowFileClient` field, exposed to workers via `getFileUploader()` — not serialized, not a model change.
- Server generates UUIDs — no collision risk.
- SDK detects either form (via `FileHandler.extractFileHandleId(...)`), wraps as `ManagedFileHandler`.
- Old SDKs see a plain string — no breakage.

---

# Server

## REST API

Base path: `/api/files`

URL paths use the bare `fileId`. JSON bodies carry the prefixed handle as `fileHandleId`. Timestamps are epoch millis (`long`).

### Create File

```
POST /api/files
Content-Type: application/json

Request:
{
    "fileName": "report.pdf",
    "contentType": "application/pdf",
    "workflowId": "wf-uuid",        // required
    "taskId": "task-uuid"            // optional — set when file is task output
}

Response: 201 Created
{
    "fileHandleId": "conductor://file/d6a4e5f7-...",
    "fileName": "report.pdf",
    "contentType": "application/pdf",
    "storageType": "S3",
    "uploadStatus": "UPLOADING",
    "uploadUrl": "https://s3.../presigned-put-url",
    "uploadUrlExpiresAt": 1760522460000,
    "createdAt": 1760522400000
}
```

### Get Upload URL

```
GET /api/files/{fileId}/upload-url

Response: 200 OK
{
    "fileHandleId": "conductor://file/...",
    "uploadUrl": "https://s3.../presigned-put-url",
    "expiresAt": 1760522460000
}
```

Generates a fresh presigned URL. Used on retry when the original URL from `POST /api/files` has expired.

### Confirm Upload

```
POST /api/files/{fileId}/upload-complete

Response: 200 OK
{
    "fileHandleId": "conductor://file/...",
    "uploadStatus": "UPLOADED",
    "contentHash": "d41d8cd98f00b204e9800998ecf8427e"
}
```

Server retrieves content hash from storage backend (S3 ETag, Azure Content-MD5, GCS md5Hash/crc32c) and
persists it. No hash computation by SDK or server — read from what the storage provider already has.
Local backend: hash is null.

### Get Download URL

```
GET /api/files/{workflowId}/{fileId}/download-url

Response: 200 OK
{
    "fileHandleId": "conductor://file/...",
    "downloadUrl": "https://s3.../presigned-get-url",
    "expiresAt": 1760522460000
}
```

`workflowId` is the **caller's** workflow. The server resolves its family (self, ancestors, descendants) and only returns a URL if the file's owning `workflowId` is in that family — otherwise 403.

### Get File Metadata

```
GET /api/files/{fileId}

Response: 200 OK
{
    "fileHandleId": "conductor://file/...",
    "fileName": "report.pdf",
    "contentType": "application/pdf",
    "contentHash": "d41d8cd98f00b204e9800998ecf8427e",
    "storageType": "S3",
    "uploadStatus": "UPLOADED",
    "workflowId": "wf-uuid",
    "taskId": "task-uuid",
    "createdAt": 1760522400000,
    "updatedAt": 1760522430000
}
```

Note: `storagePath` is server-internal — not exposed in API responses.

### Initiate Multipart Upload

```
POST /api/files/{fileId}/multipart

Response: 200 OK
{
    "fileHandleId": "conductor://file/...",
    "uploadId": "backend-specific-upload-id",
    "uploadUrl": null
}
```

Part size is chosen client-side from `conductor.file-client.multipart-part-size` (default 10 MiB). The server doesn't return one.

### Get Part Upload URL (S3 only)

```
GET /api/files/{fileId}/multipart/{uploadId}/part/{partNumber}

Response: 200 OK
{
    "fileHandleId": "conductor://file/...",
    "uploadUrl": "https://s3.../presigned-part-url",
    "expiresAt": 1760522460000
}
```

Response uses `FileUploadUrlResponse` DTO (same as Get Upload URL).

Only called when `MultipartInitResponse.uploadUrl` is null (S3). GCS and Azure use the resumable URL from initiate for all parts — the SDK picks the branch by checking `init.getUploadUrl() != null`.

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
    "fileHandleId": "conductor://file/...",
    "uploadStatus": "UPLOADED",
    "contentHash": "d41d8cd98f00b204e9800998ecf8427e"
}
```

Server finalizes multipart upload on storage, then verifies via `getStorageFileInfo()` — same as single-part
`upload-complete`. Reads content hash and actual size from storage provider, persists both.

### Error Responses

Reuses existing Conductor exception hierarchy (`com.netflix.conductor.core.exception`). Errors flow through
the existing `ApplicationExceptionMapper` (`@RestControllerAdvice`) → `ErrorResponse` DTO. No file-storage-specific error codes.

| Status | Exception | Condition |
|--------|-----------|-----------|
| 400 | `IllegalArgumentException` | Invalid request (missing `workflowId`, file not yet uploaded, etc.) |
| 403 | `AccessForbiddenException` | Caller `workflowId` is not in the file's workflow family |
| 404 | `NotFoundException` | File ID not found |
| 409 | `ConflictException` | File already uploaded (duplicate upload-complete) |
| 500 | `NonTransientException` | Storage backend error / verification failed |

There is no per-file size cap or 413 path in this iteration.

## DTOs

Server side: `org.conductoross.conductor.model.file` (in the server `common` module).
SDK side (mirrored): `org.conductoross.conductor.client.model.file` (in `conductor-client`).

Plain Java classes — explicit getters/setters, `equals()`/`hashCode()` via `Objects`, `toString()`. No proto annotations, no Lombok, no shared common module between server and SDK. Timestamps are `long` epoch millis on the wire.

### StorageType

```java
public enum StorageType { S3, AZURE_BLOB, GCS, LOCAL }
```

Defined twice — once on each side.

### FileUploadStatus

```java
public enum FileUploadStatus {
    PENDING,
    UPLOADING,
    UPLOADED,
    FAILED
}
```

`PENDING` is reserved for future use; the runtime flow enters `UPLOADING` on file creation. Defined twice — once on each side.

### FileHandle

Server-to-client DTO for file metadata. Does **not** expose `storagePath`.

```java
public class FileHandle {
    String fileHandleId;        // conductor://file/<fileId>
    String fileName;
    String contentType;
    String contentHash;         // raw value from storage provider — null until upload confirmed
    StorageType storageType;
    FileUploadStatus uploadStatus;
    String workflowId;          // nullable — set for workflow input files
    String taskId;              // nullable — set for task output files
    long createdAt;             // epoch millis
    long updatedAt;             // epoch millis
}
```

The SDK-side mirror keeps a `long fileSize` field — populated from the local file at upload time on `ManagedFileHandler` instances. The server does not return it.

### FileUploadRequest

```java
public class FileUploadRequest {
    String fileName;        // optional — SDK fills from path if absent
    String contentType;     // optional — SDK fills with application/octet-stream if absent
    @NotBlank String workflowId;  // required
    String taskId;          // optional — auto-filled from active TaskContext inside a worker
}
```

### FileUploadResponse

```java
public class FileUploadResponse {
    String fileHandleId;
    String fileName;
    String contentType;
    StorageType storageType;
    FileUploadStatus uploadStatus;
    String uploadUrl;           // presigned URL — for the local backend, the relative storage path
    long uploadUrlExpiresAt;    // epoch millis
    long createdAt;             // epoch millis
}
```

### FileUploadUrlResponse

```java
public class FileUploadUrlResponse {
    String fileHandleId;
    String uploadUrl;
    long expiresAt;             // epoch millis
}
```

### FileDownloadUrlResponse

```java
public class FileDownloadUrlResponse {
    String fileHandleId;
    String downloadUrl;
    long expiresAt;             // epoch millis
}
```

### FileUploadCompleteResponse

```java
public class FileUploadCompleteResponse {
    String fileHandleId;
    FileUploadStatus uploadStatus;
    String contentHash;         // from storage provider — null for local backend
}
```

### MultipartInitResponse

```java
public class MultipartInitResponse {
    String fileHandleId;
    String uploadId;            // backend-specific multipart upload ID
    String uploadUrl;           // resumable URL (GCS/Azure) — null for S3 (uses per-part URLs)
}
```

- **S3**: `uploadUrl` is null — SDK requests per-part presigned URLs via the per-part endpoint
- **GCS**: `uploadUrl` is a resumable session URI — SDK reuses for all parts with byte-range headers
- **Azure**: `uploadUrl` is a SAS-token URL — SDK reuses for all parts with block IDs

Part size is chosen client-side; the server does not return one.

### MultipartCompleteRequest

```java
public class MultipartCompleteRequest {
    List<String> partETags;
}
```

### FileIdToFileHandleIdConverter

Static helpers between the bare `fileId` (URL path params, `FileModel`, DAO/service params) and the prefixed `fileHandleId` (`conductor://file/<fileId>`) used in JSON DTOs.

```java
public final class FileIdToFileHandleIdConverter {
    public static final String PREFIX = "conductor://file/";
    public static String toFileHandleId(String fileId);
    public static String toFileId(String value);
    public static boolean isFileHandleId(Object value);
}
```

Server-only. The SDK exposes equivalent helpers as static methods on the `FileHandler` interface.

## Interfaces

### FileStorage

Server-side storage abstraction. One implementation per backend.

Location: `core` module — `org.conductoross.conductor.core.storage`

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

Location: `core` module — `org.conductoross.conductor.core.storage`

No `deleteFile()` — files must not be deleted after workflow execution ends (see Principles & Constraints).

Follows the existing backend module pattern from `ExternalPayloadStorage` — per-backend modules with
`@Configuration` + `@ConditionalOnProperty` + `@ConfigurationProperties`. (`ExternalPayloadStorage` itself is in
`common`; `FileStorage` is in `core` because only the server needs it — the SDK uses `FileStorageBackend` instead.)
**BYOS** supported — any custom implementation of this interface can be plugged in.

### WorkflowFamilyResolver

Resolves a caller's workflow family — self, ancestors (via `parentWorkflowId` chain), and descendants (walked via SUB_WORKFLOW tasks). Used by `FileStorageService.getDownloadUrl()` to gate access.

Location: `core` module — `org.conductoross.conductor.core.storage`

```java
public interface WorkflowFamilyResolver {
    Set<String> getFamily(String workflowId);
}
```

Default impl `WorkflowFamilyResolverImpl` walks the parent chain via `ExecutionDAO.getWorkflow(...).getParentWorkflowId()` and walks SUB_WORKFLOW children via `TaskModel.getSubWorkflowId()` — works on every backend (no parent-id query needed).

## Service

### FileStorageService

Location: `core` module — `org.conductoross.conductor.core.storage`

Orchestrates file metadata DAO + storage backend. Injected into `FileResource` controller. All methods take the bare `fileId` (no prefix); responses carry the prefixed `fileHandleId`.

```java
@Validated
public interface FileStorageService {

    FileUploadResponse createFile(@NotNull @Valid FileUploadRequest request);

    FileUploadUrlResponse getUploadUrl(@NotEmpty String fileId);

    FileUploadCompleteResponse confirmUpload(@NotEmpty String fileId);

    FileDownloadUrlResponse getDownloadUrl(@NotEmpty String fileId, @NotNull String workflowId);

    FileHandle getFileMetadata(@NotEmpty String fileId);

    // Multipart
    MultipartInitResponse initiateMultipartUpload(@NotEmpty String fileId);

    FileUploadUrlResponse getPartUploadUrl(@NotEmpty String fileId, @NotEmpty String uploadId, int partNumber);

    FileUploadCompleteResponse completeMultipartUpload(@NotEmpty String fileId, @NotEmpty String uploadId, @NotNull List<String> partETags);
}
```

Implementation: `FileStorageServiceImpl` in `core` module.

- Guards `workflowId` is present (throws `IllegalArgumentException` otherwise)
- Generates fileId (random UUID); storage path: `conductor/<workflowId>/<fileId>`
- Delegates presigned URL generation to `FileStorage`
- Generates fresh presigned URLs on each request — no caching
- On `getDownloadUrl()`: file must be `UPLOADED` and own a `workflowId`; resolves caller family via `WorkflowFamilyResolver` and throws `AccessForbiddenException` if the file's `workflowId` is not in the family
- On `confirmUpload()`: single call to `FileStorage.getStorageFileInfo()` — verifies file exists, reads
  content hash + actual size from storage provider in one round-trip. Persists both on `FileModel`
  (`storageContentHash`, `storageContentSize`). Returns 409 if status is already `UPLOADED`.
- Persists file metadata via `FileMetadataDAO`

## DAO

### FileMetadataDAO

Location: `core` module — `org.conductoross.conductor.dao`

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
| SQLite | `sqlite-persistence` | `@ConditionalOnProperty(name = "conductor.db.type", havingValue = "sqlite")` + file-storage enabled | Extends `SqliteBaseDAO` | `@Bean` + `@DependsOn({"flywayForPrimaryDb"})` in `SqliteConfiguration` |
| Redis | `redis-persistence` | `@Conditional(AnyRedisCondition.class)` + file-storage enabled | Extends `BaseDynoDAO` | `@Component` on DAO class directly |
| Cassandra | `cassandra-persistence` | `@ConditionalOnProperty(name = "conductor.db.type", havingValue = "cassandra")` + file-storage enabled | Extends `CassandraBaseDAO` | `@Bean` in `CassandraConfiguration` |

**Storage patterns per backend type:**

- **Relational (Postgres, MySQL, SQLite):** typed columns, `queryWithTransaction()` / `executeWithTransaction()`, `RetryTemplate` + `ObjectMapper` + `DataSource` injection, Flyway migration. SQLite is a single class extending `SqliteBaseDAO` directly (no facade/sub-DAO split).
- **Redis:** Hash (`hset`/`hget`) keyed by file ID, JSON string values, key namespace via `nsKey()`
- **Cassandra:** Dedicated table with prepared statements, JSON string column, consistency level config

**Stubs:** `StubFileMetadataDAO` — in-memory `ConcurrentHashMap` for testing.

### Audit & Tracking

- **Background audit workflow** — detect orphaned files with PENDING/UPLOADING status that were never completed.
  Consider a background process that scans for stale uploads and marks them FAILED.
- **Storage usage tracking** — should be able to identify how much storage is used per workflow (and per customer if
  multi-tenancy is supported). Query by `workflow_id` in `file_metadata` table.

### FileModel

Location: `core` module — `org.conductoross.conductor.model`

```java
public class FileModel {
    String fileId;              // bare id — wrapped as conductor://file/<fileId> on the wire
    String fileName;
    String contentType;

    // Hash from storage provider (S3 ETag / Azure Content-MD5 / GCS md5Hash) — set on upload-complete.
    // Never computed by the server; null for the local backend.
    String storageContentHash;
    long storageContentSize;    // actual bytes on storage — set on upload-complete
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
    file_id              VARCHAR(255)  NOT NULL PRIMARY KEY,
    file_name            VARCHAR(1024) NOT NULL,
    content_type         VARCHAR(255)  NOT NULL,
    storage_content_hash VARCHAR(255),
    storage_content_size BIGINT,
    storage_type         VARCHAR(50)   NOT NULL,
    storage_path         VARCHAR(2048) NOT NULL,
    upload_status        VARCHAR(50)   NOT NULL DEFAULT 'UPLOADING',
    workflow_id          VARCHAR(255)  NOT NULL,
    task_id              VARCHAR(255),
    created_at           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_file_metadata_workflow_id   ON file_metadata (workflow_id);
CREATE INDEX idx_file_metadata_task_id       ON file_metadata (task_id);
CREATE INDEX idx_file_metadata_upload_status ON file_metadata (upload_status);
```

### Migration / Schema Setup

**Flyway migrations (relational):**

- Postgres: `postgres-persistence/src/main/resources/db/migration_postgres/V15__file_metadata.sql`
- MySQL: `mysql-persistence/src/main/resources/db/migration/V9__file_metadata.sql`
- SQLite: `sqlite-persistence/src/main/resources/db/migration_sqlite/V3__file_metadata.sql`

**Redis:** No migration. Schema is implicit in DAO code — hash keys created on first write.

**Cassandra:** Table creation via CQL in DAO initialization or setup script — follows existing Cassandra
module pattern.

## Storage Backends

Each backend implements `FileStorage` interface. Follows existing `ExternalPayloadStorage` pattern:
`@Configuration` + `@ConditionalOnProperty` + `@ConfigurationProperties`. All backend beans **additionally**
require `@ConditionalOnProperty(name = "conductor.file-storage.enabled", havingValue = "true")` — no storage
beans created when feature is disabled.

### Local File Storage (default)

- No presigned URLs — server returns the local storage path (e.g. `conductor/<workflowId>/<fileId>`)
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

New file-storage code lives under the `org.conductoross.conductor.*` package root. Redis and Cassandra DAOs stay under their existing `com.netflix.conductor.<db>.dao` roots — no package move there.

### `common` module

```
common/src/main/java/org/conductoross/conductor/model/file/
    FileHandle.java
    FileUploadRequest.java
    FileUploadResponse.java
    FileUploadUrlResponse.java
    FileDownloadUrlResponse.java
    FileUploadCompleteResponse.java
    MultipartInitResponse.java
    MultipartCompleteRequest.java
    StorageType.java                    ← enum (defined again on the SDK side)
    FileUploadStatus.java               ← enum (defined again on the SDK side)
    FileIdToFileHandleIdConverter.java
```

### `core` module

```
core/src/main/java/org/conductoross/conductor/
    core/storage/
        FileStorage.java                    ← interface
        StorageFileInfo.java                ← value object returned by FileStorage.getStorageFileInfo()
        FileStorageService.java             ← interface
        FileStorageServiceImpl.java
        FileStorageProperties.java          ← @ConfigurationProperties
        WorkflowFamilyResolver.java         ← interface
        WorkflowFamilyResolverImpl.java
        converter/
            FileModelConverter.java         ← FileUploadRequest→FileModel, FileModel→FileHandle, FileModel→FileUploadResponse
    core/exception/
        FileStorageException.java           ← extends NonTransientException
    dao/
        FileMetadataDAO.java                ← interface
    model/
        FileModel.java
```

### `rest` module

```
rest/src/main/java/org/conductoross/conductor/controllers/
    FileResource.java                       ← @RestController
```

### `awss3-storage` module (existing)

```
awss3-storage/src/main/java/org/conductoross/conductor/s3/
    config/
        S3FileStorageConfiguration.java
        S3FileStorageProperties.java
    storage/
        S3FileStorage.java                  ← implements FileStorage
```

### `azureblob-storage` module (existing)

```
azureblob-storage/src/main/java/org/conductoross/conductor/azureblob/
    config/
        AzureBlobFileStorageConfiguration.java
        AzureBlobFileStorageProperties.java
    storage/
        AzureBlobFileStorage.java           ← implements FileStorage
```

### `gcs-storage` module (new)

```
gcs-storage/src/main/java/org/conductoross/conductor/gcs/
    config/
        GcsFileStorageConfiguration.java
        GcsFileStorageProperties.java
    storage/
        GcsFileStorage.java                 ← implements FileStorage
```

### `local-file-storage` module (new)

```
local-file-storage/src/main/java/org/conductoross/conductor/local/
    config/
        LocalFileStorageConfiguration.java
        LocalFileStorageProperties.java
    storage/
        LocalFileStorage.java               ← implements FileStorage
```

### `postgres-persistence` module (existing)

```
postgres-persistence/src/main/java/org/conductoross/conductor/postgres/dao/
    PostgresFileMetadataDAO.java            ← extends PostgresBaseDAO, implements FileMetadataDAO
postgres-persistence/src/main/resources/db/migration_postgres/
    V15__file_metadata.sql
```

Bean: add to `PostgresConfiguration` with `@DependsOn({"flywayForPrimaryDb"})`

### `mysql-persistence` module (existing)

```
mysql-persistence/src/main/java/org/conductoross/conductor/mysql/dao/
    MySQLFileMetadataDAO.java               ← extends MySQLBaseDAO, implements FileMetadataDAO
mysql-persistence/src/main/resources/db/migration/
    V9__file_metadata.sql
```

Bean: add to `MySQLConfiguration` with `@DependsOn({"flyway", "flywayInitializer"})`

### `sqlite-persistence` module (existing)

```
sqlite-persistence/src/main/java/org/conductoross/conductor/sqlite/dao/
    SqliteFileMetadataDAO.java              ← extends SqliteBaseDAO, implements FileMetadataDAO (single class — no facade/sub-DAO)
sqlite-persistence/src/main/resources/db/migration_sqlite/
    V3__file_metadata.sql
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

Location: `conductor-client` — `org.conductoross.conductor.sdk.file`

```java
public interface FileHandler {

    String PREFIX = "conductor://file/";

    String getFileHandleId();   // returns conductor://file/<fileId>; null for a not-yet-uploaded LocalFileHandler

    InputStream getInputStream();

    String getFileName();
    String getContentType();
    long getFileSize();

    default String getFileId() { return toFileId(getFileHandleId()); }  // strips prefix

    static FileHandler fromLocalFile(Path path);
    static FileHandler fromLocalFile(Path path, String contentType);

    // Static helpers — equivalent to the server's FileIdToFileHandleIdConverter
    static String toFileHandleId(String fileId);
    static String toFileId(String fileHandleId);
    static boolean isFileHandleId(Object value);
    static String extractFileHandleId(Object value);   // accepts string or {fileHandleId,...} JSON object
}
```

Annotated with `@JsonSerialize(using = FileHandlerSerializer.class)` / `@JsonDeserialize(using = FileHandlerDeserializer.class)` so Jackson always emits/accepts the `{fileHandleId, contentType, fileName}` shape.

- `getInputStream()` — lazy download on first call; returns new `InputStream` from cached local file on subsequent calls; blocks if download is in progress (lock); applies retry; throws `FileStorageException` on failure
- `fromLocalFile()` — wraps a local file for upload; no network call; no file handle id assigned yet

`FileStorageException` — SDK-side unchecked exception for all file-storage errors (download failure, storage mismatch, missing file, etc.). In `org.conductoross.conductor.sdk.file`. (Distinct from the server-side `FileStorageException` in `org.conductoross.conductor.core.exception` — same name, different class.)

### FileUploader

Developer-facing upload API. Implemented by `WorkflowFileClient`. Inside a worker, obtained via `task.getFileUploader()`.

Location: `conductor-client` — `org.conductoross.conductor.sdk.file`

```java
public interface FileUploader {

    FileHandler upload(Path localFile);
    FileHandler upload(InputStream inputStream);

    FileHandler upload(Path localFile, FileUploadOptions options);
    FileHandler upload(InputStream inputStream, FileUploadOptions options);
}
```

`workflowId` is bound at the `WorkflowFileClient` level — it is not a per-call argument here. Other metadata (`fileName`, `contentType`, `taskId`, `multipart`) is carried on `FileUploadOptions`. Default `contentType` is `application/octet-stream`; default `fileName` is the path's file name; `taskId` defaults from the active `TaskContext` when unset.

### FileUploadOptions

Optional metadata for a file upload. All fields default to `null`/`false`.

```java
public class FileUploadOptions {
    String taskId;
    String fileName;
    String contentType;
    boolean multipart;     // opt-in; SDK chunks the file when true and the backend supports multipart

    // fluent setters return `this`
}
```

## SDK-Internal Classes

### FileClient

Does **not** implement `FileUploader` — callers go through `WorkflowFileClient`. `FileClient` takes `workflowId` as the explicit first argument on every method. Composes `ConductorClient` and a `Map<StorageType, FileStorageBackend>` populated with the four built-in backends (LOCAL/S3/AZURE_BLOB/GCS); on upload, the server-reported storage type must be in the map or the client throws `FileStorageException`.

Location: `conductor-client` — `org.conductoross.conductor.client`

```java
public class FileClient {

    public FileClient(ConductorClient client);
    public FileClient(ConductorClient client, FileClientProperties properties,
                      Map<StorageType, FileStorageBackend> backends);

    // developer-facing (via WorkflowFileClient decorator)
    public FileHandler upload(String workflowId, Path localFile);
    public FileHandler upload(String workflowId, Path localFile, FileUploadOptions options);
    public FileHandler upload(String workflowId, InputStream inputStream);
    public FileHandler upload(String workflowId, InputStream inputStream, FileUploadOptions options);

    // SDK-internal — used by ManagedFileHandler
    public void download(String workflowId, String fileHandleId, StorageType storageType, Path destination);
    public FileHandle getMetadata(String fileHandleId);

    // ... plus internal multipart helpers (initiate / part-url / complete) and confirmUpload
}
```

Builder: `FileClient.builder(client).addStorageBackend(extra).build()`. The four built-in backends are always registered; `addStorageBackend` overrides one for that `StorageType`.

Fits into `OrkesClients` as a no-arg factory: `orkesClients.getFileClient()` — returns a plain `FileClient`, no `OrkesFileClient` subclass.

### WorkflowFileClient

Decorator over `FileClient` that binds a `workflowId` for the duration of a worker invocation. Implements `FileUploader`. Created by `TaskRunner` and stored on the `Task` so workers reach it via `task.getFileUploader()`.

Location: `conductor-client` — `org.conductoross.conductor.sdk.file`

```java
public class WorkflowFileClient implements FileUploader {
    public WorkflowFileClient(FileClient delegate, String workflowId);

    public FileHandler upload(Path localFile);
    public FileHandler upload(InputStream inputStream);
    public FileHandler upload(Path localFile, FileUploadOptions options);
    public FileHandler upload(InputStream inputStream, FileUploadOptions options);

    public void download(String fileHandleId, StorageType storageType, Path destination);
    public FileHandle getMetadata(String fileHandleId);
}
```

### LocalFileHandler

Public class with a package-private constructor. Returned by `FileHandler.fromLocalFile(path)`. Wraps a local file before upload — no file handle id, no network call, no server interaction. `getFileHandleId()` returns null until the handle is replaced after upload by a `ManagedFileHandler`. `TaskRunner.uploadFilesToFileStorage` casts to `LocalFileHandler` to read `getPath()` for auto-upload, which is why the class is public.

### ManagedFileHandler

Public class implementing `FileHandler`. Holds the prefixed `fileHandleId` plus a reference to a `WorkflowFileClient`; downloads content lazily on first `getInputStream()` call and caches it under `<localCacheDirectory>/<fileId>_<fileName>`. Thread-safe via a `ReentrantLock`.

Location: `conductor-client` — `org.conductoross.conductor.sdk.file`

```java
public class ManagedFileHandler implements FileHandler {
    String fileHandleId;        // conductor://file/<fileId>
    String fileName;
    String contentType;
    long fileSize;              // populated from local file at upload time, or fetched from server lazily
    StorageType storageType;
    Path localPath;             // cached local file after download
    FileDownloadStatus downloadStatus;
    WorkflowFileClient workflowFileClient;  // for lazy download, metadata
    // ReentrantLock for concurrent download
}
```

Package-private setters (`setFileName`, `setContentType`, `setFileSize`, `setStorageType`, `setLocalPath`) let `FileHandlerConverter` populate metadata immediately after upload — skipping the lazy server fetch.

### FileStorageBackend

Actual file transfer to/from storage. One implementation per backend.

Location: `conductor-client` — `org.conductoross.conductor.sdk.file`

```java
public interface FileStorageBackend {

    StorageType getStorageType();

    void upload(String url, Path localFile);
    void upload(String url, InputStream inputStream, long contentLength);
    void download(String url, Path destination);

    /** Upload a single part to the given presigned URL. Returns the part's ETag. */
    String uploadPart(String url, Path localFile, long offset, long length);

    /** Whether this backend supports multipart upload. Defaults to true. */
    default boolean hasMultipartSupport() { return true; }
}
```

`getStorageType()` keys the `Map<StorageType, FileStorageBackend>` on `FileClient`; an upload whose server-reported type is missing from the map throws `FileStorageException` — fail fast, no silent corruption.

Implementations (all in `conductor-client` module, `org.conductoross.conductor.client.storage`):
- `S3FileStorageBackend` — uses presigned URLs with HTTP PUT/GET
- `AzureFileStorageBackend` — uses SAS token URLs
- `GcsFileStorageBackend` — uses signed URLs
- `LocalFileStorageBackend` — writes/reads directly to/from the configured local directory using the relative storage path returned by the server

### FileHandlerSerializer / FileHandlerDeserializer

Jackson serializer + deserializer registered on the `FileHandler` interface via `@JsonSerialize` / `@JsonDeserialize`. The serializer emits a fixed three-field object `{fileHandleId, contentType, fileName}`; the deserializer accepts either that object or a bare `conductor://file/<fileId>` string and returns a `ManagedFileHandler` bound to the active `WorkflowFileClient` (read from the `DeserializationContext` attribute that `AnnotatedWorker` populates before deserializing POJO inputs).

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
- `AnnotatedWorker.getInputValue()` — accept either a `conductor://file/<fileId>` string or the serialized `{fileHandleId, contentType, fileName}` JSON object (via `FileHandler.extractFileHandleId(...)`), and wrap as `ManagedFileHandler` bound to `task.getWorkflowFileClient()`. For nested `FileHandler` fields inside a POJO `@InputParam`, `FileHandlerDeserializer` handles the same resolution during ObjectMapper deserialization.
- `AnnotatedWorker.setValue()` — when the return value is a `FileHandler`, store it directly in `outputData`; `TaskRunner` performs the upload-and-replace.
- Spring auto-config creates a `FileClient` bean in `ConductorClientAutoConfiguration` (and in `OrkesConductorClientAutoConfiguration` for the Orkes path).

### Imperative

```java
FileClient fileClient = FileClient.builder(conductorClient).build();   // built-in backends registered

Worker worker = new DocumentWorker();
new TaskRunnerConfigurer.Builder(taskClient, List.of(worker))
        .withFileClient(fileClient)
        .build()
        .init();
```

```java
public class DocumentWorker implements Worker {

    public TaskResult execute(Task task) {
        TaskResult result = new TaskResult(task);
        FileHandler input = task.getInputFileHandler("inputFile");
        InputStream stream = input.getInputStream();
        // process...
        FileHandler output = task.getFileUploader().upload(Path.of("/data/result.pdf"));
        result.getOutputData().put("outputFile", output.getFileHandleId());
        return result;
    }
}
```

- `task.getInputFileHandler("key")` — reads `inputData.get("key")`, accepts either the prefixed string or the serialized JSON object, wraps as `ManagedFileHandler`. Throws if the value is neither.
- `task.getFileUploader()` — returns the `FileUploader` (a `WorkflowFileClient` bound to the active workflow's id). Set up by `TaskRunner` before `execute()`.
- `Task` holds a transient (non-serialized) `WorkflowFileClient` reference: `@JsonIgnore private transient WorkflowFileClient workflowFileClient;`. Workers don't reach into it directly; `getFileUploader()` and `getInputFileHandler(...)` are the public surface.

## Upload Strategies

### Explicit Upload

Developer calls `FileUploader.upload()` directly. Gets `FileHandler` back with `conductor://file/<fileId>`.

Used outside task context (e.g., before starting a workflow via a standalone `WorkflowFileClient` over `FileClient`) or when the developer wants explicit control inside a worker.

### Automatic Upload

Framework handles on task completion. Developer returns `FileHandler.fromLocalFile(path)` from `@WorkerTask` method
or puts it in `result.getOutputData()`.

Single interception point in `TaskRunner` — after `execute()`, before `updateTask()`:
- Scan `outputData` for `FileHandler` instances
- For any with `getFileHandleId() == null` (i.e. `LocalFileHandler`), upload via `FileClient.upload(workflowId, …)` and replace the entry with the resulting `FileHandler`
- Already-uploaded handlers are left in place; `FileHandlerSerializer` renders the `{fileHandleId, contentType, fileName}` JSON object on the wire

Changes: `TaskRunner` (scan loop + `FileClient` param), `TaskRunnerConfigurer` (pass `FileClient`),
`TaskRunnerConfigurer.Builder` (`withFileClient()`).

May drop automatic upload after initial implementation — kept isolated for clean removal.

## Configuration (SDK)

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `conductor.file-client.retry-count` | int | `3` | Upload/download retry count |
| `conductor.file-client.multipart-part-size` | long | `10485760` | Multipart upload part size in bytes (10 MiB; S3 minimum) |
| `conductor.file-client.local-cache-directory` | String | `${java.io.tmpdir}/conductor/files-cache` | Client-side download cache for files fetched from S3/GCS/Azure |

Multipart is opt-in per upload via `FileUploadOptions.setMultipart(true)` — there is no automatic file-size threshold. Precedence: code defaults → config file → env var.

## Module Placement (SDK)

### `conductor-client` module

DTOs and the `FileClient` live under the `org.conductoross.conductor.client.*` root; everything developer-facing lives under `org.conductoross.conductor.sdk.file`.

```
conductor-client/src/main/java/org/conductoross/conductor/
    client/
        FileClient.java                     ← does NOT implement FileUploader; takes workflowId per call
        FileClientProperties.java
        model/file/                         ← SDK-side DTO mirrors
            FileHandle.java
            FileUploadRequest.java
            FileUploadResponse.java
            FileUploadUrlResponse.java
            FileDownloadUrlResponse.java
            FileUploadCompleteResponse.java
            MultipartInitResponse.java
            MultipartCompleteRequest.java
            StorageType.java                ← enum
            FileUploadStatus.java           ← enum
        storage/                            ← FileStorageBackend implementations
            S3FileStorageBackend.java
            AzureFileStorageBackend.java
            GcsFileStorageBackend.java
            LocalFileStorageBackend.java
            HttpBodies.java                 ← shared HTTP helper
    sdk/file/
        FileHandler.java                    ← public interface (Jackson serdes registered)
        FileUploader.java                   ← public interface
        FileStorageBackend.java             ← public interface
        FileDownloadStatus.java             ← enum
        FileStorageException.java           ← unchecked, SDK-side
        FileUploadOptions.java
        WorkflowFileClient.java             ← implements FileUploader; binds workflowId
        ManagedFileHandler.java             ← public class, package-private constructor
        LocalFileHandler.java               ← public class, package-private constructor (from fromLocalFile())
        FileHandlerConverter.java           ← FileUploadResponse→ManagedFileHandler, Path→FileUploadRequest
        FileHandlerSerializer.java          ← Jackson serializer (emits {fileHandleId, contentType, fileName})
        FileHandlerDeserializer.java        ← Jackson deserializer (accepts string or object)

src/main/java/io/orkes/conductor/client/
    OrkesClients.java                       ← add getFileClient() (no-arg)
```

There is no `OrkesFileClient` subclass — `OrkesClients.getFileClient()` returns a plain `FileClient`.

### `conductor-client-spring` module

```
conductor-client-spring/src/main/java/com/netflix/conductor/client/spring/
    ConductorClientAutoConfiguration.java          ← add FileClientProperties + FileClient beans (modify)
conductor-client-spring/src/main/java/io/orkes/conductor/client/spring/
    OrkesConductorClientAutoConfiguration.java     ← add FileClient bean (modify)
```

Spring auto-config creates a `FileClient` bean composing `ConductorClient` and the four built-in `FileStorageBackend`s (LOCAL, S3, AZURE_BLOB, GCS) — no per-backend Spring beans are registered. The conditions are `@ConditionalOnBean(ConductorClient.class)` (or `ApiClient.class` on the Orkes side) and `@ConditionalOnMissingBean`.

---

# File Operations

## Upload Flow

1. Developer creates `FileHandler.fromLocalFile(path)` — wraps local file, no network call
2. On task completion (automatic) or explicit `FileUploader.upload()`:
    1. SDK calls `POST /api/files` with file metadata → server creates record with status UPLOADING (URL generated
       eagerly), returns `FileUploadResponse` with presigned upload URL and `conductor://file/<fileId>`
    2. SDK uploads file to storage backend using presigned URL via `FileStorageBackend` (single PUT, or multipart if `FileUploadOptions.multipart=true` — see below)
    3. On URL expiry: SDK calls `GET /api/files/{fileId}/upload-url` to fetch a fresh URL
    4. SDK calls `POST /api/files/{fileId}/upload-complete` → server verifies file on storage, reads
       content hash from storage provider (S3 ETag, Azure Content-MD5, GCS md5Hash), persists hash,
       marks UPLOADED
3. SDK returns `FileHandler` with `conductor://file/<fileId>` to developer

## Download Flow

1. Workflow input passes either `conductor://file/<fileId>` strings or `{fileHandleId, contentType, fileName}` objects
2. SDK detects either form (via `FileHandler.extractFileHandleId(...)`) and wraps as `ManagedFileHandler` bound to the active `WorkflowFileClient`
3. Developer receives `FileHandler` via `@InputParam` (handled by `FileHandlerDeserializer`) or `task.getInputFileHandler("key")`
4. File downloaded only when `fileHandler.getInputStream()` is called:
    1. SDK calls `GET /api/files/{fileId}` — gets metadata including storage type
    2. SDK calls `GET /api/files/{workflowId}/{fileId}/download-url` — gets presigned download URL (`workflowId` is the active workflow's id; server enforces family-scoped access)
    3. SDK looks up the matching `FileStorageBackend` by storage type and downloads to local cache; missing backend → `FileStorageException`
    4. Returns `InputStream` from cached local file
5. Subsequent calls return new `InputStream` from same cached file — no re-download
6. Concurrent calls block on lock until first download completes

## Presigned URLs

- Created by Conductor server only
- Short-lived — configurable TTL (default 60s)
- New URL fetched on every server call — no caching
- Per-backend mechanism:
    - AWS S3: presigned URLs via `S3Presigner`
    - Azure Blob: SAS tokens
    - GCS: signed URLs
    - Local: relative storage path (not a URL); SDK's `LocalFileStorageBackend` resolves it against the configured directory

## Multipart Upload & Download

- Multipart upload is **opt-in** per call via `FileUploadOptions.setMultipart(true)`; otherwise the SDK uploads in a single PUT. There is no automatic file-size threshold and no server-side max-file-size cap in this iteration.
- Download uses HTTP range requests within `FileStorageBackend.download()` — no server-side multipart coordination needed. The SDK handles chunked download as an internal implementation detail.
- Multipart upload flow:
    1. SDK calls `POST /api/files/{fileId}/multipart` → server initiates multipart on storage backend, returns upload ID and (for GCS/Azure) a resumable URL. Part size is **not** returned — the SDK uses its own `conductor.file-client.multipart-part-size` config (default 10 MiB).
    2. Per-part upload — branch on `init.getUploadUrl() != null`:
        - **S3** (uploadUrl is null): SDK calls `GET .../multipart/{uploadId}/part/{partNumber}` per part → gets per-part presigned URL, uploads via `FileStorageBackend.uploadPart()` → returns ETag
        - **GCS/Azure** (uploadUrl is the resumable URL): SDK reuses that URL for all parts — no per-part server calls
    3. SDK calls `POST .../multipart/{uploadId}/complete` with all part ETags → server finalizes on storage backend, verifies via `getStorageFileInfo()`, persists hash and size, marks UPLOADED
- S3 per-part presigned URLs are generated fresh per request

## Failure & Retry

### Upload Failure

- SDK reuses same `conductor://file/<fileId>` — does not create new file metadata
- Requests new presigned URL if expired
- A bulk retry of the upload itself is the worker's responsibility today; the SDK only retries the download path internally

### Download Failure

- `ManagedFileHandler.ensureDownloaded()` retries automatically
- Requests new presigned URL on each attempt
- Default: 3 retries, configurable via `conductor.file-client.retry-count`

## Status Tracking

### Upload Status (server + worker)

```
UPLOADING → UPLOADED
         → FAILED
```

- `POST /api/files` creates file with status UPLOADING (URL generated eagerly, upload expected immediately)
- `POST .../upload-complete` transitions to UPLOADED
- FAILED set by background audit when an UPLOADING record remains stale
- PENDING reserved for future use (batch registration, deferred upload) — not entered by the runtime flow
- Persisted on server in `file_metadata` table

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

1. **Crash during file upload** — what happens to the `conductor://file/<fileId>`? Can the SDK resume or must it restart?
2. **Temp file missing from disk** — developer or OS deletes cached file. What happens on next `getInputStream()`?
3. **File never fully uploaded** — server has UPLOADING status but upload never completed. How is this detected?
   What does a downstream task see?
4. **File handler reuse on same worker** — same `conductor://file/<fileId>` used by two tasks on same worker. Does it re-download?
5. **File access across workflow boundaries** — workflow A uploads a file, a worker in workflow B requests its download URL. When is this allowed?
6. **Presigned URL expires during multipart upload** — mid-upload, the URL expires. What happens?
7. **Storage backend mismatch** — server configured for S3 but SDK has no S3 backend registered. When is this detected? What error does the developer see?
8. **Concurrent uploads to same file ID** — two workers try to upload to the same `conductor://file/<fileId>`. What happens?
9. **Worker restart mid-download** — worker crashes during file download. On restart, the task is retried. Does it re-download from scratch?

---

# Documentation Placement

Conductor docs use MkDocs Material. Existing nav structure in `mkdocs.yml`. None of these pages exist yet — the only file-storage docs in the repo are under `docs/file-storage/`. Treat the table below as the target placement once the user-facing docs are written.

| Document | Target Location | Description |
|----------|----------------|-------------|
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
