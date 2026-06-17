import importlib
import inspect
import os
import threading
from typing import Any, Callable


def required_env(name: str) -> str:
    value = os.getenv(name, "").strip()
    if not value:
        raise RuntimeError(f"{name} must be set in .env")
    return value


def api_base_url() -> str:
    base_url = required_env("CONDUCTOR_API_BASE_URL").rstrip("/")
    return base_url if base_url.endswith("/api") else f"{base_url}/api"


def configuration() -> Any:
    Configuration = load_symbol(
        [
            ("conductor.client.configuration.configuration", "Configuration"),
            ("conductor.client.configuration", "Configuration"),
        ],
        "Conductor SDK Configuration",
    )
    base_url = api_base_url()
    try:
        config = Configuration(server_api_url=base_url)
    except TypeError:
        config = Configuration()

    for attribute in ("server_api_url", "base_url", "host"):
        if hasattr(config, attribute):
            setattr(config, attribute, base_url)

    token = os.getenv("CONDUCTOR_AUTH_TOKEN", "").strip()
    if token and hasattr(config, "api_key"):
        header_name = os.getenv("CONDUCTOR_AUTH_HEADER_NAME", "Authorization").strip()
        config.api_key[header_name] = token
    return config


def metadata_client() -> Any:
    return instantiate_client(
        [
            ("conductor.client.orkes.orkes_metadata_client", "OrkesMetadataClient"),
            ("conductor.client.metadata_client", "MetadataClient"),
            ("conductor.client.http.api.metadata_resource_api", "MetadataResourceApi"),
        ],
        "Conductor SDK metadata client",
    )


def workflow_client() -> Any:
    return instantiate_client(
        [
            ("conductor.client.orkes.orkes_workflow_client", "OrkesWorkflowClient"),
            ("conductor.client.workflow_client", "WorkflowClient"),
            ("conductor.client.http.api.workflow_resource_api", "WorkflowResourceApi"),
        ],
        "Conductor SDK workflow client",
    )


def instantiate_client(candidates: list[tuple[str, str]], description: str) -> Any:
    config = configuration()
    errors = []
    for module_name, symbol_name in candidates:
        try:
            cls = load_symbol([(module_name, symbol_name)], description)
            if inspect.isabstract(cls):
                errors.append(f"{module_name}.{symbol_name}: abstract class")
                continue
            try:
                return cls(config)
            except TypeError as positional_error:
                try:
                    return cls(configuration=config)
                except TypeError as keyword_error:
                    try:
                        return cls()
                    except TypeError as empty_error:
                        errors.append(
                            f"{module_name}.{symbol_name}: "
                            f"{positional_error}; {keyword_error}; {empty_error}"
                        )
        except RuntimeError as exc:
            errors.append(str(exc))
    raise RuntimeError(f"Unable to instantiate {description}. Tried: " + "; ".join(errors))


def load_symbol(candidates: list[tuple[str, str]], description: str) -> Any:
    errors = []
    for module_name, symbol_name in candidates:
        try:
            module = importlib.import_module(module_name)
            return getattr(module, symbol_name)
        except (ImportError, AttributeError) as exc:
            errors.append(f"{module_name}.{symbol_name}: {exc}")
    raise RuntimeError(
        f"Unable to load {description}. Install the Conductor Python SDK from "
        "requirements.txt and ensure its import path is available. Tried: "
        + "; ".join(errors)
    )


def register_task_defs(task_defs: list[dict[str, Any]]) -> None:
    client = metadata_client()
    if client.__class__.__name__ == "OrkesMetadataClient":
        for task_def in task_defs:
            client.register_task_def(task_def)
        return
    call_first_supported(
        client,
        [
            ("register_task_def", (task_defs,), {}),
            ("register_task_defs", (task_defs,), {}),
            ("create_task_def", (task_defs,), {}),
            ("registerTaskDef", (task_defs,), {}),
        ],
        "register task definitions",
    )


def register_workflow_def(workflow_def: dict[str, Any]) -> None:
    client = metadata_client()
    if client.__class__.__name__ == "OrkesMetadataClient":
        try:
            client.register_workflow_def(workflow_def, overwrite=True)
        except Exception as exc:
            if "409" not in str(exc):
                raise
            client.update_workflow_def(workflow_def, overwrite=True)
        return
    call_first_supported(
        client,
        [
            ("register_workflow_def", (workflow_def,), {}),
            ("create_workflow_def", (workflow_def,), {}),
            ("create", (workflow_def,), {}),
            ("registerWorkflowDef", (workflow_def,), {}),
        ],
        "register workflow definition",
    )


