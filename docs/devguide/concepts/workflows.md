# Workflows

A workflow is a sequence of tasks with a defined order and execution. Each workflow encapsulates a specific process, such as:

- Classifying documents
- Ordering from a self-checkout service
- Upgrading cloud infrastructure
- Transcoding videos
- Approving expenses

In Conductor, workflows can be defined and then executed. Learn more about the two distinct but related concepts, **workflow definition** and **workflow execution**, below.


## Workflow definition

The workflow definition describes the flow and behavior of your business logic. Think of it as a blueprint specifying how it should execute at runtime until it reaches a terminal state. The workflow definition includes:

- The workflow’s input/output keys.
- A collection of [task configurations](tasks.md#task-configuration) that specify the task conditions, sequence, and data flow until the workflow is completed.
- The workflow's runtime behavior, such as the timeout policy and compensation flow.

## Workflow execution

A workflow execution is the execution instance of a workflow definition. 

Whenever a workflow definition is invoked with a given input, a new workflow execution with a unique ID is created. The workflow is governed by a defined state (like RUNNING or COMPLETED), which makes it intuitive to track the workflow.
