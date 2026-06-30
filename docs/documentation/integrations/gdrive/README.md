---
description: "Use the Google Drive integration to store account-level OAuth credentials and read file metadata from Google Drive in Conductor workflows."
---

# Google Drive Integration

The Google Drive integration reads file metadata from Google Drive using account-level OAuth credentials stored under a `connectionId`. It is available through:

- REST endpoints under `/api/integrations/gdrive`
- The `GDRIVE_READ` system task
- The Google Drive task form in the workflow definition UI

The integration returns metadata only. It does not download file contents. Account credentials are stored once under a `connectionId`; runtime restrictions such as `folderIds`, `fileIds`, `maxFiles`, and `mimeTypes` are request inputs. OAuth credentials are not folder-specific and should not be embedded in workflow JSON.

Google Drive metadata can be passed directly to the `GEMINI_LLM` system task. When a Gemini task receives Drive file metadata, it downloads file bytes using the saved Google Drive connection and sends them to Gemini with either an inline `prompt` or a server-side `.j2` prompt template.

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

## Gemini LLM handoff

Use `GEMINI_LLM` after `GDRIVE_READ` when a workflow needs to classify or extract data from Drive documents. The task accepts file metadata from `GDRIVE_READ` through `files`, `documents`, or `classifiedDocuments`.

### Gemini configuration

The server reads Gemini defaults from environment variables:

| Name | Required | Description |
| --- | ---: | --- |
| `CONDUCTOR_GEMINI_API_KEY` | Yes | Default Gemini API key used when a task does not provide `apiKey`. |
| `CONDUCTOR_GEMINI_CONNECTION_ID` | No | Default Gemini connection ID. Defaults to `gemini-default`. |
| `CONDUCTOR_GEMINI_MODEL` | No | Default Gemini model. Defaults to `gemini-2.5-flash`. |
| `CONDUCTOR_GEMINI_PROMPT_DIR` | No | Prompt template directory. Defaults to `prompts`. Use `prompts` for this repository. |

When running this checkout with Docker Compose, start the server from the repository root:

```powershell
docker compose --env-file .env -f docker\docker-compose.yaml up --build
```

Keep prompt loading relative in `.env`:

```text
CONDUCTOR_GEMINI_PROMPT_DIR=prompts
```

