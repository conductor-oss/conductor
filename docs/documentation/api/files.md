---
description: "Conductor File API — create, upload, download, and inspect workflow-scoped file payloads."
---

# File API

The File API manages binary payloads associated with workflow executions. All endpoints use the base path `/api/files` and require `conductor.file-storage.enabled=true`. When the feature is disabled, the endpoints are not registered and return `404`. See [File Storage](../advanced/file-storage.md) for backend configuration.

Conductor stores only metadata and returns short-lived signed URLs. Upload and download bytes travel directly between the caller and the configured storage backend.

## Handles and workflow scope

Files are opaque handle strings:

```text
conductor://file/<fileId>
```

Workflow inputs and outputs should carry the complete handle string. REST path variables use the bare `fileId`; do not place the `conductor://file/` prefix in a path segment.

Every operation has workflow context, with two authorization scopes:

| Operation | Required workflow relationship |
|---|---|
| Create | The request's `workflowId` becomes the owner. |
| Upload URL refresh, upload completion, and multipart mutations | Exact owning workflow. |
| Metadata and download URL | Owning workflow or a member of its workflow family (self, ancestor, or descendant). |

An unrelated workflow, or a family member attempting an upload mutation, receives `403 Forbidden`.

## Single-request upload

### 1. Create a file record

```text
POST /api/files
```

This creates an `UPLOADING` metadata record and returns the initial signed upload URL.

| Field | Description | Required |
|---|---|---|
| `workflowId` | Workflow execution that owns the file. | Yes |
| `fileName` | Original file name. | No |
| `contentType` | MIME type. | No |
| `taskId` | Task that produced the file. | No |

```shell
curl -X POST 'http://localhost:8080/api/files' \
  -H 'Content-Type: application/json' \
  -d '{
    "workflowId": "3a5b8c2d-1234-5678-9abc-def012345678",
    "fileName": "input.mp4",
    "contentType": "video/mp4",
    "taskId": "task-uuid-1"
  }'
```

Response: `201 Created`

```json
{
  "fileHandleId": "conductor://file/a1b2c3d4-5678-90ab-cdef-111111111111",
  "fileName": "input.mp4",
  "contentType": "video/mp4",
  "storageType": "S3",
  "uploadStatus": "UPLOADING",
  "uploadUrl": "<signed-upload-url>",
  "uploadUrlExpiresAt": 1700000060000,
  "createdAt": 1700000000000
}
```

### 2. Upload the bytes

Use the method and headers required by the selected provider. The returned URL is a credential: do not log it or attach Conductor authentication headers to it.

```shell
curl -X PUT --upload-file ./input.mp4 '<signed-upload-url>'
```

Azure whole-blob uploads additionally require `x-ms-blob-type: BlockBlob`.

### 3. Refresh an expired upload URL

```text
GET /api/files/{workflowId}/{fileId}/upload-url
```

Only the owning workflow can refresh the URL.

```shell
curl 'http://localhost:8080/api/files/3a5b8c2d-1234-5678-9abc-def012345678/a1b2c3d4-5678-90ab-cdef-111111111111/upload-url'
```

Response: `200 OK`

```json
{
  "fileHandleId": "conductor://file/a1b2c3d4-5678-90ab-cdef-111111111111",
  "uploadUrl": "<fresh-signed-upload-url>",
  "expiresAt": 1700000060000
}
```

### 4. Confirm the upload

```text
POST /api/files/{workflowId}/{fileId}/upload-complete
```

The server verifies that the object exists, records the backend-reported hash and size, and changes the status to `UPLOADED`.

```shell
curl -X POST 'http://localhost:8080/api/files/3a5b8c2d-1234-5678-9abc-def012345678/a1b2c3d4-5678-90ab-cdef-111111111111/upload-complete'
```

Response: `200 OK`

```json
{
  "fileHandleId": "conductor://file/a1b2c3d4-5678-90ab-cdef-111111111111",
  "uploadStatus": "UPLOADED",
  "contentHash": "d41d8cd98f00b204e9800998ecf8427e"
}
```

Completion returns `409 Conflict` if the file is already `UPLOADED`. Clients that lose the completion response should reconcile by reading workflow-scoped metadata before deciding whether to retry.

## Read metadata

```text
GET /api/files/{workflowId}/{fileId}
```

The owning workflow and its workflow family can read metadata. This endpoint can also return an `UPLOADING` record, which lets clients reconcile an ambiguous completion response.

```shell
curl 'http://localhost:8080/api/files/3a5b8c2d-1234-5678-9abc-def012345678/a1b2c3d4-5678-90ab-cdef-111111111111'
```

Response: `200 OK`

```json
{
  "fileHandleId": "conductor://file/a1b2c3d4-5678-90ab-cdef-111111111111",
  "fileName": "input.mp4",
  "contentType": "video/mp4",
  "fileSize": 1048576,
  "contentHash": "d41d8cd98f00b204e9800998ecf8427e",
  "storageType": "S3",
  "uploadStatus": "UPLOADED",
  "workflowId": "3a5b8c2d-1234-5678-9abc-def012345678",
  "taskId": "task-uuid-1",
  "createdAt": 1700000000000,
  "updatedAt": 1700000005000
}
```

