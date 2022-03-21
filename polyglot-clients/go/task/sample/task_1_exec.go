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
package sample

import (
	"github.com/netflix/conductor/client/go/task"
	log "github.com/sirupsen/logrus"
)

// Implementation for "task_1"
func Task_1_Execution_Function(t *task.Task) (taskResult *task.TaskResult, err error) {
	log.Debug("Executing Task_1_Execution_Function for", t.TaskType)

	//Do some logic
	taskResult = task.NewTaskResult(t)

	output := map[string]interface{}{"task": "task_1", "key2": "value2", "key3": 3, "key4": false}
	taskResult.OutputData = output
	taskResult.Logs = append(taskResult.Logs, task.LogMessage{Log: "Hello World"})
	taskResult.Status = "COMPLETED"
	err = nil

	return taskResult, err
}
