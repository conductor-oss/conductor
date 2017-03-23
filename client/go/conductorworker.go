package conductor

import (
	"os"
	"time"
	"log"
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


func (c *ConductorWorker) Execute(taskData string, executeFunction func(t *task.Task) (*task.TaskResult, error)) {
	t, err := task.ParseTask(taskData)
	if err != nil {
		log.Println("Error Parsing task")
		return
	}

	taskResult, err := executeFunction(t)
	if err != nil {
		log.Println("Error Executing task")
		return
	}

	taskResultJsonString, err := taskResult.ToJSONString()
	if err != nil {
		log.Println(err)
		log.Println("Error Forming TaskResult JSON body")
		return
	}
	c.ConductorHttpClient.UpdateTask(taskResultJsonString)
}

func (c *ConductorWorker) PollAndExecute(taskType string, executeFunction func(t *task.Task) (*task.TaskResult, error)) {
	for {
		time.Sleep(time.Duration(c.PollingInterval) * time.Millisecond)
		polled, err := c.ConductorHttpClient.PollForTask(taskType, hostname)
		if err == nil {
			c.Execute(polled, executeFunction)
		}
	}
}

func (c *ConductorWorker) Start(taskType string, executeFunction func(t *task.Task) (*task.TaskResult, error), wait bool) {
	log.Println("Polling for task:", taskType, "with a:", c.PollingInterval, "(ms) polling interval with", c.ThreadCount, "goroutines for task execution, with workerid as", hostname)
	for i := 1; i <= c.ThreadCount; i++ {
		go c.PollAndExecute(taskType, executeFunction)
	}

	// wait infinitely while the go routines are running
	if wait {
		select{}
	}
}
