import json
from pathlib import Path

from dotenv import load_dotenv

from conductor_sdk_adapter import register_task_defs, register_workflow_def

ROOT = Path(__file__).resolve().parent

TASK_DEFS = [
    {
        "name": "grn_pod_fetch_gdrive",
        "description": "Fetch PDF/image documents from a configured Google Drive folder and store them locally.",
        "retryCount": 2,
        "timeoutSeconds": 900,
        "responseTimeoutSeconds": 600,
        "retryLogic": "FIXED",
        "retryDelaySeconds": 30,
        "inputKeys": ["driveFolderId", "localInputDir", "mimeTypes", "maxFiles"],
        "outputKeys": ["documents", "localInputDir"],
    },
    {
        "name": "grn_pod_classify_document",
        "description": "Use Gemini to classify each fetched document as GRN, POD, or UNKNOWN.",
        "retryCount": 2,
        "timeoutSeconds": 900,
        "responseTimeoutSeconds": 600,
        "retryLogic": "FIXED",
        "retryDelaySeconds": 30,
        "inputKeys": ["documents"],
        "outputKeys": ["classifiedDocuments", "classificationOutputPath"],
    },
    {
        "name": "grn_pod_ocr_extract_document",
        "description": "Use Gemini OCR/vision to extract document and line-item data into structured tables.",
        "retryCount": 2,
        "timeoutSeconds": 1800,
        "responseTimeoutSeconds": 1500,
        "retryLogic": "FIXED",
        "retryDelaySeconds": 30,
        "inputKeys": ["classifiedDocuments"],
        "outputKeys": ["records", "extractedJsonPath", "extractedCsvPath"],
    },
    {
        "name": "grn_pod_reconcile_result",
        "description": "Reconcile extracted GRN and POD tabular data and write the final result.",
        "retryCount": 1,
        "timeoutSeconds": 600,
        "responseTimeoutSeconds": 300,
        "retryLogic": "FIXED",
        "retryDelaySeconds": 30,
        "inputKeys": ["records"],
        "outputKeys": [
            "summary",
            "records",
            "grnRecords",
            "podRecords",
            "documentMatches",
            "reconciliationRows",
            "reconciliationJsonPath",
            "reconciliationCsvPath",
        ],
    },
]

WORKFLOW_DEF = {
    "name": "grn_pod_reconciliation",
    "description": (
        "Fetch GRN/POD PDF or image documents from Google Drive, classify them with Gemini, "
        "extract tabular data, and reconcile GRN against POD."
    ),
    "version": 1,
    "tasks": [
        {
            "name": "grn_pod_fetch_gdrive",
            "taskReferenceName": "fetch_data_from_gdrive",
            "type": "SIMPLE",
            "inputParameters": {
                "driveFolderId": "${workflow.input.driveFolderId}",
                "localInputDir": "${workflow.input.localInputDir}",
                "mimeTypes": "${workflow.input.mimeTypes}",
                "maxFiles": "${workflow.input.maxFiles}",
            },
        },
        {
            "name": "grn_pod_classify_document",
            "taskReferenceName": "classify_as_grn_or_pod",
            "type": "SIMPLE",
            "inputParameters": {"documents": "${fetch_data_from_gdrive.output.documents}"},
        },
        {
            "name": "grn_pod_ocr_extract_document",
            "taskReferenceName": "ocr_to_tabular_data",
            "type": "SIMPLE",
            "inputParameters": {
                "classifiedDocuments": "${classify_as_grn_or_pod.output.classifiedDocuments}"
            },
        },
        {
            "name": "grn_pod_reconcile_result",
            "taskReferenceName": "resultant_reconciled_data",
            "type": "SIMPLE",
            "inputParameters": {"records": "${ocr_to_tabular_data.output.records}"},
        },
    ],
    "inputParameters": ["driveFolderId", "localInputDir", "mimeTypes", "maxFiles"],
    "outputParameters": {
        "summary": "${resultant_reconciled_data.output.summary}",
        "records": "${resultant_reconciled_data.output.records}",
        "grnRecords": "${resultant_reconciled_data.output.grnRecords}",
        "podRecords": "${resultant_reconciled_data.output.podRecords}",
        "documentMatches": "${resultant_reconciled_data.output.documentMatches}",
        "reconciliationRows": "${resultant_reconciled_data.output.reconciliationRows}",
        "reconciliationJsonPath": "${resultant_reconciled_data.output.reconciliationJsonPath}",
        "reconciliationCsvPath": "${resultant_reconciled_data.output.reconciliationCsvPath}",
        "extractedJsonPath": "${ocr_to_tabular_data.output.extractedJsonPath}",
        "extractedCsvPath": "${ocr_to_tabular_data.output.extractedCsvPath}",
    },
    "schemaVersion": 2,
    "restartable": True,
    "workflowStatusListenerEnabled": False,
    "timeoutPolicy": "TIME_OUT_WF",
    "timeoutSeconds": 7200,
}


def load_json(relative_path: str):
    path = ROOT / relative_path
    if not path.exists():
        return None
    with path.open("r", encoding="utf-8") as source:
        return json.load(source)


def main() -> None:
    load_dotenv(ROOT / ".env")

    register_task_defs(load_json("metadata/task_defs.json") or TASK_DEFS)
    register_workflow_def(load_json("metadata/workflow.json") or WORKFLOW_DEF)

    print("Registered task definitions and workflow grn_pod_reconciliation.")


if __name__ == "__main__":
    main()
