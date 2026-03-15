---
description: Run your first Conductor workflow in 2 minutes. Call an API, parse the response with server-side JavaScript, and see durable execution in action — no workers needed.
---

# Run Your First Workflow

**See a workflow execute in 2 minutes. Build your own in 5.**

You need [Node.js](https://nodejs.org/) (v16+) installed. That's it.

## Phase 1: See it work

### Start Conductor

```bash
npm install -g @conductor-oss/conductor-cli
conductor server start
```

Wait for the server to start, then open the UI at [http://localhost:8080](http://localhost:8080).

### Define the workflow

Save `workflow.json` — a two-task workflow that calls an API and parses the response, all server-side:

```json
{
  "name": "hello_workflow",
  "version": 1,
  "tasks": [
    {
      "name": "fetch_data",
      "taskReferenceName": "fetch_ref",
      "type": "HTTP",
      "inputParameters": {
        "http_request": {
          "uri": "https://orkes-api-tester.orkesconductor.com/api",
          "method": "GET"
        }
      }
    },
    {
      "name": "parse_response",
      "taskReferenceName": "parse_ref",
      "type": "INLINE",
      "inputParameters": {
        "data": "${fetch_ref.output.response.body}",
        "evaluatorType": "graaljs",
        "expression": "(function() { var d = $.data; return { summary: 'Host ' + d.hostName + ' responded in ' + d.apiRandomDelay + ' with random value ' + d.randomInt, host: d.hostName, randomValue: d.randomInt }; })()"
      }
    }
  ],
  "outputParameters": {
    "summary": "${parse_ref.output.result.summary}",
    "apiResponse": "${fetch_ref.output.response.body}"
  },
  "schemaVersion": 2,
  "ownerEmail": "dev@example.com"
}
```

**What's happening here:**

- **`fetch_data`** — an [HTTP task](../devguide/configuration/workflowdef/systemtasks/http-task.md) that calls an external API. No worker needed.
- **`parse_response`** — an [Inline task](../devguide/configuration/workflowdef/systemtasks/inline-task.md) that runs JavaScript server-side to extract and summarize the API response.
- Both are **system tasks** — Conductor executes them directly. No external code to deploy.

### Register and run

**Register the workflow:**

```bash
conductor workflow create workflow.json
```

**Start the workflow:**

```bash
conductor workflow start -w hello_workflow --sync
```

The `--sync` flag waits for completion and prints the result directly. Expected output:

```json
{
  "summary": "Host orkes-api-sampler-... responded in 0 ms with random value 1141",
  "apiResponse": {
    "randomString": "gbgkaofnvesptvlmocpk",
    "randomInt": 1141,
    "hostName": "orkes-api-sampler-...",
    "apiRandomDelay": "0 ms",
    "sleepFor": "0 ms",
    "statusCode": "200",
    "queryParams": {}
  }
}
```

Open [http://localhost:8080](http://localhost:8080) to see the execution visually — the task timeline, inputs/outputs, and status of each step.

!!! success "What just happened"
    Conductor called an external API, passed the response to server-side JavaScript for parsing, tracked every step, and would have retried on failure — all without writing or deploying any worker code.


## Phase 2: Add a worker

Now write real code that Conductor orchestrates — with automatic retries.

### Update the workflow

Save `workflow-v2.json` — adds a worker task that processes the parsed data:

```json
{
  "name": "hello_workflow",
  "version": 2,
  "tasks": [
    {
      "name": "fetch_data",
      "taskReferenceName": "fetch_ref",
      "type": "HTTP",
      "inputParameters": {
        "http_request": {
          "uri": "https://orkes-api-tester.orkesconductor.com/api",
          "method": "GET"
        }
      }
    },
    {
      "name": "parse_response",
      "taskReferenceName": "parse_ref",
      "type": "INLINE",
      "inputParameters": {
        "data": "${fetch_ref.output.response.body}",
        "evaluatorType": "graaljs",
        "expression": "(function() { var d = $.data; return { summary: 'Host ' + d.hostName + ' responded in ' + d.apiRandomDelay + ' with random value ' + d.randomInt, host: d.hostName, randomValue: d.randomInt }; })()"
      }
    },
    {
      "name": "process_result",
      "taskReferenceName": "process_ref",
      "type": "SIMPLE",
      "inputParameters": {
        "summary": "${parse_ref.output.result.summary}",
        "randomValue": "${parse_ref.output.result.randomValue}"
      }
    }
  ],
  "outputParameters": {
    "finalResult": "${process_ref.output.result}"
  },
  "schemaVersion": 2,
  "ownerEmail": "dev@example.com"
}
```

**Register the updated workflow and task definition:**

```bash
conductor workflow create workflow-v2.json
```

```bash
curl -X POST http://localhost:8080/api/metadata/taskdefs \
  -H 'Content-Type: application/json' \
  -d '[{
    "name": "process_result",
    "retryCount": 2,
    "retryLogic": "FIXED",
    "retryDelaySeconds": 1,
    "responseTimeoutSeconds": 10,
    "ownerEmail": "dev@example.com"
  }]'
```

### Write the worker

Save `worker.py`:

```python
from conductor.client.automator.task_handler import TaskHandler
from conductor.client.configuration.configuration import Configuration
from conductor.client.worker.worker_task import worker_task


@worker_task(task_definition_name="process_result")
def process_result(task) -> dict:
    summary = task.input_data.get("summary", "")
    random_value = task.input_data.get("randomValue", 0)

    # Fail on first attempt to demonstrate retries
    if task.retry_count == 0:
        raise Exception(f"Simulated failure processing: {summary}")

    return {
        "result": summary.upper(),
        "doubled": random_value * 2,
        "attempt": task.retry_count + 1,
    }


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

**Install and run:**

```bash
pip install conductor-python
python worker.py
```

### Start the workflow and watch retries

In a separate terminal:

```bash
conductor workflow start -w hello_workflow --version 2 --sync
```

In the terminal running your worker, you'll see:

```
Simulated failure processing: Host orkes-api-sampler-... responded in 0 ms with random value 1141
...
# 1 second later, the retry succeeds
```

Expected output:

```json
{
  "finalResult": {
    "result": "HOST ORKES-API-SAMPLER-... RESPONDED IN 0 MS WITH RANDOM VALUE 1141",
    "doubled": 2282,
    "attempt": 2
  }
}
```

Open [http://localhost:8080](http://localhost:8080) to see the retry visually in the execution diagram.

!!! success "What just happened"
    Your worker failed, Conductor retried it after 1 second, and the retry succeeded. This is durable execution — Conductor manages retries so your code doesn't have to.


??? note "Workers in other languages"

    === "Java"

        ```java
        @WorkerTask("process_result")
        public Map<String, Object> processResult(Map<String, Object> input) {
            String summary = (String) input.get("summary");
            int randomValue = (int) input.get("randomValue");
            return Map.of(
                "result", summary.toUpperCase(),
                "doubled", randomValue * 2
            );
        }
        ```

        See the [Java SDK](https://github.com/conductor-oss/java-sdk) for full setup.

    === "JavaScript"

        ```javascript
        const { ConductorWorker } = require("@conductor-oss/conductor-client");

        const worker = new ConductorWorker({
          url: "http://localhost:8080/api",
        });

        worker.register("process_result", async (task) => {
          const { summary, randomValue } = task.inputData;
          return { result: summary.toUpperCase(), doubled: randomValue * 2 };
        });

        worker.start();
        ```

        See the [JavaScript SDK](https://github.com/conductor-oss/javascript-sdk) for full setup.

    === "Go"

        ```go
        func ProcessResult(task *model.Task) (interface{}, error) {
            summary := task.InputData["summary"].(string)
            randomValue := int(task.InputData["randomValue"].(float64))
            return map[string]interface{}{
                "result":  strings.ToUpper(summary),
                "doubled": randomValue * 2,
            }, nil
        }
        ```

        See the [Go SDK](https://github.com/conductor-oss/go-sdk) for full setup.

    === "C#"

        ```csharp
        [WorkerTask("process_result")]
        public static TaskResult ProcessResult(Task task)
        {
            var summary = task.InputData["summary"].ToString();
            var randomValue = (int)task.InputData["randomValue"];
            return task.Completed(new {
                result = summary.ToUpper(),
                doubled = randomValue * 2
            });
        }
        ```

        See the [C# SDK](https://github.com/conductor-oss/csharp-sdk) for full setup.


## Cleanup

```bash
conductor server stop
```


## Using Docker instead

If you prefer Docker over the CLI, you can run Conductor with:

```bash
docker run --name conductor -p 8080:8080 conductoross/conductor:latest
```

All the workflow commands above work the same — just replace the CLI commands with their cURL equivalents:

| CLI | cURL |
|-----|------|
| `conductor workflow create workflow.json` | `curl -X POST http://localhost:8080/api/metadata/workflow -H 'Content-Type: application/json' -d @workflow.json` |
| `conductor workflow start -w hello_workflow --sync` | `curl -s -X POST http://localhost:8080/api/workflow/hello_workflow -H 'Content-Type: application/json'` |
| `conductor server stop` | `docker rm -f conductor` |

For production deployment options, see [Running with Docker](../devguide/running/docker.md).


## Next steps

- **[System tasks](../devguide/configuration/workflowdef/systemtasks/index.md)** — HTTP, Wait, Event tasks without workers
- **[Operators](../devguide/configuration/workflowdef/operators/index.md)** — Fork/join, switch, loops, sub-workflows
- **[Error handling](../devguide/how-tos/Workflows/handling-errors.md)** — Saga pattern, compensation flows
- **[Client SDKs](../devguide/clientsdks/index.md)** — Java, Python, Go, C#, JavaScript, and more
