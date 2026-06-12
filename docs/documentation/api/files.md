---
description: "Conductor File API — create, upload, download, and inspect file payloads. Includes single-shot and multipart presigned URL flows."
---

# File API

The File API manages binary file payloads associated with workflow executions. All endpoints use the base path `/api/files` and are gated by `conductor.file-storage.enabled=true` — when the feature is disabled, every endpoint returns `404`. See [File Storage](../advanced/file-storage.md) for backend setup.

Path variables carry the bare `fileId` (a UUID assigned at creation). Request and response bodies carry the prefixed handle as `fileHandleId` (`conductor://file/<fileId>`); pass either form back in to subsequent calls — the server normalizes them.

Download access is *workflow-family scoped*: callers must supply a `workflowId` that belongs to the same workflow family (self, ancestors, or descendants) as the file's owning workflow. Cross-family access returns `403 Forbidden`.

## Create a File

```
POST /api/files
```

Reserves a `fileId`, persists the metadata record, and returns a presigned upload URL. The file is created in `UPLOADING` status; the client must confirm completion via [Confirm Upload](#confirm-upload) once the bytes are uploaded.

**Request body:**

| Field | Description | Required |
|---|---|---|
| `workflowId` | Workflow execution that owns this file. Used for download-access scoping. | Yes |
| `fileName` | Original file name. | No |
| `contentType` | MIME type. | No |
| `taskId` | Task that produced the file, if applicable. | No |

```shell
curl -X POST 'http://localhost:8080/api/files' \
  -H 'Content-Type: application/json' \
  -d '{
    "workflowId": "3a5b8c2d-1234-5678-9abc-def012345678",
    "fileName": "input.mp4",
    "contentType": "video/mp4"
  }'
```

**Response** `201 Created`

```json
{
  "fileHandleId": "conductor://file/a1b2c3d4-5678-90ab-cdef-111111111111",
  "fileName": "input.mp4",
  "contentType": "video/mp4",
  "storageType": "S3",
  "uploadStatus": "UPLOADING",
  "uploadUrl": "https://bucket.s3.amazonaws.com/conductor/3a5b8c2d.../a1b2c3d4...?X-Amz-Signature=...",
  "uploadUrlExpiresAt": 1700000060000,
  "createdAt": 1700000000000
}
```

The client `PUT`s file bytes directly to `uploadUrl`, then calls [Confirm Upload](#confirm-upload).

---

## Get a Fresh Upload URL

```
GET /api/files/{fileId}/upload-url
```

Issues a new presigned upload URL — used to retry an upload after the original URL expired.

```shell
curl 'http://localhost:8080/api/files/a1b2c3d4-5678-90ab-cdef-111111111111/upload-url'
```

**Response** `200 OK`

```json
{
  "fileHandleId": "conductor://file/a1b2c3d4-5678-90ab-cdef-111111111111",
  "uploadUrl": "https://bucket.s3.amazonaws.com/conductor/3a5b8c2d.../a1b2c3d4...?X-Amz-Signature=...",
  "expiresAt": 1700000060000
}
```

---

## Confirm Upload

```
POST /api/files/{fileId}/upload-complete
```

Marks the upload as complete. The server probes the storage backend to verify the object exists, then transitions the record to `UPLOADED` and records the backend-reported content hash and size.

```shell
curl -X POST 'http://localhost:8080/api/files/a1b2c3d4-5678-90ab-cdef-111111111111/upload-complete'
```

**Response** `200 OK`

```json
{
  "fileHandleId": "conductor://file/a1b2c3d4-5678-90ab-cdef-111111111111",
  "uploadStatus": "UPLOADED",
  "contentHash": "d41d8cd98f00b204e9800998ecf8427e"
}
```

`409 Conflict` if the file is already in `UPLOADED` status; `500 Internal Server Error` if the backend reports the object is missing.

---

## Get a Download URL

```
GET /api/files/{workflowId}/{fileId}/download-url
```

Issues a presigned download URL. The caller's `workflowId` must be in the same workflow family as the file's owning workflow.

| Parameter | Description |
|---|---|
| `workflowId` | The caller's workflow ID, used for family-scope check. |
| `fileId` | The file to download. |

```shell
curl 'http://localhost:8080/api/files/3a5b8c2d-1234-5678-9abc-def012345678/a1b2c3d4-5678-90ab-cdef-111111111111/download-url'
```

**Response** `200 OK`

```json
{
  "fileHandleId": "conductor://file/a1b2c3d4-5678-90ab-cdef-111111111111",
  "downloadUrl": "https://bucket.s3.amazonaws.com/conductor/3a5b8c2d.../a1b2c3d4...?X-Amz-Signature=...",
  "expiresAt": 1700000060000
}
```

`403 Forbidden` if the caller's workflow is not in the file's family. `400 Bad Request` if the file is not yet `UPLOADED`.

---

## Get File Metadata

```
GET /api/files/{fileId}
```

Returns the file metadata record. Does not expose the server-internal storage path.

```shell
curl 'http://localhost:8080/api/files/a1b2c3d4-5678-90ab-cdef-111111111111'
```

**Response** `200 OK`

```json
{
  "fileHandleId": "conductor://file/a1b2c3d4-5678-90ab-cdef-111111111111",
  "fileName": "input.mp4",
  "contentType": "video/mp4",
  "contentHash": "d41d8cd98f00b204e9800998ecf8427e",
  "storageType": "S3",
  "uploadStatus": "UPLOADED",
  "workflowId": "3a5b8c2d-1234-5678-9abc-def012345678",
  "taskId": "task-uuid-1",
  "createdAt": 1700000000000,
  "updatedAt": 1700000005000
}
```

---

## Multipart Upload

Multipart is opt-in (typically for files above ~100 MB) and supported on `s3`, `azure-blob`, and `gcs` backends. The `local` backend does not support multipart.

### Initiate Multipart

```
POST /api/files/{fileId}/multipart
```

Begins a multipart upload session. Returns a backend-specific `uploadId`. For GCS/Azure, `uploadUrl` is the resumable session URL clients upload parts to directly; for S3 it is `null` and clients fetch a per-part URL via [Get Part Upload URL](#get-part-upload-url).

```shell
curl -X POST 'http://localhost:8080/api/files/a1b2c3d4-5678-90ab-cdef-111111111111/multipart'
```

**Response** `200 OK`

```json
{
  "fileHandleId": "conductor://file/a1b2c3d4-5678-90ab-cdef-111111111111",
  "uploadId": "S3-multipart-upload-id-string",
  "uploadUrl": null
}
```

### Get Part Upload URL

```
GET /api/files/{fileId}/multipart/{uploadId}/part/{partNumber}
```

S3 only. Returns a presigned URL for uploading a single part. Part numbers start at `1`.

```shell
curl 'http://localhost:8080/api/files/a1b2c3d4-5678-90ab-cdef-111111111111/multipart/S3-multipart-upload-id-string/part/1'
```

**Response** `200 OK`

```json
{
  "fileHandleId": "conductor://file/a1b2c3d4-5678-90ab-cdef-111111111111",
  "uploadUrl": "https://bucket.s3.amazonaws.com/conductor/.../?partNumber=1&uploadId=...&X-Amz-Signature=...",
  "expiresAt": 1700000060000
}
```

### Complete Multipart

```
POST /api/files/{fileId}/multipart/{uploadId}/complete
```

Finalizes the multipart upload and transitions the file to `UPLOADED`. The request body carries the ordered list of part ETags (or backend equivalents) from the part uploads.

```shell
curl -X POST 'http://localhost:8080/api/files/a1b2c3d4-5678-90ab-cdef-111111111111/multipart/S3-multipart-upload-id-string/complete' \
  -H 'Content-Type: application/json' \
  -d '{
    "partETags": ["\"etag-of-part-1\"", "\"etag-of-part-2\"", "\"etag-of-part-3\""]
  }'
```

**Response** `200 OK`

```json
{
  "fileHandleId": "conductor://file/a1b2c3d4-5678-90ab-cdef-111111111111",
  "uploadStatus": "UPLOADED",
  "contentHash": "d41d8cd98f00b204e9800998ecf8427e"
}
```

---

## Errors

| Status | Cause |
|---|---|
| `400 Bad Request` | Missing `workflowId` on create; download requested before file is `UPLOADED`. |
| `403 Forbidden` | Caller's `workflowId` is not in the file's workflow family. |
| `404 Not Found` | Unknown `fileId`, or `conductor.file-storage.enabled=false`. |
| `409 Conflict` | Confirm-upload called on a file already in `UPLOADED` status. |
| `413 Payload Too Large` | `FileStorageException` raised by a backend (e.g., upstream size enforcement). |
| `500 Internal Server Error` | Backend reports object missing on confirm/complete; other transient/non-transient backend errors. |
