package main

import (
	"conductor"
	"conductor/task"
)

func main() {
	c := conductor.NewConductorWorker("http://localhost:8080/api", 1, 10000)

	c.Start("task_1", task.Task_1_Execution_Function, false)
	c.Start("task_2", task.Task_2_Execution_Function, true)
}
