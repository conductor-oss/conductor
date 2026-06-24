---
description: "Use the Google Drive integration to store account-level OAuth credentials and read file metadata from Google Drive in Conductor workflows."
---

# Google Drive Integration

The Google Drive integration reads file metadata from Google Drive using account-level OAuth credentials stored under a `connectionId`. It is available through:

- REST endpoints under `/api/integrations/gdrive`
- The `GDRIVE_READ` system task
- The Google Drive task form in the workflow definition UI

The integration returns metadata only. It does not download file contents. Account credentials are stored once under a `connectionId`; runtime restrictions such as `folderIds`, `fileIds`, `maxFiles`, and `mimeTypes` are request inputs. OAuth credentials are not folder-specific and should not be embedded in workflow JSON.

For the advanced connection-management architecture, dynamic multi-document ingestion contract, and GRN/POD classification handoff, see [Google Drive Connection Management and Document Ingestion](connection-management-document-ingestion.md).

## Authentication

The integration stores Google OAuth token JSON in the Conductor persistence backend. The row key is `connectionId`, and the stored token JSON must include one of these forms:

- `access_token` or `token`
- `refresh_token`, `client_id`, and `client_secret`

When a refresh token is present, Conductor can request a new access token without starting an interactive browser flow. If a Google Drive request returns `401` and the token JSON can be refreshed, the integration refreshes the access token and retries the Drive request once.

OAuth client JSON can be supplied in either Google `installed` or `web` client format. The token exchange endpoint reads `client_id`, `client_secret`, and optional `token_uri` from that JSON. If `token_uri` is omitted, Conductor uses `https://oauth2.googleapis.com/token`.

## REST API

### Exchange an OAuth authorization code and save a connection

`POST /api/integrations/gdrive/oauth/token`

Request body:

```json
{
  "connectionId": "finance-drive",
  "accountName": "Finance Drive",
  "authorizationCode": "<authorization-code>",
  "oauthClientJson": "{\"installed\":{\"client_id\":\"...\",\"client_secret\":\"...\"}}",
  "redirectUri": "http://localhost:3000/integrations/gdrive/callback"
}
```

Response body:

```json
{
  "connectionId": "finance-drive"
}
```

If `connectionId` is omitted, the endpoint keeps the legacy behavior and returns `oauthTokenJson` instead of storing it.

### Save an existing token JSON

`POST /api/integrations/gdrive/connections`

Request body:

```json
{
  "connectionId": "finance-drive",
  "accountName": "Finance Drive",
  "oauthTokenJson": "{\"refresh_token\":\"...\"}",
  "oauthClientJson": "{\"installed\":{\"client_id\":\"...\",\"client_secret\":\"...\"}}"
}
```

Response body:

```json
{
  "connectionId": "finance-drive",
  "accountName": "Finance Drive",
  "createdAt": 1781844000000,
  "updatedAt": 1781844000000
}
```

The response does not include the stored OAuth token JSON.

### List stored connections

`GET /api/integrations/gdrive/connections`

Response body:

```json
[
  {
    "connectionId": "finance-drive",
    "accountName": "Finance Drive",
    "createdAt": 1781844000000,
    "updatedAt": 1781844000000
  }
]
```

### Delete a stored connection

`DELETE /api/integrations/gdrive/connections/{connectionId}`

### Load drive metadata

`POST /api/integrations/gdrive/load`

Request body:

```json
{
  "connectionId": "finance-drive",
  "folderIds": ["1abcDEFghi_Jkl-mno"],
  "fileIds": ["file-id"],
  "maxFiles": 25,
  "mimeTypes": ["application/pdf", "image/png"]
}
```

If `connectionId` is omitted and `oauthTokenJson` is not supplied, Conductor uses the most recently updated stored Google Drive connection. Supplying `connectionId` always takes precedence.

`folderIds` and `fileIds` are optional. Values can be raw Google Drive IDs, Drive URLs (`/folders/{id}` or `/file/d/{id}`), or URLs with an `id` query parameter. After extraction, each ID must contain only letters, numbers, underscores, or dashes.

If `folderIds` is supplied, Conductor reads files directly inside each folder. If `fileIds` is supplied, Conductor also reads those specific files. If both lists are omitted or empty, Conductor reads metadata for all non-trashed files visible to the connected account, capped by `maxFiles`.

Response body:

```json
{
  "folderId": "1abcDEFghi_Jkl-mno",
  "folderIds": ["1abcDEFghi_Jkl-mno"],
  "fileIds": ["file-id"],
  "count": 1,
  "files": [
    {
      "id": "file-id",
      "name": "invoice.pdf",
      "mimeType": "application/pdf",
      "size": "12345",
      "modifiedTime": "2026-06-19T10:00:00.000Z",
      "webViewLink": "https://drive.google.com/file/d/file-id/view",
      "webContentLink": "https://drive.google.com/uc?id=file-id&export=download"
    }
  ]
}
```

The Drive API request includes shared drive support through `supportsAllDrives=true` and `includeItemsFromAllDrives=true`.

