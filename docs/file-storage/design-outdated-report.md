# Design Doc Outdated Items Report

> **Status (2026-04-30):** all 25 findings below have been applied to `design.md`. Kept for the audit trail; treat the design doc as the source of truth from here on.

Compares `docs/file-storage/design.md` against the implementation on `feature/file-storage-wip` (server in `file-storage-conductor`, SDK in `file-storage-java-sdk`). Findings grouped by section, terse.

---

## 1. `fileSize` removed from data model — pervasive

The `fileSize` field has been dropped from the server-side data model. Design still references it everywhere.

**Server side (no longer has `fileSize`):**
- `FileUploadRequest` (common/.../file/FileUploadRequest.java) — no `fileSize` field, no getter/setter. Design lines 425, 431, 444 still list it; `POST /api/files` example body (line 162) and request table (line 169) include it.
- `FileUploadResponse` — no `fileSize` field. Design line 484, response example line 188.
- `FileHandle` — no `fileSize` field. Design line 511, response example line 311.
- `FileModel` (core/.../model/FileModel.java) — no `fileSize` field; only `storageContentSize`. Design line 1008.
- `file_metadata` SQL schema — no `file_size` column in V15/V9/V3 migrations. Design lines 1041, 1067, 1095 all show `file_size BIGINT NOT NULL`.
- `FileModelConverter.toFileModel` etc. — no `setFileSize(...)` calls. Design lines 2174, 2191, 2209.

**SDK side (still has `fileSize`):** `FileHandle` (client model) and `ManagedFileHandler` keep `fileSize` — but the server never populates it, so SDK reads `0`. Divergence.

**`FileUploadOptions`:** design says it has `long fileSize`; actual fields are `taskId, fileName, contentType, multipart` — no `fileSize`. Design line 1623, 1629, 1635, 2677.

---

## 2. File-size validation gone

- `FileStorageProperties` no longer has `maxFileSize` (`DataSize`, default 5 GB). Design lines 1144, 1156–1158, configuration table line 760, ConfigMap line 2984, E2E test `testFileTooLargeRejected` line 3045.
- No `validateFileSize()` in `FileStorageServiceImpl.createFile()`. Design line 2263 ("`validateFileSize(request.getFileSize())`") and line 2292 ("validates size, …").
- `FileTooLargeException` does not exist. Design lines 2322–2340 describe creating it; design line 348 in spec/error table maps it to 413.
- 413 error responses described in REST table (design line 198) no longer reachable.

---

## 3. `defaultWorkflowId` bypass not implemented

- `FileStorageProperties` no longer has `defaultWorkflowId`. Design lines 1168–1173.
- `FileStorageServiceImpl.getDownloadUrl()` does not check it — goes straight to family resolution. Design line 294, line 2295, what-if scenario #5 (lines 2084, 2086).
- E2E test `testDownloadUrlDefaultWorkflowId` (design line 3051) — would not pass against current code.

---

## 4. `WorkflowFamilyResolverImpl` uses different mechanism

Design (lines 944–987) says:
- Calls `executionDAO.canSearchAcrossWorkflows()` to gate descendant lookup.
- Walks descendants via `executionDAO.getWorkflowsByParentId(workflowId)`.
- Notes: "Cassandra does not support this query; on that backend only ancestors are resolved."

Actual (`core/.../WorkflowFamilyResolverImpl.java`):
- No `canSearchAcrossWorkflows()` call.
- Walks descendants by loading the workflow with `includeTasks=true` and following `TaskModel.getSubWorkflowId()`.
- Adds self to the family up front (so archived workflows still see their own files).

---

## 5. `MultipartInitResponse.partSize` removed

DTO no longer has `partSize` (server or SDK). Design references:
- DTO definition (lines 588–595) lists `long partSize`.
- Response JSON (line 343, 339): `"partSize": 5242880`.
- Field table (line 350).
- `FileStorageService.initiateMultipartUpload()` Javadoc (line 73): "Returns the backend upload ID, recommended part size, and …" — actual interface Javadoc no longer mentions partSize.
- `FileStorageServiceImpl` text (line 2297): "returns upload ID + URL + part size".
- Multipart upload pseudocode (line 2729): `long partSize = init.getPartSize();` — actual reads from `FileClientProperties.getMultipartPartSize()`.

---

## 6. Storage path format changed

- Design: `files/<fileId>/<fileName>` (lines 204, 720, 2266, 2499).
- Actual `FileStorageServiceImpl`: `conductor/<workflowId>/<fileId>` (no fileName).

---

## 7. SDK multipart trigger changed

- Design: SDK auto-decides multipart based on `multipartThreshold` config (default 100 MB); `FileClient.upload()` checks `options.getFileSize() > properties.getMultipartThreshold()` (lines 1257–1258, 2709, "SDK decides single-part vs multipart based on file size and configured threshold").
- Actual: developer opts in via `FileUploadOptions.setMultipart(true)`. `FileClientProperties` has `multipartPartSize` (default 10 MiB) instead of `multipartThreshold`.

---

