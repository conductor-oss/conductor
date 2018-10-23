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

	"github.com/netflix/conductor/client/gogrpc/conductor/grpc/tasks"

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

func (s *fakeTaskService) newTask(req *tasks.PollRequest) (*model.Task, error) {
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

func (s *fakeTaskService) updateTask(res *model.TaskResult) (*tasks.UpdateTaskResponse, error) {
	id := res.GetTaskId()

	s.mu.Lock()
	if _, found := s.completed[id]; !found {
		panic("missing task: " + id)
	}
	s.completed[id] = true
	s.mu.Unlock()

	return &tasks.UpdateTaskResponse{
		TaskId: id,
	}, nil
}

func (s *fakeTaskService) Poll(ctx context.Context, in *tasks.PollRequest, opts ...grpc.CallOption) (*tasks.PollResponse, error) {
	return nil, ErrNotImplemented
}

func (s *fakeTaskService) BatchPoll(context.Context, *tasks.BatchPollRequest, ...grpc.CallOption) (tasks.TaskService_BatchPollClient, error) {
	return nil, ErrNotImplemented
}

func (s *fakeTaskService) GetPendingTaskForWorkflow(context.Context, *tasks.PendingTaskRequest, ...grpc.CallOption) (*tasks.PendingTaskResponse, error) {
    return nil, ErrNotImplemented
}

func (s *fakeTaskService) GetTasksInProgress(ctx context.Context, in *tasks.TasksInProgressRequest, opts ...grpc.CallOption) (*tasks.TasksInProgressResponse, error) {
	return nil, ErrNotImplemented
}

func (s *fakeTaskService) UpdateTask(ctx context.Context, in *tasks.UpdateTaskRequest, opts ...grpc.CallOption) (*tasks.UpdateTaskResponse, error) {
	return nil, ErrNotImplemented
}

func (s *fakeTaskService) AckTask(ctx context.Context, in *tasks.AckTaskRequest, opts ...grpc.CallOption) (*tasks.AckTaskResponse, error) {
	return nil, ErrNotImplemented
}

func (s *fakeTaskService) AddLog(ctx context.Context, in *tasks.AddLogRequest, opts ...grpc.CallOption) (*tasks.AddLogResponse, error) {
	return nil, ErrNotImplemented
}

func (s *fakeTaskService) GetQueueAllInfo(ctx context.Context, in *tasks.QueueAllInfoRequest, opts ...grpc.CallOption) (*tasks.QueueAllInfoResponse, error) {
    return nil, ErrNotImplemented
}

func (s *fakeTaskService) GetQueueInfo(ctx context.Context, in *tasks.QueueInfoRequest, opts ...grpc.CallOption) (*tasks.QueueInfoResponse, error) {
    return nil, ErrNotImplemented
}

func (s *fakeTaskService) GetTaskLogs(ctx context.Context, in *tasks.GetTaskLogsRequest, opts ...grpc.CallOption) (*tasks.GetTaskLogsResponse, error) {
    return nil, ErrNotImplemented
}

func (s *fakeTaskService) GetTask(ctx context.Context, in *tasks.GetTaskRequest, opts ...grpc.CallOption) (*tasks.GetTaskResponse, error) {
    return nil, ErrNotImplemented
}

func (s *fakeTaskService) RemoveTaskFromQueue(ctx context.Context, in *tasks.RemoveTaskRequest, opts ...grpc.CallOption) (*tasks.RemoveTaskResponse, error) {
    return nil, ErrNotImplemented
}

func (s *fakeTaskService) GetQueueSizesForTasks(ctx context.Context, in *tasks.QueueSizesRequest, opts ...grpc.CallOption) (*tasks.QueueSizesResponse, error) {
    return nil, ErrNotImplemented
}


type fakeTaskClient struct {
	tasks *fakeTaskService
}

func (c *fakeTaskClient) Tasks() tasks.TaskServiceClient {
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
