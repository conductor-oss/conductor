# Workflows 
We will talk about two distinct topics, *defining* a workflow and *executing* a workflow.

### Workflow Definition
The Workflow Definition is the Conductor primitive that encompasses the flow of your business logic. It contains all the information necessary to describe the behavior of a workflow.

A Workflow Definition contains a collection of **Task Configurations**. This is the blueprint which specifies the order of execution of
tasks within a workflow. This blueprint also specifies how data/state is passed from one task to another (using task input/output parameters).

Additionally, the Workflow Definition contains metadata regulating the runtime behavior workflow, such what input and output parameters are expected for the entire workflow, and the workflow's the timeout and retry settings.

### Workflow Execution
If Workflow Definitions are like OOP classes, then Workflows Executions are like object instances. Each time a Workflow Definition is invoked with a given input, a new *Workflow Execution* with a unique ID is created. Definitions to Executions have a 1:N relationship.
