# Monitoring Task Queues

Conductor offers an API and UI interface to monitor the task queues. This is useful to see details of the number of
workers polling and monitoring the queue backlog.

## Using the UI

```
<your UI server URL>/taskQueue
```

Access this screen via - Home > Task Queues

On this screen you can select and view the details of the task queue. The following information is shown:

1. Queue Size - The number of tasks waiting to be executed
2. Workers - The count and list of works and their instance reference who are polling for work for this task

## Using the CLI

To list tasks and view queue information:

```bash
conductor task list
```

To get details for a specific task:

```bash
conductor task get <TASK_NAME>
```

!!! note
    Replace `<TASK_NAME>` with your task name.

## Using APIs

??? note "Queue size using cURL"
    ```shell
    curl '{{ server_host }}{{ api_prefix }}/tasks/queue/sizes?taskType=<TASK_NAME>' \
      -H 'accept: */*'
    ```

??? note "Worker poll data using cURL"
    ```shell
    curl '{{ server_host }}{{ api_prefix }}/tasks/queue/polldata?taskType=<TASK_NAME>' \
      -H 'accept: */*'
    ```

!!! note
    Replace `<TASK_NAME>` with your task name
