# GRN/POD Reconciliation Workflow

This package defines a four-task Conductor workflow and uses SDKs for external integrations:

1. `grn_pod_fetch_gdrive` fetches PDF/image files from Google Drive into local storage.
2. `grn_pod_classify_document` uses Gemini to classify each file as GRN, POD, or UNKNOWN.
3. `grn_pod_ocr_extract_document` uses Gemini OCR/vision to extract document and line-item data into JSON and CSV.
4. `grn_pod_reconcile_result` reconciles GRN and POD extracted rows and writes the final JSON and CSV result.

The Conductor metadata is embedded in `register_metadata.py`; if local `metadata/workflow.json` and `metadata/task_defs.json` files exist, the script uses those instead. The worker implementation is in `worker.py`.

SDK usage:

- Conductor Python SDK for metadata registration, workflow start, task polling, and task completion.
- Google Drive API Python client for file listing and downloads.
- Google Gen AI SDK for Gemini classification and OCR.

## Configuration

Create `.env` from `.env.example` and fill in the values. Keep secrets and credential files local.

Required values:

- `CONDUCTOR_API_BASE_URL`: Conductor REST base URL. It can include or omit `/api`.
- `GOOGLE_DRIVE_FOLDER_ID`: Drive folder containing GRN/POD PDFs or images.
- `GEMINI_API_BASE_URL`: optional Gemini API base URL override. Leave empty to use the SDK default endpoint.
- `GEMINI_API_KEY`: Gemini API key.
- `GEMINI_MODEL`: Gemini model to use for classification and OCR.
- `CONDUCTOR_GEMINI_PROMPT_DIR`: Gemini prompt template directory for the Conductor server. Use `prompts` when running from the repository root.

Gemini authentication modes:

- API key mode: `GEMINI_AUTH_MODE=api_key` and `GEMINI_API_KEY`.
- Vertex AI project mode: `GEMINI_AUTH_MODE=vertex_ai`, `GEMINI_VERTEX_PROJECT`, and `GEMINI_VERTEX_LOCATION`.

Use Vertex AI mode when an API key is tied to a blocked Google project. In Vertex AI mode the client uses `GEMINI_GOOGLE_APPLICATION_CREDENTIALS`, `GOOGLE_APPLICATION_CREDENTIALS`, or local Application Default Credentials from `gcloud auth application-default login`.

Google Drive authentication options:

- Service account: set `GOOGLE_DRIVE_AUTH_MODE=service_account` and `GOOGLE_APPLICATION_CREDENTIALS`.
- OAuth client: set `GOOGLE_DRIVE_AUTH_MODE=oauth`, `GOOGLE_CLIENT_SECRET_FILE`, `GOOGLE_TOKEN_FILE`, and `GOOGLE_OAUTH_REDIRECT_PORT`.
- OAuth in Conductor workers is always non-interactive. Create `GOOGLE_TOKEN_FILE` locally first, or provide the token with `GOOGLE_OAUTH_TOKEN_JSON` or `GOOGLE_OAUTH_TOKEN_JSON_BASE64`.

Conductor workers cannot bypass Google Drive authorization. They must either use a service account or reuse an OAuth token that was created ahead of time. The worker fails fast if OAuth mode is selected and no valid reusable token is available.

For OAuth, register this exact redirect URI in Google Cloud Console for the OAuth client:

```text
http://localhost:8089/
```

Then create or refresh the local OAuth token:

```bash
python authorize_gdrive.py
```

`authorize_gdrive.py` is the only script that opens the browser OAuth flow. `worker.py` never opens a browser during Conductor task execution.

Local paths:

- `LOCAL_INPUT_DIR` stores downloaded Drive files.
- `LOCAL_OUTPUT_DIR` stores `classification.json`, `extracted_data.json`, `extracted_data.csv`, `reconciliation_result.json`, and `reconciliation_result.csv`.
- `CONDUCTOR_GEMINI_PROMPT_DIR=prompts` loads server-side Gemini prompt templates from `prompts/`.

## Install

From the repository root, enter this package directory:

```bash
cd GRN_POD_Reconciliation
```

macOS/Linux:

```bash
python3 -m venv .venv
source .venv/bin/activate
python3 -m pip install -r requirements.txt
```

