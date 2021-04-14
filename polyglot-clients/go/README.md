# Go client for Conductor
Go client for Conductor provides two sets of functions:

1. Workflow Management APIs (start, terminate, get workflow status etc.)
2. Worker execution framework

## Prerequisites
Go must be installed and GOPATH env variable set.

## Install

```shell
go get github.com/netflix/conductor/client/go
```
This will create a Go project under $GOPATH/src and download any dependencies.

## Run

```shell
go run $GOPATH/src/netflix-conductor/client/go/startclient/startclient.go
```

## Using Workflow Management API
Go struct ```ConductorHttpClient``` provides client API calls to the conductor server to start and manage workflows and tasks.

### Example
```go
package main

import (
    conductor "github.com/netflix/conductor/client/go"
)

func main() {
    conductorClient := conductor.NewConductorHttpClient("http://localhost:8080")
    
    // Example API that will print out workflow definition meta
    conductorClient.GetAllWorkflowDefs()
}

```

## Task Worker Execution
Task Worker execution APIs facilitates execution of a task worker using go.  The API provides necessary tools to poll for tasks at a specified interval and executing the go worker in a separate goroutine.

### Example
The following go code demonstrates workers for tasks "task_1" and "task_2".

```go
package task

import (
    "fmt"
)

// Implementation for "task_1"
func Task_1_Execution_Function(t *task.Task) (taskResult *task.TaskResult, err error) {
    log.Println("Executing Task_1_Execution_Function for", t.TaskType)

    //Do some logic
    taskResult = task.NewTaskResult(t)
    
    output := map[string]interface{}{"task":"task_1", "key2":"value2", "key3":3, "key4":false}
    taskResult.OutputData = output
    taskResult.Status = "COMPLETED"
    err = nil

    return taskResult, err
}

// Implementation for "task_2"
func Task_2_Execution_Function(t *task.Task) (taskResult *task.TaskResult, err error) {
    log.Println("Executing Task_2_Execution_Function for", t.TaskType)

    //Do some logic
    taskResult = task.NewTaskResult(t)
    
    output := map[string]interface{}{"task":"task_2", "key2":"value2", "key3":3, "key4":false}
    taskResult.OutputData = output
    taskResult.Status = "COMPLETED"
    err = nil

    return taskResult, err
}

```


Then main application to utilize these workers

```go
package main

import (
    "github.com/netflix/conductor/client/go"
    "github.com/netflix/conductor/client/go/task/sample"
)

func main() {
    c := conductor.NewConductorWorker("http://localhost:8080", 1, 10000)

    c.Start("task_1", "", sample.Task_1_Execution_Function, false)
    c.Start("task_2", "mydomain", sample.Task_2_Execution_Function, true)
}

```

Note: For the example listed above the example task implementations are in conductor/task/sample package.  Real task implementations can be placed in conductor/task directory or new subdirectory.

