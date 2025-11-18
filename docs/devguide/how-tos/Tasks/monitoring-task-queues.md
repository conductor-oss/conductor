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

## Using APIs

To see the size of the task queue via API:

```shell
curl '{{ server_host }}{{ api_prefix }}/tasks/queue/sizes?taskType=<TASK_NAME>' \
  -H 'accept: */*' 
```

To see the worker poll information of the task queue via API:

```shell
curl '{{ server_host }}{{ api_prefix }}/tasks/queue/polldata?taskType=<TASK_NAME>' \
  -H 'accept: */*'
```

!!! note
    Replace `<TASK_NAME>` with your task name
