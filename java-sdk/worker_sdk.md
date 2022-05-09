# Worker SDK
Worker SDK makes it easy to write conductor workers which are strongly typed with specific inputs and outputs.

Annotations for the worker methods:

* `@WorkerTask` When annotated converts a method to a conductor worker
* `@InputParam` name of the input parameter to bind to from the task's input
* `@OutputParam` name of the output key of the task's output.

Please note, inputs and outputs to a task in Conductor are JSON documents.


**Examples**

Create a worker named `task1` that gets Task as input and produces TaskResult as output.
```java
@WorkerTask("task1")
    public TaskResult task1(Task task) {
        task.setStatus(Task.Status.COMPLETED);
        return new TaskResult(task);
    }
```

Create a worker named `task2` that takes `name` as a String input and produces a
```java
@WorkerTask("task2")
public @OutputParam("greetings") String task2(@InputParam("name") String name) {
    return "Hello, " + name;
}
```
Example Task Input/Output

Input:
```json
{
   "name": "conductor"
}
```

Output:
```json
{
   "greetings": "Hello, conductor"
}
```
A worker that takes complex java type as input and produces complex output:
```java
@WorkerTask("get_insurance_quote")
 public InsuranceQuote getInsuranceQuote(GetInsuranceQuote quoteInput) {
     InsuranceQuote quote = new InsuranceQuote();
     //Implementation
     return quote;
 }
```

Example Task Input/Output

Input:
```json
{
   "name": "personA",
   "zipCode": "10121",
   "amount": 1000000
}
```

Output:
```json
{
   "name": "personA",
   "quotedPremium": 123.50,
   "quotedAmount": 1000000
}
```

## Managing Task Workers
Annotated Workers are managed by [WorkflowExecutor](https://github.com/netflix/conductor/java-sdk/src/main/java/com/netflix/conductor/sdk/workflow/executor/WorkflowExecutor.java)

### Start Workers
```java
WorkflowExecutor executor = new WorkflowExecutor("http://server/api/");
//List of packages  (comma separated) to scan for annotated workers.  
// Please note,the worker method MUST be public and the class in which they are defined
//MUST have a no-args constructor        
executor.initWorkers("com.company.package1,com.company.package2");
```

### Stop Workers
Code fragment to stop workers at the shutdown of the application
```java
executor.shutdown();
```

### Unit Testing Workers
Workers implemented with the annotations are regular Java methods can be united tested with any testing framework.

#### Mock workers for workflow testing
Create a mock worker in a different package (e.g. test) and scan for these packages when loading up the workers for integration testing.

See [Unit Testing Framework](testing_framework.md) for more details on testing.

## Best Practices
In a typical production environment, you will have multiple workers across different machines/VMs/pods polling for the same task.
As with all the Conductor workers, the following best practices applies:

1. Workers should be stateless and should not maintain any state on the process they are running
2. Ideally workers should be idempotent
3. Worker should follow Single Responsibility Principle and do exactly one thing they are responsible for
4. Worker should not embed any workflow logic - ie scheduling another worker, sending a message etc.  Conductor has features to do this making it possible to decouple your workflow logic from worker implementation.







