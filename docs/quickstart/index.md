---
description: Get the Conductor durable code execution engine running in under 5 minutes. Install the CLI, define a JSON workflow, write workers in Java, Python, or JavaScript, and execute your first durable workflow.
---

# Quickstart

Get Conductor running and execute your first workflow in under 5 minutes.

## 1. Start the server

=== "CLI (recommended)"

    ```bash
    npm install -g @conductor-oss/conductor-cli
    conductor server start
    ```

=== "Docker"

    ```bash
    docker run -p 8080:8080 conductoross/conductor:latest
    ```

The Conductor UI is available at [http://localhost:8127](http://localhost:8127) and the API at [http://localhost:8080](http://localhost:8080).


## 2. Define a workflow

Create a file called `workflow.json` with a simple two-step workflow:

```json
{
  "name": "process_order",
  "description": "Validate and charge an order",
  "version": 1,
  "tasks": [
    {
      "name": "validate_order",
      "taskReferenceName": "validate",
      "type": "SIMPLE",
      "inputParameters": {
        "orderId": "${workflow.input.orderId}"
      }
    },
    {
      "name": "charge_payment",
      "taskReferenceName": "charge",
      "type": "SIMPLE",
      "inputParameters": {
        "orderId": "${workflow.input.orderId}",
        "amount": "${validate.output.amount}"
      }
    }
  ],
  "outputParameters": {
    "chargeId": "${charge.output.chargeId}"
  },
  "schemaVersion": 2,
  "ownerEmail": "dev@example.com"
}
```

**What's happening here:**

- `process_order` defines a workflow with two sequential tasks.
- Each task has a `name` (task definition) and a `taskReferenceName` (unique alias within this workflow).
- `type: "SIMPLE"` means these tasks will be executed by your **workers** (external code you write).
- `inputParameters` use Conductor's expression syntax (`${...}`) to pass data from the workflow input and between tasks.


## 3. Register the workflow

```bash
conductor workflow create workflow.json
```

You also need to register the task definitions so Conductor knows about them. Create a file called `tasks.json`:

```json
[
  { "name": "validate_order", "retryCount": 3, "retryLogic": "FIXED", "retryDelaySeconds": 10 },
  { "name": "charge_payment", "retryCount": 3, "retryLogic": "FIXED", "retryDelaySeconds": 10 }
]
```

Then register the task definitions:

```bash
conductor task create tasks.json
```

??? note "Using cURL"
    ```bash
    curl -X POST http://localhost:8080/api/metadata/workflow \
      -H 'Content-Type: application/json' \
      -d @workflow.json
    ```

    ```bash
    curl -X POST http://localhost:8080/api/metadata/taskdefs \
      -H 'Content-Type: application/json' \
      -d @tasks.json
    ```


## 4. Write workers

Workers are the code that execute your tasks. They poll Conductor for work, execute your logic, and report results back.

=== "Java"

    ```java
    @WorkerTask("validate_order")
    public Map<String, Object> validateOrder(Map<String, Object> input) {
        String orderId = (String) input.get("orderId");
        // Your validation logic here
        return Map.of(
            "valid", true,
            "amount", 49.99
        );
    }

    @WorkerTask("charge_payment")
    public Map<String, Object> chargePayment(Map<String, Object> input) {
        String orderId = (String) input.get("orderId");
        double amount = (double) input.get("amount");
        // Your payment logic here
        return Map.of("chargeId", "ch_" + orderId);
    }
    ```

=== "Python"

    ```python
    from conductor.client.worker.worker_task import worker_task

    @worker_task(task_definition_name="validate_order")
    def validate_order(task):
        order_id = task.input_data.get("orderId")
        # Your validation logic here
        return {"valid": True, "amount": 49.99}

    @worker_task(task_definition_name="charge_payment")
    def charge_payment(task):
        order_id = task.input_data.get("orderId")
        amount = task.input_data.get("amount")
        # Your payment logic here
        return {"chargeId": f"ch_{order_id}"}
    ```

=== "JavaScript"

    ```javascript
    const { ConductorWorker } = require("@conductor-oss/conductor-client");

    const worker = new ConductorWorker({
      url: "http://localhost:8080/api",
    });

    worker.register("validate_order", async (task) => {
      const orderId = task.inputData.orderId;
      // Your validation logic here
      return { valid: true, amount: 49.99 };
    });

    worker.register("charge_payment", async (task) => {
      const orderId = task.inputData.orderId;
      const amount = task.inputData.amount;
      // Your payment logic here
      return { chargeId: `ch_${orderId}` };
    });

    worker.start();
    ```

=== "Go"

    ```go
    package main

    import (
        "fmt"
        "github.com/conductor-sdk/conductor-go/sdk/model"
        "github.com/conductor-sdk/conductor-go/sdk/worker"
    )

    func ValidateOrder(task *model.Task) (interface{}, error) {
        orderId := task.InputData["orderId"]
        // Your validation logic here
        return map[string]interface{}{
            "valid":  true,
            "amount": 49.99,
        }, nil
    }

    func ChargePayment(task *model.Task) (interface{}, error) {
        orderId := task.InputData["orderId"]
        // Your payment logic here
        return map[string]interface{}{
            "chargeId": fmt.Sprintf("ch_%s", orderId),
        }, nil
    }
    ```

    See the [Go SDK repo](https://github.com/conductor-oss/go-sdk) for full setup instructions.

=== "C#"

    ```csharp
    using ConductorSharp.Engine;

    [WorkerTask("validate_order")]
    public static TaskResult ValidateOrder(Task task)
    {
        var orderId = task.InputData["orderId"];
        // Your validation logic here
        return task.Completed(new {
            valid = true,
            amount = 49.99
        });
    }

    [WorkerTask("charge_payment")]
    public static TaskResult ChargePayment(Task task)
    {
        var orderId = task.InputData["orderId"];
        var amount = task.InputData["amount"];
        // Your payment logic here
        return task.Completed(new {
            chargeId = $"ch_{orderId}"
        });
    }
    ```

    See the [C# SDK repo](https://github.com/conductor-oss/csharp-sdk) for full setup instructions.

=== "Ruby"

    ```ruby
    require 'conductor_worker'

    ConductorWorker.new(url: 'http://localhost:8080/api') do |w|
      w.register('validate_order') do |task|
        order_id = task.input_data['orderId']
        # Your validation logic here
        { valid: true, amount: 49.99 }
      end

      w.register('charge_payment') do |task|
        order_id = task.input_data['orderId']
        amount = task.input_data['amount']
        # Your payment logic here
        { chargeId: "ch_#{order_id}" }
      end
    end
    ```

    See the [Ruby SDK repo](https://github.com/conductor-oss/ruby-sdk) for full setup instructions.

=== "Rust"

    ```rust
    use conductor::worker::{Worker, Task, TaskResult};

    fn validate_order(task: &Task) -> TaskResult {
        let order_id = task.input_data.get("orderId").unwrap();
        // Your validation logic here
        TaskResult::completed(serde_json::json!({
            "valid": true,
            "amount": 49.99
        }))
    }

    fn charge_payment(task: &Task) -> TaskResult {
        let order_id = task.input_data.get("orderId").unwrap();
        let amount = task.input_data.get("amount").unwrap();
        // Your payment logic here
        TaskResult::completed(serde_json::json!({
            "chargeId": format!("ch_{}", order_id)
        }))
    }
    ```

    See the [Rust SDK repo](https://github.com/conductor-oss/rust-sdk) for full setup instructions.

Start your workers so they begin polling for tasks.


## 5. Run the workflow

```bash
conductor workflow start -w process_order -i '{"orderId": "order-123"}'
```

Conductor returns a **workflow execution ID**. Use it to check the status:

```bash
conductor workflow status {workflowId}
```

??? note "Using cURL"
    ```bash
    curl -X POST http://localhost:8080/api/workflow/process_order \
      -H 'Content-Type: application/json' \
      -d '{"orderId": "order-123"}'
    ```

    ```bash
    curl http://localhost:8080/api/workflow/{workflowId}
    ```

Or open the Conductor UI at [http://localhost:8127](http://localhost:8127) to see the execution visually—including the task timeline, inputs/outputs, and status of each step.


## What just happened?

1. You **registered** a workflow definition and task definitions with Conductor.
2. Your **workers** started polling for tasks.
3. When you **started** the workflow, Conductor scheduled `validate_order` first.
4. Your worker picked it up, executed the logic, and returned the result.
5. Conductor then scheduled `charge_payment` with the output from the previous step.
6. Once both tasks completed, the workflow reached the `COMPLETED` state.

Conductor handled the orchestration, retries, state management, and data flow between tasks—your workers just focused on business logic.


## Next steps

- **[System tasks](../documentation/configuration/workflowdef/systemtasks/index.md)** — Use built-in HTTP, Event, and Inline tasks without writing workers.
- **[Operators](../documentation/configuration/workflowdef/operators/index.md)** — Add fork/join, switch, loops, and sub-workflows.
- **[Error handling](../devguide/how-tos/Workflows/handling-errors.md)** — Configure retries, timeouts, and failure workflows.
- **[Client SDKs](../documentation/clientsdks/index.md)** — Java, Python, Go, C#, JavaScript, and Clojure.
- **[Running with Docker](../devguide/running/docker.md)** — Production-ready deployment with Docker.
