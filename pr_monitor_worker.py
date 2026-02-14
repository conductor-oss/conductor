#!/usr/bin/env python3
"""
Worker to monitor PR #749 build status and report to Claude Code.
"""
import json
import os
import subprocess
from datetime import datetime, timezone
from conductor.client.automator.task_handler import TaskHandler
from conductor.client.configuration.configuration import Configuration
from conductor.client.http.models import Task, TaskResult
from conductor.client.http.models.task_result_status import TaskResultStatus
from conductor.client.worker.worker_interface import WorkerInterface

class PRStatusParser(WorkerInterface):
    """Worker that parses GitHub PR status and reports results."""

    def __init__(self):
        self.status_file = "/Users/nthmost/.claude-monitor/pr749_build.json"
        os.makedirs(os.path.dirname(self.status_file), exist_ok=True)

    def execute(self, task: Task) -> TaskResult:
        """Parse PR status and update monitoring file."""
        try:
            # Get PR status using gh CLI
            result = subprocess.run(
                ["gh", "pr", "checks", "749"],
                capture_output=True,
                text=True,
                cwd="/Users/nthmost/projects/git/conductor-oss/conductor"
            )

            output = result.stdout

            # Determine overall status
            if "pending" in output.lower():
                status = "pending"
                message = "Build checks are still running"
            elif result.returncode == 0 and "fail" not in output.lower():
                status = "completed"
                message = "✅ All checks passed!"
            elif "fail" in output.lower():
                status = "failed"
                message = "❌ Some checks failed"
            else:
                status = "unknown"
                message = f"Unable to determine status: {output[:200]}"

            # Update status file for Claude Code monitoring
            status_data = {
                "task_name": "PR #749 Build Monitor",
                "status": status,
                "message": message,
                "pr_url": "https://github.com/conductor-oss/conductor/pull/749",
                "last_check": output,
                "updated_at": datetime.now(timezone.utc).isoformat()
            }

            with open(self.status_file, 'w') as f:
                json.dump(status_data, f, indent=2)

            # Return result to Conductor
            task_result = TaskResult(
                task_id=task.task_id,
                workflow_instance_id=task.workflow_instance_id,
                worker_id="pr_monitor_worker"
            )
            task_result.status = TaskResultStatus.COMPLETED
            task_result.output_data = {
                "status": status,
                "message": message,
                "details": output
            }

            return task_result

        except Exception as e:
            # Error handling
            error_msg = f"Error checking PR status: {str(e)}"

            status_data = {
                "task_name": "PR #749 Build Monitor",
                "status": "error",
                "message": error_msg,
                "updated_at": datetime.now(timezone.utc).isoformat()
            }

            with open(self.status_file, 'w') as f:
                json.dump(status_data, f, indent=2)

            task_result = TaskResult(
                task_id=task.task_id,
                workflow_instance_id=task.workflow_instance_id,
                worker_id="pr_monitor_worker"
            )
            task_result.status = TaskResultStatus.FAILED
            task_result.reason_for_incompletion = error_msg

            return task_result

    def get_polling_interval_in_seconds(self) -> float:
        return 30.0  # Poll every 30 seconds

    def get_task_definition_name(self) -> str:
        return "parse_and_report"


if __name__ == "__main__":
    # Configuration for local Conductor server
    config = Configuration(
        server_api_url="http://localhost:8080/api",
        debug=True
    )

    # Create and start the task handler
    task_handler = TaskHandler(
        workers=[PRStatusParser()],
        configuration=config,
        scan_for_annotated_workers=False
    )

    print("🔍 PR Monitor Worker started!")
    print("📊 Monitoring PR #749 at: https://github.com/conductor-oss/conductor/pull/749")
    print(f"📁 Status updates: /Users/nthmost/.claude-monitor/pr749_build.json")
    print("Press Ctrl+C to stop...\n")

    task_handler.start_processes()
