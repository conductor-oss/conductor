---
sidebar_position: 1
---

# View Workflow Executions

In this article we will learn how to view workflow executions via the UI.

### Prerequisites

1. Conductor app and UI installed and running in an environment. If required we can look at the following options to get
   an environment up and running.

    1. [Build and Run Conductor Locally](/gettingstarted/local.html)
    2. [Running via Docker Compose](/gettingstarted/docker.html)

### Viewing a Workflow Execution

Refer to [Searching Workflows](/how-tos/Workflows/searching-workflows.html) to filter and find an execution you want to
view. Click on the workflow id hyperlink to open the Workflow Execution Details page.

The following tabs are available to view the details of the Workflow Execution

| Tab Name              | Description                                                                                                       |
|-----------------------|-------------------------------------------------------------------------------------------------------------------|
| Tasks                 | Shows a view with the sub tabs **Diagram**, **Task List** and **Timeline**                                        |
| Tasks > Diagram       | Visual view of the workflow and its tasks.                                                                        |
| Tasks > Task List     | Tabular view of the task executions under this workflow. If there were failures, we will be able to see that here |
| Tasks > Timeline      | Shows the time each of the tasks took for execution in a timeline view                                            |
| Summary               | Summary view of the workflow execution                                                                            |
| Workflow Input/Output | Shows the input and output payloads of the workflow. Enable copy mode to copy all or parts of the payload         |
| JSON                  | Full JSON payload of the workflow including all tasks, inputs and outputs. Useful for detailed debugging.         |

### Viewing a Workflow Task Detail

From both the **Tasks > Diagram** and **Tasks > Task List** views, we can click to see a task execution detail. This
opens a flyout panel from the side and contains the following tabs.

| Tab Name   | Description                                                                                                                              |
|------------|------------------------------------------------------------------------------------------------------------------------------------------|
| Summary    | Summary info of the task execution                                                                                                       |
| Input      | Task input payload - refer to this tab to see computed inputs passed into the task. Enable copy mode to copy all or parts of the payload |
| Output     | Shows the output payload produced by the executed task. Enable copy mode to copy all or parts of the payload                             |
| Log        | Any log messages logged by the task worked will show up here                                                                             |
| JSON       | Complete JSON payload for debugging issues                                                                                               |
| Definition | Task definition used when executing this task                                                                                            |

### Execution Path

An exciting feature of conductor is the ability to see the exact execution path of a workflow. The executed paths are
shown in green and is easy to follow like the example below. The alternative paths are greyed out for reference

![Conductor UI - Workflow Run](/img/tutorial/workflow_execution_view.png)

Errors will be visible on the UI in ref such as the example below

![Conductor UI - Failed Task](/img/tutorial/workflow_task_fail.png)