Windows PowerShell:

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
```

### Docker Compose From This Repository

Use this option when you want to run the Conductor server image built from this checkout:

```powershell
cd finteract-conductor
docker compose --env-file .env -f docker\docker-compose.yaml up --build
```

Run this command from the repository root so `.env`, `docker\docker-compose.yaml`, and `prompts\` all resolve as relative paths. The compose file in this repository maps container port `8080` to host port `8000`.

The server-side Gemini prompt templates are loaded from `CONDUCTOR_GEMINI_PROMPT_DIR`. For this checkout, keep the value relative:

```text
CONDUCTOR_GEMINI_PROMPT_DIR=prompts
```

The expected prompt files are:

- `prompts\attachment_classify.j2`
- `prompts\grn_extraction.j2`
- `prompts\pod_extraction.j2`

Local URLs:

| Purpose | URL |
|---|---|
| Conductor UI / server | `http://localhost:8000` |
| REST API base URL | `http://localhost:8000/api` |
| Swagger UI | `http://localhost:8000/swagger-ui/index.html` |
| Health check | `http://localhost:8000/health` |

Set this in `.env`:

```text
CONDUCTOR_API_BASE_URL=http://localhost:8000/api
```

Stop the stack when done:

```powershell
docker compose --env-file .env -f docker\docker-compose.yaml down
```


## Register Metadata

The script uses the Conductor Python SDK rather than direct HTTP calls.

```bash
python register_metadata.py
```

The same metadata can be registered from Swagger UI if needed. Use the definitions embedded in `register_metadata.py`, or local `metadata/workflow.json` and `metadata/task_defs.json` files if you keep them in your workspace:

- Task definitions: `POST /api/metadata/taskdefs`
- Workflow definition: `POST /api/metadata/workflow`

Register task definitions first, then register the workflow definition. The worker must be running separately before the workflow can make progress.

## Run Workers

Start the worker process before or immediately after starting a workflow execution:

```bash
python worker.py
```

On Windows, the adapter runs Conductor task runners in threads instead of SDK subprocesses. This avoids Python multiprocessing pickling failures for the dynamic function workers.

## Start Workflow

```bash
python start_workflow.py
```

## Execute Workflow And Wait For Output

Start `worker.py` first, then run:

```bash
python execute_workflow_sync.py
```

The synchronous response is printed and saved to `output/workflow_execute_response.json`.

## Swagger API Execution

Use this section when starting the workflow from Conductor Swagger UI.

Before calling the API:

1. Register the task definitions and workflow definition.
2. Start the Python worker with `python worker.py`.
3. Confirm Google Drive auth is non-interactive and ready. Use a service account or run `python authorize_gdrive.py` once to create `GOOGLE_TOKEN_FILE`.
4. Confirm Gemini credentials are available in `.env`.

The workflow name is `grn_pod_reconciliation` and the version is `1`.

### Postman Setup

Create a Postman environment with this variable:

| Variable | Value |
|---|---|
| `baseUrl` | `http://localhost:8080/api` |

For every request below, add this header:

```text
Content-Type: application/json
```

Do not put Google, Gemini, OAuth, service account, or Conductor credentials in the Postman body. Keep credentials in `.env` or in the worker process environment.

### API Call Sequence

1. Start Conductor with Docker.
2. Register metadata with `python register_metadata.py`.
3. Start the Python workers with `python worker.py`.
4. Start the workflow from Swagger or Postman.
5. Read workflow output from the synchronous response, Conductor UI, or the workflow status API.

Postman can call Conductor APIs, but it does not run the worker. Keep the `python worker.py` terminal open while the workflow executes.

### Register Metadata From Postman

The recommended path is still:

```bash
python register_metadata.py
```

If metadata must be registered through Postman, use the embedded JSON objects in `register_metadata.py`.

Task definitions:

```text
POST {{baseUrl}}/metadata/taskdefs
```

Request body: the `TASK_DEFS` array from `register_metadata.py`.

Workflow definition:

```text
POST {{baseUrl}}/metadata/workflow
```

Request body: the `WORKFLOW_DEF` object from `register_metadata.py`.

Register task definitions first, then register the workflow definition.

### Option 1: Synchronous Execute

Use this when Swagger should wait and return the workflow result.

Swagger operation:

```text
POST /api/workflow/execute/grn_pod_reconciliation/1
```

Postman URL:

