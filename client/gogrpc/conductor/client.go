package conductor

import (
	"github.com/netflix/conductor/client/gogrpc/conductor/grpc/tasks"
	"github.com/netflix/conductor/client/gogrpc/conductor/grpc/metadata"
	"github.com/netflix/conductor/client/gogrpc/conductor/grpc/workflows"
	grpc "google.golang.org/grpc"
)

// TasksClient is a Conductor client that exposes the Conductor
// Tasks API.
type TasksClient interface {
	Tasks() tasks.TaskServiceClient
	Shutdown()
}

// MetadataClient is a Conductor client that exposes the Conductor
// Metadata API.
type MetadataClient interface {
	Metadata() metadata.MetadataServiceClient
	Shutdown()
}

// WorkflowsClient is a Conductor client that exposes the Conductor
// Workflows API.
type WorkflowsClient interface {
	Workflows() workflows.WorkflowServiceClient
	Shutdown()
}

// Client encapsulates a GRPC connection to a Conductor server and
// the different services it exposes.
type Client struct {
	conn      *grpc.ClientConn
	tasks     tasks.TaskServiceClient
	metadata  metadata.MetadataServiceClient
	workflows workflows.WorkflowServiceClient
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
func (client *Client) Tasks() tasks.TaskServiceClient {
	if client.tasks == nil {
		client.tasks = tasks.NewTaskServiceClient(client.conn)
	}
	return client.tasks
}

// Metadata returns the Metadata service for this client
func (client *Client) Metadata() metadata.MetadataServiceClient {
	if client.metadata == nil {
		client.metadata = metadata.NewMetadataServiceClient(client.conn)
	}
	return client.metadata
}

// Workflows returns the workflows service for this client
func (client *Client) Workflows() workflows.WorkflowServiceClient {
	if client.workflows == nil {
		client.workflows = workflows.NewWorkflowServiceClient(client.conn)
	}
	return client.workflows
}
