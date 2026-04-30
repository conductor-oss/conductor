# Spec Outdated Items Report

> **Status (2026-04-30):** all 33 findings below have been applied to `spec.md`. Kept for the audit trail; treat the spec as the source of truth from here on.

Compares `docs/file-storage/spec.md` against the implementation on `feature/file-storage-wip` (server in `file-storage-conductor`, SDK in `file-storage-java-sdk`). Many findings overlap with the design doc; this report focuses on items unique to the spec or that read from the spec's POV.

---

## 1. Package roots — entire spec uses `com.netflix.conductor.*`, code is `org.conductoross.conductor.*`

The spec consistently writes packages under `com.netflix.conductor.*`. New file-storage code is under `org.conductoross.conductor.*`. Affects every "Module Placement" path and every code-block `package` line.

| Where the spec says | Code actually has |
|---|---|
| `com.netflix.conductor.common.run` | `org.conductoross.conductor.model.file` |
| `com.netflix.conductor.core.storage` | `org.conductoross.conductor.core.storage` |
| `com.netflix.conductor.dao` | `org.conductoross.conductor.dao` |
| `com.netflix.conductor.model` | `org.conductoross.conductor.model` |
| `com.netflix.conductor.rest.controllers` | `org.conductoross.conductor.controllers` |
| `com.netflix.conductor.s3.config` / `…s3.storage` | `org.conductoross.conductor.s3.config` / `…s3.storage` |
| `com.netflix.conductor.azureblob.*` | `org.conductoross.conductor.azureblob.*` |
| `com.netflix.conductor.gcs.*` | `org.conductoross.conductor.gcs.*` |
| `com.netflix.conductor.local.*` | `org.conductoross.conductor.local.*` |
| `com.netflix.conductor.postgres.dao` | `org.conductoross.conductor.postgres.dao` |
| `com.netflix.conductor.mysql.dao` | `org.conductoross.conductor.mysql.dao` |
| `com.netflix.conductor.sqlite.dao` | `org.conductoross.conductor.sqlite.dao` |
| Redis / Cassandra DAOs | stay under `com.netflix.conductor.<db>.dao` (not migrated) |
| `com.netflix.conductor.sdk.file` | `org.conductoross.conductor.sdk.file` |
| `com.netflix.conductor.sdk.file.storage` | `org.conductoross.conductor.client.storage` (note: parent moved from `sdk.file` to `client`) |
| `com.netflix.conductor.client.http.FileClient` | `org.conductoross.conductor.client.FileClient` (no `http` subpackage) |

---

## 2. DTOs — spec calls for proto annotations, code is plain Java

Spec line 354: "Follows existing common module convention: `@ProtoMessage`/`@ProtoField` annotations on DTOs, `@ProtoEnum` on enums, manual getters/setters, `equals()`/`hashCode()`, `toString()`."

Actual: plain Java classes (no proto annotations) in `org.conductoross.conductor.model.file` (server `common` module) and `org.conductoross.conductor.client.model.file` (SDK `conductor-client`). Drop the proto-annotation guidance.

---

## 3. `StorageType` and `FileUploadStatus` — not actually shared

- Spec lines 358–377: "Shared enum — used by both server and SDK." Implies one definition both sides import.
- Actual: defined twice — once in `org.conductoross.conductor.model.file` (server `common`), once in `org.conductoross.conductor.client.model.file` (SDK). Server and SDK have no shared common module.

---

## 4. `FileHandle` DTO drift

Spec (lines 384–396):
```java
String fileId;              // conductor://file/uuid
…
Instant createdAt;
Instant updatedAt;
```

Actual:
- Field is **`fileHandleId`**, not `fileId`. The bare id (no prefix) lives only on `FileModel`; the wire DTO carries the prefixed string.
- **No `fileSize`** field on the server-side DTO. (SDK-side mirror still has `long fileSize` — populated from local file at upload time.)
- `createdAt` / `updatedAt` are **`long` (epoch millis)**, not `Instant`. Same for every other DTO that exposes timestamps.
- Adds a `String contentHash` field — present in spec ✓.

