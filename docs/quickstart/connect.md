---
description: Connect the Conductor CLI, SDKs, and agent runtime to Developer Edition or a local server.
---

# Connect to Conductor

<section class="integration-hero integration-hero--workflow" aria-label="Recommended: Orkes Developer Edition" markdown="1">

## Recommended: Orkes Developer Edition

Create a free [account](https://developer.orkescloud.com/), [application](https://orkes.io/content/access-control-and-security/applications#configuring-applications), and [access key](https://orkes.io/content/sdks/authentication#retrieving-access-keys) in [Orkes Developer Edition](https://developer.orkescloud.com/). Then set the following environment variables.

```bash
export CONDUCTOR_SERVER_URL=https://developer.orkescloud.com/api
export CONDUCTOR_AUTH_KEY=<your-access-key>
export CONDUCTOR_AUTH_SECRET=<your-access-secret>
```

You can then proceed to configure the local CLI and core SDKs.

</section>

## Install the CLI

The CLI registers workflows and starts executions against your chosen Conductor server.

```bash
npm install -g @conductor-oss/conductor-cli
```

## Local server alternative

Use when you need a self-managed development server. It requires Java 21+ and Node.js.

```bash
conductor server start
export CONDUCTOR_SERVER_URL=http://localhost:8080/api
conductor workflow list
```

## AI and agent credentials

Configure model access and credentials for AI workflows and agents.

- **Developer Edition:** add an integration for your model provider under [Integrations](https://orkes.io/content/category/integrations/ai-llm)
- **Local server:** [export the provider key](../devguide/ai/llm-orchestration.md#supported-llm-providers) before starting the server so it inherits it. For example:

    ```bash
    export OPENAI_API_KEY=<your-openai-api-key>
    conductor server start
    ```

## Docker

You can also run Conductor via the [official Docker container](https://hub.docker.com/r/conductoross/conductor).

```bash
docker run --rm -p 8080:8080 conductoross/conductor:3.32.0-rc.23
export CONDUCTOR_SERVER_URL=http://localhost:8080/api
conductor workflow list
```

## Next steps

Once you have Conductor up and reachable, choose what you want to build.

<div class="integration-action-grid integration-action-grid--four">
  <a class="integration-action-card" href="first-worker.html">
    <span class="integration-action-card__title">Your first workflow &amp; worker</span>
    <span>Author and run a durable workflow in your chosen language.</span>
  </a>
  <a class="integration-action-card" href="first-agent.html">
    <span class="integration-action-card__title">Run your first agent</span>
    <span>Author and run a Conductor Agent with Python, Java, TypeScript/JavaScript, or C#.</span>
  </a>
  <a class="integration-action-card" href="framework-agents.html">
    <span class="integration-action-card__title">Bring a framework agent</span>
    <span>Run an existing OpenAI Agents, LangChain, LangGraph, or Google ADK agent through Conductor.</span>
  </a>
  <a class="integration-action-card" href="first-workflow.html">
    <span class="integration-action-card__title">No-code</span>
    <span>Register and run a workflow with the CLI and JSON</span>
  </a>
</div>
