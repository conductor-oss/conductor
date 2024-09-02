# Unit Testing Framework for Workflows

The framework allows you to test the workflow definitions against a specific version of Conductor server.

The unit tests allow the following:
1. **Input/Output Wiring**: Ensure the tasks are wired up correctly.
2. **Parameter check**: Workflow behavior with missing mandatory parameters is expected (fail if required).
3. **Task Failure behavior**: Ensure the task definitions have the right number of retries etc.  
   For example, if the task is not idempotent, it does not get retried.
4. **Branch Testing**: Given a specific input, ensure the workflow executes a specific branch of the fork/decision.

The local test server is self-contained with no additional dependencies required and stores all the data
in memory.  Once the test completes, the server is terminated and all the data is wiped out.

## Unit Testing Frameworks
The unit testing framework is agnostic to the framework you use for testing and can be easily integrated into 
JUnit, Spock and other testing frameworks being used.

## Setting Up Local Server for Testing​

```java
//Setup method  code - should be called once per the test lifecycle
//e.g. @BeforeClass in JUnit

//Download the published conductor server version 3.5.2 
//Start the local server at port 8096
testRunner = new WorkflowTestRunner(8096, "3.5.2");

//Scan the packages for task workers
testRunner.init("com.netflix.conductor.testing.workflows");

//Get the executor instance used for  loading workflows 
executor = testRunner.getWorkflowExecutor();
```

Clean up method:
```java
//Clean up method code -- place in a clean up method e.g. @AfterClass in Junit

//Shutdown local workers and servers and clean up any local resources in use.
testRunner.shutdown();
```

Loading workflows from JSON files for testing:
```java
executor.loadTaskDefs("/tasks.json");
executor.loadWorkflowDefs("/simple_workflow.json");
```

## Sample test code that starts a workflow and verifies its execution

```java
GetInsuranceQuote getQuote = new GetInsuranceQuote();
getQuote.setName("personA");
getQuote.setAmount(1000000.0);
getQuote.setZipCode("10121");

// Start the workflow and wait for it to complete
CompletableFuture<Workflow> workflowFuture = executor.executeWorkflow("InsuranceQuoteWorkflow", 1, getQuote);

//Wait for the workflow execution to complete
Workflow workflow = workflowFuture.get();

//Assertions
assertNotNull(workflow);
assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
assertNotNull(workflow.getOutput());
assertNotNull(workflow.getTasks());
assertFalse(workflow.getTasks().isEmpty());
assertTrue(workflow.getTasks().stream().anyMatch(task -> task.getTaskDefName().equals("task_6")));
```