---

## 5. `FileUploadRequest` — `fileSize` gone, `workflowId` required

Spec (lines 401–409):
```java
String fileName;
String contentType;
long fileSize;
String workflowId;         // optional
String taskId;             // optional
```

Actual:
- No `fileSize` field.
- `workflowId` is **required** (`@NotBlank`). `FileStorageServiceImpl.createFile()` throws `IllegalArgumentException("workflowId is required")` if missing.
- `fileName` and `contentType` are **optional** (no validation; SDK's `fillDefaults` fills them from path / `application/octet-stream` if absent).

---

## 6. `FileUploadResponse` / URL responses — `fileSize` gone, timestamps as long

Spec (lines 413–425):
```java
long fileSize;
Instant uploadUrlExpiresAt;
Instant createdAt;
```

Actual: no `fileSize`; `uploadUrlExpiresAt` and `createdAt` are `long` epoch millis. Same shift for `FileUploadUrlResponse.expiresAt` and `FileDownloadUrlResponse.expiresAt`.

The spec line 429 / 441 phrasing "null for local backend" is misleading: actual fields are primitive `long`, so they can't be null — they'll be 0 if unset.

---

## 7. `MultipartInitResponse.partSize` — removed

Spec (lines 463–470, JSON sample line 290):
```java
long partSize;              // recommended part size in bytes
```

Actual: no `partSize` field. The SDK chunks the file using its own `conductor.file-client.multipart-part-size` (default 10 MiB).

---

## 8. REST surface drift

| Spec | Actual |
|---|---|
| `GET /api/files/{fileId}/download-url` (line 250) | `GET /api/files/{workflowId}/{fileId}/download-url` — workflowId is part of the path because download is workflow-family-scoped |
| `POST /api/files` body includes `"fileSize"` (line 197) | No `fileSize` |
| Get File Metadata response includes `"fileSize"` (line 270) | No `fileSize` |
| MultipartInitResponse JSON sample includes `"partSize": 5242880` (line 293) | No `partSize` |
| `FileUploadResponse.uploadUrl` "null for local backend" (line 421 / 188) | Local backend returns the relative storage path (not null); the SDK's `LocalFileStorageBackend` resolves it against the configured directory |

---

## 9. Error Responses table — invented codes

Spec (lines 339–349) lists error codes like `INVALID_REQUEST`, `FILE_NOT_FOUND`, `ALREADY_UPLOADED`, etc., and a new `FileTooLargeException` (extends `NonTransientException`) for 413.

Actual:
- No file-storage-specific exception classes apart from a server-side `FileStorageException` (`extends NonTransientException`) — used internally for storage-backend failures, not surfaced as a distinct HTTP code. There is **no `FileTooLargeException`** and **no 413 path**; file-size validation is deferred.
- Errors reuse the existing Conductor mapping: `IllegalArgumentException → 400`, `NotFoundException → 404`, `ConflictException → 409`, `AccessForbiddenException → 403`, `NonTransientException → 500`. No structured error-code field — `ErrorResponse` carries `status / message / instance / retryable` only.

---

## 10. `FileStorageService` interface signature drift

Spec (lines 552–571):
```java
FileDownloadUrlResponse getDownloadUrl(String fileId);
```

Actual:
```java
FileDownloadUrlResponse getDownloadUrl(@NotEmpty String fileId, @NotNull String workflowId);
```

The caller-workflow is required (used for family-scoped access). All `String` params on the interface have `@NotEmpty`/`@NotNull` constraints; the spec doesn't show validation annotations.

Spec line 575 says the impl "Validates file size against configured maximum" — no such validation exists.
Spec line 577 says "Generates storage path: `files/<uuid>/<fileName>`" — actual is `conductor/<workflowId>/<fileId>` (workflowId in the path, no fileName).

---

## 11. `WorkflowFamilyResolver` not in spec

The implementation introduces a public interface `WorkflowFamilyResolver` and `WorkflowFamilyResolverImpl` (`core/.../core/storage/`) that resolves the caller's workflow family for download access control. Spec doesn't mention them at all. They should appear under "Interfaces" alongside `FileStorage`, and be reflected in module placement.

---

## 12. `FileModel` — `fileSize` removed, `Instant` retained internally

Spec (lines 645–668):
```java
long fileSize; // in bytes, set at creation time
String storageContentHash;
long storageContentSize;
…
Instant createdAt;
Instant updatedAt;
```

Actual:
- No `fileSize` field.
- `Instant createdAt` / `updatedAt` are kept on the **model** (the converter maps to epoch millis when filling DTOs). Spec's note "set at creation time" needs to come off `fileSize` since the field is gone.

---

## 13. Database schema — `file_size` column dropped

Spec (lines 674–693):
```sql
file_size       BIGINT NOT NULL,
```

Actual: column not present in any of the V15 (Postgres), V9 (MySQL), V3 (SQLite) migrations.

Migration version numbers match for Postgres/MySQL ✓. SQLite is **V3** (spec line 702 says "V<next>" — fill in V3).

---

## 14. DAO module placements

Spec line 619 table shows DAO base classes and bean registration patterns.

| Backend | Spec module path | Actual |
|---|---|---|
| Postgres | `postgres-persistence/.../com/netflix/conductor/postgres/dao/` | `…/org/conductoross/conductor/postgres/dao/` |
| MySQL | `…com/netflix/conductor/mysql/dao/` | `…/org/conductoross/conductor/mysql/dao/` |
| SQLite | `…com/netflix/conductor/sqlite/dao/` (line 633: facade/sub-DAO pattern) | `…/org/conductoross/conductor/sqlite/dao/SqliteFileMetadataDAO` — **single class, no facade**, extends `SqliteBaseDAO` directly |
| Redis | `redis-persistence` | stays at `com.netflix.conductor.redis.dao` ✓ |
| Cassandra | `cassandra-persistence` | stays at `com.netflix.conductor.cassandra.dao` ✓ |

Spec line 633 mandates the SQLite facade/`SqliteFileMetadataSubDAO` split — that pattern was not adopted; remove the bullet.

---

## 15. Configuration table — drop `max-file-size`

Spec (line 760):
```
| `conductor.file-storage.max-file-size` | DataSize | `5GB` | Max file size |
```

Actual: not present. `FileStorageProperties` carries only `enabled`, `type`, `signedUrlExpiration`. No `defaultWorkflowId` either (which appears in the design but never made the spec — fine here, but worth noting).

Spec line 761 (`signed-url-expiration` = 60s default) ✓.

---

## 16. SDK `FileHandler` — `getFileId()` return value, plus undocumented helpers

Spec (line 928):
```java
String getFileId();         // returns conductor://file/uuid
```

Actual: the developer-facing accessor is **`getFileHandleId()`** which returns the prefixed string. There is also a default `getFileId()` that returns the **bare** id (prefix stripped). The spec collapses these and uses the wrong name.

The interface also exposes static helpers (`PREFIX`, `toFileHandleId`, `toFileId`, `isFileHandleId`, `extractFileHandleId`) and Jackson `@JsonSerialize`/`@JsonDeserialize` annotations — none mentioned in the spec.

---

## 17. `FileUploader` — string-content-type overloads don't exist

Spec (lines 956–968):
```java
FileHandler upload(InputStream inputStream, String contentType);
FileHandler upload(Path localFile, String contentType);
```

Actual signatures:
```java
FileHandler upload(Path localFile);
FileHandler upload(InputStream inputStream);
FileHandler upload(Path localFile, FileUploadOptions options);
FileHandler upload(InputStream inputStream, FileUploadOptions options);
```

`FileUploadOptions` carries everything except `workflowId`. Spec doesn't mention `FileUploadOptions` at all.

Spec line 972: "Default content type: `application/octet-stream`." Still true ✓ (filled by `fillDefaults`).

---

## 18. `FileClient` shape and access path

Spec (lines 980–1009):
- "Implements `FileUploader`" (line 981) — actual: `FileClient` does **not** implement `FileUploader`. Developer-facing uploads go through `WorkflowFileClient` (a decorator that binds `workflowId`); `FileClient.upload(...)` requires `workflowId` as the first arg.
- Methods `download(fileId, destination)`, `getUploadUrl(fileId)`, `confirmUpload(fileId)` are listed (lines 989–1004) — actual signatures take more parameters: `download(workflowId, fileHandleId, storageType, destination)`; `getUploadUrl` is server-only (the SDK calls `POST /api/files/{fileId}/upload-url` indirectly through retry logic — there is no `getUploadUrl` SDK method).
- Builder shape (line 1008): `FileClient.builder().conductorClient(client).storageBackend(backend).build()` — actual: `FileClient.builder(client).addStorageBackend(...).build()`. `conductorClient` is a constructor arg, not a builder method.
- `OrkesClients.getFileClient(storageBackend)` (line 1010) — actual: `getFileClient()`, no parameter; `FileClient` registers the four built-in backends (LOCAL/S3/AZURE_BLOB/GCS) internally. There is no `OrkesFileClient` subclass either.

---

## 19. `WorkflowFileClient` not in spec

The SDK has a public class `org.conductoross.conductor.sdk.file.WorkflowFileClient` that wraps `FileClient` and binds `workflowId` for the duration of a worker invocation. Returned by `task.getFileUploader()`. Spec is silent on it; should be added under "SDK-Internal Classes".

---

## 20. `FileUploadOptions` not in spec

Required type for the multi-arg `upload(...)` overloads. Carries `taskId`, `fileName`, `contentType`, `multipart`. Should be a top-level entry in the SDK section.

---

## 21. `LocalFileHandler` / `ManagedFileHandler` visibility

Spec lines 1011, 1019: both labeled "Package-private."

Actual: both classes are **public** (so neighboring packages — `AnnotatedWorker`, `FileHandlerConverter`, `TaskRunner` — can construct, cast, and configure them). Constructors are package-private; `ManagedFileHandler` exposes package-private setters consumed by `FileHandlerConverter`.

Spec also says `ManagedFileHandler.fileClient: FileClient` (line 1031) — actual field is `WorkflowFileClient workflowFileClient`. Same drift as on `Task`.

---

## 22. `FileStorageBackend` — multi-backend now supported

Spec line 1066: "Single backend per client — multiple backends is a future improvement."

Actual: `FileClient` holds `Map<StorageType, FileStorageBackend>` populated with all four backends (LOCAL/S3/AZURE_BLOB/GCS) by default; the builder lets callers register more or override. The "single backend" caveat is no longer accurate.

The interface itself adds `default boolean hasMultipartSupport()` (defaults to true) — not in spec.

---

## 23. SDK backend module path

Spec (line 1068): "all in `conductor-client` module, `com.netflix.conductor.sdk.file.storage`".

Actual: `org.conductoross.conductor.client.storage` — different parent (`client.storage`, not `sdk.file.storage`). Includes a `HttpBodies` helper not mentioned in the spec.

---

## 24. Worker integration — `Task.fileClient` field name and type

Spec (lines 1106–1108):
- "Spring auto-config creates `FileClient` bean in `ConductorClientAutoConfiguration`" ✓.
- "`Task` holds transient (non-serialized) `FileClient` reference set by `TaskRunner`: `@JsonIgnore private transient FileClient fileClient;`" (line 1144) — actual field is `transient WorkflowFileClient workflowFileClient`, exposed to workers as `getFileUploader()` returning `FileUploader`.

Spec line 1094: workflow-input file flows as `inputData` "value is not a `conductor://file/` reference" — accurate, but the implementation also supports the serialized `{fileHandleId, contentType, fileName}` form (via `FileHandler.extractFileHandleId(...)` + `FileHandlerSerializer`), which is invisible in the spec.

---

## 25. SDK Configuration — `multipart-threshold` replaced

Spec (line 1175):
```
| `conductor.file-client.multipart-threshold` | long | `104857600` | Bytes above which multipart upload kicks in |
```

Actual: replaced by `conductor.file-client.multipart-part-size` (default 10 MiB / `10L * 1024 * 1024`). Multipart is no longer auto-triggered by file size; callers opt in via `FileUploadOptions.setMultipart(true)`.

---

## 26. SDK Module Placement (spec lines 1181–1217)

| Spec entry | Actual |
|---|---|
| `com.netflix.conductor.sdk.file/FileHandler.java`, `FileUploader.java`, etc. | `org.conductoross.conductor.sdk.file/…` |
| `com.netflix.conductor.sdk.file.converter/FileHandlerConverter.java` | `org.conductoross.conductor.sdk.file.FileHandlerConverter` (flat — no `.converter` subpackage) |
| `com.netflix.conductor.sdk.file.storage/…Backend.java` | `org.conductoross.conductor.client.storage/…Backend.java` |
| `client/http/FileClient.java` | `org.conductoross.conductor.client.FileClient` (top-level `client`, not `client.http`) |
| `OrkesFileClient.java extends FileClient (follows OrkesTaskClient pattern)` (line 1206) | **does not exist** — `OrkesClients.getFileClient()` returns plain `FileClient` |
| `OrkesClients.java add getFileClient(FileStorageBackend) (modify)` (line 1204) | `getFileClient()` no-arg |

Also missing from the placement listings:
- `FileHandlerSerializer` / `FileHandlerDeserializer` (Jackson serdes for `FileHandler`)
- `WorkflowFileClient`
- `FileUploadOptions`
- `HttpBodies`

---

## 27. File Operations — Download Flow URL

Spec (line 1244): "SDK calls `GET /api/files/{fileId}/download-url`".

Actual URL is `GET /api/files/{workflowId}/{fileId}/download-url`. Same divergence as REST surface drift (#8).

Spec line 1246: "SDK validates storage type matches configured backend — fail fast on mismatch" — implementation does this in `FileClient.upload(...)` (the `Map<StorageType, …>` lookup), not in the download path. The download path looks the backend up by storage type and will NPE if missing rather than throwing a typed error.

---

## 28. Multipart Upload Flow — server determines part size

Spec (line 1267): "server initiates multipart on storage backend, returns upload ID, part size, and optional resumable URL".
Spec (line 296) says: "Server determines part size based on file size and backend constraints."

Actual: server returns only `uploadId` (and the resumable URL for GCS/Azure). Part size is determined client-side from `FileClientProperties.multipartPartSize`. Spec's "Server determines part size" line should flip to client.

Spec (line 1278): "SDK decides single-part vs multipart based on file size and configured threshold" — actual: explicit opt-in via `FileUploadOptions.setMultipart(true)`. No threshold-based auto-decision.

---

## 29. Failure & Retry — upload retry detail

Spec line 1284: "Default: 3 retries, configurable" ✓ (`FileClientProperties.retryCount = 3`).

But line 1283 "Restarts upload from beginning" — actual `FileClient.upload(...)` does not retry the upload internally; retries are on `ManagedFileHandler.ensureDownloaded()` (download path) only. Re-uploading on failure is the worker's responsibility today. Spec should match.

---

## 30. What-If Scenario #10 — fileSize-based mismatch detection

Spec line 1444: "File is larger than declared in `FileUploadRequest`. What happens?"

Implementation answer: nothing — `FileUploadRequest` no longer has `fileSize`, no max is enforced, no mismatch error path exists. The scenario should be reframed (or marked deferred) to match.

---

## 31. Testing Strategy — test placement matches but stubs differ

Spec line 1521 introduces `StubFileMetadataDAO`, `StubFileStorage`, `StubFileStorageBackend`. The first two exist on the server (`core/src/test/.../StubFileStorage.java`, `StubFileMetadataDAO.java`); `StubFileStorageBackend` is not present in the SDK tests directory under that name (search: no hit). Either the SDK uses a different name or the stub hasn't landed.

Spec line 1530: `./e2e/run_tests-file-storage.sh` — script exists ✓.

---

## 32. Documentation Placement — paths likely stale

Spec lines 1452–1462 list `docs/documentation/quickstart/file-storage.md`, `docs/documentation/api/file-storage.md`, etc. These pages have not been written yet — the only file-storage docs in the repo are `docs/file-storage/{spec,design,…}.md`. Either the placement table is aspirational (mark as TODO) or the paths need updating to match where the real docs end up.

---

## 33. Other minor

- Spec line 174 ("`Task` gets a `@JsonIgnore private transient FileClient` field — not serialized, not a model change") — field type is `WorkflowFileClient`, not `FileClient`. (Repeat of #24.)
- Spec line 312 ("Response uses `FileUploadUrlResponse` DTO (same as Get Upload URL).") and line 314 ("the SDK skips this endpoint entirely based on `getStorageType()`") — actual SDK skips based on `init.getUploadUrl() != null`, not on `StorageType`. Same outcome, different mechanism.
- Spec line 1141 imperative-worker example calls `task.getInputFileHandler("inputFile")` — exists ✓. The accompanying upload uses `fileUploader.upload(Path.of(...))` — overload exists ✓ (one of the `Path`-only forms).
- Spec line 1143 mentions a typed `transient FileClient` getter set by `TaskRunner` — actual is `setWorkflowFileClient(...)` / `getFileUploader()` (returns `FileUploader`).

---

## Quick fix table

| # | Spec section | Severity |
|---|---|---|
| 1 | Module Placement (Server + SDK) — package roots throughout | High |
| 2 | DTOs — drop proto annotation guidance | Medium |
| 3 | StorageType / FileUploadStatus "shared" claim | Medium |
| 4 | FileHandle DTO drift (fileId → fileHandleId, fileSize gone, Instant → long) | High |
| 5 | FileUploadRequest — drop fileSize, mark workflowId required | High |
| 6 | URL/response timestamp types (Instant → long) | Medium |
| 7 | MultipartInitResponse.partSize | Medium |
| 8 | REST path drift (workflowId in download URL) + JSON examples | High |
| 9 | Error Responses table — invented codes, no FileTooLargeException | High |
| 10 | FileStorageService.getDownloadUrl(workflowId) + storage path | High |
| 11 | Add WorkflowFamilyResolver | High |
| 12 | FileModel.fileSize | High |
| 13 | DB schema — file_size column gone, SQLite V3 | High |
| 14 | DAO module placements + SQLite no-facade | Medium |
| 15 | Configuration — drop max-file-size | Medium |
| 16 | FileHandler.getFileId() vs getFileHandleId() | High |
| 17 | FileUploader signatures — drop string-content-type overloads | High |
| 18 | FileClient builder/factory/method shapes | High |
| 19 | Add WorkflowFileClient | High |
| 20 | Add FileUploadOptions | High |
| 21 | LocalFileHandler/ManagedFileHandler visibility + field | Medium |
| 22 | FileStorageBackend — multi-backend supported | Medium |
| 23 | SDK backend package path | Medium |
| 24 | Task field name/type | High |
| 25 | SDK config — multipart-part-size, opt-in | High |
| 26 | SDK module placement table | Medium |
| 27 | Download flow URL | High |
| 28 | Multipart — client decides part size, opt-in | High |
| 29 | Upload retry — no SDK-side retry today | Low |
| 30 | What-if #10 — defunct | Medium |
| 31 | Stub naming | Low |
| 32 | Documentation Placement TODO | Low |
| 33 | Misc rephrases | Low |
