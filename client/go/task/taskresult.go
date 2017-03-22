package task

import (
	"encoding/json"
)

type TaskResultStatus string

type TaskResult struct {
	Status TaskResultStatus				`json:"status"`
	WorkflowInstanceId string			`json:"workflowInstanceId"`
	TaskId string						`json:"taskId"`
	ReasonForIncompletion string		`json:"reasonForIncompletion"`
	CallbackAfterSeconds int64			`json:"callbackAfterSeconds"`
	WorkerId string						`json:"workerId"`
	OutputData map[string]interface{}	`json:"outputData"`
}

// "Constructor" to initialze non zero value defaults
func NewEmptyTaskResult() *TaskResult {
	taskResult := new(TaskResult)
	taskResult.OutputData = make(map[string]interface{})
	return taskResult
}

func NewTaskResult(t *Task) *TaskResult {
	taskResult := new(TaskResult)
	taskResult.CallbackAfterSeconds = t.CallbackAfterSeconds
	taskResult.WorkflowInstanceId = t.WorkflowInstanceId
	taskResult.TaskId = t.TaskId
	taskResult.ReasonForIncompletion = t.ReasonForIncompletion
	taskResult.Status = TaskResultStatus(t.Status)
	taskResult.WorkerId = t.WorkerId
	taskResult.OutputData = t.OutputData
	return taskResult
}

func (t *TaskResult) ToJSONString() (string, error) {
	var jsonString string
	b, err := json.Marshal(t)
	if err == nil {
		jsonString = string(b)
	}
	return jsonString, err
}

func ParseTaskResult(inputJSON string) (*TaskResult, error) {
	t := NewEmptyTaskResult()
	err := json.Unmarshal([]byte(inputJSON), t)
	return t, err
}