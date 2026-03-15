---
description: "Learn about workers in Conductor — the code that executes tasks in workflows, written in any language and hosted anywhere you choose."
---

# Workers
A worker is responsible for executing a task in a workflow. Each type of worker implements the core functionality of each task, handling the logic as defined in its code.

System task workers are managed by Conductor within its JVM, while `SIMPLE` task workers are to be implemented by yourself. These workers can be implemented in any programming language of your choice (Python, Java, JavaScript, C#, Go, and Clojure) and hosted anywhere outside the Conductor environment.

!!! Note
    Conductor provides a set of worker frameworks in its SDKs. These frameworks come with comes with features like polling threads, metrics, and server communication, making it easy to create custom workers.

These workers communicate with the Conductor server via REST/gRPC, allowing them to poll for tasks and update the task status. Learn more in [Architecture](../architecture/index.md).


## How workers work

1. **Poll** — The worker polls the Conductor server for tasks of a specific type.
2. **Execute** — The worker receives a task, executes the business logic, and produces an output.
3. **Report** — The worker reports the task result (COMPLETED or FAILED) back to the server.

Conductor handles scheduling, retries, and state persistence. Your worker just focuses on business logic.


## Worker examples

=== "Python"

    The `@worker_task` decorator maps task input parameters directly to function arguments with type hints:

    ```python
    from conductor.client.automator.task_handler import TaskHandler
    from conductor.client.configuration.configuration import Configuration
    from conductor.client.worker.worker_task import worker_task
    from dataclasses import dataclass


    @dataclass
    class OrderResult:
        orderId: str
        total: float
        discount: float
        status: str


    @worker_task(task_definition_name="process_order")
    def process_order(orderId: str, amount: float = 0) -> OrderResult:
        discount = amount * 0.1 if amount > 100 else 0
        total = amount - discount
        return OrderResult(
            orderId=orderId,
            total=total,
            discount=discount,
            status="processed",
        )


    def main():
        config = Configuration(server_api_url="http://localhost:8080/api")
        handler = TaskHandler(configuration=config)
        handler.start_processes()

        try:
            while True:
                pass
        except KeyboardInterrupt:
            handler.stop_processes()


    if __name__ == "__main__":
        main()
    ```

    ```bash
    pip install conductor-python
    python worker.py
    ```

=== "Java"

    Use `@WorkerTask` with `@InputParam` to map task inputs directly to typed method parameters. Return a typed object — the framework serializes it automatically:

    ```java
    import com.netflix.conductor.sdk.workflow.task.WorkerTask;
    import com.netflix.conductor.sdk.workflow.task.InputParam;
    import com.netflix.conductor.sdk.workflow.task.OutputParam;
    import lombok.Data;
    import lombok.AllArgsConstructor;

    @Data
    @AllArgsConstructor
    public class OrderResult {
        private String orderId;
        private double total;
        private double discount;
        private String status;
    }

    @Component
    public class OrderWorker {

        @WorkerTask("process_order")
        public OrderResult processOrder(
                @InputParam("orderId") String orderId,
                @InputParam("amount") double amount) {

            double discount = amount > 100 ? amount * 0.1 : 0;
            double total = amount - discount;

            return new OrderResult(orderId, total, discount, "processed");
        }
    }
    ```

    See the [Java SDK](https://github.com/conductor-oss/java-sdk) for full setup with Spring Boot or standalone runner.

=== "JavaScript"

    ```javascript
    const { ConductorWorker } = require("@conductor-oss/conductor-client");

    class OrderResult {
      constructor(orderId, total, discount, status) {
        this.orderId = orderId;
        this.total = total;
        this.discount = discount;
        this.status = status;
      }
    }

    const worker = new ConductorWorker({
      url: "http://localhost:8080/api",
    });

    worker.register("process_order", async (task) => {
      const { orderId, amount = 0 } = task.inputData;

      const discount = amount > 100 ? amount * 0.1 : 0;
      const total = amount - discount;

      return new OrderResult(orderId, total, discount, "processed");
    });

    worker.start();
    ```

    ```bash
    npm install @conductor-oss/conductor-client
    node worker.js
    ```

=== "Go"

    Define input and output as structs. The SDK deserializes task input into the input struct and serializes the output struct back:

    ```go
    package main

    type OrderInput struct {
        OrderId string  `json:"orderId"`
        Amount  float64 `json:"amount"`
    }

    type OrderResult struct {
        OrderId  string  `json:"orderId"`
        Total    float64 `json:"total"`
        Discount float64 `json:"discount"`
        Status   string  `json:"status"`
    }

    func ProcessOrder(input *OrderInput) (*OrderResult, error) {
        discount := 0.0
        if input.Amount > 100 {
            discount = input.Amount * 0.1
        }
        total := input.Amount - discount

        return &OrderResult{
            OrderId:  input.OrderId,
            Total:    total,
            Discount: discount,
            Status:   "processed",
        }, nil
    }
    ```

    See the [Go SDK](https://github.com/conductor-oss/go-sdk) for full setup and worker registration.

=== "C#"

    Implement `IWorkflowTask` with typed input/output models:

    ```csharp
    using Conductor.Client.Models;
    using Conductor.Client.Worker;

    public class OrderInput
    {
        public string OrderId { get; set; }
        public double Amount { get; set; }
    }

    public class OrderResult
    {
        public string OrderId { get; set; }
        public double Total { get; set; }
        public double Discount { get; set; }
        public string Status { get; set; }
    }

    public class OrderWorker : IWorkflowTask
    {
        public string TaskType => "process_order";
        public int? Priority => null;

        public async Task<TaskResult> Execute(
            Conductor.Client.Models.Task task,
            CancellationToken token)
        {
            var orderId = task.InputData["orderId"].ToString();
            var amount = Convert.ToDouble(task.InputData["amount"]);

            var discount = amount > 100 ? amount * 0.1 : 0;
            var total = amount - discount;

            var result = new OrderResult
            {
                OrderId = orderId,
                Total = total,
                Discount = discount,
                Status = "processed"
            };

            return task.Completed(result);
        }
    }
    ```

    See the [C# SDK](https://github.com/conductor-oss/csharp-sdk) for full setup and DI registration.


## Worker configuration

Workers are configured through the task definition on the Conductor server. Key settings:

| Parameter | Description |
| :--- | :--- |
| `retryCount` | Number of times Conductor retries a failed task. |
| `retryDelaySeconds` | Delay between retries. |
| `responseTimeoutSeconds` | Max time for a worker to respond after polling. |
| `timeoutSeconds` | Overall SLA for task completion. |
| `pollTimeoutSeconds` | Max time for a worker to poll before timeout. |
| `rateLimitPerFrequency` | Max task executions per frequency window. |
| `concurrentExecLimit` | Max concurrent executions across all workers. |

See [Task Definitions](../../documentation/configuration/taskdef.md) for the full reference.


## Scaling workers

Workers can be scaled independently of the Conductor server:

- **Horizontal scaling** — Run multiple instances of the same worker. Conductor distributes tasks across all polling workers automatically.
- **Rate limiting** — Use `rateLimitPerFrequency` to control throughput per task type.
- **Concurrency limits** — Use `concurrentExecLimit` to cap parallel executions.
- **Domain isolation** — Use [task domains](../../documentation/api/taskdomains.md) to route tasks to specific worker groups.

See [Scaling Workers](../how-tos/Workers/scaling-workers.md) for detailed guidance.
