// Example for task implementation
package task

import (
	"log"
)

/* Format for functions must be:
	func Name (t *Task) (taskResult TaskResult, err error)
 		- taskResult (TaskResult) should return struct with populated fields
 		- err (error) returns error if any
*/

func ExampleTaskExecutionFunction(t *Task) (taskResult *TaskResult, err error) {
	log.Println("Executing Example Function for", t.TaskType)
	log.Println(t)

	//Do some logic
	taskResult = NewTaskResult(t)

	output := map[string]interface{}{"task":"example", "key2":"value2", "key3":3, "key4":false}
	taskResult.OutputData = output
	taskResult.Status = "COMPLETED"
	err = nil

	return taskResult, err
}