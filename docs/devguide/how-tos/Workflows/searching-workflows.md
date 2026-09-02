---
description: "Searching Workflows — find Conductor workflow executions by name, status, correlation ID, or time range from the CLI, API, or UI."
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

Go to **[Executions > Workflow](http://localhost:8080/executions)** in the Conductor UI. Fill in one or more filters and select **Search**. Results can be sorted by column, and **Show as code** displays the equivalent `GET /api/workflow/search` call for the current filters.

### Filters

| Filter | Description |
|---|---|
| Workflow name | One or more workflow definition names. |
| Workflow id | A specific workflow execution ID. |
| Correlation id | One or more correlation IDs. Press Enter after each value. |
| Idempotency key | One or more idempotency keys. Press Enter after each value. |
| Status | One or more of `RUNNING`, `COMPLETED`, `FAILED`, `TIMED_OUT`, `TERMINATED`, `PAUSED`. |
| Start / End | Only executions that started within the selected time range. |
| Free text search | Full-text query over indexed workflow data such as input and output values. Requires indexing to be enabled on the server. |

### SQL format

Turn on **SQL format** to replace the filter form with a query box that accepts the same SQL-like expressions as the `query` parameter of the search API, for example `workflowType = 'order_processing' AND status = 'FAILED'`. See [Query syntax](../../../documentation/api/workflow.md#query-syntax).

### Searching by task

The open-source UI searches workflow executions only. To find workflows by the tasks they contain, or to search task executions directly, use the API:

* `GET /api/workflow/search-by-tasks` — workflows filtered by task attributes. See [Search by Tasks](../../../documentation/api/workflow.md#search-by-tasks).
* `GET /api/tasks/search` — task executions. See [Search Tasks](../../../documentation/api/task.md#search-tasks).

## Limitations and next step

Free-text and task searches depend on the configured index backend and its indexing latency. Search results identify candidates; always inspect the execution before retrying, restarting, or terminating it. Continue with [View executions](viewing-workflow-executions.md) or [Debug and recover](debugging-workflows.md).