```text
POST {{baseUrl}}/workflow/execute/grn_pod_reconciliation/1?waitForSeconds=300&consistency=DURABLE&returnStrategy=TARGET_WORKFLOW
```

Recommended query parameters:

```text
waitForSeconds=300
consistency=DURABLE
returnStrategy=TARGET_WORKFLOW
```

Request body:

```json
{
  "name": "grn_pod_reconciliation",
  "version": 1,
  "input": {
    "driveFolderId": "YOUR_GOOGLE_DRIVE_FOLDER_ID",
    "localInputDir": "data/input",
    "mimeTypes": [
      "application/pdf",
      "image/jpeg",
      "image/png",
      "image/tiff",
      "image/webp"
    ],
    "maxFiles": 10
  }
}
```

`swagger_execute_body.json` contains the same body and can be pasted directly into Swagger.

Expected response:

- The response is JSON.
- If the workflow completes within `waitForSeconds`, the response includes workflow output.
- If the workflow is still running when the wait limit is reached, use the workflow ID from the response to inspect execution status in the Conductor UI.

### Option 2: Start Async And Poll Later

Use this when Swagger should return immediately with a workflow ID.

Swagger operation:

```text
POST /api/workflow
```

Postman URL:

```text
POST {{baseUrl}}/workflow
```

Request body:

```json
{
  "name": "grn_pod_reconciliation",
  "version": 1,
  "input": {
    "driveFolderId": "YOUR_GOOGLE_DRIVE_FOLDER_ID",
    "localInputDir": "data/input",
    "mimeTypes": [
      "application/pdf",
      "image/jpeg",
      "image/png",
      "image/tiff",
      "image/webp"
    ],
    "maxFiles": 10
  }
}
```

Expected response:

```text
<workflow-id>
```

Open the workflow ID in the Conductor UI to inspect the task statuses and output.

### Option 3: Start By Workflow Name

Use this if the Swagger operation asks for workflow input only in the request body.

Swagger operation:

```text
POST /api/workflow/grn_pod_reconciliation?version=1
```

Postman URL:

```text
POST {{baseUrl}}/workflow/grn_pod_reconciliation?version=1
```

Request body:

```json
{
  "driveFolderId": "YOUR_GOOGLE_DRIVE_FOLDER_ID",
  "localInputDir": "data/input",
  "mimeTypes": [
    "application/pdf",
    "image/jpeg",
    "image/png",
    "image/tiff",
    "image/webp"
  ],
  "maxFiles": 10
}
```

Expected response:

```text
<workflow-id>
```

### Get Workflow Status

Use this after async start or after sync execution returns a workflow ID:

```text
GET {{baseUrl}}/workflow/{{workflowId}}?includeTasks=true
```

The response contains workflow status, task execution details, task outputs, and final workflow output when completed.

### Input Fields

| Field | Required | Example | Notes |
|---|---:|---|---|
| `driveFolderId` | Yes | `1kFNN32E3EhSVjFVDp4nCXrcxKY9sdLPl` | Google Drive folder ID or folder URL. The worker extracts the folder ID if a URL is provided. |
| `localInputDir` | No | `data/input` | Local folder where the worker downloads PDFs/images. Relative paths resolve under `GRN_POD_Reconciliation`. |
| `mimeTypes` | No | `["application/pdf", "image/jpeg"]` | Supported values are PDF, JPEG, PNG, TIFF, and WEBP. |
| `maxFiles` | No | `10` | Limits how many supported documents are downloaded from Drive. Omit or set `null` for no explicit limit. |

### Output Fields

The workflow output contains:

| Field | Meaning |
|---|---|
| `summary` | Counts of GRN/POD documents, invoice-matched documents, item-count matches/mismatches, row matches, row mismatches, and total reconciliation rows. |
| `records` | Complete OCR/extraction records for all documents, including classification and extracted line items. |
| `grnRecords` | Extracted records classified as GRN. |
| `podRecords` | Extracted records classified as POD. |
| `documentMatches` | Document-level matching result. Documents are matched by normalized identifiers collected across the extracted dictionary and classification identifiers. Each match includes the field combination used, such as `invoice_number=document_number`, plus GRN/POD item counts. |
| `reconciliationRows` | Document-level item-count rows plus row-level comparison between GRN and POD line items. |
| `reconciliationJsonPath` | Local path to the final reconciliation JSON. |
| `reconciliationCsvPath` | Local path to the final reconciliation CSV. |
| `extractedJsonPath` | Local path to OCR/extraction JSON. |
| `extractedCsvPath` | Local path to OCR/extraction CSV. |

