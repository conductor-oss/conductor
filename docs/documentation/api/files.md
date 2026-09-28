# File API

The file API creates workflow-scoped metadata records and issues upload and download URLs. The
workflow-visible handle is `conductor://file/<file-id>`; route path variables use the bare
`file-id`.

All routes below are relative to the Conductor server URL.

## Handles and workflow scope

Every file has an owning workflow.

- Creating a file and every upload mutation require the exact owning `workflowId`.
- Metadata and downloads accept a workflow in the owner's workflow family, including parent and
  child workflows.
- A file does not become downloadable until upload completion records it as `UPLOADED`.

## Single-request upload

### 1. Create a file record

```http
POST /api/files
Content-Type: application/json

{
  "workflowId": "3a5b8c2d-1234-5678-9abc-def012345678",
  "taskId": "optional-task-id",
  "fileName": "report.pdf",
  "contentType": "application/pdf"
}
```

`workflowId` is required. `fileName`, `contentType`, and `taskId` are optional metadata.

The response is `201 Created` with this shape:

```json
{
  "fileHandleId": "conductor://file/<file-id>",
  "fileName": "report.pdf",
  "contentType": "application/pdf",
  "storageType": "CONDUCTOR",
  "uploadStatus": "UPLOADING",
  "uploadUrl": "<backend upload URL>",
  "uploadUrlExpiresAt": 0,
  "createdAt": 0
}
```

`uploadUrlExpiresAt` and `createdAt` are epoch milliseconds. The `storageType` value reflects the
configured backend; it is `CONDUCTOR` for Conductor-managed storage.

### 2. Upload the bytes

Send the bytes to `uploadUrl`. The transfer protocol depends on the backend:

- `conductor`: the URL points to Conductor's raw content endpoint. See
  [Conductor-managed raw content](#conductor-managed-raw-content).
- `s3`, `azure-blob`, and `gcs`: the URL is a provider URL and the client transfers directly to
  that object store.

### 3. Refresh an expired upload URL

```http
GET /api/files/{workflowId}/{fileId}/upload-url
```

The response is `200 OK`:

```json
{
  "fileHandleId": "conductor://file/<file-id>",
  "uploadUrl": "<backend upload URL>",
  "expiresAt": 0
}
```

`expiresAt` is epoch milliseconds.

### 4. Confirm the upload

After the bytes are stored, confirm the upload:

```http
POST /api/files/{workflowId}/{fileId}/upload-complete
```

The response is `200 OK`:

```json
{
  "fileHandleId": "conductor://file/<file-id>",
  "uploadStatus": "UPLOADED",
  "contentHash": "<backend content hash>"
}
```

## Conductor-managed raw content

When `conductor.file-storage.type=conductor`, the upload and download URL use Conductor's raw
content routes. The request and response bodies are raw file bytes, not JSON.

### Upload content

```http
PUT /api/files/content/{workflowId}/{fileId}

<raw file bytes>
```

The exact response is:

```http
204 No Content
```

There is no response body. The owning workflow ID is required; a different workflow cannot upload
or overwrite the file.

### Download content

```http
GET /api/files/content/{workflowId}/{fileId}
```

The exact successful response shape is:

```http
200 OK
Content-Type: <the stored file content type>
Content-Length: <the stored file size>

<raw file bytes>
```

The controller streams the response body from storage. It sets `Content-Type` from the file
metadata and `Content-Length` from the stored file size. The file must already be `UPLOADED`.

If content URL signing is enabled, use the signed URL returned by the create, refresh, or download
URL endpoint. The signed content URL contains `op`, `exp`, `kid`, and `sig`; do not log it.

## Read metadata

```http
GET /api/files/{workflowId}/{fileId}
```

The response is `200 OK`:

```json
{
  "fileHandleId": "conductor://file/<file-id>",
  "fileName": "report.pdf",
  "contentType": "application/pdf",
  "fileSize": 0,
  "contentHash": "<backend content hash>",
  "storageType": "CONDUCTOR",
  "uploadStatus": "UPLOADED",
  "workflowId": "<owning workflow id>",
  "taskId": "optional-task-id",
  "createdAt": 0,
  "updatedAt": 0
}
```

`fileSize`, `createdAt`, and `updatedAt` are numeric values; timestamps are epoch milliseconds.

## Download

### 1. Get a download URL

```http
GET /api/files/{workflowId}/{fileId}/download-url
```

The response is `200 OK`:

```json
{
  "fileHandleId": "conductor://file/<file-id>",
  "downloadUrl": "<backend download URL>",
  "expiresAt": 0
}
```

For the `conductor` backend, `downloadUrl` is the raw `GET /api/files/content/{workflowId}/{fileId}`
endpoint. For object-store backends, it is a provider URL.

### 2. Download the bytes

Issue a `GET` to `downloadUrl`. For Conductor-managed storage, the response is the raw-stream shape
shown above. For object-store backends, follow the provider's signed-URL contract.

## Multipart upload

Multipart is available only for backends that implement it. S3 and Azure Blob Storage support the
multipart routes; `conductor` and GCS do not. Do not start a multipart session for the `conductor`
backend: Conductor-managed uploads are single-request and bounded by
`conductor.file-storage.conductor.max-size`.

### 1. Initiate

```http
POST /api/files/{workflowId}/{fileId}/multipart
```

The response is `200 OK`:

```json
{
  "fileHandleId": "conductor://file/<file-id>",
  "uploadId": "<backend multipart upload id>"
}
```

### 2. Get a URL for each part

```http
GET /api/files/{workflowId}/{fileId}/multipart/{uploadId}/part/{partNumber}
```

The response uses the upload URL shape:

```json
{
  "fileHandleId": "conductor://file/<file-id>",
  "uploadUrl": "<provider part upload URL>",
  "expiresAt": 0
}
```

### 3. Complete

```http
POST /api/files/{workflowId}/{fileId}/multipart/{uploadId}/complete
Content-Type: application/json

{
  "partETags": ["<part 1 token>", "<part 2 token>"]
}
```

The response uses the upload-complete shape shown above.

### Abort a failed session

```http
DELETE /api/files/{workflowId}/{fileId}/multipart/{uploadId}
```

The response is `204 No Content`.

## Errors

| Status | Meaning |
|---|---|
| `400 Bad Request` | Invalid request, an upload is not complete, or multipart is unsupported by the selected backend. |
| `403 Forbidden` | The workflow does not have the required owner or family access; a signed content URL is invalid or expired when signing is enabled. |
| `404 Not Found` | The file ID does not exist, or file storage is not enabled. |
| `405 Method Not Allowed` | A signed URL was used with the wrong operation, such as a download URL for a `PUT`. |
| `413 Payload Too Large` | A Conductor-managed upload exceeds `conductor.file-storage.conductor.max-size`. |
