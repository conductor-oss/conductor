import argparse
import json
import mimetypes
import os
import traceback
from pathlib import Path

from dotenv import load_dotenv

from worker import (
    classify_documents,
    fetch_gdrive,
    ocr_documents,
    reconcile_documents,
)


ROOT = Path(__file__).resolve().parent
SUPPORTED_SUFFIXES = {".pdf", ".jpg", ".jpeg", ".png", ".tif", ".tiff", ".webp"}


def write_stage(stage_dir: Path, name: str, payload) -> Path:
    stage_dir.mkdir(parents=True, exist_ok=True)
    path = stage_dir / f"{name}.json"
    path.write_text(json.dumps(payload, indent=2, ensure_ascii=False), encoding="utf-8")
    return path


def workflow_input() -> dict:
    max_files = os.getenv("MAX_FILES", "").strip()
    debug_max_files = os.getenv("STEP_DEBUG_MAX_FILES", "2").strip()
    payload = {
        "driveFolderId": os.getenv("GOOGLE_DRIVE_FOLDER_ID", "").strip(),
        "localInputDir": os.getenv("LOCAL_INPUT_DIR", "data/input").strip(),
        "mimeTypes": [
            "application/pdf",
            "image/jpeg",
            "image/png",
            "image/tiff",
            "image/webp",
        ],
    }
    if max_files:
        payload["maxFiles"] = int(max_files)
    elif debug_max_files:
        payload["maxFiles"] = int(debug_max_files)
    return payload


def run_stage(stage_dir: Path, stage_name: str, func, input_payload: dict) -> dict:
    print(f"Running {stage_name}...", flush=True)
    write_stage(stage_dir, f"{stage_name}_input", input_payload)
    try:
        output = func(input_payload)
        write_stage(stage_dir, f"{stage_name}_output", output)
        print(f"Completed {stage_name}", flush=True)
        return output
    except Exception as exc:
        failure = {
            "stage": stage_name,
            "error": str(exc),
            "traceback": traceback.format_exc(),
        }
        write_stage(stage_dir, f"{stage_name}_failure", failure)
        print(f"Failed {stage_name}: {exc}", flush=True)
        print(f"Failure details saved in {stage_dir}", flush=True)
        raise


def local_documents(input_payload: dict) -> dict:
    local_input_dir = resolve_path(input_payload.get("localInputDir"), "data/input")
    max_files = input_payload.get("maxFiles")
    documents = []
    for path in sorted(local_input_dir.rglob("*")):
        if not path.is_file() or path.suffix.lower() not in SUPPORTED_SUFFIXES:
            continue
        documents.append(
            {
                "driveFileId": None,
                "name": path.name,
                "mimeType": mimetypes.guess_type(path)[0] or "application/octet-stream",
                "size": path.stat().st_size,
                "modifiedTime": None,
                "md5Checksum": None,
                "localPath": str(path),
            }
        )
        if max_files and len(documents) >= int(max_files):
            break
    if not documents:
        raise RuntimeError(f"No supported PDF/image files found under {local_input_dir}")
    return {"documents": documents, "localInputDir": str(local_input_dir)}


def resolve_path(value: str | None, default_relative: str) -> Path:
    raw = value or default_relative
    path = Path(raw)
    return path if path.is_absolute() else ROOT / path


def parse_args():
    parser = argparse.ArgumentParser(
        description="Run GRN/POD reconciliation stages without Conductor."
    )
    parser.add_argument(
        "--local-only",
        action="store_true",
        help="Skip Google Drive fetch and use files already in LOCAL_INPUT_DIR.",
    )
    parser.add_argument(
        "--fetch-only",
        action="store_true",
        help="Only fetch files from Google Drive, then stop.",
    )
    parser.add_argument(
        "--start-at",
        choices=["fetch", "classify", "ocr", "reconcile"],
        default="fetch",
        help="Start from a later stage using previous output JSON files in output/step_debug.",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    load_dotenv(ROOT / ".env")

    stage_dir = ROOT / os.getenv("LOCAL_OUTPUT_DIR", "output") / "step_debug"
    initial_input = workflow_input()

    if args.start_at == "fetch":
        if args.local_only:
            fetch_output = run_stage(stage_dir, "01_local_files", local_documents, initial_input)
        else:
            fetch_output = run_stage(stage_dir, "01_fetch_gdrive", fetch_gdrive, initial_input)
    else:
        fetch_output = read_first_stage(
            stage_dir,
            ["01_fetch_gdrive_output", "01_local_files_output"],
        )

    if args.fetch_only:
        print(f"Fetch output saved in {stage_dir}", flush=True)
        return

    if args.start_at in ("fetch", "classify"):
        classify_output = run_stage(stage_dir, "02_classify", classify_documents, fetch_output)
    else:
        classify_output = read_stage(stage_dir, "02_classify_output")

    if args.start_at in ("fetch", "classify", "ocr"):
        ocr_output = run_stage(stage_dir, "03_ocr", ocr_documents, classify_output)
    else:
        ocr_output = read_stage(stage_dir, "03_ocr_output")

    reconcile_output = run_stage(stage_dir, "04_reconcile", reconcile_documents, ocr_output)

    final_path = write_stage(stage_dir, "final_result", reconcile_output)
    print(f"Final result saved to {final_path}", flush=True)


def read_stage(stage_dir: Path, name: str) -> dict:
    path = stage_dir / f"{name}.json"
    if not path.exists():
        raise RuntimeError(f"Missing previous stage output: {path}")
    return json.loads(path.read_text(encoding="utf-8"))


def read_first_stage(stage_dir: Path, names: list[str]) -> dict:
    for name in names:
        path = stage_dir / f"{name}.json"
        if path.exists():
            return json.loads(path.read_text(encoding="utf-8"))
    expected = ", ".join(str(stage_dir / f"{name}.json") for name in names)
    raise RuntimeError(f"Missing previous stage output. Expected one of: {expected}")


if __name__ == "__main__":
    main()
