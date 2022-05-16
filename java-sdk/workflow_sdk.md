# Workflow SDK
Workflow SDK provides fluent API to create workflows with strongly typed interfaces.

## APIs
### ConductorWorkflow
[ConductorWorkflow](https://github.com/Netflix/conductor/blob/main/java-sdk/src/main/java/com/netflix/conductor/sdk/workflow/def/ConductorWorkflow.java) is the SDK representation of a Conductor workflow.

#### Create a `ConductorWorkflow` instance
```java
ConductorWorkflow<GetInsuranceQuote> conductorWorkflow = new WorkflowBuilder<GetInsuranceQuote>(executor)
    .name("sdk_workflow_example")
    .version(1)
    .ownerEmail("hello@example.com")
    .description("Example Workflow")
    .timeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF, 100)
    .add(new SimpleTask("calculate_insurance_premium", "calculate_insurance_premium"))
    .add(new SimpleTask("send_email", "send_email"))
    .build();
```
### Working with Simple Worker Tasks
Use [SimpleTask](https://github.com/Netflix/conductor/blob/main/java-sdk/src/main/java/com/netflix/conductor/sdk/workflow/def/tasks/SimpleTask.java) to add simple task to a workflow.

Example:
```java
...
builder.add(new SimpleTask("send_email", "send_email"))
...
```
### Wiring inputs the task
use `input` methods to configure the inputs the task.

See https://netflix.github.io/conductor/how-tos/Tasks/task-inputs/ for details on Task Inputs/Outputs

Example
```java
builder.add(
        new SimpleTask("send_email", "send_email")
                .input("email", "${workflow.input.email}")
                .input("subject", "Your insurance quote for the amount ${generate_quote.output.amount}")
);
```

### Working with operators
Each of the operator - 

[ForkJoin](https://github.com/Netflix/conductor/blob/main/java-sdk/src/main/java/com/netflix/conductor/sdk/workflow/def/tasks/ForkJoin.java), 
[Wait](https://github.com/Netflix/conductor/blob/main/java-sdk/src/main/java/com/netflix/conductor/sdk/workflow/def/tasks/Wait.java), 
[Switch](https://github.com/Netflix/conductor/blob/main/java-sdk/src/main/java/com/netflix/conductor/sdk/workflow/def/tasks/Switch.java),
[DynamicFork](https://github.com/Netflix/conductor/blob/main/java-sdk/src/main/java/com/netflix/conductor/sdk/workflow/def/tasks/DynamicFork.java),
[DoWhile](https://github.com/Netflix/conductor/blob/main/java-sdk/src/main/java/com/netflix/conductor/sdk/workflow/def/tasks/DoWhile.java),
[Join](https://github.com/Netflix/conductor/blob/main/java-sdk/src/main/java/com/netflix/conductor/sdk/workflow/def/tasks/Join.java),
[Dynamic](https://github.com/Netflix/conductor/blob/main/java-sdk/src/main/java/com/netflix/conductor/sdk/workflow/def/tasks/Dynamic.java),
[Terminate](https://github.com/Netflix/conductor/blob/main/java-sdk/src/main/java/com/netflix/conductor/sdk/workflow/def/tasks/Terminate.java),
[SubWorkflow](https://github.com/Netflix/conductor/blob/main/java-sdk/src/main/java/com/netflix/conductor/sdk/workflow/def/tasks/SubWorkflow.java),
[SetVariable](https://github.com/Netflix/conductor/blob/main/java-sdk/src/main/java/com/netflix/conductor/sdk/workflow/def/tasks/SetVariable.java),

have their own class that can be added to the workflow builder.


#### Register Workflow with Conductor Server
```java
//Returns true if the workflow is successfully created
//Reasons why this method will return false
//1. Network connectivity issue
//2. Workflow already exists with the specified name and version 
//3. There are missing task definitions
boolean registered = workflow.registerWorkflow();
```
#### Overwrite existing workflow definition
```java
boolean registered = workflow.registerWorkflow(true);
```

#### Overwrite existing workflow definition & register any missing task definitions
```java
boolean registered = workflow.registerWorkflow(true, true);
```

#### Create `ConductorWorkflow` based on the definition registered on the server

```java
ConductorWorkflow<GetInsuranceQuote> conductorWorkflow = 
                        new ConductorWorkflow<GetInsuranceQuote>(executor)
                        .from("sdk_workflow_example", 1);
```

#### Start a workflow execution
Start the execution of the workflow based on the definition registered on the server.
Use register method to register a workflow on the server before executing.

```java

//Returns a completable future
CompletableFuture<Workflow> execution = conductorWorkflow.execute(input);

//Wait for the workflow to complete -- useful if workflow completes within a reasonable amount of time
Workflow workflowRun = execution.get();

//Get the workflowId
String workflowId = workflowRun.getWorkflowId();

//Get the status of workflow execution
WorkflowStatus status = workflowRun.getStatus();
```
See [Workflow](https://github.com/Netflix/conductor/blob/main/common/src/main/java/com/netflix/conductor/common/run/Workflow.java) for more details on Workflow object.

#### Start a dynamic workflow execution
Dynamic workflows are executed by specifying the workflow definition along with the execution and does not require registering the workflow on the server before executing.

##### Use cases for dynamic workflows
1. Each workflow run has a unique workflow definition 
2. Workflows are defined based on the user data and cannot be modeled ahead of time statically 

```java
//1. Use WorkflowBuilder to create ConductorWorkflow
//2. Execute using the definition created by SDK
CompletableFuture<Workflow> execution = conductorWorkflow.executeDynamic(input);

```






