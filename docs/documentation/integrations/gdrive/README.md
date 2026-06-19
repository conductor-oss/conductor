---
description: "Use the Google Drive integration to exchange OAuth credentials and read file metadata from Google Drive folders in Conductor workflows."
---

# Google Drive Integration

The Google Drive integration reads file metadata from a Google Drive folder using OAuth token JSON supplied at runtime. It is available through:

- REST endpoints under `/api/integrations/gdrive`
- The `GDRIVE_READ` system task
- The Google Drive task form in the workflow definition UI

The integration returns metadata only. It does not download file contents.

## Authentication

The integration uses Google OAuth token JSON. The token JSON must include one of these forms:

- `access_token` or `token`
- `refresh_token`, `client_id`, and `client_secret`

When a refresh token is present, Conductor can request a new access token without starting an interactive browser flow. If a Google Drive request returns `401` and the token JSON can be refreshed, the integration refreshes the access token and retries the Drive request once.

OAuth client JSON can be supplied in either Google `installed` or `web` client format. The token exchange endpoint reads `client_id`, `client_secret`, and optional `token_uri` from that JSON. If `token_uri` is omitted, Conductor uses `https://oauth2.googleapis.com/token`.

## REST API

### Exchange an OAuth authorization code

`POST /api/integrations/gdrive/oauth/token`

Request body:

```json
{
  "authorizationCode": "<authorization-code>",
  "oauthClientJson": "{\"installed\":{\"client_id\":\"...\",\"client_secret\":\"...\"}}",
  "redirectUri": "http://localhost:3000/integrations/gdrive/callback"
}
```

Response body:

```json
{
  "oauthTokenJson": "{\n  \"access_token\" : \"...\",\n  \"refresh_token\" : \"...\",\n  \"client_id\" : \"...\",\n  \"client_secret\" : \"...\",\n  \"token_uri\" : \"https://oauth2.googleapis.com/token\",\n  \"redirect_uri\" : \"http://localhost:3000/integrations/gdrive/callback\"\n}"
}
```

### Load folder metadata

`POST /api/integrations/gdrive/load`

Request body:

```json
{
  "folderId": "1abcDEFghi_Jkl-mno",
  "oauthTokenJson": "{\"refresh_token\":\"...\",\"client_id\":\"...\",\"client_secret\":\"...\"}",
  "maxFiles": 25,
  "mimeTypes": ["application/pdf", "image/png"]
}
```

`folderId` can be a raw folder ID, a folder URL containing `/folders/{id}`, or a URL with an `id` query parameter. After extraction, the folder ID must contain only letters, numbers, underscores, or dashes.

Response body:

```json
{
  "folderId": "1abcDEFghi_Jkl-mno",
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

Use `GDRIVE_READ` when a workflow needs file metadata from a Drive folder.

```json
{
  "name": "read_drive_folder",
  "taskReferenceName": "read_drive_folder_ref",
  "type": "GDRIVE_READ",
  "inputParameters": {
    "folderId": "${workflow.input.folderId}",
    "oauthTokenJson": "${workflow.input.oauthTokenJson}",
    "maxFiles": 25,
    "mimeTypes": ["application/pdf", "image/png"]
  }
}
```

### Inputs

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `folderId` | String | Yes | Google Drive folder ID, folder URL, URL with an `id` query parameter, or workflow input expression. |
| `driveFolderId` | String | No | Legacy alias used when `folderId` is not supplied. |
| `oauthTokenJson` | String | Yes | OAuth token JSON string containing `access_token` or `token`, or refresh credentials. |
| `maxFiles` | Integer or String | No | Maximum number of file metadata records to return. Defaults to `100`; values above `1000` are capped at `1000`. |
| `mimeTypes` | List[String] or comma-separated String | No | MIME types to keep from the Drive response. When omitted or empty, all returned file metadata is included. |

### Outputs

| Name | Type | Description |
| --- | --- | --- |
| `folderId` | String | Normalized Google Drive folder ID used for the request. |
| `files` | List[Object] | File metadata returned by Google Drive after optional MIME type filtering. |
| `count` | Integer | Number of files in `files`. |
| `error` | String | Error message when the task fails. |

Each item in `files` can include `id`, `name`, `mimeType`, `size`, `modifiedTime`, `webViewLink`, and `webContentLink`, depending on what Google Drive returns for that file.

## Failure behavior

The task fails with `FAILED_WITH_TERMINAL_ERROR` when required workflow inputs are missing or invalid before making a Drive request. Examples include missing `folderId`, missing `oauthTokenJson`, or a non-numeric `maxFiles` string.

The task fails with `FAILED` when Google Drive or OAuth processing fails. Examples include invalid token JSON, an expired token that cannot be refreshed, a Drive API error response, or an interrupted Drive request.

REST endpoint validation and integration failures are returned as HTTP `400` responses.

## Implementation references

- REST controller: `rest/src/main/java/com/netflix/conductor/rest/controllers/IntegrationsResource.java`
- Integration service and DTOs: `common/src/main/java/org/conductoross/conductor/common/integrations/gdrive/`
- System task: `core/src/main/java/com/netflix/conductor/core/execution/tasks/GDriveRead.java`
- Task mapper: `core/src/main/java/com/netflix/conductor/core/execution/mapper/GDriveReadTaskMapper.java`
- UI task form: `ui-next/src/pages/definition/EditorPanel/TaskFormTab/forms/GDriveReadTaskForm.tsx`
