// Example for task implementation
package task

import (
	"log"
)

/* Format for functions must be:
	func Name (t *Task) (resultStatus TaskStatus, outputData string, err error)
 		- resultStatus (string) should return ("COMPETED"| "FAILED" | "TIMED_OUT")
 		- outputData (string) should return JSON Formatted string
 		- err (error) returns error if any
*/

func ExampleTaskExecutionFunction(t *Task) (resultStatus TaskStatus, outputData string, err error) {
	log.Println("Executing Example Function for", t.TaskType)
	log.Println(t)

	//Do some logic
	resultStatus = COMPLETED
	outputData = `{"key1":"value1", "key2":"value2", "key3":3, "key4":false}`
	err = nil

	return resultStatus, outputData, err
}