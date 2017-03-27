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
package conductor

import (
    "github.com/cquon/httpclient"
    "strconv"
    "log"
)

type ConductorHttpClient struct {
	httpClient *httpclient.HttpClient
}

func NewConductorHttpClient(baseUrl string) *ConductorHttpClient {
	conductorClient := new(ConductorHttpClient)
	headers := map[string]string{"Content-Type": "application/json", "Accept": "application/json"}
	httpClient := httpclient.NewHttpClient(baseUrl, headers, true)
	conductorClient.httpClient = httpClient
	return conductorClient
}


/**********************/
/* Metadata Functions */
/**********************/

func (c *ConductorHttpClient) GetWorkflowDef(workflowName string, version int) (string, error) {
	url := c.httpClient.MakeUrl("/metadata/workflow/{workflowName}", "{workflowName}", workflowName)
	versionString := "1"

	// Set default version as 1
	if version > 0 {
		versionString = strconv.Itoa(version)
	}
	params := map[string]string{"version":versionString}
	outputString, err := c.httpClient.Get(url, params, nil)
	if err != nil {
		log.Println("Error while trying to Get Workflow Definition", err)
		return "", nil
	} else {
		return outputString, nil
	}
}

func (c *ConductorHttpClient) CreateWorkflowDef(workflowDefBody string) (string, error) {
	url := c.httpClient.MakeUrl("/metadata/workflow")
	outputString, err := c.httpClient.Post(url, nil, nil, workflowDefBody)
	if err != nil {
		log.Println("Error while trying to Create Workflow Definition", err)
		return "", nil
	} else {
		return outputString, nil
	}
}

func (c *ConductorHttpClient) UpdateWorkflowDefs(workflowDefsBody string) (string, error) {
	url := c.httpClient.MakeUrl("/metadata/workflow")
	outputString, err := c.httpClient.Put(url, nil, nil, workflowDefsBody)
	if err != nil {
		log.Println("Error while trying to Update Workflow Definitions", err)
		return "", nil
	} else {
		return outputString, nil
	}
}

func (c *ConductorHttpClient) GetAllWorkflowDefs() (string, error) {
	url := c.httpClient.MakeUrl("/metadata/workflow")
	outputString, err := c.httpClient.Get(url, nil, nil)
	if err != nil {
		log.Println("Error while trying to Get All Workflow Definitions", err)
		return "", nil
	} else {
		return outputString, nil
	}
}

func (c *ConductorHttpClient) GetTaskDef(taskDefName string) (string, error) {
	url := c.httpClient.MakeUrl("/metadata/taskdefs/{taskDefName}", "{taskDefName}", taskDefName)
	outputString, err := c.httpClient.Get(url, nil, nil)
	if err != nil {
		log.Println("Error while trying to Get Task Definition", err)
		return "", nil
	} else {
		return outputString, nil
	}
}

func (c *ConductorHttpClient) RegisterTaskDefs(taskDefsMeta string) (string, error) {
	url := c.httpClient.MakeUrl("/metadata/taskdefs")
	outputString, err := c.httpClient.Post(url, nil, nil, taskDefsMeta)
	if err != nil {
		log.Println("Error while trying to Register Task Definitions", err)
		return "", nil
	} else {
		return outputString, nil
	}
}

func (c *ConductorHttpClient) UpdateTaskDef(taskDefMeta string) (string, error) {
	url := c.httpClient.MakeUrl("/metadata/taskdefs")
	outputString, err := c.httpClient.Put(url, nil, nil, taskDefMeta)
	if err != nil {
		log.Println("Error while trying to Update Task Definition", err)
		return "", nil
	} else {
		return outputString, nil
	}
}

func (c *ConductorHttpClient) UnRegisterTaskDef(taskDefName string) (string, error) {
	url := c.httpClient.MakeUrl("/metadata/taskdefs/{taskDefName}", "{taskDefName}", taskDefName)
	outputString, err := c.httpClient.Delete(url, nil, nil, "")
	if err != nil {
		log.Println("Error while trying to Unregister Task Definition", taskDefName, err)
		return "", nil
	} else {
		return outputString, nil
	}
}

func (c *ConductorHttpClient) GetAllTaskDefs() (string, error) {
	url := c.httpClient.MakeUrl("/metadata/taskdefs")
	outputString, err := c.httpClient.Get(url, nil, nil)
	if err != nil {
		log.Println("Error while trying to Get All Task Definitions", err)
		return "", nil
	} else {
		return outputString, nil
	}
}


