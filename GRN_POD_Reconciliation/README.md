# GRN/POD Reconciliation Workflow

This package defines a four-task Conductor workflow and uses SDKs for external integrations:

1. `grn_pod_fetch_gdrive` fetches PDF/image files from Google Drive into local storage.
2. `grn_pod_classify_document` uses Gemini to classify each file as GRN, POD, or UNKNOWN.
3. `grn_pod_ocr_extract_document` uses Gemini OCR/vision to extract document and line-item data into JSON and CSV.
4. `grn_pod_reconcile_result` reconciles GRN and POD extracted rows and writes the final JSON and CSV result.

The Conductor metadata is in `metadata/workflow.json` and `metadata/task_defs.json`. The worker implementation is in `worker.py`.

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

```powershell
python authorize_gdrive.py
```

`authorize_gdrive.py` is the only script that opens the browser OAuth flow. `worker.py` never opens a browser during Conductor task execution.

Local paths:

- `LOCAL_INPUT_DIR` stores downloaded Drive files.
- `LOCAL_OUTPUT_DIR` stores `classification.json`, `extracted_data.json`, `extracted_data.csv`, `reconciliation_result.json`, and `reconciliation_result.csv`.

## Install

```powershell
cd D:\Profintech\Work\finteract-conductor\GRN_POD_Reconciliation
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
```

## Register Metadata

The script uses the Conductor Python SDK rather than direct HTTP calls.

```powershell
python register_metadata.py
```

## Run Workers

Start the worker process before or immediately after starting a workflow execution:

```powershell
python worker.py
```

On Windows, the adapter runs Conductor task runners in threads instead of SDK subprocesses. This avoids Python multiprocessing pickling failures for the dynamic function workers.

## Start Workflow

```powershell
python start_workflow.py
```

## Execute Workflow And Wait For Output

Start `worker.py` first, then run:

```powershell
python execute_workflow_sync.py
```

The synchronous response is printed and saved to `output/workflow_execute_response.json`.

## Debug Without Conductor

To validate the integration logic step by step without Conductor:

```powershell
python run_steps_without_conductor.py
```

Each stage writes its input, output, or failure details under `output/step_debug/`.

Useful debug modes:

```powershell
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
