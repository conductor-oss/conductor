---
description: "Workflow as code — build code-first workflows dynamically in Python using the Conductor SDK. Conditional branching, loops, parallel execution, and runtime-generated dynamic workflows."
---

# Dynamic workflows in code

## Workflow as code

Conductor supports a code-first workflow approach — build workflows programmatically using the Python SDK instead of writing JSON by hand. This workflow as code pattern lets you chain tasks with the `>>` operator, add conditional logic, loops, and parallel branches — all in Python. Code-first workflows are ideal for dynamic workflows where the task graph is determined at runtime.

### Simple sequential workflow

Chain tasks with the `>>` operator. Worker functions decorated with `@worker_task` become reusable task building blocks.

```python
from conductor.client.workflow.conductor_workflow import ConductorWorkflow
from conductor.client.worker.worker_task import worker_task


@worker_task(task_definition_name='fetch_order')
def fetch_order(order_id: str) -> dict:
    return {'order_id': order_id, 'amount': 99.99, 'item': 'Widget'}


@worker_task(task_definition_name='process_payment')
def process_payment(order_id: str, amount: float) -> dict:
    return {'transaction_id': 'txn_abc123', 'status': 'charged'}


@worker_task(task_definition_name='ship_order')
def ship_order(order_id: str, transaction_id: str) -> dict:
    return {'tracking': 'TRACK-456', 'carrier': 'FedEx'}


workflow = ConductorWorkflow(name='order_fulfillment', version=1, executor=executor)

fetch = fetch_order(task_ref_name='fetch', order_id=workflow.input('order_id'))
pay = process_payment(
    task_ref_name='pay',
    order_id=workflow.input('order_id'),
    amount=fetch.output('amount'),
)
ship = ship_order(
    task_ref_name='ship',
    order_id=workflow.input('order_id'),
    transaction_id=pay.output('transaction_id'),
)

workflow >> fetch >> pay >> ship
workflow.output_parameters({
    'tracking': ship.output('tracking'),
    'transaction_id': pay.output('transaction_id'),
})
workflow.register(overwrite=True)
```

---

### Conditional branching with Switch

Route execution based on task output or workflow input. Each case gets its own task chain.

```python
from conductor.client.workflow.conductor_workflow import ConductorWorkflow
from conductor.client.workflow.task.switch_task import SwitchTask


workflow = ConductorWorkflow(name='route_by_priority', version=1, executor=executor)

classify = classify_ticket(
    task_ref_name='classify',
    description=workflow.input('description'),
)

switch = SwitchTask(task_ref_name='priority_router', case_expression=classify.output('priority'))

# Each case is a list of tasks to execute
switch.switch_case('critical', [
    page_oncall(task_ref_name='page', ticket_id=workflow.input('ticket_id')),
    escalate(task_ref_name='escalate', ticket_id=workflow.input('ticket_id')),
])
switch.switch_case('high', [
    assign_senior(task_ref_name='assign', ticket_id=workflow.input('ticket_id')),
])
switch.default_case([
    add_to_backlog(task_ref_name='backlog', ticket_id=workflow.input('ticket_id')),
])

workflow >> classify >> switch
workflow.register(overwrite=True)
```

---

### Parallel execution with Fork/Join

Run independent tasks in parallel and wait for all to complete.

```python
from conductor.client.workflow.conductor_workflow import ConductorWorkflow
from conductor.client.workflow.task.fork_task import ForkTask
from conductor.client.workflow.task.join_task import JoinTask


workflow = ConductorWorkflow(name='parallel_enrichment', version=1, executor=executor)

# Define independent tasks
credit_check = check_credit(task_ref_name='credit', customer_id=workflow.input('customer_id'))
fraud_check = check_fraud(task_ref_name='fraud', customer_id=workflow.input('customer_id'))
kyc_check = check_kyc(task_ref_name='kyc', customer_id=workflow.input('customer_id'))

# Fork runs all branches in parallel
fork = ForkTask(
    task_ref_name='parallel_checks',
    forked_tasks=[
        [credit_check],
        [fraud_check],
        [kyc_check],
    ],
)

# Join waits for all branches
join = JoinTask(task_ref_name='wait_all', join_on=['credit', 'fraud', 'kyc'])

# Merge results
decide = make_decision(
    task_ref_name='decide',
    credit_score=credit_check.output('score'),
    fraud_risk=fraud_check.output('risk_level'),
    kyc_status=kyc_check.output('status'),
)

workflow >> fork >> join >> decide
workflow.output_parameters({'decision': decide.output('result')})
workflow.register(overwrite=True)
```

---

### Loops with Do/While

Repeat a set of tasks until a condition is met — useful for polling, retries, or iterative AI agent loops.

```python
from conductor.client.workflow.conductor_workflow import ConductorWorkflow
from conductor.client.workflow.task.do_while_task import DoWhileTask


workflow = ConductorWorkflow(name='agent_loop', version=1, executor=executor)

# The task(s) to repeat each iteration
think = call_llm(
    task_ref_name='think',
    prompt=workflow.input('goal'),
)
act = execute_tool(
    task_ref_name='act',
    tool=think.output('tool'),
    args=think.output('args'),
)

# Loop until the LLM says it's done (max 10 iterations)
loop = DoWhileTask(
    task_ref_name='agent_loop',
    termination_condition='if ($.act["output"]["done"] == true) { false; } else { true; }',
    tasks=[think, act],
)
loop.input_parameters.update({'max_iterations': 10})

summarize = summarize_results(task_ref_name='summarize', results=act.output('results'))

workflow >> loop >> summarize
workflow.register(overwrite=True)
```

---

### HTTP + system tasks mixed with workers

Combine built-in system tasks (HTTP, Wait, JQ Transform) with custom workers — no extra deployment needed for system tasks.

