package conductor

import (
	"fmt"
	"os"
	"time"
	"log"
	"encoding/json"
	"conductor/task"
)

var (
	hostname, hostnameError = os.Hostname()
)

func init() {
	if hostnameError != nil {
		log.Fatal("Could not get hostname")
	}
}

type ConductorWorker struct {
	ConductorHttpClient *ConductorHttpClient
	ThreadCount int
	PollingInterval int
}

func NewConductorWorker(baseUrl string, threadCount int, pollingInterval int) *ConductorWorker {
	conductorWorker := new(ConductorWorker)
	conductorWorker.ThreadCount = threadCount
	conductorWorker.PollingInterval = pollingInterval
	conductorHttpClient := NewConductorHttpClient(baseUrl)
	conductorWorker.ConductorHttpClient = conductorHttpClient
	return conductorWorker	
}


func (c *ConductorWorker) Execute(taskData string, executeFunction func(t *task.Task) (task.TaskStatus, string, error)) {
	t, err := task.ParseTask(taskData)
	if err != nil {
		fmt.Println("Error Parsing task")
		return
	}

	taskResultStatus, outputData, err := executeFunction(t)
	if err != nil {
		fmt.Println("Error Executing task")
		return
	}

	t.Status = taskResultStatus
	err = json.Unmarshal([]byte(outputData), &t.OutputData)
	if err != nil {
		fmt.Println(err)
		fmt.Println("Error Parsing outputData")
		return
	}

	jsonString, err := t.ToJSONString()
	if err != nil {
		fmt.Println(err)
		fmt.Println("Error Forming Task JSON body")
		return
	}
	c.ConductorHttpClient.UpdateTask(jsonString)
}

func (c *ConductorWorker) PollAndExecute(taskType string, executeFunction func(t *task.Task) (task.TaskStatus, string, error)) {
	for {
		time.Sleep(time.Duration(c.PollingInterval) * time.Millisecond)
		polled, err := c.ConductorHttpClient.PollForTask(taskType, hostname)
		if err == nil {
			c.Execute(polled, executeFunction)
		}
	}
}

func (c *ConductorWorker) Start(taskType string, executeFunction func(t *task.Task) (task.TaskStatus, string, error), wait bool) {
	fmt.Println("Polling for task:", taskType, "with a:", c.PollingInterval, "(ms) polling interval with", c.ThreadCount, "goroutines for task execution, with workerid as", hostname)
	for i := 1; i <= c.ThreadCount; i++ {
		go c.PollAndExecute(taskType, executeFunction)
	}

	// wait infinitely while the go routines are running
	if wait {
		for {
		}
	}
}
