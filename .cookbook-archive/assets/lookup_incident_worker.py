from conductor.client.worker.worker_task import worker_task
from conductor.client.automator.task_handler import TaskHandler
from conductor.client.configuration.configuration import Configuration
from conductor.client.http.models.task_def import TaskDef


LOOKUP_INCIDENT_TASK_DEF = TaskDef(
    name="lookup_incident",
    description="Read-only incident lookup used by the cookbook tool-calling agent.",
    retry_count=2,
    retry_logic="EXPONENTIAL_BACKOFF",
    retry_delay_seconds=2,
    poll_timeout_seconds=30,
    response_timeout_seconds=30,
    timeout_seconds=60,
    timeout_policy="TIME_OUT_WF",
    concurrent_exec_limit=8,
    rate_limit_per_frequency=60,
    rate_limit_frequency_in_seconds=60,
)


@worker_task(
    task_definition_name="lookup_incident",
    register_task_def=True,
    task_def=LOOKUP_INCIDENT_TASK_DEF,
)
def lookup_incident(incidentId: str) -> dict:
    """Read-only deterministic starter; replace with an idempotent incident-system read."""
    return {
        "incidentId": incidentId,
        "status": "investigating",
        "summary": "Replace this fixture with your incident-system lookup.",
    }


if __name__ == "__main__":
    with TaskHandler(configuration=Configuration(), scan_for_annotated_workers=True) as handler:
        handler.start_processes()
        handler.join_processes()