## 8. SDK `FileIdToFileHandleIdConverter` does not exist

- Design (line 639): "Mirrored in the SDK at `org.conductoross.conductor.client.model.file.FileIdToFileHandleIdConverter`."
- Actual: only the server-side class exists. SDK code uses static helpers on `FileHandler` itself (`FileHandler.toFileId`, `toFileHandleId`, `isFileHandleId`, `extractFileHandleId`, `PREFIX`). Annotation integration code (design line 2768) calls `FileIdToFileHandleIdConverter.isFileHandleId(...)` — actual `AnnotatedWorker` calls `FileHandler.isFileHandleId(...)`.

---

## 9. `Task` SDK integration uses `WorkflowFileClient`, not `FileClient`

- Design (lines 173–174, 1144–1145, 2769, 2826): `Task` carries `@JsonIgnore private transient FileClient fileClient;` with `getFileClient()`.
- Actual `Task`: holds `transient WorkflowFileClient workflowFileClient`, exposes `getFileUploader()` (returns `FileUploader`) and `setWorkflowFileClient(...)`. Worker code reads files via `task.getFileUploader()` and `task.getInputFileHandler()` — never `task.getFileClient()`.

DX showcase examples (lines 3335, 3396, 3434) already use `task.getFileUploader()` — so the design is internally inconsistent (ID-2769 says `task.getFileClient()`, examples say `task.getFileUploader()`).

---

## 10. `FileClient` Javadoc/design mismatch on `FileUploader`

- Design (lines 1419, 1440): "Does NOT implement `FileUploader`" — correct, actual `FileClient` does not implement it.
- But design's `FileUploader` Javadoc (line 1346) and SDK's `FileUploader.java` Javadoc say "Implemented by `WorkflowFileClient`" / "Implemented by `FileClient`" inconsistently. (Cosmetic — `WorkflowFileClient` is the real impl.)

---

## 11. `OrkesFileClient` does not exist

Design (lines 1205, 2841–2849) describes `OrkesFileClient extends FileClient`. Class is absent. `OrkesClients.getFileClient()` returns a plain `FileClient`.

Spring auto-config: design (line 2854) places the bean in `ConductorClientAutoConfiguration` — actual matches. But the `OrkesConductorClientAutoConfiguration` also defines a `FileClient` bean (not mentioned in design), with signature `fileClient(ApiClient client)` returning `new FileClient(client)`.

---

## 12. Server has its own `FileStorageException`

- Design (line 1647): `FileStorageException` is "SDK-side unchecked exception" only.
- Actual: `core/.../core/exception/FileStorageException.java` exists on the server too (extends `NonTransientException`). Not in design.

---

## 13. SDK class visibility

Design says `LocalFileHandler` and `ManagedFileHandler` are package-private (lines 1022, 1657, 1670, 1711, 1729). Actual: both are `public` classes. (Constructors are mostly package-private; setters on `ManagedFileHandler` are package-private.) `ManagedFileHandler` also has package-private setters (`setFileName`, `setContentType`, `setFileSize`, `setStorageType`, `setLocalPath`) that the design doesn't mention.

---

## 14. SDK module placement / package mismatches

- Design (line 1196 area, modeled after spec): SDK storage backends in `org.conductoross.conductor.sdk.file.storage`. Actual: `org.conductoross.conductor.client.storage` (`S3FileStorageBackend`, `AzureFileStorageBackend`, `GcsFileStorageBackend`, `LocalFileStorageBackend`, plus `HttpBodies` helper).
- Design lists `FileClient` under `org.conductoross.conductor.sdk.file` (line 980). Actual: `org.conductoross.conductor.client` package (the `FileClient.java` file).

---

## 15. SQLite DAO — no facade/delegate

- Design (lines 633, 884–890, 2580–2586): `SqliteFileMetadataDAO` is a facade delegating to `SqliteFileMetadataSubDAO extends SqliteBaseDAO`.
- Actual: single class `SqliteFileMetadataDAO extends SqliteBaseDAO` directly. No sub-DAO.

---

## 16. DAO module package divergence

- Design (table line 619, sections 2516+): All DAOs under `org.conductoross.conductor.<db>.dao`.
- Actual: Postgres / MySQL / SQLite are under `org.conductoross.conductor.<db>.dao` ✓. **Redis** at `com.netflix.conductor.redis.dao.RedisFileMetadataDAO`. **Cassandra** at `com.netflix.conductor.cassandra.dao.CassandraFileMetadataDAO`.

---

## 17. `FileUploader` interface signatures

Design (lines 1349–1361):
```java
FileHandler upload(Path localFile);
FileHandler upload(InputStream inputStream);
FileHandler upload(Path localFile, FileUploadOptions options);
FileHandler upload(InputStream inputStream, FileUploadOptions options);
```
Actual interface: identical. ✓

