import json
from pathlib import Path

from dotenv import load_dotenv

from conductor_sdk_adapter import register_task_defs, register_workflow_def

ROOT = Path(__file__).resolve().parent


def load_json(relative_path: str):
    with (ROOT / relative_path).open("r", encoding="utf-8") as source:
        return json.load(source)


def main() -> None:
    load_dotenv(ROOT / ".env")

    register_task_defs(load_json("metadata/task_defs.json"))
    register_workflow_def(load_json("metadata/workflow.json"))

    print("Registered task definitions and workflow grn_pod_reconciliation.")


if __name__ == "__main__":
    main()