The prompt names used by workflow tasks map to `.j2` files under `prompts\`:

| Task input `promptname` | Template file |
| --- | --- |
| `attachment_classify` | `prompts\attachment_classify.j2` |
| `grn_extraction` | `prompts\grn_extraction.j2` |
| `pod_extraction` | `prompts\pod_extraction.j2` |

Prompt names are normalized to the file name without `.j2`; do not pass absolute paths in workflow JSON.

### Gemini REST API

Save or override a Gemini connection:

```text
POST /api/integrations/gemini/connections
```

Request body:

```json
{
  "connectionId": "gemini-default",
  "apiKey": "<gemini-api-key>",
  "model": "gemini-2.5-flash",
  "promptName": "attachment_classify"
}
```

List Gemini connections:

```text
GET /api/integrations/gemini/connections
```

Delete a Gemini connection:

```text
DELETE /api/integrations/gemini/connections/{connectionId}
```

List available prompt templates and defaults:

```text
GET /api/integrations/gemini/prompts
```

Response body:

```json
{
  "promptDirectory": "prompts",
  "prompts": [
    "attachment_classify",
    "grn_extraction",
    "pod_extraction"
  ],
  "defaultConnectionId": "gemini-default",
  "defaultModel": "gemini-2.5-flash"
}
```

### GEMINI_LLM system task

```json
{
  "name": "classify_documents",
  "taskReferenceName": "classify_documents_ref",
  "type": "GEMINI_LLM",
  "inputParameters": {
    "connectionId": "gemini-default",
    "model": "gemini-2.5-flash",
    "jsonOutput": true,
    "promptname": "attachment_classify",
    "gdriveConnectionId": "${workflow.input.gdriveConnectionId}",
    "files": "${read_g_drive_ref.output.files}"
  }
}
```

Inputs:

| Name | Type | Required | Description |
| --- | --- | ---: | --- |
| `connectionId` | String | No | Gemini connection ID. Defaults to `CONDUCTOR_GEMINI_CONNECTION_ID` when available. |
| `apiKey` | String | No | Per-task Gemini API key override. Prefer server configuration or a saved connection. |
| `model` | String | No | Gemini model override. |
| `jsonOutput` | Boolean | No | Requests JSON output from Gemini. Defaults to `true`. |
| `promptname` | String | No | Prompt template name. The task loads `{promptname}.j2` from `CONDUCTOR_GEMINI_PROMPT_DIR`. |
| `promptName` | String | No | Legacy alias for `promptname`. |
| `prompt` | String | No | Inline prompt text. Used when no usable prompt template name is provided. |
| `promptVariables` | Object | No | Values substituted into prompt templates using `{{ key }}` or `{{key}}`. Nested values can be referenced with dotted keys. |
| `files` | List[Object] | No | Drive file metadata from `GDRIVE_READ`, local file records, or classified document records. |
| `documents` | List[Object] | No | Alias for `files`. |
| `classifiedDocuments` | List[Object] | No | Alias for `files`, useful after classification. |
| `gdriveConnectionId` | String | No | Google Drive connection used to download Drive file bytes when file records do not include `localPath`. |

At least one of `promptname`, `promptName`, or `prompt` must resolve to a real prompt. Placeholder UI values such as `enterj2 name`, `select j2`, `enter your prompt`, and `paste your prompt here` are ignored.

File records can include:

| Name | Description |
| --- | --- |
| `localPath` | Relative or absolute local file path readable by the server process. |
| `id` | Google Drive file ID from `GDRIVE_READ`. |
| `driveFileId` | Explicit Google Drive file ID. |
| `mimeType` | File MIME type. Defaults to `application/pdf` when omitted. |

For portable workflows, prefer Drive file IDs from `GDRIVE_READ` instead of machine-specific local paths.

Outputs:

| Name | Type | Description |
| --- | --- | --- |
| `promptname` | String | Prompt template name used by the task. |
| `promptName` | String | Same value as `promptname`. |
| `model` | String | Gemini model used by the task. |
| `responses` | List[Object] | One response per input document, including original file metadata and `result`. |
| `results` | List[Object] | Alias for `responses`. |
| `classifiedDocuments` | List[Object] | Present for classification prompts. Contains original file metadata and `classification`. |
| `grnDocuments` | List[Object] | Documents classified as GRN. |
| `podDocuments` | List[Object] | Documents classified as POD. |
| `grn` | List[Object] | Alias for `grnDocuments`. |
| `pod` | List[Object] | Alias for `podDocuments`. |
| `records` | List[Object] | Present for extraction prompts. Contains original file metadata and `extracted`. |

Classification prompts are detected by the `attachment_classify` prompt name or any prompt name containing `classify`. Classification output is routed into `grnDocuments` and `podDocuments` for downstream extraction.

### GDrive to Gemini workflow example

This workflow reads Drive file metadata, classifies attachments, then runs GRN and POD extraction in parallel.

```json
{
  "name": "gdrive_gemini_demo",
  "description": "Classify Google Drive documents and extract GRN/POD data with Gemini",
  "version": 1,
  "tasks": [
    {
      "name": "read_g_drive",
      "taskReferenceName": "read_g_drive_ref",
      "type": "GDRIVE_READ",
      "inputParameters": {
        "connectionId": "${workflow.input.gdriveConnectionId}",
        "folderIds": "${workflow.input.driveFolderIds}",
        "fileIds": "${workflow.input.driveFileIds}",
        "maxFiles": 100
      }
    },
    {
      "name": "attachment_classify",
      "taskReferenceName": "attachment_classify_ref",
      "type": "GEMINI_LLM",
      "inputParameters": {
        "connectionId": "gemini-default",
        "model": "gemini-2.5-flash",
        "jsonOutput": true,
        "promptname": "attachment_classify",
        "gdriveConnectionId": "${workflow.input.gdriveConnectionId}",
        "files": "${read_g_drive_ref.output.files}"
      }
    },
    {
      "name": "extract_by_type",
      "taskReferenceName": "extract_by_type_ref",
      "type": "FORK_JOIN",
      "inputParameters": {},
      "forkTasks": [
        [
          {
            "name": "extract_grn",
            "taskReferenceName": "extract_grn_ref",
            "type": "GEMINI_LLM",
            "inputParameters": {
              "connectionId": "gemini-default",
              "model": "gemini-2.5-flash",
              "jsonOutput": true,
              "promptname": "grn_extraction",
              "gdriveConnectionId": "${workflow.input.gdriveConnectionId}",
              "files": "${attachment_classify_ref.output.grnDocuments}"
            }
          }
        ],
        [
          {
            "name": "extract_pod",
            "taskReferenceName": "extract_pod_ref",
            "type": "GEMINI_LLM",
            "inputParameters": {
              "connectionId": "gemini-default",
              "model": "gemini-2.5-flash",
              "jsonOutput": true,
              "promptname": "pod_extraction",
              "gdriveConnectionId": "${workflow.input.gdriveConnectionId}",
              "files": "${attachment_classify_ref.output.podDocuments}"
            }
          }
        ]
      ]
    },
    {
      "name": "join_extractions",
      "taskReferenceName": "join_extractions_ref",
      "type": "JOIN",
      "inputParameters": {},
      "joinOn": [
        "extract_grn_ref",
        "extract_pod_ref"
      ]
    }
  ],
  "inputParameters": [
    "gdriveConnectionId",
    "driveFolderIds",
    "driveFileIds"
  ],
  "outputParameters": {
    "classified": "${attachment_classify_ref.output}",
    "grn": "${extract_grn_ref.output.records}",
    "pod": "${extract_pod_ref.output.records}"
  },
  "schemaVersion": 2,
  "restartable": true,
  "workflowStatusListenerEnabled": false,
  "ownerEmail": "example@email.com",
  "timeoutPolicy": "ALERT_ONLY",
  "timeoutSeconds": 0
}
```

## Failure behavior

The task fails with `FAILED_WITH_TERMINAL_ERROR` when required workflow inputs are missing or invalid before making a Drive request. Examples include no available stored connection, an unknown stored connection, an invalid Drive ID, or a non-numeric `maxFiles` string.

The task fails with `FAILED` when Google Drive or OAuth processing fails. Examples include invalid token JSON, an expired token that cannot be refreshed, a Drive API error response, or an interrupted Drive request.

`GEMINI_LLM` fails when Gemini credentials are missing, no prompt can be resolved, a named prompt template is missing, a file record has no `localPath`, `driveFileId`, or `id`, Drive file download fails, or Gemini returns non-JSON text while `jsonOutput` is `true`.

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

Gemini prompt templates are server-side files. Keep `CONDUCTOR_GEMINI_PROMPT_DIR` relative, such as `prompts`, when building and running from this repository. The Docker server image copies `prompts\` into the image, so the same relative prompt directory works on another machine after checkout and build.

## Implementation references

- REST controller: `rest/src/main/java/com/netflix/conductor/rest/controllers/IntegrationsResource.java`
- Integration service and DTOs: `common/src/main/java/org/conductoross/conductor/common/integrations/gdrive/`
- Gemini integration service and DTOs: `common/src/main/java/org/conductoross/conductor/common/integrations/gemini/`
- System task: `core/src/main/java/com/netflix/conductor/core/execution/tasks/GDriveRead.java`
- Gemini worker: `ai/src/main/java/org/conductoross/conductor/ai/tasks/worker/GeminiWorkflowWorkers.java`
- Task mapper: `core/src/main/java/com/netflix/conductor/core/execution/mapper/GDriveReadTaskMapper.java`
- UI task form: `ui-next/src/pages/definition/EditorPanel/TaskFormTab/forms/GDriveReadTaskForm.tsx`
- Gemini UI task form: `ui-next/src/pages/definition/EditorPanel/TaskFormTab/forms/GeminiLlmTaskForm.tsx`