The final output is intentionally verbose so Swagger users can inspect the complete chain without opening local files: fetched document metadata is carried into classification, classification is carried into OCR records, and OCR records are carried into reconciliation.

### Reconciliation Logic

Reconciliation pairs documents before comparing line items. The worker builds a fuzzy identifier dictionary for every GRN and POD record by scanning the full extracted JSON and classification identifiers, not only the top-level fields.

The identifier scan includes invoice numbers, document numbers, reference numbers, PO numbers, GRN numbers, delivery numbers, and inward numbers. This supports combinations such as:

- GRN `invoice_number` equals POD `document_number`.
- GRN `document_number` equals POD `invoice_number`.
- GRN `invoice_number` equals POD `invoice_number`.
- Any extracted invoice or document number is found deeper inside the extracted dictionary or `classification.identifiers`.

The matching order is:

1. Exact normalized identifier match across any supported GRN/POD field combination.
2. Numeric-only identifier match when OCR changes prefixes but keeps the same invoice digits, for example `BRL-INV-012584` versus `BLR-INV-012584`.
3. Unmatched document reporting when no unique POD candidate is found.

For every matched GRN/POD pair, the worker first emits an item-count row:

- `ITEM_COUNT_MATCHED` when GRN and POD have the same number of line items.
- `ITEM_COUNT_MISMATCH` when the line item counts differ.

After the item-count check, line items are reconciled by normalized item description first, then SKU, then item code. This avoids false mismatches when OCR extracts the same generic item code for multiple invoice lines.

### Swagger Checklist

- If the first task stays `SCHEDULED` with `poll_count: 0`, the Python worker is not polling. Start or restart `python worker.py`.
- If Drive auth fails, run `python authorize_gdrive.py` locally once or configure a service account. Swagger execution never opens OAuth in the browser.
- If sync execution times out but tasks continue running, increase `waitForSeconds` or use async start and inspect the workflow ID.
- Do not paste secrets into Swagger request bodies. Keep Google, Gemini, and Conductor credentials in `.env` or the worker process environment.

## Debug Without Conductor

To validate the integration logic step by step without Conductor:

```bash
python run_steps_without_conductor.py
```

Each stage writes its input, output, or failure details under `output/step_debug/`.

Useful debug modes:

```bash
python run_steps_without_conductor.py --fetch-only
python run_steps_without_conductor.py --local-only
python run_steps_without_conductor.py --start-at classify
python run_steps_without_conductor.py --start-at ocr
python run_steps_without_conductor.py --start-at reconcile
```

The workflow can also be started directly from the Conductor UI using workflow name `grn_pod_reconciliation` and version `1`.

## Classification Rules

The classifier returns only `GRN`, `POD`, or `UNKNOWN`.

- `GRN`: an internal goods receipt note with explicit GRN signals such as `GRN No`, inbound number, received quantity, or `Recv qty`.
- `POD`: proof of delivery evidence, including signed or stamped delivery documents and tax invoices with `GOODS RECEIVED`, `GOODS RECEIPT`, inward number, receiver stamp, signature, or delivery acknowledgment.
- `UNKNOWN`: any document that does not clearly match GRN or POD.

For this workflow, a stamped tax invoice is treated as `POD`, not `GRN`, unless it also has explicit GRN fields. The worker normalizes Gemini output and filename hints such as `*-POD.jpeg` and `*-GRN.pdf` before passing records to OCR and reconciliation.

## Troubleshooting

If the fetch task waits in Conductor with `poll_count: 0`, the worker is not polling. Restart `python worker.py` and check the worker logs for SDK startup errors.

If Google Drive fetch asks for OAuth during workflow execution, stop the old worker process and restart it from the current code. The patched worker loads `GOOGLE_TOKEN_FILE` or `GOOGLE_OAUTH_TOKEN_JSON*` and never starts browser OAuth from a Conductor task.

If Gemini labels a stamped invoice as `GRN`, rerun with the current worker. The classifier prompt and normalization rules classify stamped or signed tax invoices as `POD`.

Worker runtime logs are ignored by Git via `*.log`. Local downloads, outputs, `.env`, OAuth token files, and credential files are also ignored.
