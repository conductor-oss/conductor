package conductor

import (
	"context"
	"fmt"
	"os"
	"runtime"
	"sync"
	"sync/atomic"
	"time"

	"github.com/netflix/conductor/client/gogrpc/conductor/grpc/tasks"
	"github.com/netflix/conductor/client/gogrpc/conductor/model"
)

// An Executor is a struct that executes the logic required to resolve
// a task. Each Worker instance uses an Executor to run the polled tasks.
type Executor interface {
	// Execute attempt to resolve the given Task and returns a TaskResult
	// with its output. The given Context carries a deadline which must be
	// enforced by the implementation.
	// This function will be called by the Worker for each incoming Task,
	// and must be threadsafe as it can be called by several goroutines
	// concurrently.
	Execute(context.Context, *model.Task) (*model.TaskResult, error)

	// ConnectionError is called by a Worker whenever there's an error with
	// a GRPC connection. The GRPC error is passed in as its only argument.
	// If this function returns nil, the Worker will continue retrying the
	// connection; if it returns a non-nill error, the Worker will stop its
	// execution and return the given error as the result of the Worker.Run
	// function.
	ConnectionError(error) error
}

// A Worker uses a TaskClient to poll the Conductor server for new tasks and
// executes them using an Executor instance, returning the result of the task
// to the upstream server.
// The Worker struct must be created manually with the desired settings, and then
// ran with Worker.Run.
// Client implementations usually run a single Worker per process, or one worker per Task Type
// if a process needs to execute tasks of different types. The Concurrency
// field allows the worker to execute tasks concurrently in several goroutines.
type Worker struct {
	// TaskType is the identifier for the type of tasks that this worker can
	// execute. This will be send to Conductor when polling for new tasks.
	TaskType string

	// TaskTimeout is the total duration that a task will be executed for. This
	// includes the time required to poll, execute and return the task's results.
	// If not set, tasks will not timeout.
	TaskTimeout time.Duration

	// Identifier is an unique identifier for this worker. If not set, it defaults
	// to the local hostname.
	Identifier string

	// Concurrency is the amount of goroutines that wil poll for tasks and execute
	// them concurrently. If not set, it defaults to GOMAXPROCS, a sensible default.
	Concurrency int

	// Executor is an instance of an Executor that will actually run the logic required
	// for each task. See conductor.Executor.
	Executor Executor

	// Client is an instance of a conductor.Client that implements a Task service.
	// See conductor.Client
	Client TasksClient

	waitThreads  sync.WaitGroup
	active       int32 // atomic
	shutdown     chan struct{}
	shutdownFlag sync.Once
	result       error
}

// Run executes the main loop of the Worker, spawning several gorutines to poll and
// resolve tasks from a Conductor server.
// This is a blocking call that will not return until Worker.Shutdown is called from
// another goroutine. When shutting down cleanly, this function returns nil; otherwise
// an error is returned if there's been a problem with the GRPC connection and the Worker
// cannot continue running.
func (worker *Worker) Run() error {
	if worker.TaskType == "" {
		return fmt.Errorf("conductor: missing field 'TaskType'")
	}
	if worker.Executor == nil {
		return fmt.Errorf("conductor: missing field 'Executor'")
	}
	if worker.Client == nil {
		return fmt.Errorf("conductor: missing field 'Client'")
	}
	if worker.Identifier == "" {
		hostname, err := os.Hostname()
		if err != nil {
			return err
		}
		worker.Identifier = fmt.Sprintf("%s (conductor-go)", hostname)
	}
	if worker.Concurrency == 0 {
		worker.Concurrency = runtime.GOMAXPROCS(0)
	}

	worker.active = 0
	worker.result = nil
	worker.shutdown = make(chan struct{})
	worker.waitThreads.Add(worker.Concurrency)

	for i := 0; i < worker.Concurrency; i++ {
		go worker.thread()
	}

	worker.waitThreads.Wait()
	return worker.result
}

// Shutdown stops this worker gracefully. This function is thread-safe and may
// be called from any goroutine. Only the first call to Shutdown will have
// an effect.
func (worker *Worker) Shutdown() {
	worker.shutdownOnce(nil)
}

func (worker *Worker) shutdownOnce(err error) {
	worker.shutdownFlag.Do(func() {
		worker.result = err
		close(worker.shutdown)
		worker.waitThreads.Wait()
		worker.Client.Shutdown()
	})
}

func (worker *Worker) onError(err error) {
	userErr := worker.Executor.ConnectionError(err)
	if userErr != nil {
		worker.shutdownOnce(userErr)
	}
}

func (worker *Worker) runTask(req *tasks.PollRequest) error {
	ctx, cancel := context.WithTimeout(context.Background(), worker.TaskTimeout)
	defer cancel()

	task, err := worker.Client.Tasks().Poll(ctx, req)
	if err != nil {
		return err
	}

	result, err := worker.Executor.Execute(ctx, task.Task)
	// TODO: what if the task failed?
	if err == nil {
		request := tasks.UpdateTaskRequest{Result: result}
		_, err := worker.Client.Tasks().UpdateTask(context.Background(), &request)
		if err != nil {
			return err
		}
	}
	return nil
}

func (worker *Worker) thread() {
	defer worker.waitThreads.Done()

	pollRequest := &tasks.PollRequest{
		TaskType: worker.TaskType,
		WorkerId: worker.Identifier,
	}

	for range worker.shutdown {
		atomic.AddInt32(&worker.active, 1)
		err := worker.runTask(pollRequest)
		if err != nil {
			worker.onError(err)
		}
		atomic.AddInt32(&worker.active, -1)
	}
}
