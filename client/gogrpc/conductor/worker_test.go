package conductor

import (
	"context"
	"flag"
	"fmt"
	"io"
	"math/rand"
	"sync"
	"testing"
	"time"

	"github.com/golang/protobuf/ptypes/empty"
	pb "github.com/netflix/conductor/client/gogrpc/conductor/grpc"
	"github.com/netflix/conductor/client/gogrpc/conductor/model"
	"google.golang.org/grpc"

	"github.com/stretchr/testify/assert"
)

var doTrace = flag.Bool("dotrace", false, "print tracing information")

func trace(format string, args ...interface{}) {
	if *doTrace {
		fmt.Printf(format, args...)
	}
}

type fakeTaskService struct {
	latency   time.Duration
	shutdown  chan struct{}
	mu        sync.Mutex
	completed map[string]bool
	result    error
}

func randomTaskID() string {
	return fmt.Sprintf("task-%08x", rand.Int63())
}

var ErrNotImplemented = fmt.Errorf("API call not implemented")

func (s *fakeTaskService) newTask(req *pb.PollRequest) (*model.Task, error) {
	id := randomTaskID()

	s.mu.Lock()
	s.completed[id] = false
	s.mu.Unlock()

	return &model.Task{
		TaskType: req.GetTaskType(),
		Status:   model.Task_SCHEDULED,
		TaskId:   id,
	}, nil
}

func (s *fakeTaskService) updateTask(res *model.TaskResult) (*pb.TaskUpdateResponse, error) {
	id := res.GetTaskId()

	s.mu.Lock()
	if _, found := s.completed[id]; !found {
		panic("missing task: " + id)
	}
	s.completed[id] = true
	s.mu.Unlock()

	return &pb.TaskUpdateResponse{
		TaskId: id,
	}, nil
}

func (s *fakeTaskService) Poll(ctx context.Context, in *pb.PollRequest, opts ...grpc.CallOption) (*model.Task, error) {
	select {
	case <-time.After(s.latency):
		return s.newTask(in)
	case <-s.shutdown:
		return nil, s.result
	}
}
func (s *fakeTaskService) PollStream(ctx context.Context, opts ...grpc.CallOption) (pb.TaskService_PollStreamClient, error) {
	return nil, ErrNotImplemented
}
func (s *fakeTaskService) GetTasksInProgress(ctx context.Context, in *pb.TasksInProgressRequest, opts ...grpc.CallOption) (*pb.TasksInProgressResponse, error) {
	return nil, ErrNotImplemented
}
func (s *fakeTaskService) GetPendingTaskForWorkflow(ctx context.Context, in *pb.PendingTaskRequest, opts ...grpc.CallOption) (*model.Task, error) {
	return nil, ErrNotImplemented
}
func (s *fakeTaskService) UpdateTask(ctx context.Context, in *model.TaskResult, opts ...grpc.CallOption) (*pb.TaskUpdateResponse, error) {
	select {
	case <-time.After(s.latency):
		return s.updateTask(in)
	case <-s.shutdown:
		return nil, s.result
	}
}
func (s *fakeTaskService) AckTask(ctx context.Context, in *pb.AckTaskRequest, opts ...grpc.CallOption) (*pb.AckTaskResponse, error) {
	return nil, ErrNotImplemented
}
func (s *fakeTaskService) AddLog(ctx context.Context, in *pb.AddLogRequest, opts ...grpc.CallOption) (*empty.Empty, error) {
	return nil, ErrNotImplemented
}
func (s *fakeTaskService) GetLogs(ctx context.Context, in *pb.TaskId, opts ...grpc.CallOption) (*pb.GetLogsResponse, error) {
	return nil, ErrNotImplemented
}

type fakeTaskClient struct {
	tasks *fakeTaskService
}

func (c *fakeTaskClient) Tasks() pb.TaskServiceClient {
	return c.tasks
}

func (c *fakeTaskClient) forceShutdown(err error) {
	c.tasks.result = err
	close(c.tasks.shutdown)
}

func (c *fakeTaskClient) Shutdown() {
	c.tasks.result = io.EOF
	close(c.tasks.shutdown)
}

func newFakeTaskClient(latency time.Duration) *fakeTaskClient {
	return &fakeTaskClient{
		tasks: &fakeTaskService{
			shutdown: make(chan struct{}),
			latency:  latency,
		},
	}
}

type slowExecutor struct {
	mu    sync.Mutex
	recv  []*model.Task
	delay time.Duration
}

func (exe *slowExecutor) Execute(ctx context.Context, m *model.Task) (*model.TaskResult, error) {
	exe.mu.Lock()
	exe.recv = append(exe.recv, m)
	exe.mu.Unlock()

	time.Sleep(exe.delay)
	return &model.TaskResult{
		TaskId: m.GetTaskId(),
		Status: model.TaskResult_COMPLETED,
	}, nil
}

func (exe *slowExecutor) ConnectionError(err error) error {
	panic(err)
}

func TestWorkerInterface(t *testing.T) {
	mock := newFakeTaskClient(200 * time.Millisecond)
	exec := &slowExecutor{
		delay: 100 * time.Millisecond,
	}

	worker := &Worker{
		TaskType:    "fake-task",
		Concurrency: 4,
		Executor:    exec,
		Client:      mock,
	}

	time.AfterFunc(1*time.Second, func() {
		worker.Shutdown()
	})

	assert.NoError(t, worker.Run())

	for id, completed := range mock.tasks.completed {
		assert.Truef(t, completed, "task %s was not reported as completed", id)
	}
	assert.Equal(t, len(mock.tasks.completed), len(exec.recv))
}
