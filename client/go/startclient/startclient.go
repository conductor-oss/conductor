package main

import (
	"conductor"
	"conductor/task/sample"
)

func main() {
	c := conductor.NewConductorWorker("http://localhost:8080/api", 1, 10000)

	c.Start("task_1", sample.Task_1_Execution_Function, false)
	c.Start("task_2", sample.Task_2_Execution_Function, true)
}
