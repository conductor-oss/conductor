# Workflow Message Queue (WMQ)

**tl;dr** — every workflow now has a queue. You can use this queue to turn your workflow into an event loop: it sits idle, waiting for messages, processes each one, then goes back to waiting.

## How it works

WMQ adds a persistent message queue to every running Conductor workflow. While the workflow is active you can push messages to it from anywhere — another service, a Kafka consumer, a webhook handler, a human — and the workflow will pick them up and act on them.

Two pieces make this work:

1. **`POST /api/workflow/{workflowId}/messages`** — an HTTP endpoint exposed by Conductor that accepts a JSON payload and enqueues it on the workflow's queue.
2. **`PULL_WORKFLOW_MESSAGES`** — a new Conductor system task that blocks until messages arrive, then completes with `output.messages` containing the batch.

## Prerequisites

WMQ requires changes that are currently in review:

| Component | PR |
|---|---|
| Conductor OSS | https://github.com/conductor-oss/conductor/pull/917 |
| Python SDK (`conductor-python`) | https://github.com/conductor-oss/python-sdk/pull/389 |

## Using WMQ

Add a `PULL_WORKFLOW_MESSAGES` task to your workflow definition:

```json
{
  "name": "wait_for_message",
  "taskReferenceName": "wait_for_message_ref",
  "type": "PULL_WORKFLOW_MESSAGES",
  "inputParameters": {
    "batchSize": 1
  }
}
```

Then push to it:

```bash
curl -X POST http://localhost:8080/api/workflow/{workflowId}/messages \
  -H "Content-Type: application/json" \
  -d '{"text": "hello"}'
```

The task completes with:

```json
{
  "messages": [
    {
      "id": "3f2504e0-4f89-11d3-9a0c-0305e82c3301",
      "workflowId": "8e2c14e1-...",
      "payload": { "text": "hello" },
      "receivedAt": "2025-06-15T10:30:00Z"
    }
  ],
  "count": 1
}
```

Your workflow accesses the user data via `output.messages[0].payload`. The `id` and `receivedAt` fields are added by Conductor at ingestion time.

**Push errors:**
- `409 Conflict` — workflow is not in `RUNNING` state (completed, failed, terminated, etc.). The message is not stored.
- `500` — queue is full (`maxQueueSize` reached). Caller must back off and retry.

### Event loop pattern

For workflows that process an unbounded stream of messages, wrap the task in a `DO_WHILE`:

```json
{
  "name": "message_loop",
  "taskReferenceName": "message_loop_ref",
  "type": "DO_WHILE",
  "loopCondition": "$.message_loop_ref['iteration'] < 100",
  "loopOver": [
    {
      "name": "pull_message",
      "taskReferenceName": "pull_message_ref",
      "type": "PULL_WORKFLOW_MESSAGES",
      "inputParameters": { "batchSize": 1 }
    },
    {
      "name": "process_message",
      "taskReferenceName": "process_message_ref",
      "type": "INLINE",
      "inputParameters": {
        "evaluatorType": "javascript",
        "expression": "function e() { return { payload: $.messages[0].payload }; } e();",
        "messages": "${pull_message_ref.output.messages}"
      }
    }
  ]
}
```

The loop parks on `PULL_WORKFLOW_MESSAGES` until the next message arrives.

## Using WMQ with Agentspan

Agentspan wraps WMQ behind `wait_for_message_tool` and `runtime.send_message()`. See https://github.com/agentspan/agentspan/pull/23.

### Define a message-waiting tool

```python
from agentspan.agents import Agent, wait_for_message_tool

inbox = wait_for_message_tool(
    name="wait_for_message",
    description="Wait for the next incoming message.",
)

agent = Agent(
    name="my-agent",
    model="openai/gpt-4o",
    tools=[inbox],
    system_prompt="You are a message processing agent. Wait for messages and process them one by one.",
)
```

When the agent calls this tool the runtime emits a `WAITING` event, the workflow parks on a `PULL_WORKFLOW_MESSAGES` task, and nothing runs until a message arrives.

### Send a message to the running agent

```python
with AgentRuntime() as runtime:
    handle = runtime.start(agent, "Start processing messages.")

    # from anywhere, at any time:
    runtime.send_message(handle.workflow_id, {"text": "hello"})
```

`send_message` POSTs the payload to `/api/workflow/{workflowId}/messages`. The workflow unblocks, the agent sees the message as a tool result, and the loop continues.

### Kafka bridge example

The pattern also works as a bridge from external event streams.

Run the agent (it runs as a workflow in Conductor), then send messages from a Kafka consumer:

```python
with AgentRuntime() as runtime:
    handle = runtime.start(agent, "Start consuming messages from Kafka.")

    consumer = Consumer({...})
    consumer.subscribe([KAFKA_TOPIC])

    while True:
        msg = consumer.poll(timeout=1.0)
        if msg:
            runtime.send_message(handle.workflow_id, {
                "topic": msg.topic(),
                "value": msg.value().decode("utf-8"),
            })
```

Full examples: [`72_wait_for_message.py`](../sdk/python/examples/72_wait_for_message.py), [`73_wait_for_message_streaming.py`](../sdk/python/examples/73_wait_for_message_streaming.py), [`74_kafka_consumer_agent.py`](../sdk/python/examples/74_kafka_consumer_agent.py).

## Configuration

```properties
conductor.workflow-message-queue.enabled=true
conductor.workflow-message-queue.maxQueueSize=1000
conductor.workflow-message-queue.ttlSeconds=86400
conductor.workflow-message-queue.maxBatchSize=100
```

| Property | Default | Description |
|---|---|---|
| `enabled` | `false` | Enable the WMQ feature |
| `maxQueueSize` | `1000` | Max messages queued per workflow |
| `ttlSeconds` | `86400` | Message TTL (24 h) |
| `maxBatchSize` | `100` | Max messages returned per `PULL_WORKFLOW_MESSAGES` poll |