/**********************/
/* Task Functions	  */
/**********************/

func (c *ConductorHttpClient) GetTask(taskId string) (string, error) {
	url := c.httpClient.MakeUrl("/tasks/{taskId}", "{taskId}", taskId)
	outputString, err := c.httpClient.Get(url, nil, nil)
	if err != nil {
		log.Println("Error while trying to Get Task", taskId, err)
		return "", nil
	} else {
		return outputString, nil
	}
}

func (c *ConductorHttpClient) UpdateTask(taskBody string) (string, error) {
	url := c.httpClient.MakeUrl("/tasks")
	outputString, err := c.httpClient.Post(url, nil, nil, taskBody)
	if err != nil {
		log.Println("Error while trying to Update Task", err)
		return "", nil
	} else {
		return outputString, nil
	}
}

func (c *ConductorHttpClient) PollForTask(taskType string, workerid string) (string, error) {
	url := c.httpClient.MakeUrl("/tasks/poll/{taskType}", "{taskType}", taskType)
	params := map[string]string{"workerid":workerid}
	outputString, err := c.httpClient.Get(url, params, nil)
	if err != nil {
		log.Println("Error while trying to Poll For Task taskType:", taskType, ",workerid:", workerid, err)
		return "", nil
	} else {
		return outputString, nil
	}
}

func (c *ConductorHttpClient) AckTask(taskType string, workerid string) (string, error) {
	url := c.httpClient.MakeUrl("/tasks/{taskType}/ack", "{taskType}", taskType)
	params := map[string]string{"workerid":workerid}
	headers := map[string]string{"Accept":"text/plain"}
	outputString, err := c.httpClient.Post(url, params, headers, "")
	if err != nil {
		log.Println("Error while trying to Ack Task taskType:", taskType, ",workerid:", workerid, err)
		return "", nil
	} else {
		return outputString, nil
	}
}

func (c *ConductorHttpClient) GetAllTasksInQueue() (string, error) {
	url := c.httpClient.MakeUrl("/tasks/queue/all")
	outputString, err := c.httpClient.Get(url, nil, nil)
	if err != nil {
		log.Println("Error while trying to Get All Tasks in Queue", err)
		return "", nil
	} else {
		return outputString, nil
	}
}

func (c *ConductorHttpClient) RemoveTaskFromQueue(taskType string, taskId string) (string, error) {
	url := c.httpClient.MakeUrl("/tasks/queue/{taskType}/{taskId}", "{taskType}", taskType, "{taskId}", taskId)
	outputString, err := c.httpClient.Delete(url, nil, nil, "")
	if err != nil {
		log.Println("Error while trying to Delete Task taskType:", taskType, ",taskId:", taskId, err)
		return "", nil
	} else {
		return outputString, nil
	}
}

func (c *ConductorHttpClient) GetTaskQueueSizes(taskNames string) (string, error) {
	url := c.httpClient.MakeUrl("/tasks/queue/sizes")
	outputString, err := c.httpClient.Post(url, nil, nil, taskNames)
	if err != nil {
		log.Println("Error while trying to Get Task Queue Sizes", err)
		return "", nil
	} else {
		return outputString, nil
	}
}


/**********************/
/* Workflow Functions */
/**********************/

func (c *ConductorHttpClient) GetWorkflow(workflowId string, includeTasks bool) (string, error) {
	url := c.httpClient.MakeUrl("/workflow/{workflowId}", "{workflowId}", workflowId)
	includeTasksString := "false"
	if includeTasks {
		includeTasksString = "true"
	}
	params := map[string]string{"includeTasks":includeTasksString}
	outputString, err := c.httpClient.Get(url, params, nil)
	if err != nil {
		log.Println("Error while trying to Get Workflow", workflowId, err)
		return "", nil
	} else {
		return outputString, nil
	}
}

