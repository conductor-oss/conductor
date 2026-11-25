---
description: "Connect Conductor to the systems around it: message brokers and webhooks, MCP tool servers, and remote A2A agents."
---

# Integrations

Integrations connect Conductor to the systems around it. They come in three kinds. Event-driven orchestration moves messages between workflows and the outside world. MCP integration connects agents to tools. A2A integration connects Conductor to agents that run elsewhere.

- **[Event-Driven Orchestration](../how-tos/event-bus.md)**: publish workflow messages to a broker, start or advance workflows from incoming messages and webhooks, signal waiting executions, and emit status events.
- **[MCP Integration](../ai/mcp-guide.md)**: discover and call tools over the Model Context Protocol from workflows and agents.
- **[A2A Integration](../ai/a2a-integration.md)**: call an independently deployed agent as a durable workflow step over the Agent2Agent protocol, or expose your own.
