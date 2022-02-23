---
sidebar_position: 1
---

# Build a Go Task Worker

## Install
```shell 
go get github.com/netflix/conductor/client/go
```
This will create a Go project under $GOPATH/src and download any dependencies.

## Implementing a Task a Worker
`task`package provies the types used to implement the worker.  Here is a reference worker implementation:

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
```

## Worker Polling
Here is an example that shows how to start polling for tasks after defining the tasks.

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
### `NewConductorWoker` parameters
1. baseUrl: Server address.  
2. threadCount: No. of threads.  Number of threads should be at-least same as the number of workers
3. pollingInterval: Time in millisecond between subsequent polls