{% raw %}
```python
from conductor.client.workflow.conductor_workflow import ConductorWorkflow
from conductor.client.workflow.task.http_task import HttpTask
from conductor.client.workflow.task.json_jq_task import JsonJQTask
from conductor.client.workflow.task.wait_task import WaitTask


workflow = ConductorWorkflow(name='data_pipeline', version=1, executor=executor)

# HTTP task — fetch data from an external API (no worker needed)
fetch = HttpTask(task_ref_name='fetch_data', http_input={
    'uri': 'https://api.example.com/records',
    'method': 'GET',
    'headers': {'Authorization': ['Bearer ${workflow.input.api_key}']},
})

# JQ Transform — reshape the response (no worker needed)
transform = JsonJQTask(
    task_ref_name='transform',
    script='.body.records | map({id: .id, value: .metrics.total})',
)
transform.input_parameters.update({
    'records': fetch.output('response.body'),
})

# Custom worker — run business logic
enrich = enrich_records(
    task_ref_name='enrich',
    records=transform.output('result'),
)

# Wait — pause for 5 seconds before the next step
cooldown = WaitTask(task_ref_name='cooldown', wait_for_seconds=5)

# Custom worker — store results
store = save_to_database(task_ref_name='store', records=enrich.output('enriched'))

workflow >> fetch >> transform >> enrich >> cooldown >> store
workflow.output_parameters({'stored': store.output('count')})
workflow.register(overwrite=True)
```
{% endraw %}

---

### Sub-workflows

Break large workflows into reusable pieces. A parent workflow invokes child workflows as tasks.

```python
from conductor.client.workflow.conductor_workflow import ConductorWorkflow
from conductor.client.workflow.task.sub_workflow_task import SubWorkflowTask


# Child workflow (registered separately)
child = ConductorWorkflow(name='process_single_item', version=1, executor=executor)
validate = validate_item(task_ref_name='validate', item=child.input('item'))
transform = transform_item(task_ref_name='transform', item=validate.output('validated'))
child >> validate >> transform
child.output_parameters({'result': transform.output('transformed')})
child.register(overwrite=True)


# Parent workflow invokes the child
parent = ConductorWorkflow(name='batch_processor', version=1, executor=executor)

prepare = prepare_batch(task_ref_name='prepare', batch_id=parent.input('batch_id'))

run_child = SubWorkflowTask(
    task_ref_name='process_item',
    workflow_name='process_single_item',
    version=1,
)
run_child.input_parameters.update({'item': prepare.output('first_item')})

aggregate = aggregate_results(
    task_ref_name='aggregate',
    result=run_child.output('result'),
)

parent >> prepare >> run_child >> aggregate
parent.register(overwrite=True)
```

---

### Runtime-generated dynamic workflow

Build a workflow definition at runtime and execute it without pre-registration. This runtime workflow pattern enables dynamic workflows where the task graph is generated on-the-fly — useful for AI agents, data pipelines, and any scenario where the steps are not known ahead of time.

{% raw %}
```python
from conductor.client.configuration.configuration import Configuration
from conductor.client.orkes_clients import OrkesClients
from conductor.client.http.models import StartWorkflowRequest


config = Configuration()
clients = OrkesClients(configuration=config)
executor = clients.get_workflow_executor()

# Build the workflow definition dynamically
steps = ['validate', 'enrich', 'store']  # determined at runtime

tasks = []
for i, step in enumerate(steps):
    tasks.append({
        'name': step,
        'taskReferenceName': f'{step}_{i}',
        'type': 'SIMPLE',
        'inputParameters': {
            'data': '${workflow.input.data}' if i == 0 else f'${{{steps[i-1]}_{i-1}.output.result}}',
        },
    })

# Start with inline definition — no pre-registration needed
request = StartWorkflowRequest(
    name='dynamic_pipeline',
    workflow_def={
        'name': 'dynamic_pipeline',
        'version': 1,
        'tasks': tasks,
        'outputParameters': {
            'result': f'${{{steps[-1]}_{len(steps)-1}.output.result}}',
        },
    },
    input={'data': {'key': 'value'}},
)

workflow_id = executor.start_workflow(request)
print(f'Started dynamic workflow: {workflow_id}')
```
{% endraw %}

This pattern is powerful for AI agents that generate execution plans at runtime — the LLM produces the list of steps, your code builds the workflow definition, and Conductor executes it with full durability, retries, and observability.

---

### Execute and wait for result

Run a workflow synchronously and get the result inline — useful for APIs and interactive applications.

```python
from conductor.client.configuration.configuration import Configuration
from conductor.client.orkes_clients import OrkesClients

config = Configuration()
clients = OrkesClients(configuration=config)
executor = clients.get_workflow_executor()

# Execute synchronously — blocks until the workflow completes
run = executor.execute(
    name='order_fulfillment',
    version=1,
    workflow_input={'order_id': 'ORD-789'},
)

print(f'Status:  {run.status}')
print(f'Output:  {run.output}')
print(f'View:    {config.ui_host}/execution/{run.workflow_id}')
```

---

## Setup

All examples above assume a `WorkflowExecutor` instance. Here is the standard setup:

```python
from conductor.client.configuration.configuration import Configuration
from conductor.client.orkes_clients import OrkesClients

config = Configuration()  # reads CONDUCTOR_SERVER_URL from env
clients = OrkesClients(configuration=config)
executor = clients.get_workflow_executor()
```

```shell
pip install conductor-python
export CONDUCTOR_SERVER_URL=http://localhost:8080/api
```

For more Python SDK examples, see the [Python SDK documentation](../sdk/python-sdk.md) and the [examples on GitHub](https://github.com/conductor-oss/python-sdk/tree/main/examples).
