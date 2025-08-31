# Searching Workflows

The Conductor UI provides a convenient interface for searching workflow executions. There are two modes of searching:

* **Workflows** tab — Search using workflow parameters.
* **Tasks** tab — Search workflows by tasks.

**To search workflow executions:**

1. Go to **[Executions](http://localhost:8127/executions)** in the Conductor UI.
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

