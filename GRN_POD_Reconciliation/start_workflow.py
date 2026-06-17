import os
from pathlib import Path

from dotenv import load_dotenv

from conductor_sdk_adapter import start_workflow_by_name

ROOT = Path(__file__).resolve().parent


def main() -> None:
    load_dotenv(ROOT / ".env")
    workflow_input = {
        "driveFolderId": os.getenv("GOOGLE_DRIVE_FOLDER_ID", "").strip(),
        "localInputDir": os.getenv("LOCAL_INPUT_DIR", "").strip(),
        "mimeTypes": [
            "application/pdf",
            "image/jpeg",
            "image/png",
            "image/tiff",
            "image/webp",
        ],
    }
    max_files = os.getenv("MAX_FILES", "").strip()
    if max_files:
        workflow_input["maxFiles"] = int(max_files)

    workflow_id = start_workflow_by_name(
        "grn_pod_reconciliation",
        1,
        workflow_input,
    )
    print(workflow_id)


if __name__ == "__main__":
    main()
