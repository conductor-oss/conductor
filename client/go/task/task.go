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
	"encoding/json"
)

type WorkflowTaskType uint8
type TaskStatus string

const (
	SIMPLE WorkflowTaskType = iota
	DYNAMIC
	FORK_JOIN
	FORK_JOIN_DYNAMIC
	DECISION
	JOIN
	SUB_WORKFLOW
	EVENT
	WAIT
	USER_DEFINED
)

const (
	IN_PROGRESS TaskStatus = "IN_PROGRESS"
	CANCELED = "CANCELED"
	FAILED = "FAILED"
	COMPLETED = "COMPLETED"
	SCHEDULED = "SCHEDULED"
	TIMED_OUT = "TIMED_OUT"
	READY_FOR_RERUN = "READY_FOR_RERUN"
	SKIPPED = "SKIPPED"
)

type Task struct {
	TaskType string 					`json:"taskType"`
	Status TaskStatus					`json:"status"`
	InputData map[string]interface{}	`json:"inputData"`
	ReferenceTaskName string			`json:"referenceTaskName"`
	RetryCount int 						`json:"retryCount"`
	Seq int 							`json:"seq"`
	CorrelationId string				`json:"correlationId"`
	PollCount int 						`json:"pollCount"`
	TaskDefName string					`json:"taskDefName"`
	// Time when the task was scheduled
	ScheduledTime int64 				`json:"scheduledTime"`
	// Time when the task was first polled
	StartTime int64 					`json:"startTime"`
	// Time when the task completed executing
	EndTime int64						`json:"endTime"`
	// Time when the task was last updated
	UpdateTime int64 					`json:"updateTime"`
	StartDelayInSeconds int 			`json:"startDelayInSeconds"`
	RetriedTaskId string				`json:"retriedTaskId"`
	Retried bool						`json:"retried"`
	// Default = true
	CallbackFromWorker bool				`json:"callbackFromWorker"`
	// DynamicWorkflowTask
	ResponseTimeoutSeconds int 			`json:"responseTimeoutSeconds"`
	WorkflowInstanceId string			`json:"workflowInstanceId"`
	TaskId string						`json:"taskId"`
	ReasonForIncompletion string		`json:"reasonForIncompletion"`
	CallbackAfterSeconds int64			`json:"callbackAfterSeconds"`
	WorkerId string						`json:"workerId"`
	OutputData map[string]interface{}	`json:"outputData"`
}

// "Constructor" to initialze non zero value defaults
func NewTask() *Task {
	task := new(Task)
	task.CallbackFromWorker = true
	task.InputData = make(map[string]interface{})
	task.OutputData = make(map[string]interface{})
	return task
}

func (t *Task) ToJSONString() (string, error) {
	var jsonString string
	b, err := json.Marshal(t)
	if err == nil {
		jsonString = string(b)
	}
	return jsonString, err
}

func ParseTask(inputJSON string) (*Task, error) {
	t := NewTask()
	err := json.Unmarshal([]byte(inputJSON), t)
	return t, err
}
