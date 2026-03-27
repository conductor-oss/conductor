---
description: "Build Conductor workers in Ruby with idiomatic task definitions and workflow management."
---

# Ruby SDK

!!! info "Source"
    GitHub: [conductor-oss/ruby-sdk](https://github.com/conductor-oss/ruby-sdk) | Report issues and contribute on GitHub.

## Features

- **Full Feature Parity** with Python SDK
- **Ruby-Idiomatic Workflow DSL** - Clean block-based syntax with 25+ task types
- **Worker Framework** - Multi-threaded task execution with class-based and block-based workers
- **LLM/AI Tasks** - Chat completion, embeddings, RAG, image/audio generation
- **Orkes Cloud Support** - Authentication, secrets, integrations, prompts
- **Comprehensive Testing** - 400+ unit tests, 110 integration tests

## Installation

Add to your Gemfile:

```ruby
gem 'conductor_ruby'
```

Or install directly:

```bash
gem install conductor_ruby
```

## Quick Start

### Hello World

```ruby
require 'conductor'

# Configuration (reads CONDUCTOR_SERVER_URL from environment)
config = Conductor::Configuration.new

# Create clients
clients = Conductor::Orkes::OrkesClients.new(config)
executor = clients.get_workflow_executor

# Define a worker
class GreetWorker
  include Conductor::Worker::WorkerModule
  worker_task 'greet'

  def execute(task)
    name = get_input(task, 'name', 'World')
    { 'result' => "Hello, #{name}!" }
  end
end

# Build workflow using new DSL
workflow = Conductor.workflow :greetings, version: 1, executor: executor do
  greet = simple :greet, name: wf[:name]
  output result: greet[:result]
end

# Register and execute
workflow.register(overwrite: true)

# Start workers
runner = Conductor::Worker::TaskRunner.new(config)
runner.register_worker(GreetWorker.new)
runner.start

# Execute workflow
result = workflow.execute(input: { 'name' => 'Ruby' }, wait_for_seconds: 30)
puts "Result: #{result.output['result']}"  # => "Hello, Ruby!"

runner.stop
```

## Workflow DSL

The SDK provides a clean, Ruby-idiomatic DSL for building workflows:

```ruby
workflow = Conductor.workflow :order_processing, version: 1, executor: executor do
  # Access workflow inputs with wf[:param]
  user = simple :get_user, user_id: wf[:user_id]
  
  # Reference task outputs with task[:field]
  order = simple :validate_order, email: user[:email]
  
  # HTTP calls
  http :call_api, url: 'https://api.example.com', method: :post, body: { id: order[:id] }
  
  # Parallel execution
  parallel do
    simple :ship_order, order_id: order[:id]
    simple :send_confirmation, email: user[:email]
  end
  
  # Conditional branching
  decide order[:region] do
    on 'US' do
      simple :us_shipping
    end
    on 'EU' do
      simple :eu_shipping
    end
    otherwise do
      terminate :failed, 'Unsupported region'
    end
  end
  
  # Set workflow output
  output tracking: order[:tracking_number], status: 'completed'
end

# Register and execute
workflow.register(overwrite: true)
result = workflow.execute(input: { user_id: 123 }, wait_for_seconds: 60)
```

### Task Methods Reference

#### Basic Tasks

```ruby
# Simple task (worker execution)
result = simple :task_name, input1: 'value', input2: wf[:param]

# Inline code execution
jq :transform, query: '.items | map(.name)', input: previous[:data]
javascript :compute, script: 'return inputs.a + inputs.b', a: 1, b: 2

# Set workflow variables
set_variable :save_state, user_id: user[:id], status: 'active'

# Human/manual task
human :approval, display_name: 'Manager Approval', form_template: 'approval_form'
```

#### HTTP Tasks

```ruby
# HTTP request
http :call_api,
  url: 'https://api.example.com/users',
  method: :post,
  headers: { 'Authorization' => 'Bearer ${workflow.secrets.api_token}' },
  body: { name: wf[:name], email: wf[:email] }

# HTTP polling (wait for condition)
http_poll :wait_for_ready,
  url: 'https://api.example.com/status/${workflow.input.job_id}',
  method: :get,
  termination_condition: '$.status == "ready"',
  polling_interval: 5,
  polling_strategy: :fixed
```

#### Control Flow

```ruby
# Parallel execution (fork/join)
parallel do
  simple :branch_a
  simple :branch_b
  simple :branch_c
end

# Conditional branching
decide order[:status] do
  on 'pending' do
    simple :process_pending
  end
  on 'approved' do
    simple :process_approved
  end
  otherwise do
    simple :handle_unknown
  end
end

# Conditional shortcuts
when_true user[:is_premium] do
  simple :apply_discount
end

when_false order[:validated] do
  terminate :failed, 'Order validation failed'
end

# Loop over items
loop_over users[:list], as: :user do
  simple :process_user, user_id: iteration[:user][:id]
end

# Do-while loop
do_while :retry_loop, condition: '${retry_ref.output.success} == false' do
  simple :retry_operation
end
```

#### Sub-workflows

```ruby
# Call another workflow
sub_workflow :process_order,
  workflow_name: 'order_processor',
  version: 2,
  input: { order_id: wf[:order_id] }

# Start workflow (fire-and-forget)
start_workflow :trigger_notification,
  workflow_name: 'send_notifications',
  input: { user_id: user[:id] }

# Inline sub-workflow definition
inline_workflow :nested_process do
  simple :step1
  simple :step2
end
```

#### Wait and Events

```ruby
# Wait for duration
wait :pause, duration: '30s'   # or '5m', '1h', '2d'

# Wait until specific time
wait :scheduled, until: '2024-12-25T00:00:00Z'

# Wait for external webhook
wait_for_webhook :external_callback,
  matches: { 'type' => 'payment', 'order_id' => '${workflow.input.order_id}' }

# Publish event
event :notify, sink: 'conductor:workflow_events', payload: { status: 'completed' }
```

#### Termination

```ruby
# Complete workflow
terminate :success, 'Processing completed successfully'

# Fail workflow
terminate :failed, 'Validation error: missing required field'
```

#### Dynamic Tasks

```ruby
# Dynamic task name (resolved at runtime)
dynamic :run_handler, task_to_execute: wf[:handler_name]

# Dynamic fork (parallel tasks determined at runtime)
dynamic_fork :process_all,
  tasks_input: wf[:items],
  task_name: 'process_item'
```

### LLM/AI Tasks

```ruby
workflow = Conductor.workflow :ai_assistant, executor: executor do
  # Chat completion (messages auto-converted from simple format)
  response = llm_chat :chat,
    provider: 'openai',
    model: 'gpt-4',
    messages: [
      { role: :system, message: 'You are a helpful assistant.' },
      { role: :user, message: wf[:question] }
    ],
    temperature: 0.7

  # Text completion
  llm_text :complete,
    provider: 'anthropic',
    model: 'claude-3-sonnet',
    prompt: 'Summarize: ${workflow.input.text}'

  # Generate embeddings
  embeddings = llm_embeddings :embed,
    provider: 'openai',
    model: 'text-embedding-3-small',
    text: wf[:document]

  # Store embeddings in vector DB
  llm_store_embeddings :store,
    provider: 'pinecone',
    index: 'documents',
    embeddings: embeddings[:embeddings],
    metadata: { doc_id: wf[:doc_id] }

  # Search embeddings
  llm_search_embeddings :search,
    provider: 'pinecone',
    index: 'documents',
    query: wf[:search_query],
    max_results: 10

  # Generate image
  generate_image :create_image,
    provider: 'openai',
    model: 'dall-e-3',
    prompt: 'A sunset over mountains',
    size: '1024x1024'

  # Generate audio (text-to-speech)
  generate_audio :speak,
    provider: 'openai',
    model: 'tts-1',
    text: response[:content],
    voice: 'nova'

  # MCP (Model Context Protocol) integration
  tools = list_mcp_tools :get_tools, server_name: 'my_mcp_server'
  
  call_mcp_tool :use_tool,
    server_name: 'my_mcp_server',
    tool_name: 'search_documents',
    arguments: { query: wf[:query] }

  output answer: response[:content]
end
```

### Output References

The DSL uses a clean syntax for referencing outputs:

```ruby
# Workflow input reference
wf[:user_id]              # => '${workflow.input.user_id}'

# Task output reference
task[:field]              # => '${task_ref.output.field}'
task[:nested][:path]      # => '${task_ref.output.nested.path}'

# Loop iteration references (inside loop_over)
iteration[:current_item]  # Current item being processed
iteration[:index]         # Current index (0-based)
iteration[:user][:name]   # If `as: :user` specified
```

## Examples

The `examples/` directory contains comprehensive examples:

| Example | Description |
|---------|-------------|
| [`helloworld/`](https://github.com/conductor-oss/ruby-sdk/blob/main/examples/helloworld/) | Simplest complete example - worker + workflow + execution |
| [`workflow_dsl.rb`](https://github.com/conductor-oss/ruby-sdk/blob/main/examples/workflow_dsl.rb) | Comprehensive new DSL showcase |
| [`simple_worker.rb`](https://github.com/conductor-oss/ruby-sdk/blob/main/examples/simple_worker.rb) | Worker patterns: class-based, block-based, error handling |
| [`kitchensink.rb`](https://github.com/conductor-oss/ruby-sdk/blob/main/examples/kitchensink.rb) | All major task types using new DSL |
| [`dynamic_workflow.rb`](https://github.com/conductor-oss/ruby-sdk/blob/main/examples/dynamic_workflow.rb) | Create and execute workflows at runtime |
| [`workflow_ops.rb`](https://github.com/conductor-oss/ruby-sdk/blob/main/examples/workflow_ops.rb) | Lifecycle operations: pause, resume, restart, retry |
| [`agentic_workflows/`](https://github.com/conductor-oss/ruby-sdk/blob/main/examples/agentic_workflows/) | LLM chat and AI workflow examples |

Run examples:

```bash
# Set environment variables
export CONDUCTOR_SERVER_URL=http://localhost:8080/api
# For Orkes Cloud:
# export CONDUCTOR_AUTH_KEY=your_key
# export CONDUCTOR_AUTH_SECRET=your_secret

# Run hello world
cd examples/helloworld && bundle exec ruby helloworld.rb

# Run DSL showcase
bundle exec ruby examples/workflow_dsl.rb

# Run kitchen sink
bundle exec ruby examples/kitchensink.rb
```

## Worker Framework

### Class-Based Workers

```ruby
class ImageProcessor
  include Conductor::Worker::WorkerModule

  worker_task 'process_image', poll_interval: 1, thread_count: 4

  def execute(task)
    url = get_input(task, 'image_url')
    # Process image...
    
    result = Conductor::Http::Models::TaskResult.complete
    result.add_output_data('processed_url', processed_url)
    result.log('Image processed successfully')
    result
  end
end
```

### Block-Based Workers

```ruby
worker = Conductor::Worker.define('simple_task') do |task|
  input = task.input_data['value']
  { result: input * 2 }  # Return hash for automatic TaskResult
end
```

### Running Workers

```ruby
runner = Conductor::Worker::TaskRunner.new(config)
runner.register_worker(ImageProcessor.new)
runner.register_worker(worker)
runner.start(threads: 4)

# Graceful shutdown
trap('INT') { runner.stop }
sleep while runner.running?
```

## Configuration

### Environment Variables

```bash
export CONDUCTOR_SERVER_URL=http://localhost:8080/api
export CONDUCTOR_AUTH_KEY=your_key        # For Orkes Cloud
export CONDUCTOR_AUTH_SECRET=your_secret  # For Orkes Cloud
```

### Programmatic

```ruby
config = Conductor::Configuration.new(
  server_api_url: 'https://play.orkes.io/api',
  auth_key: 'your_key',
  auth_secret: 'your_secret',
  auth_token_ttl_min: 45,
  verify_ssl: true
)
```

## API Coverage

### Resource APIs (17 classes)

| API | Description |
|-----|-------------|
| WorkflowResourceApi | Workflow execution and management |
| TaskResourceApi | Task polling and updates |
| MetadataResourceApi | Workflow/task definitions |
| SchedulerResourceApi | Scheduled workflows |
| EventResourceApi | Event handlers |
| WorkflowBulkResourceApi | Bulk operations |
| PromptResourceApi | AI prompt templates |
| SecretResourceApi | Secret management |
| IntegrationResourceApi | External integrations |
| + 8 more | Authorization, Users, Groups, Roles, etc. |

### High-Level Clients (9 classes)

```ruby
clients = Conductor::Orkes::OrkesClients.new(config)

workflow_client = clients.get_workflow_client
task_client = clients.get_task_client
metadata_client = clients.get_metadata_client
scheduler_client = clients.get_scheduler_client
prompt_client = clients.get_prompt_client
secret_client = clients.get_secret_client
authorization_client = clients.get_authorization_client
workflow_executor = clients.get_workflow_executor
```

## Testing

```bash
# Unit tests
bundle exec rspec spec/conductor/

# Integration tests (requires Conductor server)
CONDUCTOR_SERVER_URL=http://localhost:8080/api bundle exec rspec spec/integration/
```

## Requirements

- Ruby 2.6+ (Ruby 3+ recommended)
- Conductor OSS 3.x or Orkes Cloud

## Dependencies

- `faraday ~> 2.0` - HTTP client
- `faraday-net_http_persistent ~> 2.0` - Connection pooling
- `faraday-retry ~> 2.0` - Automatic retries
- `concurrent-ruby ~> 1.2` - Thread-safe concurrency

## Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Run tests (`bundle exec rspec`)
4. Commit your changes (`git commit -m 'Add amazing feature'`)
5. Push to the branch (`git push origin feature/amazing-feature`)
6. Open a Pull Request

## License

Apache 2.0 - see [LICENSE](https://github.com/conductor-oss/ruby-sdk/blob/main/LICENSE) for details.

## Links

- [Conductor OSS](https://github.com/conductor-oss/conductor)
- [Orkes Cloud](https://orkes.io)
- [Documentation](https://conductor-oss.org)
- [Python SDK](https://github.com/conductor-sdk/conductor-python)
- [Community Slack](https://join.slack.com/t/orkes-conductor/shared_invite/zt-2vdbx239s-Eacdyqya9giNLHfrCavfaA)


## Examples

Browse all examples on GitHub: [conductor-oss/ruby-sdk/examples](https://github.com/conductor-oss/ruby-sdk/tree/main/examples)

| Example | Type |
|---|---|
| [Agentic Workflows](https://github.com/conductor-oss/ruby-sdk/tree/main/examples/agentic_workflows) | directory |
| [Dynamic Workflow](https://github.com/conductor-oss/ruby-sdk/blob/main/examples/dynamic_workflow.rb) | file |
| [Event Handler](https://github.com/conductor-oss/ruby-sdk/blob/main/examples/event_handler.rb) | file |
| [Event Listener Examples](https://github.com/conductor-oss/ruby-sdk/blob/main/examples/event_listener_examples.rb) | file |
| [Helloworld](https://github.com/conductor-oss/ruby-sdk/tree/main/examples/helloworld) | directory |
| [Kitchensink](https://github.com/conductor-oss/ruby-sdk/blob/main/examples/kitchensink.rb) | file |
| [Metadata Journey](https://github.com/conductor-oss/ruby-sdk/blob/main/examples/metadata_journey.rb) | file |
| [Metrics Example](https://github.com/conductor-oss/ruby-sdk/blob/main/examples/metrics_example.rb) | file |
| [New Dsl Demo](https://github.com/conductor-oss/ruby-sdk/blob/main/examples/new_dsl_demo.rb) | file |
| [Orkes](https://github.com/conductor-oss/ruby-sdk/tree/main/examples/orkes) | directory |
| [Prompt Journey](https://github.com/conductor-oss/ruby-sdk/blob/main/examples/prompt_journey.rb) | file |
| [Rag Workflow](https://github.com/conductor-oss/ruby-sdk/blob/main/examples/rag_workflow.rb) | file |
| [Schedule Journey](https://github.com/conductor-oss/ruby-sdk/blob/main/examples/schedule_journey.rb) | file |
| [Simple Worker](https://github.com/conductor-oss/ruby-sdk/blob/main/examples/simple_worker.rb) | file |
| [Simple Workflow](https://github.com/conductor-oss/ruby-sdk/blob/main/examples/simple_workflow.rb) | file |
| [Task Context Example](https://github.com/conductor-oss/ruby-sdk/blob/main/examples/task_context_example.rb) | file |
| [Task Listener Example](https://github.com/conductor-oss/ruby-sdk/blob/main/examples/task_listener_example.rb) | file |
| [Worker Configuration Example](https://github.com/conductor-oss/ruby-sdk/blob/main/examples/worker_configuration_example.rb) | file |
| [Workflow Dsl](https://github.com/conductor-oss/ruby-sdk/blob/main/examples/workflow_dsl.rb) | file |
| [Workflow Ops](https://github.com/conductor-oss/ruby-sdk/blob/main/examples/workflow_ops.rb) | file |
