package conductor

import (
	pb "github.com/netflix/conductor/client/gogrpc/conductor/grpc"
	grpc "google.golang.org/grpc"
)

type TasksClient interface {
	Tasks() pb.TaskServiceClient
	Shutdown()
}

type MetadataClient interface {
	Metadata() pb.MetadataServiceClient
	Shutdown()
}

type WorkflowsClient interface {
	Workflows() pb.WorkflowServiceClient
	Shutdown()
}

type Client struct {
	conn      *grpc.ClientConn
	tasks     pb.TaskServiceClient
	metadata  pb.MetadataServiceClient
	workflows pb.WorkflowServiceClient
}

func NewClient(address string, options ...grpc.DialOption) (*Client, error) {
	conn, err := grpc.Dial(address, options...)
	if err != nil {
		return nil, err
	}
	return &Client{conn: conn}, nil
}

func (client *Client) Shutdown() {
	client.conn.Close()
}

func (client *Client) Tasks() pb.TaskServiceClient {
	if client.tasks == nil {
		client.tasks = pb.NewTaskServiceClient(client.conn)
	}
	return client.tasks
}

func (client *Client) Metadata() pb.MetadataServiceClient {
	if client.metadata == nil {
		client.metadata = pb.NewMetadataServiceClient(client.conn)
	}
	return client.metadata
}

func (client *Client) Workflows() pb.WorkflowServiceClient {
	if client.workflows == nil {
		client.workflows = pb.NewWorkflowServiceClient(client.conn)
	}
	return client.workflows
}
