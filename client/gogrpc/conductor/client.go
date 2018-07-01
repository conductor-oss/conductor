package conductor

import (
	pb "github.com/netflix/conductor/client/gogrpc/conductor/grpc"
	grpc "google.golang.org/grpc"
)

// TasksClient is a Conductor client that exposes the Conductor
// Tasks API.
type TasksClient interface {
	Tasks() pb.TaskServiceClient
	Shutdown()
}

// MetadataClient is a Conductor client that exposes the Conductor
// Metadata API.
type MetadataClient interface {
	Metadata() pb.MetadataServiceClient
	Shutdown()
}

// WorkflowsClient is a Conductor client that exposes the Conductor
// Workflows API.
type WorkflowsClient interface {
	Workflows() pb.WorkflowServiceClient
	Shutdown()
}

// Client encapsulates a GRPC connection to a Conductor server and
// the different services it exposes.
type Client struct {
	conn      *grpc.ClientConn
	tasks     pb.TaskServiceClient
	metadata  pb.MetadataServiceClient
	workflows pb.WorkflowServiceClient
}

// NewClient returns a new Client with a GRPC connection to the given address,
// and any optional grpc.Dialoption settings.
func NewClient(address string, options ...grpc.DialOption) (*Client, error) {
	conn, err := grpc.Dial(address, options...)
	if err != nil {
		return nil, err
	}
	return &Client{conn: conn}, nil
}

// Shutdown closes the underlying GRPC connection for this client.
func (client *Client) Shutdown() {
	client.conn.Close()
}

// Tasks returns the Tasks service for this client
func (client *Client) Tasks() pb.TaskServiceClient {
	if client.tasks == nil {
		client.tasks = pb.NewTaskServiceClient(client.conn)
	}
	return client.tasks
}

// Metadata returns the Metadata service for this client
func (client *Client) Metadata() pb.MetadataServiceClient {
	if client.metadata == nil {
		client.metadata = pb.NewMetadataServiceClient(client.conn)
	}
	return client.metadata
}

// Workflows returns the workflows service for this client
func (client *Client) Workflows() pb.WorkflowServiceClient {
	if client.workflows == nil {
		client.workflows = pb.NewWorkflowServiceClient(client.conn)
	}
	return client.workflows
}
