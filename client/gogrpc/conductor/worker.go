package conductor

import (
	"context"
	"fmt"
	"os"
	"runtime"
	"time"

	pb "github.com/netflix/conductor/client/gogrpc/conductor/grpc"
	"github.com/netflix/conductor/client/gogrpc/conductor/model"
)

type Executor interface {
	Execute(*model.Task) (*model.TaskResult, error)
	ConnectionError(error) error
}

type Worker struct {
	TaskType    string
	Identifier  string
	Concurrency int
	Executor    Executor
	Client      TasksClient

	tasks    chan *model.Task
	results  chan *model.TaskResult
	shutdown chan struct{}
}

func (worker *Worker) Run() error {
	if worker.Identifier == "" {
		return fmt.Errorf("conductor: missing field 'Identifier'")
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

	worker.tasks = make(chan *model.Task, worker.Concurrency)
	worker.results = make(chan *model.TaskResult, worker.Concurrency)
	worker.shutdown = make(chan struct{})

	for i := 0; i < worker.Concurrency; i++ {
		go worker.thread()
	}

	for {
		err := worker.run()
		if err != nil {
			err = worker.Executor.ConnectionError(err)
			if err != nil {
				worker.Shutdown()
				return err
			}
		}
	}
}

func (worker *Worker) Shutdown() {
	close(worker.tasks)
	close(worker.shutdown)
	worker.Client.Shutdown()
}

func (worker *Worker) getRequest(pending int) *pb.PollRequest {
	return &pb.PollRequest{
		TaskType:  worker.TaskType,
		WorkerId:  worker.Identifier,
		TaskCount: int32(pending),
	}
}

func (worker *Worker) thread() {
	for task := range worker.tasks {
		result, err := worker.Executor.Execute(task)
		if err == nil {
			// TODO: what if the task failed?
			worker.results <- result
		}
	}
}

func (worker *Worker) run() error {
	stream, err := worker.Client.Tasks().PollStream(context.Background())
	if err != nil {
		return err
	}
	defer stream.CloseSend()

	errc := make(chan error)
	go func() {
		for {
			task, err := stream.Recv()
			if err != nil {
				errc <- err
				return
			}
			worker.tasks <- task
		}
	}()

	pending := worker.Concurrency

	for {
		timeout := time.NewTimer(1 * time.Second)

		select {
		case result := <-worker.results:
			_, err := worker.Client.Tasks().UpdateTask(context.Background(), result)
			if err != nil {
				return err
			}
			pending--

		case err := <-errc:
			return err

		case <-worker.shutdown:
			return nil

		case <-timeout.C:
			err := stream.Send(worker.getRequest(0))
			if err != nil {
				return err
			}

		default:
			if pending > 0 {
				err := stream.Send(worker.getRequest(pending))
				if err != nil {
					return err
				}
				pending = 0
			}
		}
		timeout.Stop()
	}
}
