import csv
import base64
import json
import mimetypes
import os
import re
from pathlib import Path
from typing import Any
from urllib.parse import parse_qs, urlparse

from dotenv import load_dotenv
import google.auth
from google.auth.exceptions import DefaultCredentialsError
from google import genai
from google.genai import types
from google.oauth2 import service_account
from google.oauth2.credentials import Credentials
from google_auth_oauthlib.flow import InstalledAppFlow
from google.auth.transport.requests import Request
from googleapiclient.discovery import build
from googleapiclient.http import MediaIoBaseDownload

from conductor_sdk_adapter import run_task_workers


ROOT = Path(__file__).resolve().parent
DRIVE_SCOPES = ["https://www.googleapis.com/auth/drive.readonly"]
FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
SHORTCUT_MIME_TYPE = "application/vnd.google-apps.shortcut"
GOOGLE_DOC_EXPORT_MIME_TYPES = {
    "application/vnd.google-apps.document": ("application/pdf", ".pdf"),
    "application/vnd.google-apps.spreadsheet": (
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        ".xlsx",
    ),
    "application/vnd.google-apps.presentation": ("application/pdf", ".pdf"),
}
LIST_FIELDS = (
    "nextPageToken,"
    "files("
    "id,name,mimeType,modifiedTime,createdTime,size,webViewLink,"
    "parents,md5Checksum,shortcutDetails"
    ")"
)
SUPPORTED_MIME_TYPES = {
    "application/pdf",
    "image/jpeg",
    "image/png",
    "image/tiff",
    "image/webp",
}
CANONICAL_DOCUMENT_TYPES = {"GRN", "POD", "UNKNOWN"}


def required_env(name: str) -> str:
    value = os.getenv(name, "").strip()
    if not value:
        raise RuntimeError(f"{name} must be set in .env")
    return value


def optional_int(value: Any) -> int | None:
    if value in (None, ""):
        return None
    return int(value)


def env_bool(name: str, default: bool = False) -> bool:
    value = os.getenv(name)
    if value is None:
        return default
    return value_bool(value)


def value_bool(value: Any) -> bool:
    if isinstance(value, bool):
        return value
    if value is None:
        return False
    return str(value).strip().lower() in ("1", "true", "yes", "y", "on")


def resolve_path(value: str | None, default_relative: str) -> Path:
    raw = value or default_relative
    path = Path(raw)
    return path if path.is_absolute() else ROOT / path


def output_dir() -> Path:
    path = resolve_path(os.getenv("LOCAL_OUTPUT_DIR"), "output")
    path.mkdir(parents=True, exist_ok=True)
    return path


def safe_filename(name: str | None, fallback: str = "document") -> str:
    cleaned = re.sub(r'[<>:"/\\|?*\x00-\x1f]+', "_", name or fallback).strip().strip(".")
    return cleaned or fallback


def folder_id_from_value(value: str) -> str:
    if not value:
        raise ValueError("Folder URL or folder ID is required.")

    parsed = urlparse(value)
    if not parsed.scheme:
        return value.strip()

    match = re.search(r"/folders/([^/?#]+)", parsed.path)
    if match:
        return match.group(1)

    query = parse_qs(parsed.query)
    if "id" in query and query["id"]:
        return query["id"][0]

    raise ValueError(f"Could not parse folder ID from: {value}")


def unique_path(path: Path) -> Path:
    if not path.exists():
        return path

    stem = path.stem
    suffix = path.suffix
    parent = path.parent
    index = 2
    while True:
        candidate = parent / f"{stem} ({index}){suffix}"
        if not candidate.exists():
            return candidate
        index += 1


def oauth_credentials_from_env() -> Credentials | None:
    token_json = os.getenv("GOOGLE_OAUTH_TOKEN_JSON", "").strip()
    token_json_base64 = os.getenv("GOOGLE_OAUTH_TOKEN_JSON_BASE64", "").strip()
    if token_json_base64:
        token_json = base64.b64decode(token_json_base64).decode("utf-8")
    if not token_json:
        return None
    token_info = json.loads(token_json)
    return Credentials.from_authorized_user_info(token_info, DRIVE_SCOPES)


