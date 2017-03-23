# Go client for Conductor
Go client for Conductor provides two sets of functions:

1. Workflow Management APIs (start, terminate, get workflow status etc.)
2. Worker execution framework

## Prerequisites
Go must be installed and GOPATH env variable set.  Directory $GOPATH/src/conductor must not be in use.

## Install

```shell
./install.sh
```
This will create a Go project under $GOPATH/src/conductor and download any dependencies.
It can then be ran:
```shell
go run $GOPATH/src/conductor/startclient/startclient.go
```

## Install and Run

```shell
./install_and_run.sh
```
This will create a Go project under $GOPATH/src/conductor and download any dependencies.  In addition, it will run the go application listed under startclient/startclient.go

## Uninstall
WARNING: This will simply remove the $GOPATH/src/conductor directory where it has installed so if other work is there, it will be deleted.  Use with caution.

```shell
./uninstall.sh
```

## Using Workflow Management API
Go struct ```ConductorHttpClient``` provides client API calls to the conductor server to start and manage workflows and tasks.

### Example
```go
package main

import (
    "conductor"
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
    "conductor"
    "conductor/task/sample"
)

func main() {
    c := conductor.NewConductorWorker("http://localhost:8080", 1, 10000)

    c.Start("task_1", sample.Task_1_Execution_Function, false)
    c.Start("task_2", sample.Task_2_Execution_Function, true)
}

```

Note: For the example listed above the example task implementations are in conductor/task/sample package.  Real task implementations can be placed in conductor/task directory or new subdirectory.