## Download

### 1. Get a download URL

```text
GET /api/files/{workflowId}/{fileId}/download-url
```

The file must be `UPLOADED`. The owning workflow or any workflow in its family can request the URL.

```shell
curl 'http://localhost:8080/api/files/3a5b8c2d-1234-5678-9abc-def012345678/a1b2c3d4-5678-90ab-cdef-111111111111/download-url'
```

Response: `200 OK`

```json
{
  "fileHandleId": "conductor://file/a1b2c3d4-5678-90ab-cdef-111111111111",
  "downloadUrl": "<signed-download-url>",
  "expiresAt": 1700000060000
}
```

### 2. Download the bytes

```shell
curl --fail --output ./input.mp4 '<signed-download-url>'
```

Do not attach Conductor authentication headers to the signed storage request. Application clients should download to a temporary file and atomically replace the destination only after a complete response; the Java SDK's `FileClient` does this automatically.

## Multipart upload

The Java SDK selects multipart automatically when a file exceeds its configured threshold and the storage adapter supports it. Application code should normally call `FileClient.upload(...)` instead of these endpoints directly.

The supported completion token is provider-specific:

| Provider | Part completion token |
|---|---|
| Amazon S3 | Required `ETag` response header from each part. |
| Azure Blob | Stable Base64 block ID used for that part. |

GCS and local storage use a single request in the Java SDK. GCS resumable upload is intentionally not presented as multipart until its protocol is implemented end to end.

### 1. Initiate

```text
POST /api/files/{workflowId}/{fileId}/multipart
```

```shell
curl -X POST 'http://localhost:8080/api/files/3a5b8c2d-1234-5678-9abc-def012345678/a1b2c3d4-5678-90ab-cdef-111111111111/multipart'
```

Response: `200 OK`

```json
{
  "fileHandleId": "conductor://file/a1b2c3d4-5678-90ab-cdef-111111111111",
  "uploadId": "backend-multipart-upload-id"
}
```

### 2. Get a URL for each part

```text
GET /api/files/{workflowId}/{fileId}/multipart/{uploadId}/part/{partNumber}
```

Part numbers are 1-based. Request a fresh URL before each retry.

```shell
curl 'http://localhost:8080/api/files/3a5b8c2d-1234-5678-9abc-def012345678/a1b2c3d4-5678-90ab-cdef-111111111111/multipart/backend-multipart-upload-id/part/1'
```

Response: `200 OK`

```json
{
  "fileHandleId": "conductor://file/a1b2c3d4-5678-90ab-cdef-111111111111",
  "uploadUrl": "<signed-part-upload-url>",
  "expiresAt": 1700000060000
}
```

Upload exactly that part's byte range. For S3, retain the non-blank `ETag` response header. For Azure, use deterministic Base64 block IDs and append `comp=block&blockid=<encoded-block-id>` to the signed URL.

### 3. Complete

```text
POST /api/files/{workflowId}/{fileId}/multipart/{uploadId}/complete
```

The `partETags` field is the ordered list of provider completion tokens: S3 ETags or Azure block IDs.

```shell
curl -X POST 'http://localhost:8080/api/files/3a5b8c2d-1234-5678-9abc-def012345678/a1b2c3d4-5678-90ab-cdef-111111111111/multipart/backend-multipart-upload-id/complete' \
  -H 'Content-Type: application/json' \
  -d '{"partETags":["part-token-1","part-token-2","part-token-3"]}'
```

Response: `200 OK`

```json
{
  "fileHandleId": "conductor://file/a1b2c3d4-5678-90ab-cdef-111111111111",
  "uploadStatus": "UPLOADED",
  "contentHash": "backend-reported-content-hash"
}
```

### Abort a failed session

```text
DELETE /api/files/{workflowId}/{fileId}/multipart/{uploadId}
```

```shell
curl -X DELETE 'http://localhost:8080/api/files/3a5b8c2d-1234-5678-9abc-def012345678/a1b2c3d4-5678-90ab-cdef-111111111111/multipart/backend-multipart-upload-id'
```

Response: `204 No Content`. S3 aborts the backend session. Backends whose uncommitted blocks expire do not require an explicit provider operation.

## Errors

| Status | Cause |
|---|---|
| `400 Bad Request` | Blank workflow ID, invalid request, or download requested before `UPLOADED`. |
| `403 Forbidden` | Exact owner required for an upload mutation, or caller is outside the file's workflow family for read access. |
| `404 Not Found` | Unknown `fileId`, or file storage is disabled. |
| `409 Conflict` | Single-request completion called after the file is already `UPLOADED`. |
| `413 Payload Too Large` | Backend or deployment size enforcement rejected the request. |
| `500 Internal Server Error` | Storage completion or metadata verification failed. |