def drive_service(allow_interactive_oauth: bool = False):
    auth_mode = os.getenv("GOOGLE_DRIVE_AUTH_MODE", "service_account").strip().lower()
    print(f"Google Drive auth mode: {auth_mode}", flush=True)
    if auth_mode == "service_account":
        credentials_path = required_env("GOOGLE_APPLICATION_CREDENTIALS")
        print(f"Loading Google service account credentials from {credentials_path}", flush=True)
        credentials = service_account.Credentials.from_service_account_file(
            credentials_path,
            scopes=DRIVE_SCOPES,
        )
    elif auth_mode == "oauth":
        token_path = resolve_path(os.getenv("GOOGLE_TOKEN_FILE"), "token.json")
        credentials = oauth_credentials_from_env()
        if credentials:
            print("Loading Google OAuth token from environment", flush=True)
        if token_path.exists():
            print(f"Loading Google OAuth token from {token_path}", flush=True)
            credentials = Credentials.from_authorized_user_file(str(token_path), DRIVE_SCOPES)
        if credentials and credentials.expired and credentials.refresh_token:
            print("Refreshing expired Google OAuth token...", flush=True)
            credentials.refresh(Request())
        if not credentials or not credentials.valid:
            if not allow_interactive_oauth:
                raise RuntimeError(
                    "No valid Google OAuth token found for Google Drive. "
                    f"Expected a reusable token at {token_path} or in "
                    "GOOGLE_OAUTH_TOKEN_JSON/GOOGLE_OAUTH_TOKEN_JSON_BASE64. "
                    "Run authorize_gdrive.py locally once to create the token, "
                    "or use a service account."
                )

            client_secret_path = required_env("GOOGLE_CLIENT_SECRET_FILE")
            redirect_port = int(os.getenv("GOOGLE_OAUTH_REDIRECT_PORT", "8089"))
            no_browser = env_bool("GOOGLE_OAUTH_NO_BROWSER")
            print(
                "No valid Google OAuth token found. Opening browser for Google Drive authorization. "
                f"Using redirect URI http://localhost:{redirect_port}/. "
                f"Token will be saved to {token_path}",
                flush=True,
            )
            flow = InstalledAppFlow.from_client_secrets_file(client_secret_path, DRIVE_SCOPES)
            credentials = flow.run_local_server(
                port=redirect_port,
                open_browser=not no_browser,
                access_type="offline",
                include_granted_scopes="true",
                prompt="consent",
            )
            token_path.write_text(credentials.to_json(), encoding="utf-8")
    else:
        raise RuntimeError("GOOGLE_DRIVE_AUTH_MODE must be service_account or oauth")

    return build("drive", "v3", credentials=credentials, cache_discovery=False)


def list_children(service: Any, folder_id: str) -> list[dict[str, Any]]:
    files: list[dict[str, Any]] = []
    page_token = None
    query = f"'{folder_id}' in parents and trashed = false"

    while True:
        response = (
            service.files()
            .list(
                q=query,
                spaces="drive",
                fields=LIST_FIELDS,
                pageSize=1000,
                pageToken=page_token,
                includeItemsFromAllDrives=True,
                supportsAllDrives=True,
            )
            .execute()
        )
        files.extend(response.get("files", []))
        page_token = response.get("nextPageToken")
        if not page_token:
            return files


