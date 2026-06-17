import json
import os
import time
from pathlib import Path
from uuid import uuid4

from dotenv import load_dotenv
from conductor.client.http.rest import ApiException
from conductor.client.http.models.start_workflow_request import StartWorkflowRequest

from conductor_sdk_adapter import start_workflow_by_name, workflow_client


ROOT = Path(__file__).resolve().parent


def workflow_input() -> dict:
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
    max_files = os.getenv("MAX_FILES", "").strip()
    if max_files:
        payload["maxFiles"] = int(max_files)
    return payload


def to_plain_json(value):
    if value is None or isinstance(value, (str, int, float, bool)):
        return value
    if hasattr(value, "to_dict"):
        return to_plain_json(value.to_dict())
    if isinstance(value, dict):
        return {str(key): to_plain_json(item) for key, item in value.items()}
    if isinstance(value, list):
        return [to_plain_json(item) for item in value]
    if isinstance(value, tuple):
        return [to_plain_json(item) for item in value]
    if isinstance(value, set):
        return [to_plain_json(item) for item in value]
    if hasattr(value, "value"):
        return value.value
    return str(value)


def main() -> None:
    load_dotenv(ROOT / ".env")

    payload = workflow_input()
    request = StartWorkflowRequest(
        name="grn_pod_reconciliation",
        version=1,
        input=payload,
    )

    client = workflow_client()
    wait_seconds = int(os.getenv("CONDUCTOR_EXECUTE_WAIT_SECONDS", "300"))
    try:
        result = client.execute_workflow_with_return_strategy(
            request,
            request_id=str(uuid4()),
            wait_for_seconds=wait_seconds,
            consistency="DURABLE",
            return_strategy="WAIT_WORKFLOW",
        )
    except ApiException as exc:
        # Some Conductor server/client combinations fail the reactive execute endpoint.
        # Fall back to normal start plus status polling so the same script still returns output.
        if exc.status != 500:
            raise
        workflow_id = start_workflow_by_name("grn_pod_reconciliation", 1, payload)
        result = wait_for_workflow(client, workflow_id, wait_seconds)

    output_dir = ROOT / os.getenv("LOCAL_OUTPUT_DIR", "output")
    output_dir.mkdir(parents=True, exist_ok=True)
    output_path = output_dir / "workflow_execute_response.json"
    output_path.write_text(json.dumps(to_plain_json(result), indent=2), encoding="utf-8")

    print(json.dumps(to_plain_json(result), indent=2))
    print(f"Saved response to {output_path}")


def wait_for_workflow(client, workflow_id: str, wait_seconds: int):
    terminal_statuses = {"COMPLETED", "FAILED", "TERMINATED", "TIMED_OUT"}
    deadline = time.time() + wait_seconds
    workflow = client.get_workflow(workflow_id, include_tasks=True)
    while time.time() < deadline:
        workflow = client.get_workflow(workflow_id, include_tasks=True)
        status = str(getattr(workflow, "status", "")).split(".")[-1]
        if status in terminal_statuses:
            return workflow
        time.sleep(2)
    return workflow


if __name__ == "__main__":
    main()