But design lines 956–968 (a separate definition earlier in the doc) show an obsolete shape:
```java
FileHandler upload(InputStream inputStream);
FileHandler upload(InputStream inputStream, String contentType);
FileHandler upload(Path localFile);
FileHandler upload(Path localFile, String contentType);
```
That earlier section (which also references the spec's `String contentType` overloads) is stale.

---

## 18. Annotation-based input — wrapping path

- Design (line 2768): wraps with `new ManagedFileHandler((String) value, task.getFileClient())`.
- Actual `AnnotatedWorker`: `new ManagedFileHandler(fileHandleId, task.getWorkflowFileClient())`. Also calls `FileHandler.extractFileHandleId(value)` first, supporting both raw strings and the serialized `{fileHandleId, contentType, fileName}` object. The serialized-object form is not described in the design's `getInputValue()` snippet.

---

## 19. `FileHandler` wire shape — undocumented

The SDK uses `FileHandlerSerializer` / `FileHandlerDeserializer` (registered via `@JsonSerialize`/`@JsonDeserialize` on the interface) to render `FileHandler` as `{fileHandleId, contentType, fileName}`. Design references these classes by name (lines 2026, 2796, 2816, 3220, 3250, 3352) but never defines them or shows that the serialization happens via Jackson annotations on the interface — readers cannot reproduce the wire shape from the design.

---

## 20. Auto-upload uses `getPath()` cast — narrower than design

Design `uploadFilesToFileStorage` (line 2809) casts to `LocalFileHandler` to call `getPath()`. Implementation likely matches, but design assumes `LocalFileHandler` is package-private (it is `public` in actual code, see #13), which makes the cast cross-package safe — design isn't aware.

---

## 21. Stub class signatures

Design (lines 2890–2895): `StorageFileInfo` constructor `new StorageFileInfo(true, null, data.length)` (positional 3-arg). Actual `StorageFileInfo` has only no-arg constructor + setters — that constructor doesn't exist. Stub code in `core/src/test/.../StubFileStorage.java` won't compile against the snippet as written.

---

## 22. Error response shapes

Design REST tables list error codes like `INVALID_REQUEST`, `FILE_NOT_FOUND`, `ALREADY_UPLOADED`, `UPLOAD_NOT_COMPLETE`, `ACCESS_FORBIDDEN`, `VERIFICATION_FAILED`, `FILE_TOO_LARGE` (e.g. lines 196, 226, 254, 287, 327). Actual responses use the standard Conductor `ApplicationExceptionMapper` → `ErrorResponse` shape — no per-error code strings. The design tables imply structured error codes that don't exist.

---

## 23. What-If scenarios drift

- #5 (line 2086) references `defaultWorkflowId` bypass — not implemented (see #3).
- #10 (lines 2134–2143) describes file-size mismatch detection via declared `fileSize` vs storage actual — `fileSize` is no longer declared (#1), no validation, no mismatch error.
- #4 / #7 / #8 still match implementation behavior (#7 fail-fast wording matches code message exactly).

---

## 24. Multipart endpoint S3 vs GCS/Azure note

`getPartUploadUrl` (line 311) says it's "S3 only" and that GCS/Azure skip it. Implementation aligns, but the FileClient.uploadMultipart code path determines this purely by checking `init.getUploadUrl() != null`, not by `StorageType` — internal detail not reflected in design's "SDK skips this endpoint entirely based on `getStorageType()`" wording (spec line 314, design line 1276 area).

---

## 25. Workflow-id required validation

REST request table (line 174) marks `workflowId` as Required. Implementation enforces this both via `@NotBlank` on the DTO and an explicit `if (request.getWorkflowId() == null || ...isBlank())` in `createFile()`. Design doesn't mention the redundant guard but it's harmless.

---

## Quick table — where to fix

| # | Section in design.md | Severity |
|---|----------------------|----------|
| 1 | DTOs, FileModel, schema, converters, examples | High — model-wide |
| 2 | FileStorageProperties, error tables, E2E tests | High |
| 3 | getDownloadUrl logic, what-if #5, E2E test | High |
| 4 | WorkflowFamilyResolverImpl | High |
| 5 | MultipartInitResponse + multipart pseudocode | Medium |
| 6 | Storage path constant | Medium |
| 7 | FileClientProperties + multipart trigger | Medium |
| 8 | SDK converter ref | Medium |
| 9 | Task field + worker examples already mixed | High |
| 10 | FileClient/FileUploader Javadoc | Low |
| 11 | OrkesFileClient | Medium |
| 12 | Server FileStorageException | Low |
| 13 | Class visibility | Low |
| 14 | SDK package paths | Medium |
| 15 | SqliteFileMetadataDAO shape | Medium |
| 16 | Redis/Cassandra DAO packages | Low |
| 17 | Stale FileUploader signature block | Medium |
| 18 | Annotation input snippet | Medium |
| 19 | FileHandler wire shape undocumented | Medium |
| 20 | LocalFileHandler visibility assumption | Low |
| 21 | StorageFileInfo constructor in stubs | Low |
| 22 | Error code labels | Medium |
| 23 | What-if #5, #10 | Medium |
| 24 | Multipart skip mechanism wording | Low |
| 25 | workflowId guard | Low |
