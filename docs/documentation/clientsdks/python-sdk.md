---
description: "Build Conductor workers in Python with decorator-based task definitions, async support, and workflow management."
---

# Python SDK

!!! info "Source"
    GitHub: [conductor-oss/python-sdk](https://github.com/conductor-oss/python-sdk) | Report issues and contribute on GitHub.

## Start Conductor Server

If you don't already have a Conductor server running, pick one:

**Docker Compose (recommended, includes UI):**

```shell
docker run -p 8080:8080 conductoross/conductor:latest
```
The UI will be available at `http://localhost:8080` and the API at `http://localhost:8080/api`

**MacOS / Linux (one-liner):** (If you don't want to use docker, you can install and run the binary directly)
```shell
curl -sSL https://raw.githubusercontent.com/conductor-oss/conductor/main/conductor_server.sh | sh
```

**Conductor CLI**
```shell
# Installs conductor cli
npm install -g @conductor-oss/conductor-cli

# Start the open source conductor server
conductor server start
# see conductor server --help for all the available commands
```

## Install the SDK

```shell
pip install conductor-python
```

## 60-Second Quickstart

**Step 1: Create a workflow**

Workflows are definitions that reference task types (e.g. a SIMPLE task called `greet`). We'll build a workflow called
`greetings` that runs one task and returns its output.

Assuming you have a `WorkflowExecutor` (`executor`) and a worker task (`greet`):

```python
from conductor.client.workflow.conductor_workflow import ConductorWorkflow

workflow = ConductorWorkflow(name='greetings', version=1, executor=executor)
greet_task = greet(task_ref_name='greet_ref', name=workflow.input('name'))
workflow >> greet_task
workflow.output_parameters({'result': greet_task.output('result')})
workflow.register(overwrite=True)
```

**Step 2: Write a worker**

Workers are just Python functions decorated with `@worker_task` that poll Conductor for tasks and execute them.

```python
from conductor.client.worker.worker_task import worker_task

# register_task_def=True is convenient for local dev quickstarts; in production, manage task definitions separately.
@worker_task(task_definition_name='greet', register_task_def=True)
def greet(name: str) -> str:
    return f'Hello {name}'
```

**Step 3: Run your first workflow app**

Create a `quickstart.py` with the following:

```python
from conductor.client.automator.task_handler import TaskHandler
from conductor.client.configuration.configuration import Configuration
from conductor.client.orkes_clients import OrkesClients
from conductor.client.workflow.conductor_workflow import ConductorWorkflow
from conductor.client.worker.worker_task import worker_task


# A worker is any Python function.
@worker_task(task_definition_name='greet', register_task_def=True)
def greet(name: str) -> str:
    return f'Hello {name}'


def main():
    # Configure the SDK (reads CONDUCTOR_SERVER_URL / CONDUCTOR_AUTH_* from env).
    config = Configuration()

    clients = OrkesClients(configuration=config)
    executor = clients.get_workflow_executor()

    # Build a workflow with the >> operator.
    workflow = ConductorWorkflow(name='greetings', version=1, executor=executor)
    greet_task = greet(task_ref_name='greet_ref', name=workflow.input('name'))
    workflow >> greet_task
    workflow.output_parameters({'result': greet_task.output('result')})
    workflow.register(overwrite=True)

    # Start polling for tasks (one worker subprocess per worker function).
    with TaskHandler(configuration=config, scan_for_annotated_workers=True) as task_handler:
        task_handler.start_processes()

        # Run the workflow and get the result.
        run = executor.execute(name='greetings', version=1, workflow_input={'name': 'Conductor'})
        print(f'result: {run.output["result"]}')
        print(f'execution: {config.ui_host}/execution/{run.workflow_id}')


if __name__ == '__main__':
    main()
```

Run it:

```shell
python quickstart.py
```

> ### Using Orkes Conductor / Remote Server?
> Export your authentication credentials as well:
>
> ```shell
> export CONDUCTOR_SERVER_URL="https://your-cluster.orkesconductor.io/api"
>
> # If using Orkes Conductor that requires auth key/secret
> export CONDUCTOR_AUTH_KEY="your-key"
> export CONDUCTOR_AUTH_SECRET="your-secret"
>
> # Optional — set to false to force HTTP/1.1 if your network environment has unstable long-lived HTTP/2 connections (default: true)
> # export CONDUCTOR_HTTP2_ENABLED=false
> ```
> See the [Worker Configuration](https://github.com/conductor-oss/python-sdk/blob/main/WORKER_CONFIGURATION.md) guide for details.

That's it — you just defined a worker, built a workflow, and executed it. Open the Conductor UI (default:
[http://localhost:8127](http://localhost:8127)) to see the execution.

---

## Feature Showcase

### Workers: Sync and Async

The SDK automatically selects the right runner based on your function signature — `TaskRunner` (thread pool) for sync functions, `AsyncTaskRunner` (event loop) for async.

```python
from conductor.client.worker.worker_task import worker_task

# Sync worker — for CPU-bound work (uses ThreadPoolExecutor)
@worker_task(task_definition_name='process_image', thread_count=4)
def process_image(image_url: str) -> dict:
    import PIL.Image, io, requests
    img = PIL.Image.open(io.BytesIO(requests.get(image_url).content))
    img.thumbnail((256, 256))
    return {'width': img.width, 'height': img.height}


# Async worker — for I/O-bound work (uses AsyncTaskRunner, no thread overhead)
@worker_task(task_definition_name='fetch_data', thread_count=50)
async def fetch_data(url: str) -> dict:
    import httpx
    async with httpx.AsyncClient() as client:
        resp = await client.get(url)
    return resp.json()
```

Start workers with `TaskHandler` — it auto-discovers `@worker_task` functions and spawns one subprocess per worker:

```python
from conductor.client.automator.task_handler import TaskHandler
from conductor.client.configuration.configuration import Configuration

config = Configuration()
with TaskHandler(configuration=config, scan_for_annotated_workers=True) as task_handler:
    task_handler.start_processes()
    task_handler.join_processes()  # blocks forever (workers poll continuously)
```

See [examples/worker_example.py](https://github.com/conductor-oss/python-sdk/blob/main/examples/worker_example.py) and [examples/workers_e2e.py](https://github.com/conductor-oss/python-sdk/blob/main/examples/workers_e2e.py) for complete examples.

### Workflows with HTTP Calls and Waits

Chain custom workers with built-in system tasks — HTTP calls, waits, JavaScript, JQ transforms — all in one workflow:

```python
from conductor.client.workflow.conductor_workflow import ConductorWorkflow
from conductor.client.workflow.task.http_task import HttpTask
from conductor.client.workflow.task.wait_task import WaitTask

workflow = ConductorWorkflow(name='order_pipeline', version=1, executor=executor)

# Custom worker task
validate = validate_order(task_ref_name='validate', order_id=workflow.input('order_id'))

# Built-in HTTP task — call any API, no worker needed
charge_payment = HttpTask(task_ref_name='charge_payment', http_input={
    'uri': 'https://api.stripe.com/v1/charges',
    'method': 'POST',
    'headers': {'Authorization': ['Bearer ${workflow.input.stripe_key}']},
    'body': {'amount': '${validate.output.amount}'}
})

# Built-in Wait task — pause the workflow for 10 seconds
cool_down = WaitTask(task_ref_name='cool_down', wait_for_seconds=10)

# Another custom worker task
notify = send_notification(task_ref_name='notify', message='Order complete')

# Chain with >> operator
workflow >> validate >> charge_payment >> cool_down >> notify

# Execute synchronously and wait for the result
result = workflow.execute(workflow_input={'order_id': 'ORD-123', 'stripe_key': 'sk_test_...'})
print(result.output)
```

See [examples/kitchensink.py](https://github.com/conductor-oss/python-sdk/blob/main/examples/kitchensink.py) for all task types (HTTP, JavaScript, JQ, Switch, Terminate) and [examples/workflow_ops.py](https://github.com/conductor-oss/python-sdk/blob/main/examples/workflow_ops.py) for lifecycle operations.

### Long-Running Tasks with TaskContext

For tasks that take minutes or hours (batch processing, ML training, external approvals), use `TaskContext` to report progress and poll incrementally:

```python
from typing import Union
from conductor.client.worker.worker_task import worker_task
from conductor.client.context.task_context import get_task_context, TaskInProgress

@worker_task(task_definition_name='batch_job')
def batch_job(batch_id: str) -> Union[dict, TaskInProgress]:
    ctx = get_task_context()
    ctx.add_log(f"Processing batch {batch_id}, poll #{ctx.get_poll_count()}")

    if ctx.get_poll_count() < 3:
        # Not done yet — re-queue and check again in 30 seconds
        return TaskInProgress(callback_after_seconds=30, output={'progress': ctx.get_poll_count() * 33})

    # Done after 3 polls
    return {'status': 'completed', 'batch_id': batch_id}
```

`TaskContext` also provides access to task metadata, retry counts, workflow IDs, and the ability to add logs visible in the Conductor UI.

See [examples/task_context_example.py](https://github.com/conductor-oss/python-sdk/blob/main/examples/task_context_example.py) for all patterns (polling, retry-aware logic, async context, input access).

### Monitoring with Metrics

Enable Prometheus metrics with a single setting — the SDK exposes poll counts, execution times, error rates, and HTTP latency:

```python
from conductor.client.automator.task_handler import TaskHandler
from conductor.client.configuration.configuration import Configuration
from conductor.client.configuration.settings.metrics_settings import MetricsSettings

config = Configuration()
metrics = MetricsSettings(directory='/tmp/conductor-metrics', http_port=8000)

with TaskHandler(configuration=config, metrics_settings=metrics, scan_for_annotated_workers=True) as task_handler:
    task_handler.start_processes()
    task_handler.join_processes()
```

```shell
# Prometheus-compatible endpoint
curl http://localhost:8000/metrics
```

See [examples/metrics_example.py](https://github.com/conductor-oss/python-sdk/blob/main/examples/metrics_example.py) and [METRICS.md](https://github.com/conductor-oss/python-sdk/blob/main/METRICS.md) for details on all tracked metrics.

### Managing Workflow Executions

Full lifecycle control — start, execute, pause, resume, terminate, retry, restart, rerun, signal, and search:

```python
from conductor.client.configuration.configuration import Configuration
from conductor.client.http.models import StartWorkflowRequest, RerunWorkflowRequest, TaskResult
from conductor.client.orkes_clients import OrkesClients

config = Configuration()
clients = OrkesClients(configuration=config)
workflow_client = clients.get_workflow_client()
task_client = clients.get_task_client()
executor = clients.get_workflow_executor()

# Start async (returns workflow ID immediately)
workflow_id = executor.start_workflow(StartWorkflowRequest(name='my_workflow', input={'key': 'value'}))

# Execute sync (blocks until workflow completes)
result = executor.execute(name='my_workflow', version=1, workflow_input={'key': 'value'})

# Lifecycle management
workflow_client.pause_workflow(workflow_id)
workflow_client.resume_workflow(workflow_id)
workflow_client.terminate_workflow(workflow_id, reason='no longer needed')
workflow_client.retry_workflow(workflow_id)          # retry from last failed task
workflow_client.restart_workflow(workflow_id)         # restart from the beginning
workflow_client.rerun_workflow(workflow_id,           # rerun from a specific task
    RerunWorkflowRequest(re_run_from_task_id=task_id))

# Send a signal to a waiting workflow (complete a WAIT task externally)
task_client.update_task(TaskResult(
    workflow_instance_id=workflow_id,
    task_id=wait_task_id,
    status='COMPLETED',
    output_data={'approved': True}
))

# Search workflows
results = workflow_client.search(query='status IN (RUNNING) AND correlationId = "order-123"')
```

See [examples/workflow_ops.py](https://github.com/conductor-oss/python-sdk/blob/main/examples/workflow_ops.py) for a complete walkthrough of every operation.

---

## AI & LLM Workflows

Conductor supports AI-native workflows including agentic tool calling, RAG pipelines, and multi-agent orchestration.

**Agentic Workflows**

Build AI agents where LLMs dynamically select and call Python workers as tools. See [examples/agentic_workflows/](https://github.com/conductor-oss/python-sdk/blob/main/examples/agentic_workflows/) for all examples.

| Example | Description |
|---------|-------------|
| [llm_chat.py](https://github.com/conductor-oss/python-sdk/blob/main/examples/agentic_workflows/llm_chat.py) | Automated multi-turn science Q&A between two LLMs |
| [llm_chat_human_in_loop.py](https://github.com/conductor-oss/python-sdk/blob/main/examples/agentic_workflows/llm_chat_human_in_loop.py) | Interactive chat with WAIT task pauses for user input |
| [multiagent_chat.py](https://github.com/conductor-oss/python-sdk/blob/main/examples/agentic_workflows/multiagent_chat.py) | Multi-agent debate with moderator routing between panelists |
| [function_calling_example.py](https://github.com/conductor-oss/python-sdk/blob/main/examples/agentic_workflows/function_calling_example.py) | LLM picks which Python function to call based on user queries |
| [mcp_weather_agent.py](https://github.com/conductor-oss/python-sdk/blob/main/examples/agentic_workflows/mcp_weather_agent.py) | AI agent using MCP tools for weather queries |

**LLM and RAG Workflows**

| Example | Description |
|---------|-------------|
| [rag_workflow.py](https://github.com/conductor-oss/python-sdk/blob/main/examples/rag_workflow.py) | End-to-end RAG: document conversion (PDF/Word/Excel), pgvector indexing, semantic search, answer generation |
| [vector_db_helloworld.py](https://github.com/conductor-oss/python-sdk/blob/main/examples/orkes/vector_db_helloworld.py) | Vector database operations: text indexing, embedding generation, and semantic search with Pinecone |

```shell
# Automated multi-turn chat
python examples/agentic_workflows/llm_chat.py

# Multi-agent debate
python examples/agentic_workflows/multiagent_chat.py --topic "renewable energy"

# RAG pipeline
pip install "markitdown[pdf]"
python examples/rag_workflow.py document.pdf "What are the key findings?"
```

---

## Why Conductor?

| | |
|---|---|
| **Language agnostic** | Workers in Python, Java, Go, JS, C# — all in one workflow |
| **Durable execution** | Survives crashes, retries automatically, never loses state |
| **Built-in HTTP/Wait/JS tasks** | No code needed for common operations |
| **Horizontal scaling** | Built at Netflix for millions of workflows |
| **Full visibility** | UI shows every execution, every task, every retry |
| **Sync + Async execution** | Start-and-forget OR wait-for-result |
| **Human-in-the-loop** | WAIT tasks pause until an external signal |
| **AI-native** | LLM chat, RAG pipelines, function calling, MCP tools built-in |

---

## Examples

See the [Examples Guide](https://github.com/conductor-oss/python-sdk/blob/main/examples/README.md) for the full catalog. Key examples:

| Example | Description | Run |
|---------|-------------|-----|
| [workers_e2e.py](https://github.com/conductor-oss/python-sdk/blob/main/examples/workers_e2e.py) | End-to-end: sync + async workers, metrics | `python examples/workers_e2e.py` |
| [kitchensink.py](https://github.com/conductor-oss/python-sdk/blob/main/examples/kitchensink.py) | All task types (HTTP, JS, JQ, Switch) | `python examples/kitchensink.py` |
| [workflow_ops.py](https://github.com/conductor-oss/python-sdk/blob/main/examples/workflow_ops.py) | Pause, resume, terminate, retry, restart, rerun, signal | `python examples/workflow_ops.py` |
| [task_context_example.py](https://github.com/conductor-oss/python-sdk/blob/main/examples/task_context_example.py) | Long-running tasks with TaskInProgress | `python examples/task_context_example.py` |
| [metrics_example.py](https://github.com/conductor-oss/python-sdk/blob/main/examples/metrics_example.py) | Prometheus metrics collection | `python examples/metrics_example.py` |
| [fastapi_worker_service.py](https://github.com/conductor-oss/python-sdk/blob/main/examples/fastapi_worker_service.py) | FastAPI: expose a workflow as an API (+ workers) | `uvicorn examples.fastapi_worker_service:app --port 8081 --workers 1` |
| [helloworld.py](https://github.com/conductor-oss/python-sdk/blob/main/examples/helloworld/helloworld.py) | Minimal hello world | `python examples/helloworld/helloworld.py` |
| [dynamic_workflow.py](https://github.com/conductor-oss/python-sdk/blob/main/examples/dynamic_workflow.py) | Build workflows programmatically | `python examples/dynamic_workflow.py` |
| [test_workflows.py](https://github.com/conductor-oss/python-sdk/blob/main/examples/test_workflows.py) | Unit testing workflows | `python -m unittest examples.test_workflows` |

**API Journey Examples**

End-to-end examples covering all APIs for each domain:

| Example | APIs | Run |
|---------|------|-----|
| [authorization_journey.py](https://github.com/conductor-oss/python-sdk/blob/main/examples/authorization_journey.py) | Authorization APIs | `python examples/authorization_journey.py` |
| [metadata_journey.py](https://github.com/conductor-oss/python-sdk/blob/main/examples/metadata_journey.py) | Metadata APIs | `python examples/metadata_journey.py` |
| [schedule_journey.py](https://github.com/conductor-oss/python-sdk/blob/main/examples/schedule_journey.py) | Schedule APIs | `python examples/schedule_journey.py` |
| [prompt_journey.py](https://github.com/conductor-oss/python-sdk/blob/main/examples/prompt_journey.py) | Prompt APIs | `python examples/prompt_journey.py` |

## Documentation

| Document | Description |
|----------|-------------|
| [Worker Design](https://github.com/conductor-oss/python-sdk/blob/main/docs/design/WORKER_DESIGN.md) | Architecture: AsyncTaskRunner vs TaskRunner, discovery, lifecycle |
| [Worker Guide](https://github.com/conductor-oss/python-sdk/blob/main/docs/WORKER.md) | All worker patterns (function, class, annotation, async) |
| [Worker Configuration](https://github.com/conductor-oss/python-sdk/blob/main/WORKER_CONFIGURATION.md) | Hierarchical environment variable configuration |
| [Workflow Management](https://github.com/conductor-oss/python-sdk/blob/main/docs/WORKFLOW.md) | Start, pause, resume, terminate, retry, search |
| [Workflow Testing](https://github.com/conductor-oss/python-sdk/blob/main/docs/WORKFLOW_TESTING.md) | Unit testing with mock outputs |
| [Task Management](https://github.com/conductor-oss/python-sdk/blob/main/docs/TASK_MANAGEMENT.md) | Task operations |
| [Metadata](https://github.com/conductor-oss/python-sdk/blob/main/docs/METADATA.md) | Task & workflow definitions |
| [Authorization](https://github.com/conductor-oss/python-sdk/blob/main/docs/AUTHORIZATION.md) | Users, groups, applications, permissions |
| [Schedules](https://github.com/conductor-oss/python-sdk/blob/main/docs/SCHEDULE.md) | Workflow scheduling |
| [Secrets](https://github.com/conductor-oss/python-sdk/blob/main/docs/SECRET_MANAGEMENT.md) | Secret storage |
| [Prompts](https://github.com/conductor-oss/python-sdk/blob/main/docs/PROMPT.md) | AI/LLM prompt templates |
| [Integrations](https://github.com/conductor-oss/python-sdk/blob/main/docs/INTEGRATION.md) | AI/LLM provider integrations |
| [Metrics](https://github.com/conductor-oss/python-sdk/blob/main/METRICS.md) | Prometheus metrics collection |
| [Examples](https://github.com/conductor-oss/python-sdk/blob/main/examples/README.md) | Complete examples catalog |

## Frequently Asked Questions

**Is this the same as Netflix Conductor?**

Yes. Conductor OSS is the continuation of the original [Netflix Conductor](https://github.com/Netflix/conductor) repository after Netflix contributed the project to the open-source foundation.

**Is this project actively maintained?**

Yes. [Orkes](https://orkes.io) is the primary maintainer and offers an enterprise SaaS platform for Conductor across all major cloud providers.

**Can Conductor scale to handle my workload?**

Conductor was built at Netflix to handle massive scale and has been battle-tested in production environments processing millions of workflows. It scales horizontally to meet virtually any demand.

**Does Conductor support durable code execution?**

Yes. Conductor ensures workflows complete reliably even in the face of infrastructure failures, process crashes, or network issues.

**Are workflows always asynchronous?**

No. While Conductor excels at asynchronous orchestration, it also supports synchronous workflow execution when immediate results are required.

**Do I need to use a Conductor-specific framework?**

No. Conductor is language and framework agnostic. Use your preferred language and framework — the [SDKs](https://github.com/conductor-oss/conductor#conductor-sdks) provide native integration for Python, Java, JavaScript, Go, C#, and more.

**Can I mix workers written in different languages?**

Yes. A single workflow can have workers written in Python, Java, Go, or any other supported language. Workers communicate through the Conductor server, not directly with each other.

**What Python versions are supported?**

Python 3.9 and above.

**Should I use `def` or `async def` for my workers?**

Use `async def` for I/O-bound tasks (API calls, database queries) — the SDK uses `AsyncTaskRunner` with a single event loop for high concurrency with low overhead. Use regular `def` for CPU-bound or blocking work — the SDK uses `TaskRunner` with a thread pool. The SDK selects the right runner automatically based on your function signature.

**How do I run workers in production?**

Workers are standard Python processes. Deploy them as you would any Python application — in containers, VMs, or bare metal. Workers poll the Conductor server for tasks, so no inbound ports need to be opened. See [Worker Design](https://github.com/conductor-oss/python-sdk/blob/main/docs/design/WORKER_DESIGN.md) for architecture details.

**How do I test workflows without running a full Conductor server?**

The SDK provides a test framework that uses Conductor's `POST /api/workflow/test` endpoint to evaluate workflows with mock task outputs. See [Workflow Testing](https://github.com/conductor-oss/python-sdk/blob/main/docs/WORKFLOW_TESTING.md) for details.

## Support

- [Open an issue (SDK)](https://github.com/conductor-sdk/conductor-python/issues) for SDK bugs, questions, and feature requests
- [Open an issue (Conductor server)](https://github.com/conductor-oss/conductor/issues) for Conductor OSS server issues
- [Join the Conductor Slack](https://join.slack.com/t/orkes-conductor/shared_invite/zt-2vdbx239s-Eacdyqya9giNLHfrCavfaA) for community discussion and help
- [Orkes Community Forum](https://community.orkes.io/) for Q&A

## License

Apache 2.0


## Examples

Browse all examples on GitHub: [conductor-oss/python-sdk/examples](https://github.com/conductor-oss/python-sdk/tree/main/examples)

| Example | Type |
|---|---|
| [Readme](https://github.com/conductor-oss/python-sdk/blob/main/examples/README.md) | file |
| [Agentic Workflow](https://github.com/conductor-oss/python-sdk/blob/main/examples/agentic_workflow.py) | file |
| [Agentic Workflows](https://github.com/conductor-oss/python-sdk/tree/main/examples/agentic_workflows) | directory |
| [Authorization Journey](https://github.com/conductor-oss/python-sdk/blob/main/examples/authorization_journey.py) | file |
| [Dynamic Workflow](https://github.com/conductor-oss/python-sdk/blob/main/examples/dynamic_workflow.py) | file |
| [Event Listener Examples](https://github.com/conductor-oss/python-sdk/blob/main/examples/event_listener_examples.py) | file |
| [Fastapi Worker Service](https://github.com/conductor-oss/python-sdk/blob/main/examples/fastapi_worker_service.py) | file |
| [Helloworld](https://github.com/conductor-oss/python-sdk/tree/main/examples/helloworld) | directory |
| [Kitchensink](https://github.com/conductor-oss/python-sdk/blob/main/examples/kitchensink.py) | file |
| [Metadata Journey](https://github.com/conductor-oss/python-sdk/blob/main/examples/metadata_journey.py) | file |
| [Metadata Journey Oss](https://github.com/conductor-oss/python-sdk/blob/main/examples/metadata_journey_oss.py) | file |
| [Metrics Example](https://github.com/conductor-oss/python-sdk/blob/main/examples/metrics_example.py) | file |
| [Orkes](https://github.com/conductor-oss/python-sdk/tree/main/examples/orkes) | directory |
| [Prompt Journey](https://github.com/conductor-oss/python-sdk/blob/main/examples/prompt_journey.py) | file |
| [Rag Workflow](https://github.com/conductor-oss/python-sdk/blob/main/examples/rag_workflow.py) | file |
| [Schedule Journey](https://github.com/conductor-oss/python-sdk/blob/main/examples/schedule_journey.py) | file |
| [Shell Worker](https://github.com/conductor-oss/python-sdk/blob/main/examples/shell_worker.py) | file |
| [Task Configure](https://github.com/conductor-oss/python-sdk/blob/main/examples/task_configure.py) | file |
| [Task Context Example](https://github.com/conductor-oss/python-sdk/blob/main/examples/task_context_example.py) | file |
| [Task Listener Example](https://github.com/conductor-oss/python-sdk/blob/main/examples/task_listener_example.py) | file |
| [Task Workers](https://github.com/conductor-oss/python-sdk/blob/main/examples/task_workers.py) | file |
| [Test Ai Examples](https://github.com/conductor-oss/python-sdk/blob/main/examples/test_ai_examples.py) | file |
| [Test Workflows](https://github.com/conductor-oss/python-sdk/blob/main/examples/test_workflows.py) | file |
| [Untrusted Host](https://github.com/conductor-oss/python-sdk/blob/main/examples/untrusted_host.py) | file |
| [User Example](https://github.com/conductor-oss/python-sdk/tree/main/examples/user_example) | directory |
| [Worker Configuration Example](https://github.com/conductor-oss/python-sdk/blob/main/examples/worker_configuration_example.py) | file |
| [Worker Discovery](https://github.com/conductor-oss/python-sdk/tree/main/examples/worker_discovery) | directory |
| [Worker Example](https://github.com/conductor-oss/python-sdk/blob/main/examples/worker_example.py) | file |
| [Workers E2E](https://github.com/conductor-oss/python-sdk/blob/main/examples/workers_e2e.py) | file |
| [Workers E2E Workflow](https://github.com/conductor-oss/python-sdk/blob/main/examples/workers_e2e_workflow.json) | file |
| [Workflow Ops](https://github.com/conductor-oss/python-sdk/blob/main/examples/workflow_ops.py) | file |
| [Workflow Status Listner](https://github.com/conductor-oss/python-sdk/blob/main/examples/workflow_status_listner.py) | file |