## GDRIVE_READ system task

Use `GDRIVE_READ` when a workflow needs file metadata from Google Drive. Create the Google Drive connection from the integration UI first. A workflow can pass `connectionId` explicitly, or omit it to use the latest saved Google Drive connection.

```json
{
  "name": "read_g_drive",
  "taskReferenceName": "read_g_drive_ref",
  "type": "GDRIVE_READ",
  "inputParameters": {
    "connectionId": "finance-drive",
    "folderIds": "${workflow.input.driveFolderIds}",
    "fileIds": "${workflow.input.driveFileIds}",
    "maxFiles": 25,
    "mimeTypes": ["application/pdf", "image/png"]
  }
}
```

### Inputs

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `connectionId` | String | No | Stored Google Drive connection ID. When omitted, the task uses the most recently updated stored connection. |
| `folderIds` | List[String] or comma-separated String | No | Google Drive folder IDs, folder URLs, URLs with an `id` query parameter, or workflow input expression. |
| `fileIds` | List[String] or comma-separated String | No | Google Drive file IDs, file URLs, URLs with an `id` query parameter, or workflow input expression. |
| `folderId` | String | No | Legacy single-folder input. New workflow definitions should use `folderIds`. |
| `driveFolderId` | String | No | Legacy alias used when `folderId` is not supplied. |
| `oauthTokenJson` | String | No | Legacy compatibility input. New workflow definitions should use `connectionId` instead. |
| `maxFiles` | Integer or String | No | Maximum number of file metadata records to return. Defaults to `100`; values above `1000` are capped at `1000`. |
| `mimeTypes` | List[String] or comma-separated String | No | MIME types to keep from the Drive response. When omitted or empty, all returned file metadata is included. |

If neither `folderIds` nor `fileIds` is supplied, the task reads metadata for all non-trashed files visible to the connected account, up to `maxFiles`.

### Outputs

| Name | Type | Description |
| --- | --- | --- |
| `connectionId` | String | Stored Google Drive connection ID used by the task. |
| `folderId` | String | First normalized Google Drive folder ID used for the request, retained for backward compatibility. |
| `folderIds` | List[String] | Normalized folder IDs used for the request. |
| `fileIds` | List[String] | Normalized file IDs used for the request. |
| `files` | List[Object] | File metadata returned by Google Drive after optional MIME type filtering. |
| `count` | Integer | Number of files in `files`. |
| `error` | String | Error message when the task fails. |

Each item in `files` can include `id`, `name`, `mimeType`, `size`, `modifiedTime`, `webViewLink`, and `webContentLink`, depending on what Google Drive returns for that file.

## Failure behavior

The task fails with `FAILED_WITH_TERMINAL_ERROR` when required workflow inputs are missing or invalid before making a Drive request. Examples include no available stored connection, an unknown stored connection, an invalid Drive ID, or a non-numeric `maxFiles` string.

The task fails with `FAILED` when Google Drive or OAuth processing fails. Examples include invalid token JSON, an expired token that cannot be refreshed, a Drive API error response, or an interrupted Drive request.

REST endpoint validation and integration failures are returned as HTTP `400` responses.

## Deployment notes

Google Drive connections are stored in the configured Conductor persistence backend. For deployable environments, use a durable database such as PostgreSQL or MySQL and include that database in your backup and restore plan. SQLite and in-memory storage are appropriate only for local development.

Treat `oauthClientJson` and `oauthTokenJson` as secrets:

- Do not commit OAuth client JSON, access tokens, refresh tokens, or exported connection payloads.
- Use the integration UI or REST API to create connections after deployment.
- Restrict access to `/api/integrations/gdrive/**` with the same authentication and authorization controls used for other administrative Conductor APIs.
- Rotate the Google OAuth client secret or refresh token if a connection export or database backup is exposed.

Configure the Google OAuth app with redirect URIs that match each deployed UI origin. For example, if the UI is served at `https://conductor.example.com`, register:

```text
https://conductor.example.com/integrations/gdrive/callback
```

When deploying behind a reverse proxy or load balancer, make sure the browser-visible UI URL, the OAuth redirect URI, and the API base URL used by the UI all resolve to the same deployed environment. Mismatched localhost callback URLs are the most common cause of successful local setup failing after deployment.

If multiple Conductor server instances are running, all instances must use the same persistence backend so stored Google Drive connections are visible to every instance.

## Implementation references

- REST controller: `rest/src/main/java/com/netflix/conductor/rest/controllers/IntegrationsResource.java`
- Integration service and DTOs: `common/src/main/java/org/conductoross/conductor/common/integrations/gdrive/`
- System task: `core/src/main/java/com/netflix/conductor/core/execution/tasks/GDriveRead.java`
- Task mapper: `core/src/main/java/com/netflix/conductor/core/execution/mapper/GDriveReadTaskMapper.java`
- UI task form: `ui-next/src/pages/definition/EditorPanel/TaskFormTab/forms/GDriveReadTaskForm.tsx`
