---
description: Connect the Conductor CLI, SDKs, and agent runtime to Developer Edition or a local server.
---

# Connect to Conductor

<section class="integration-hero integration-hero--workflow" aria-labelledby="connect-hero-title">
  <div class="integration-hero__identity" aria-hidden="true">
    <img class="integration-hero__logo integration-hero__logo--conductor" src="../img/logo.svg" alt="" />
    <span class="integration-hero__connector">→</span>
    <img class="integration-hero__logo" src="../assets/images/concepts/durable-checkpoint.svg" alt="" />
  </div>
  <p class="integration-hero__eyebrow">One connection, every path</p>
  <h2 id="connect-hero-title">Connect once. Build workflows and agents anywhere.</h2>
  <p>Use Developer Edition by default, or run a local server with the CLI. Your workflow and agent quickstarts assume one of these connections is already verified.</p>
</section>

## Install the CLI

The CLI registers workflows, starts executions, and starts the local server when you choose the self-managed path.

```bash
npm install -g @conductor-oss/conductor-cli
```

## Recommended: Orkes Developer Edition

Create a free account, application, and access key in [Orkes Developer Edition](https://developer.orkescloud.com/). Keep the access key and secret out of source control, then configure the CLI and core SDKs:

```bash
export CONDUCTOR_SERVER_URL=https://developer.orkescloud.com/api
export CONDUCTOR_AUTH_KEY=<your-access-key>
export CONDUCTOR_AUTH_SECRET=<your-access-secret>
conductor workflow list
```

The final command verifies the connection without changing server state. Use this same `CONDUCTOR_*` configuration for the Java, Python, JavaScript, and Rust workflow SDKs.

### AI and agent credentials

For hosted AI workflows and agents, configure the model-provider integration in Developer Edition. Do not put provider API keys in workflow input or source files.

The Python agent runtime uses its own connection variable names:

```bash
export AGENTSPAN_SERVER_URL=https://developer.orkescloud.com/api
export AGENTSPAN_AUTH_KEY=<your-access-key>
export AGENTSPAN_AUTH_SECRET=<your-access-secret>
```

Use the provider configuration required by your selected framework and model. The agent quickstarts and the [Python SDK agent guide](https://github.com/conductor-oss/python-sdk/blob/main/docs/agents/getting-started.md) cover the runtime-specific details.

## Local alternative: Conductor CLI

Use this path when you need a self-managed development server. It requires Java 21+ and Node.js.

```bash
conductor server start
export CONDUCTOR_SERVER_URL=http://localhost:8080/api
conductor workflow list
```

For local AI and agent experiments, export the model provider key before starting the server so its workers inherit it:

```bash
export OPENAI_API_KEY=<your-openai-api-key>
conductor server start
```

Then point the Python agent runtime at the local server:

```bash
export AGENTSPAN_SERVER_URL=http://localhost:8080/api
```

## Docker fallback

If you cannot use the CLI server, run a local container instead:

```bash
docker run --rm -p 8080:8080 conductoross/conductor:latest
export CONDUCTOR_SERVER_URL=http://localhost:8080/api
conductor workflow list
```

## Recovery

- A connection failure usually means the URL is missing `/api`, the local server is not running, or the access key/secret does not belong to the selected Developer Edition application.
- If a local AI task cannot reach its provider, stop the server, export the provider key, and start it again so the server process receives the key.
- For deployment-specific configuration, see [Hosted Solutions](../devguide/running/hosted.md) and [Deploy Conductor](../devguide/running/deploy.md).

## Next step

With the connection verified, choose the first result you want to build:

<div class="integration-action-grid integration-action-grid--four">
  <a class="integration-action-card" href="first-workflow.md">
    <span class="integration-action-card__title">Run your first workflow</span>
    <span>Coordinate an API call and a JSON transformation with built-in tasks.</span>
  </a>
  <a class="integration-action-card" href="first-worker.html">
    <span class="integration-action-card__title">Write your first worker</span>
    <span>Poll a <code>SIMPLE</code> task, run business logic, and return a result.</span>
  </a>
  <a class="integration-action-card" href="first-agent.md">
    <span class="integration-action-card__title">Run your first agent</span>
    <span>Author and run a Conductor Agent with Python, Java, TypeScript/JavaScript, or C#.</span>
  </a>
  <a class="integration-action-card" href="framework-agents.md">
    <span class="integration-action-card__title">Bring a framework agent</span>
    <span>Run an existing OpenAI Agents, LangChain, LangGraph, or ADK agent through Conductor.</span>
  </a>
</div>
