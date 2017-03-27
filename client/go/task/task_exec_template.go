// Copyright 2017 Netflix, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
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