func (c *ConductorHttpClient) GetRunningWorkflows(workflowName string, version int, startTime float64, endTime float64) (string, error) {
	url := c.httpClient.MakeUrl("/workflow/running/{workflowName}", "{workflowName}", workflowName)
	versionString := "1"
	// Set default version as 1
	if version > 0 {
		versionString = strconv.Itoa(version)
	}
	params := map[string]string{"version":versionString}
	if startTime != 0 {
		params["startTime"] = strconv.FormatFloat(startTime, 'f', -1, 64)
	}
	if endTime != 0 {
		params["endTime"] = strconv.FormatFloat(endTime, 'f', -1, 64)
	}

	outputString, err := c.httpClient.Get(url, params, nil)
	if err != nil {
		log.Println("Error while trying to Get Running Workflows", workflowName, err)
		return "", nil
	} else {
		return outputString, nil
	}
}

func (c *ConductorHttpClient) StartWorkflow(workflowName string, version int, correlationId string, inputJson string) (string, error) {
	url := c.httpClient.MakeUrl("/workflow/{workflowName}", "{workflowName}", workflowName)

	params := make(map[string]string)
	if version > 0 {
		params["version"] = strconv.Itoa(version)
	}

	if correlationId != "" {
		params["correlationId"] = correlationId
	}

	if inputJson == "" {
		inputJson = "{}"
	}

	headers := map[string]string{"Accept":"text/plain"}

	outputString, err := c.httpClient.Post(url, params, headers, inputJson)
	if err != nil {
		log.Println("Error while trying to Start Workflow", workflowName, err)
		return "", nil
	} else {
		return outputString, nil
	}
}

func (c *ConductorHttpClient) TerminateWorkflow(workflowId string, reason string) (string, error) {
	url := c.httpClient.MakeUrl("/workflow/{workflowId}", "{workflowId}", workflowId)

	params := make(map[string]string)

	if reason != "" {
		params["reason"] = reason
	}

	outputString, err := c.httpClient.Delete(url, params, nil, "")
	if err != nil {
		log.Println("Error while trying to Terminate Workflow", workflowId, err)
		return "", nil
	} else {
		return outputString, nil
	}
}

func (c *ConductorHttpClient) PauseWorkflow(workflowId string) (string, error) {
	url := c.httpClient.MakeUrl("/workflow/{workflowId}/pause", "{workflowId}", workflowId)
	outputString, err := c.httpClient.Put(url, nil, nil, "")
	if err != nil {
		log.Println("Error while trying to Pause Workflow", workflowId, err)
		return "", nil
	} else {
		return outputString, nil
	}
}

func (c *ConductorHttpClient) ResumeWorkflow(workflowId string) (string, error) {
	url := c.httpClient.MakeUrl("/workflow/{workflowId}/resume", "{workflowId}", workflowId)
	outputString, err := c.httpClient.Put(url, nil, nil, "")
	if err != nil {
		log.Println("Error while trying to Resume Workflow", workflowId, err)
		return "", nil
	} else {
		return outputString, nil
	}
}

func (c *ConductorHttpClient) SkipTaskFromWorkflow(workflowId string, taskReferenceName string, skipTaskRequestBody string) (string, error) {
	url := c.httpClient.MakeUrl("/workflow/{workflowId}/skiptask/{taskReferenceName}", "{workflowId}", workflowId, "{taskReferenceName}", taskReferenceName)

	outputString, err := c.httpClient.Put(url, nil, nil, skipTaskRequestBody)
	if err != nil {
		log.Println("Error while trying to Skip Task From Workflow", workflowId, err)
		return "", nil
	} else {
		return outputString, nil
	}
}

func (c *ConductorHttpClient) RerunWorkflow(workflowId string, rerunWorkflowRequest string) (string, error) {
	url := c.httpClient.MakeUrl("/workflow/{workflowId}/rerun", "{workflowId}", workflowId)
	if rerunWorkflowRequest == "" {
		rerunWorkflowRequest = "{}"
	}

	outputString, err := c.httpClient.Post(url, nil, nil, rerunWorkflowRequest)
	if err != nil {
		log.Println("Error while trying to Rerun Workflow", workflowId, err)
		return "", nil
	} else {
		return outputString, nil
	}
}

func (c *ConductorHttpClient) RestartWorkflow(workflowId string) (string, error) {
	url := c.httpClient.MakeUrl("/workflow/{workflowId}/restart", "{workflowId}", workflowId)

	outputString, err := c.httpClient.Post(url, nil, nil, "")
	if err != nil {
		log.Println("Error while trying to Restart Completed Workflow", workflowId, err)
		return "", nil
	} else {
		return outputString, nil
	}
}
