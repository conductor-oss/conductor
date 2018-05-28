package conductor

import (
	"context"
	"fmt"
	"testing"

	"github.com/golang/protobuf/ptypes/empty"
	pb "github.com/netflix/conductor/client/gogrpc/conductor/grpc"
	"github.com/netflix/conductor/client/gogrpc/conductor/model"
	"google.golang.org/grpc"
)

type fakePollStream struct {
	grpc.ClientStream
	service *fakeTaskService
	open    bool
}

func (stream *fakePollStream) Send(req *pb.PollRequest) error {
	stream.service.pollRequest(req)
	return nil
}

func (stream *fakePollStream) Recv() (*model.Task, error) {
	select {
	case task := <-stream.service.pending:
		return task, nil
	default:
		return nil, nil
	}
}

type fakeTaskService struct {
	pending chan *model.Task
}

func (s *fakeTaskService) pollRequest(req *pb.PollRequest) {
	for i := 0; i < int(req.GetTaskCount()); i++ {
		s.pending <- &model.Task{
			TaskType: req.GetTaskType(),
			Status:   model.Task_SCHEDULED,
		}
	}
}

var ErrNotImplemented = fmt.Errorf("API call not implemented")

func (s *fakeTaskService) Poll(ctx context.Context, in *pb.PollRequest, opts ...grpc.CallOption) (*model.Task, error) {
	return nil, ErrNotImplemented
}
func (s *fakeTaskService) PollStream(ctx context.Context, opts ...grpc.CallOption) (pb.TaskService_PollStreamClient, error) {
	return &fakePollStream{
		ClientStream: nil,
		service:      s,
		open:         true,
	}, nil
}
func (s *fakeTaskService) GetTasksInProgress(ctx context.Context, in *pb.TasksInProgressRequest, opts ...grpc.CallOption) (*pb.TasksInProgressResponse, error) {
	return nil, ErrNotImplemented
}
func (s *fakeTaskService) GetPendingTaskForWorkflow(ctx context.Context, in *pb.PendingTaskRequest, opts ...grpc.CallOption) (*model.Task, error) {
	return nil, ErrNotImplemented
}
func (s *fakeTaskService) UpdateTask(ctx context.Context, in *model.TaskResult, opts ...grpc.CallOption) (*pb.TaskUpdateResponse, error) {
	return nil, ErrNotImplemented
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

func TestWorkerInterface(t *testing.T) {

}