def start_workflow_by_name(
    name: str, version: int | None, workflow_input: dict[str, Any]
) -> Any:
    client = workflow_client()
    calls: list[tuple[str, tuple[Any, ...], dict[str, Any]]] = [
        ("start_workflow_by_name", (name, workflow_input), {"version": version}),
        ("start_workflow_by_name", (name, workflow_input, version), {}),
        ("start_workflow", (name, workflow_input), {"version": version}),
        ("start_workflow", (name, workflow_input, version), {}),
        ("start_workflow_1", (name,), {"body": workflow_input, "version": version}),
    ]
    return call_first_supported(client, calls, "start workflow")


def run_task_workers(handlers: dict[str, Callable[[dict[str, Any]], dict[str, Any]]]) -> None:
    TaskHandler = load_symbol(
        [
            ("conductor.client.automator.task_handler", "TaskHandler"),
            ("conductor.client.worker.task_handler", "TaskHandler"),
        ],
        "Conductor SDK TaskHandler",
    )
    WorkerInterface = load_symbol(
        [
            ("conductor.client.worker.worker_interface", "WorkerInterface"),
            ("conductor.client.worker.worker", "WorkerInterface"),
        ],
        "Conductor SDK WorkerInterface",
    )
    TaskResultStatus = load_symbol(
        [
            ("conductor.client.http.models.task_result_status", "TaskResultStatus"),
        ],
        "Conductor SDK TaskResultStatus",
    )
    TaskRunner = load_symbol(
        [
            ("conductor.client.automator.task_runner", "TaskRunner"),
        ],
        "Conductor SDK TaskRunner",
    )

    class FunctionWorker(WorkerInterface):
        def __init__(self, task_name: str, handler: Callable[[dict[str, Any]], dict[str, Any]]):
            super().__init__(task_name)
            self.task_name = task_name
            self.handler = handler
            self.worker_id = os.getenv("CONDUCTOR_WORKER_ID", "grn-pod-worker")
            self.poll_interval = int(float(os.getenv("CONDUCTOR_POLL_INTERVAL_SECONDS", "2")) * 1000)

        def get_task_definition_name(self) -> str:
            return self.task_name

        def get_identity(self) -> str:
            return self.worker_id

        def get_polling_interval_in_seconds(self) -> float:
            return self.poll_interval / 1000

        def execute(self, task: Any) -> Any:
            task_result = self.get_task_result_from_task(task)
            try:
                task_result.output_data = self.handler(task_input(task))
                task_result.status = TaskResultStatus.COMPLETED
            except Exception as exc:
                task_result.output_data = {"error": str(exc)}
                task_result.reason_for_incompletion = str(exc)
                task_result.status = TaskResultStatus.FAILED
            return task_result

    workers = [FunctionWorker(task_name, handler) for task_name, handler in handlers.items()]
    config = configuration()

    if os.name == "nt":
        threads = []
        for worker in workers:
            runner = TaskRunner(worker=worker, configuration=config)
            thread = threading.Thread(
                target=runner.run,
                name=f"conductor-{worker.get_task_definition_name()}",
            )
            thread.start()
            threads.append(thread)
        for thread in threads:
            thread.join()
        return

    task_handler = TaskHandler(workers=workers, configuration=config)

    if hasattr(task_handler, "start_processes"):
        task_handler.start_processes()
    elif hasattr(task_handler, "start"):
        try:
            task_handler.start(workers)
        except TypeError:
            task_handler.start()
    else:
        raise RuntimeError("The installed Conductor SDK TaskHandler has no start method.")


def task_input(task: Any) -> dict[str, Any]:
    if isinstance(task, dict):
        return task.get("inputData") or task.get("input_data") or {}
    for attribute in ("input_data", "inputData"):
        value = getattr(task, attribute, None)
        if value is not None:
            return value
    if hasattr(task, "to_dict"):
        payload = task.to_dict()
        return payload.get("inputData") or payload.get("input_data") or {}
    return {}


def call_first_supported(
    client: Any,
    calls: list[tuple[str, tuple[Any, ...], dict[str, Any]]],
    action: str,
) -> Any:
    errors = []
    for method_name, args, kwargs in calls:
        kwargs = {key: value for key, value in kwargs.items() if value is not None}
        method = getattr(client, method_name, None)
        if not method:
            continue
        try:
            return method(*args, **kwargs)
        except TypeError as exc:
            errors.append(f"{method_name}: {exc}")
    raise RuntimeError(f"Unable to {action} with the installed Conductor SDK. Tried: {errors}")
