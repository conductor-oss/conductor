---
description: "Searching Workflows — find Conductor workflow executions by name, status, time range, or task parameters in the UI."
---
# Search executions

Search when you know attributes such as workflow name, status, correlation ID, or time range but not the workflow ID.

## Search with the CLI

```bash
conductor workflow search -w order_processing -s FAILED -c 20
conductor workflow search -s COMPLETED \
  --start-time-after "2026-07-01" --start-time-before "2026-07-31"
```

| CLI option | Filters or controls | Example |
|---|---|---|
| `-w`, `--workflow` | Workflow name | `--workflow order_processing` |
| `-s`, `--status` | Execution status | `--status FAILED` |
| `-c`, `--count` | Number of executions returned (maximum 1000) | `--count 20` |
| `--start-time-after` | Executions started after a timestamp | `--start-time-after "2026-07-01"` |
| `--start-time-before` | Executions started before a timestamp | `--start-time-before "2026-07-31"` |
| `--json` | JSON output instead of the table view | `--json` |
| `--csv` | CSV output instead of the table view | `--csv` |

Results should include `workflowId`, name, status, and start time. Use the returned ID with `conductor workflow get-execution <workflow-id> -c` before taking a recovery action.

For structured/free-text or task-based searches beyond CLI flags, use `GET /api/workflow/search` or `GET /api/workflow/search-by-tasks`; the [Workflow API](../../../documentation/api/workflow.md#search-workflows) owns the query syntax and pagination contract.

### REST query parameters

`GET /api/workflow/search` accepts the following query parameters:

| Parameter | Meaning | Default |
|---|---|---|
| `start` | Page offset | `0` |
| `size` | Number of results | `100` |
| `sort` | Sort order as `<field>:ASC` or `<field>:DESC` | None |
| `freeText` | Full-text search query | `*` |
| `query` | SQL-like filter expression | None |
| `classifier` | Filter or group agent workflow executions by classifier | None |
| `topLevelOnly` | Limit results to top-level workflow executions | `false` |

## Search with the UI

The UI has two modes:

* **Workflows** tab — Search using workflow parameters.
* **Tasks** tab — Search workflows by tasks.

**To search workflow executions:**

1. Go to **[Executions](http://localhost:8080/executions)** in the Conductor UI.
2. Configure the [search parameters](#search-parameters).
3. Select **Search**.

Once the search results are displayed, you can sort the results by different column values and select additional columns to display.


## Search parameters

Here are the search parameters for each search mode.

### Search by workflows
The following fields are available for searching workflows in the **Workflows** tab.

| Search Field Name | Description                                                                                             |
|-------------------|---------------------------------------------------------------------------------------------------------|
| Workflow Name     | Filters workflow executions by its name.                                   |
| Workflow ID       | Filters to a specific workflow execution by its execution ID.                                               |
| Status            | Filters workflow executions by its status (RUNNING, COMPLETED, FAILED, TIMED_OUT, TERMINATED, PAUSED).      |
| Start Time - From | Filters workflow executions that started on or after the specified time.                          |
| Start Time - To   | Filters workflow executions that started on or before the specified time.                         |
| Lookback (days)   | Filters workflow executions that ran in the last given number of days.                            |
| Lucene-syntax Query (Double-quote strings for Free Text)  | (If indexing is enabled) Filters workflow executions by querying workflow input and output values. |


### Search workflows by tasks

The following fields are available for searching workflows by its tasks in the **Tasks** tab.

| Search Field Name  | Description                                                                                                  |
|--------------------|--------------------------------------------------------------------------------------------------------------|
| Task Name  | Filters workflow executions by its task name.                                        |
| Task ID    | Filters to a specific workflow execution that contains this task execution ID.                                |
| Task Status | Filters workflow executions by its task status (IN_PROGRESS, CANCELED, FAILED, FAILED_WITH_TERMINAL_ERROR, COMPLETED, COMPLETED_WITH_ERRORS, SCHEDULED, TIMED_OUT, SKIPPED).  |
| Task Type  | Filters workflow executions by its task type. |
| Workflow Name | Filters workflow executions by its workflow name.       |
| Update Time - From   | Filters workflow executions by tasks that started on or after the specified time.                         |
| Update Time - To   | Filters workflow executions by tasks that started on or before the specified time.                         |
| Lookback (days)   | Filters workflow executions by tasks that ran in the last given number of days.                          |
| Lucene-syntax Query (Double-quote strings for Free Text) | (If indexing is enabled) Filters workflow executions by querying task input and output values. |

## Limitations and next step

Free-text and task searches depend on the configured index backend and its indexing latency. Search results identify candidates; always inspect the execution before retrying, restarting, or terminating it. Continue with [View executions](viewing-workflow-executions.md) or [Debug and recover](debugging-workflows.md).
