package sample

import (
	"log"
	"conductor/task"
)

// Implementation for "task_1"
func Task_1_Execution_Function(t *task.Task) (resultStatus task.TaskStatus, outputData string, err error) {
	log.Println("Executing Task_1_Execution_Function for", t.TaskType)

	//Do some logic
	resultStatus = task.COMPLETED
	outputData = `{"key1":"value1", "key2":"value2", "key3":3, "key4":false}`
	err = nil

	return resultStatus, outputData, err
}