def download_request(request: Any, destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    with destination.open("wb") as handle:
        downloader = MediaIoBaseDownload(handle, request)
        done = False
        while not done:
            _, done = downloader.next_chunk()


def download_drive_file(service: Any, file_metadata: dict[str, Any], target_dir: Path) -> Path:
    target_dir.mkdir(parents=True, exist_ok=True)
    target_path = unique_path(target_dir / safe_filename(file_metadata["name"]))
    print(f"Downloading {file_metadata['name']} to {target_path}", flush=True)
    request = service.files().get_media(fileId=file_metadata["id"], supportsAllDrives=True)
    download_request(request, target_path)
    return target_path


def download_or_export_drive_file(
    service: Any, file_metadata: dict[str, Any], target_dir: Path
) -> dict[str, Any]:
    mime_type = file_metadata.get("mimeType")
    file_id = file_metadata.get("id") or file_metadata.get("driveFileId")
    if not file_id:
        raise RuntimeError(f"Drive document is missing id/driveFileId: {file_metadata}")

    target_dir.mkdir(parents=True, exist_ok=True)
    if mime_type in GOOGLE_DOC_EXPORT_MIME_TYPES:
        export_mime_type, suffix = GOOGLE_DOC_EXPORT_MIME_TYPES[mime_type]
        target_path = unique_path(
            target_dir / f"{safe_filename(file_metadata.get('name'), file_id)}{suffix}"
        )
        print(
            f"Exporting Google Workspace file {file_metadata.get('name')} to {target_path}",
            flush=True,
        )
        request = service.files().export_media(fileId=file_id, mimeType=export_mime_type)
        download_request(request, target_path)
        return {
            **file_metadata,
            "driveFileId": file_id,
            "mimeType": export_mime_type,
            "localPath": str(target_path),
        }

    if mime_type not in SUPPORTED_MIME_TYPES:
        raise RuntimeError(
            f"Unsupported Drive file mimeType for Gemini document processing: {mime_type}. "
            f"Supported files are {sorted(SUPPORTED_MIME_TYPES)} or Google Docs/Slides exports."
        )

    target_path = download_drive_file(service, {**file_metadata, "id": file_id}, target_dir)
    return {**file_metadata, "driveFileId": file_id, "localPath": str(target_path)}


def collect_drive_documents(
    service: Any,
    folder_id: str,
    target_dir: Path,
    max_files: int | None,
    relative_parts: list[str] | None = None,
) -> list[dict[str, Any]]:
    relative_parts = relative_parts or []
    folder_path = target_dir / Path(*relative_parts) if relative_parts else target_dir
    documents = []
    print(f"Listing Google Drive folder {folder_id}", flush=True)

    for item in list_children(service, folder_id):
        item_name = safe_filename(item.get("name", ""), item["id"])
        item_mime_type = item.get("mimeType")

        if item_mime_type == FOLDER_MIME_TYPE:
            documents.extend(
                collect_drive_documents(
                    service,
                    item["id"],
                    target_dir,
                    max_files - len(documents) if max_files else None,
                    [*relative_parts, item_name],
                )
            )
        elif item_mime_type == SHORTCUT_MIME_TYPE:
            print(f"Skipping shortcut: {item.get('name')}", flush=True)
        elif item_mime_type in SUPPORTED_MIME_TYPES:
            local_path = download_drive_file(service, item, folder_path)
            documents.append(document_from_drive_item(item, local_path))
        else:
            print(f"Skipping unsupported file type {item_mime_type}: {item.get('name')}", flush=True)

        if max_files and len(documents) >= max_files:
            return documents[:max_files]

    return documents


def document_from_drive_item(item: dict[str, Any], local_path: Path) -> dict[str, Any]:
    return {
        "driveFileId": item["id"],
        "name": item["name"],
        "mimeType": item["mimeType"],
        "size": int(item.get("size", 0) or 0),
        "modifiedTime": item.get("modifiedTime"),
        "createdTime": item.get("createdTime"),
        "webViewLink": item.get("webViewLink"),
        "md5Checksum": item.get("md5Checksum"),
        "localPath": str(local_path),
    }


def fetch_gdrive(input_data: dict[str, Any]) -> dict[str, Any]:
    folder = input_data.get("driveFolderId") or os.getenv("GOOGLE_DRIVE_FOLDER_ID")
    if not folder:
        raise RuntimeError("driveFolderId input or GOOGLE_DRIVE_FOLDER_ID is required")
    folder_id = folder_id_from_value(folder)

    local_input_dir = resolve_path(
        input_data.get("localInputDir") or os.getenv("LOCAL_INPUT_DIR"),
        "data/input",
    )
    max_files = optional_int(input_data.get("maxFiles") or os.getenv("MAX_FILES"))
    # Conductor task workers run headlessly, so they must never enter the OAuth browser flow.
    # Use authorize_gdrive.py for one-time local OAuth setup, or switch Drive auth to a service account.
    service = drive_service(allow_interactive_oauth=False)
    documents = collect_drive_documents(service, folder_id, local_input_dir, max_files)
    print(f"Fetched {len(documents)} supported Google Drive documents", flush=True)

    return {"documents": documents, "localInputDir": str(local_input_dir)}


def gemini_client():
    auth_mode = os.getenv("GEMINI_AUTH_MODE", "api_key").strip().lower()
    api_base = os.getenv("GEMINI_API_BASE_URL", "").strip()

    if auth_mode == "vertex_ai":
        project = (
            os.getenv("GEMINI_VERTEX_PROJECT")
            or os.getenv("GOOGLE_CLOUD_PROJECT")
            or os.getenv("GCLOUD_PROJECT")
            or ""
        ).strip()
        if not project:
            raise RuntimeError(
                "GEMINI_AUTH_MODE=vertex_ai requires GEMINI_VERTEX_PROJECT or GOOGLE_CLOUD_PROJECT."
            )
        location = os.getenv("GEMINI_VERTEX_LOCATION", "us-central1").strip()
        credentials_path = (
            os.getenv("GEMINI_GOOGLE_APPLICATION_CREDENTIALS")
            or os.getenv("GOOGLE_APPLICATION_CREDENTIALS")
            or ""
        ).strip()
        if credentials_path:
            credentials = service_account.Credentials.from_service_account_file(
                credentials_path,
                scopes=["https://www.googleapis.com/auth/cloud-platform"],
            )
        else:
            try:
                credentials, _ = google.auth.default(
                    scopes=["https://www.googleapis.com/auth/cloud-platform"]
                )
            except DefaultCredentialsError as exc:
                raise RuntimeError(
                    "Gemini is configured for Vertex AI project mode, but no Google Cloud "
                    "credentials were found. Set GEMINI_GOOGLE_APPLICATION_CREDENTIALS to a "
                    "service-account JSON for GEMINI_VERTEX_PROJECT, or run "
                    "`gcloud auth application-default login` and retry."
                ) from exc
        return genai.Client(
            vertexai=True,
            project=project,
            location=location,
            credentials=credentials,
        )

    if auth_mode != "api_key":
        raise RuntimeError("GEMINI_AUTH_MODE must be api_key or vertex_ai")

    api_key = required_env("GEMINI_API_KEY")
    if api_base:
        return genai.Client(
            api_key=api_key,
            http_options=types.HttpOptions(base_url=api_base),
        )
    return genai.Client(api_key=api_key)


def gemini_json(prompt: str, local_path: str, mime_type: str) -> dict[str, Any]:
    file_bytes = Path(local_path).read_bytes()
    client = gemini_client()
    response = client.models.generate_content(
        model=required_env("GEMINI_MODEL"),
        contents=[
            prompt,
            types.Part.from_bytes(
                data=file_bytes,
                mime_type=mime_type or mimetypes.guess_type(local_path)[0],
            ),
        ],
        config=types.GenerateContentConfig(
            response_mime_type="application/json",
            temperature=float(os.getenv("GEMINI_TEMPERATURE", "0")),
        ),
    )
    return parse_json_text(response.text or "")


def ensure_local_documents(input_data: dict[str, Any]) -> list[dict[str, Any]]:
    documents = input_data.get("documents") or []
    if not documents:
        return []

    local_input_dir = resolve_path(
        input_data.get("localInputDir") or os.getenv("LOCAL_INPUT_DIR"),
        "data/input",
    )
    service = None
    normalized_documents = []
    for document in documents:
        local_path = document.get("localPath")
        if local_path and Path(local_path).exists():
            normalized_documents.append(document)
            continue
        if local_path:
            raise RuntimeError(f"Document localPath does not exist: {local_path}")

        # GDRIVE_READ returns Drive metadata only. Materialize those files locally before
        # Gemini reads bytes from disk.
        if document.get("id") or document.get("driveFileId"):
            if service is None:
                service = drive_service(allow_interactive_oauth=False)
            normalized_documents.append(
                download_or_export_drive_file(service, document, local_input_dir)
            )
            continue

        raise RuntimeError(f"Document must include localPath or Drive id/driveFileId: {document}")
    return normalized_documents


def parse_json_text(text: str) -> Any:
    cleaned = text.strip()
    if cleaned.startswith("```"):
        cleaned = re.sub(r"^```(?:json)?", "", cleaned).strip()
        cleaned = re.sub(r"```$", "", cleaned).strip()
    return json.loads(cleaned)


def require_json_object(value: Any, context: str) -> dict[str, Any]:
    if isinstance(value, dict):
        return value
    if isinstance(value, list) and len(value) == 1 and isinstance(value[0], dict):
        return value[0]
    raise RuntimeError(
        f"{context} must return a JSON object, but returned {type(value).__name__}: {value}"
    )


def write_json(path: Path, payload: Any) -> None:
    path.write_text(json.dumps(payload, indent=2, ensure_ascii=False), encoding="utf-8")


def normalize_document_type(value: Any) -> str:
    text = str(value or "").strip().upper()
    if text in CANONICAL_DOCUMENT_TYPES:
        return text
    if "PROOF OF DELIVERY" in text or text == "DELIVERY NOTE":
        return "POD"
    if text == "GOODS RECEIPT NOTE":
        return "GRN"
    return ""


def filename_document_type(name: str | None) -> str:
    normalized_name = re.sub(r"[^A-Z0-9]+", "_", (name or "").upper())
    parts = {part for part in normalized_name.split("_") if part}
    if "POD" in parts:
        return "POD"
    if "GRN" in parts:
        return "GRN"
    return ""


def normalize_classification(document: dict[str, Any], classification: dict[str, Any]) -> dict[str, Any]:
    result = require_json_object(classification or {}, "Classification response").copy()
    model_type = normalize_document_type(result.get("document_type")) or "UNKNOWN"
    file_type = filename_document_type(document.get("name"))
    reason = str(result.get("reason") or "")
    reason_lower = reason.lower()

    # A signed/stamped tax invoice or delivery note is the POD evidence for this workflow.
    # The separate internal GRN document should have explicit GRN fields such as GRN No/Recv qty.
    stamped_invoice_pod = (
        model_type == "GRN"
        and ("tax invoice" in reason_lower or "invoice" in reason_lower)
        and (
            "goods receipt" in reason_lower
            or "goods received" in reason_lower
            or "stamp" in reason_lower
            or "signed" in reason_lower
        )
        and "grn no" not in reason_lower
    )

    final_type = file_type or ("POD" if stamped_invoice_pod else model_type)
    if final_type not in CANONICAL_DOCUMENT_TYPES:
        final_type = "UNKNOWN"

    if final_type != model_type:
        suffix = (
            f" Normalized from {model_type} to {final_type} because "
            "stamped/signed invoices are treated as PODs in this workflow."
        )
        result["reason"] = (reason + suffix).strip()
        result["confidence"] = min(float(result.get("confidence") or 0.8), 0.95)

    result["document_type"] = final_type
    return result


def classify_documents(input_data: dict[str, Any]) -> dict[str, Any]:
    documents = ensure_local_documents(input_data)
    classified = []
    for document in documents:
        prompt = (
            "Classify this logistics document as exactly one of GRN, POD, or UNKNOWN. "
            "GRN means an internal Goods Received Note with explicit GRN fields such as "
            "GRN No, inbound number, received quantity, or recv qty. "
            "POD means Proof of Delivery: a signed/stamped delivery document, delivery challan, "
            "or tax invoice used as delivery proof. A tax invoice with a GOODS RECEIVED, "
            "GOODS RECEIPT, receiver stamp, inward number, signature, or delivery acknowledgment "
            "must be classified as POD, not GRN, unless it also has explicit GRN No fields. "
            "Return JSON only with keys document_type, confidence, reason, and identifiers. "
            "identifiers should include any visible document_number, po_number, invoice_number, "
            "delivery_number, vendor_name, and customer_name when present."
        )
        result = normalize_classification(
            document,
            require_json_object(
                gemini_json(prompt, document["localPath"], document["mimeType"]),
                "Classification response",
            ),
        )
        classified.append({**document, "classification": result})

    path = output_dir() / "classification.json"
    write_json(path, classified)
    return {"classifiedDocuments": classified, "classificationOutputPath": str(path)}


def ocr_documents(input_data: dict[str, Any]) -> dict[str, Any]:
    classified_documents = input_data.get("classifiedDocuments") or []
    records = []
    for document in classified_documents:
        document_type = document.get("classification", {}).get("document_type", "UNKNOWN")
        prompt = (
            f"Extract structured tabular data from this {document_type} document. "
            "Return JSON only with keys document_type, document_number, reference_number, "
            "po_number, invoice_number, vendor_name, customer_name, document_date, "
            "delivery_date, vehicle_number, line_items, totals, and notes. "
            "line_items must be an array of objects with item_code, sku, description, "
            "quantity, uom, batch_number, and remarks. Use null when a value is absent."
        )
        extracted = require_json_object(
            gemini_json(prompt, document["localPath"], document["mimeType"]),
            "OCR response",
        )
        classification_type = normalize_document_type(document_type)
        extracted_type = normalize_document_type(extracted.get("document_type"))
        if classification_type and classification_type != extracted_type:
            extracted["document_type"] = classification_type
        records.append(
            {
                "driveFileId": document["driveFileId"],
                "name": document["name"],
                "localPath": document["localPath"],
                "classification": document.get("classification"),
                "extracted": extracted,
            }
        )

    json_path = output_dir() / "extracted_data.json"
    csv_path = output_dir() / "extracted_data.csv"
    write_json(json_path, records)
    write_extracted_csv(csv_path, records)
    return {"records": records, "extractedJsonPath": str(json_path), "extractedCsvPath": str(csv_path)}


def write_extracted_csv(path: Path, records: list[dict[str, Any]]) -> None:
    headers = [
        "source_file",
        "document_type",
        "document_number",
        "reference_number",
        "po_number",
        "invoice_number",
        "vendor_name",
        "customer_name",
        "document_date",
        "delivery_date",
        "vehicle_number",
        "item_code",
        "sku",
        "description",
        "quantity",
        "uom",
        "batch_number",
        "remarks",
    ]
    with path.open("w", newline="", encoding="utf-8") as target:
        writer = csv.DictWriter(target, fieldnames=headers)
        writer.writeheader()
        for record in records:
            extracted = record.get("extracted", {})
            line_items = extracted.get("line_items") or [{}]
            for item in line_items:
                row = {key: extracted.get(key) for key in headers if key not in item}
                row["source_file"] = record.get("name")
                row.update({key: item.get(key) for key in item.keys() & set(headers)})
                writer.writerow(row)


def reconcile_documents(input_data: dict[str, Any]) -> dict[str, Any]:
    records = input_data.get("records") or []
    grn_records = [record for record in records if document_type(record) == "GRN"]
    pod_records = [record for record in records if document_type(record) == "POD"]
    rows = []
    document_matches = []

    available_pods = list(pod_records)
    matched_pod_ids = set()
    for grn in grn_records:
        pod, match_key_value, match_strategy = find_matching_pod(grn, available_pods)
        if not pod:
            document_matches.append(document_match(grn, None, "MISSING_POD", match_key_value, match_strategy))
            rows.append(reconciliation_row(grn, None, "MISSING_POD", "No POD matched this GRN invoice."))
            continue

        available_pods = [candidate for candidate in available_pods if candidate["driveFileId"] != pod["driveFileId"]]
        matched_pod_ids.add(pod["driveFileId"])
        match = document_match(grn, pod, "MATCHED", match_key_value, match_strategy)
        document_matches.append(match)
        rows.extend(compare_document_counts(grn, pod))
        rows.extend(compare_line_items(grn, pod))

    for pod in pod_records:
        if pod["driveFileId"] not in matched_pod_ids:
            key, strategy = preferred_document_key(pod)
            document_matches.append(document_match(None, pod, "MISSING_GRN", key, strategy))
            rows.append(reconciliation_row(None, pod, "MISSING_GRN", "No GRN matched this POD invoice."))

    summary = {
        "grnDocuments": len(grn_records),
        "podDocuments": len(pod_records),
        "matchedDocuments": sum(1 for match in document_matches if match["status"] == "MATCHED"),
        "missingPodDocuments": sum(1 for match in document_matches if match["status"] == "MISSING_POD"),
        "missingGrnDocuments": sum(1 for match in document_matches if match["status"] == "MISSING_GRN"),
        "itemCountMatchedDocuments": sum(
            1 for match in document_matches if match.get("itemCountStatus") == "MATCHED"
        ),
        "itemCountMismatchDocuments": sum(
            1 for match in document_matches if match.get("itemCountStatus") == "ITEM_COUNT_MISMATCH"
        ),
        "matchedRows": sum(1 for row in rows if row["status"] in ("MATCHED", "ITEM_COUNT_MATCHED")),
        "mismatchRows": sum(1 for row in rows if row["status"] not in ("MATCHED", "ITEM_COUNT_MATCHED")),
        "totalRows": len(rows),
    }
    result = {
        "summary": summary,
        "records": records,
        "grnRecords": grn_records,
        "podRecords": pod_records,
        "documentMatches": document_matches,
        "reconciliationRows": rows,
    }
    json_path = output_dir() / "reconciliation_result.json"
    csv_path = output_dir() / "reconciliation_result.csv"
    write_json(json_path, result)
    write_reconciliation_csv(csv_path, rows)
    return {
        "summary": summary,
        "records": records,
        "grnRecords": grn_records,
        "podRecords": pod_records,
        "documentMatches": document_matches,
        "reconciliationRows": rows,
        "reconciliationJsonPath": str(json_path),
        "reconciliationCsvPath": str(csv_path),
    }


def document_type(record: dict[str, Any]) -> str:
    extracted = record.get("extracted", {})
    classification = record.get("classification", {})
    classification_type = normalize_document_type(classification.get("document_type"))
    extracted_type = normalize_document_type(extracted.get("document_type"))
    return extracted_type or classification_type or "UNKNOWN"


def match_key(record: dict[str, Any]) -> str:
    extracted = record.get("extracted", {})
    for field in ("invoice_number", "po_number", "reference_number", "document_number"):
        value = normalize(extracted.get(field))
        if value:
            return f"{field}:{value}"
    return f"file:{record.get('driveFileId')}"


def preferred_document_key(record: dict[str, Any]) -> tuple[str, str]:
    candidates = document_identifier_candidates(record)
    for candidate in candidates:
        if candidate["normalized"]:
            return candidate["normalized"], candidate["field"]
    for candidate in candidates:
        if candidate["digits"]:
            return candidate["digits"], f"{candidate['field']}_digits"
    return str(record.get("driveFileId")), "driveFileId"


def find_matching_pod(
    grn: dict[str, Any], pod_records: list[dict[str, Any]]
) -> tuple[dict[str, Any] | None, str, str]:
    grn_candidates = document_identifier_candidates(grn)

    for grn_candidate in grn_candidates:
        value = grn_candidate["normalized"]
        if not value:
            continue
        matches = []
        for pod in pod_records:
            pod_candidate = matching_identifier_candidate(pod, "normalized", value)
            if pod_candidate:
                matches.append((pod, pod_candidate))
        if len(matches) == 1:
            pod, pod_candidate = matches[0]
            return (
                pod,
                value,
                f"{grn_candidate['field']}={pod_candidate['field']}",
            )

    for grn_candidate in grn_candidates:
        value = grn_candidate["digits"]
        if not value:
            continue
        matches = []
        for pod in pod_records:
            pod_candidate = matching_identifier_candidate(pod, "digits", value)
            if pod_candidate:
                matches.append((pod, pod_candidate))
        if len(matches) == 1:
            pod, pod_candidate = matches[0]
            return (
                pod,
                value,
                f"{grn_candidate['field']}_digits={pod_candidate['field']}_digits",
            )

    key, strategy = preferred_document_key(grn)
    return None, key, strategy


def document_identifier_candidates(record: dict[str, Any]) -> list[dict[str, str]]:
    # Build a document-level identifier index from every likely identifier field.
    raw_candidates = []
    collect_identifier_values(record.get("extracted", {}), "", raw_candidates)
    collect_identifier_values(
        record.get("classification", {}).get("identifiers", {}),
        "classification",
        raw_candidates,
    )

    candidates = []
    seen = set()
    for field, value in raw_candidates:
        normalized = normalize(value)
        digits = numeric_key(value)
        if not normalized and not digits:
            continue
        key = (field, normalized, digits)
        if key in seen:
            continue
        seen.add(key)
        candidates.append(
            {
                "field": field,
                "value": str(value),
                "normalized": normalized,
                "digits": digits,
            }
        )
    return sorted(
        candidates,
        key=lambda candidate: identifier_priority(candidate["field"]),
    )


def collect_identifier_values(payload: Any, path: str, values: list[tuple[str, Any]]) -> None:
    if isinstance(payload, dict):
        for key, value in payload.items():
            next_path = f"{path}.{key}" if path else str(key)
            if is_identifier_field(str(key)):
                if isinstance(value, (str, int, float)):
                    values.append((next_path, value))
                elif isinstance(value, list):
                    for item in value:
                        if isinstance(item, (str, int, float)):
                            values.append((next_path, item))
            collect_identifier_values(value, next_path, values)
    elif isinstance(payload, list):
        for index, item in enumerate(payload):
            collect_identifier_values(item, f"{path}[{index}]", values)


def is_identifier_field(field_name: str) -> bool:
    normalized_field = normalize(field_name)
    return any(
        token in normalized_field
        for token in (
            "invoice",
            "invno",
            "documentnumber",
            "documentno",
            "docnumber",
            "docno",
            "referencenumber",
            "referenceno",
            "refnumber",
            "refno",
            "ponumber",
            "pono",
            "grnnumber",
            "grnno",
            "deliverynumber",
            "deliveryno",
            "inwardnumber",
            "inwardno",
        )
    )


def identifier_priority(field: str) -> tuple[int, str]:
    normalized_field = normalize(field)
    if "invoice" in normalized_field or "invno" in normalized_field:
        return (0, field)
    if "document" in normalized_field or "docno" in normalized_field:
        return (1, field)
    if "reference" in normalized_field or "refno" in normalized_field:
        return (2, field)
    if "po" in normalized_field:
        return (3, field)
    return (4, field)


def matching_identifier_candidate(
    record: dict[str, Any],
    key: str,
    value: str,
) -> dict[str, str] | None:
    for candidate in document_identifier_candidates(record):
        if candidate[key] == value:
            return candidate
    return None


def numeric_key(value: Any) -> str:
    if value is None:
        return ""
    return "".join(re.findall(r"\d+", str(value)))


def item_key(item: dict[str, Any]) -> str:
    for field in ("description", "sku", "item_code"):
        value = normalize(item.get(field))
        if value:
            return value
    return "unknown"


def quantity(value: Any) -> float | None:
    if value in (None, ""):
        return None
    match = re.search(r"-?\d+(?:\.\d+)?", str(value).replace(",", ""))
    return float(match.group(0)) if match else None


def normalize(value: Any) -> str:
    if value is None:
        return ""
    return re.sub(r"[^a-z0-9]+", "", str(value).lower())


def compare_line_items(grn: dict[str, Any], pod: dict[str, Any]) -> list[dict[str, Any]]:
    rows = []
    grn_items = grn.get("extracted", {}).get("line_items") or []
    pod_items = pod.get("extracted", {}).get("line_items") or []
    pod_by_item_key = {item_key(item): item for item in pod_items}

    for grn_item in grn_items:
        key = item_key(grn_item)
        pod_item = pod_by_item_key.get(key)
        if not pod_item:
            rows.append(reconciliation_row(grn, pod, "MISSING_POD_ITEM", "No matching POD line item.", grn_item))
            continue
        grn_qty = quantity(grn_item.get("quantity"))
        pod_qty = quantity(pod_item.get("quantity"))
        status = "MATCHED" if grn_qty == pod_qty else "QUANTITY_MISMATCH"
        note = "" if status == "MATCHED" else "GRN and POD quantities differ."
        rows.append(reconciliation_row(grn, pod, status, note, grn_item, pod_item))

    grn_item_keys = {item_key(item) for item in grn_items}
    for pod_item in pod_items:
        if item_key(pod_item) not in grn_item_keys:
            rows.append(reconciliation_row(grn, pod, "MISSING_GRN_ITEM", "No matching GRN line item.", None, pod_item))

    return rows


def compare_document_counts(grn: dict[str, Any], pod: dict[str, Any]) -> list[dict[str, Any]]:
    grn_count = item_count(grn)
    pod_count = item_count(pod)
    if grn_count == pod_count:
        return [
            reconciliation_row(
                grn,
                pod,
                "ITEM_COUNT_MATCHED",
                "GRN and POD have the same number of line items.",
            )
        ]
    return [
        reconciliation_row(
            grn,
            pod,
            "ITEM_COUNT_MISMATCH",
            "GRN and POD line item counts differ.",
        )
    ]


def item_count(record: dict[str, Any] | None) -> int:
    if not record:
        return 0
    return len(record.get("extracted", {}).get("line_items") or [])


def document_match(
    grn: dict[str, Any] | None,
    pod: dict[str, Any] | None,
    status: str,
    key: str,
    strategy: str,
) -> dict[str, Any]:
    grn_count = item_count(grn)
    pod_count = item_count(pod)
    item_count_status = None
    if grn and pod:
        item_count_status = "MATCHED" if grn_count == pod_count else "ITEM_COUNT_MISMATCH"
    return {
        "status": status,
        "matchKey": key,
        "matchStrategy": strategy,
        "itemCountStatus": item_count_status,
        "grnFile": grn.get("name") if grn else None,
        "podFile": pod.get("name") if pod else None,
        "grnInvoiceNumber": grn.get("extracted", {}).get("invoice_number") if grn else None,
        "podInvoiceNumber": pod.get("extracted", {}).get("invoice_number") if pod else None,
        "grnItemCount": grn_count,
        "podItemCount": pod_count,
    }


def reconciliation_row(
    grn: dict[str, Any] | None,
    pod: dict[str, Any] | None,
    status: str,
    note: str,
    grn_item: dict[str, Any] | None = None,
    pod_item: dict[str, Any] | None = None,
) -> dict[str, Any]:
    grn_extracted = grn.get("extracted", {}) if grn else {}
    pod_extracted = pod.get("extracted", {}) if pod else {}
    grn_item = grn_item or {}
    pod_item = pod_item or {}
    return {
        "status": status,
        "note": note,
        "matchKey": match_key(grn or pod),
        "grnFile": grn.get("name") if grn else None,
        "podFile": pod.get("name") if pod else None,
        "grnDocumentNumber": grn_extracted.get("document_number"),
        "podDocumentNumber": pod_extracted.get("document_number"),
        "poNumber": grn_extracted.get("po_number") or pod_extracted.get("po_number"),
        "invoiceNumber": grn_extracted.get("invoice_number") or pod_extracted.get("invoice_number"),
        "itemKey": item_key(grn_item or pod_item),
        "grnDescription": grn_item.get("description"),
        "podDescription": pod_item.get("description"),
        "grnQuantity": grn_item.get("quantity"),
        "podQuantity": pod_item.get("quantity"),
        "uom": grn_item.get("uom") or pod_item.get("uom"),
        "grnItemCount": item_count(grn),
        "podItemCount": item_count(pod),
    }


def write_reconciliation_csv(path: Path, rows: list[dict[str, Any]]) -> None:
    headers = [
        "status",
        "note",
        "matchKey",
        "grnFile",
        "podFile",
        "grnDocumentNumber",
        "podDocumentNumber",
        "poNumber",
        "invoiceNumber",
        "itemKey",
        "grnDescription",
        "podDescription",
        "grnQuantity",
        "podQuantity",
        "uom",
        "grnItemCount",
        "podItemCount",
    ]
    with path.open("w", newline="", encoding="utf-8") as target:
        writer = csv.DictWriter(target, fieldnames=headers)
        writer.writeheader()
        writer.writerows(rows)


def run_workers() -> None:
    run_task_workers(
        {
            "grn_pod_fetch_gdrive": fetch_gdrive,
            "grn_pod_classify_document": classify_documents,
            "grn_pod_ocr_extract_document": ocr_documents,
            "grn_pod_reconcile_result": reconcile_documents,
        }
    )


if __name__ == "__main__":
    load_dotenv(ROOT / ".env")
    run_workers()
