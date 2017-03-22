package task

import (
	"fmt"
)

// Implementation for "task_2"
func Task_2_Execution_Function(t *Task) (resultStatus TaskStatus, outputData string, err error) {
	fmt.Println("Executing Task_2_Execution_Function for", t.TaskType)

	//Do some logic
	resultStatus = COMPLETED
	outputData = `{"key1":"value1", "key2":"value2", "key3":3, "key4":false}`
	err = nil

	return resultStatus, outputData, err
}