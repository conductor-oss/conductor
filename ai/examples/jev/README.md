# Jev decision agent

This example uses Conductor's Python agent SDK: `@agent`, `@tool`, and
`AgentRuntime`. The runtime compiles the agent, registers its tool, starts the
worker, and manages execution. There is no handwritten polling loop or workflow
JSON to register.

`JevDecisionAgent.assistant` is the agent. Its `jev_decide` tool calls Jev's
System One API through OpenRouter or TypeSafe and validates Choice, Score, and
Noul answers. The **orchestration model** runs the agent's conversation and tool
calling; **Jev** evaluates the supplied state. These are separate model calls,
with separate costs. Jev is not exposed as a chat-completion model.

## Setup

Use Python 3.10+ and a running Conductor server with agent and AI support. The
orchestration provider and model must be configured on that server. From this
directory:

```bash
python3 -m venv .venv
source .venv/bin/activate
python3 -m pip install -r requirements.txt
export CONDUCTOR_SERVER_URL=http://localhost:8080/api
export CONDUCTOR_AGENT_LLM_MODEL=openai/gpt-4o-mini
```

Replace the orchestration model with one configured on your server. `--model`
overrides this environment variable. The request file's `model` selects the
Jev model independently.

## Run

Set `OPENROUTER_API_KEY` in the process environment, or use a key file outside
the repository:

```bash
python3 jev_agent.py run --key-file /path/to/openrouter-key.txt
```

The default `request.json` asks Jev which team should handle a support issue.
Use `--request` for another state and typed questions. The agent is instructed
to pass them unchanged, call Jev once, and report its result. It has at most
three turns; the tool declares `max_calls=1`, `retry_count=0`, and a 30-second
timeout. The provider HTTP timeout is 20 seconds. These limits do not make
execution exactly once: restarting an execution can issue a new request.

The CLI prints the execution ID, status, and final agent output. Conductor's
execution view contains the tool's validated answers, actual Jev model revision,
usage/cost metadata, and latency. The final agent response is model-generated;
use the recorded tool output when exact decision values matter.

Compile and inspect without executing model calls:

```bash
python3 jev_agent.py plan --key-file /path/to/openrouter-key.txt
```

Test just the Jev connection with one paid decision request:

```bash
python3 jev_agent.py smoke --key-file /path/to/openrouter-key.txt
```

`--api` overrides the Conductor URL. Authentication uses the SDK's
`CONDUCTOR_AUTH_KEY` and `CONDUCTOR_AUTH_SECRET`, or an existing token passed via
`CONDUCTOR_AUTH_TOKEN` (sent as `X-Authorization`). Use TLS for remote servers.
Credentials remain in the local provider client and worker memory, outside agent
configuration, tool arguments, prompts, and workflow input. HTTP redirects are
rejected and provider error bodies are omitted from tool errors.

## Extend the agent

Edit the `@agent` instructions or add `@tool` methods to `JevDecisionAgent` and
include their names in its `tools` list. `create_agent` resolves these decorators
using the public `Agent.from_instance` API and configures an independent model
choice for each definition. The `DecisionProvider` interface keeps provider
transport separate from agent orchestration.

`AgentRuntime` also provides deployment and worker-serving APIs; this example
uses `run` for a self-contained local execution. SDK behavior was checked against
`conductor-python[agents]` 2.0.0.

## TypeSafe and Vercel Connect

For direct TypeSafe access, set `TYPESAFE_API_KEY` and select a Jev revision
available on your account in the request file:

```bash
python3 jev_agent.py run --route typesafe
```

Vercel Connect obtains credentials for Jev's TypeSafe endpoint. Configure the
project, connector, and local OIDC access using the
[Jev connector guide](https://vercel.com/connect/jev) and
[Connect SDK reference](https://vercel.com/docs/connect/ts-sdk-reference).
Set `JEV_CONNECTOR` to your `jev/<connector-name>` in `.env.local`.
Keep the Python virtual environment activated:

```bash
npm ci --ignore-scripts
node --env-file=.env.local with-vercel.mjs run
```

The bridge obtains one token and passes it to the Python child environment.
It does not print the token. Refresh local OIDC access when credentials expire.
Live Vercel authentication has not been verified.

## Tests

```bash
python3 -m unittest discover -v
node --check with-vercel.mjs
```

The default tests use a real loopback Jev server, validate decorated tool
binding and worker serialization, and check that exported agent configuration
contains no credential. They make no paid inference requests.

To test the complete agent against local Conductor, explicitly enable the live
orchestration test. This uses the configured chat model (paid inference) and a
loopback Jev fixture:

```bash
CONDUCTOR_AGENT_TEST_MODEL=openai/gpt-4o-mini python3 -m unittest test_agent.AgentTest.test_live_agent_runtime_calls_jev_tool -v
```

The live test compiles the agent, runs its tool through Conductor, and verifies
that Jev receives exactly one request with the expected state and questions.

References: [Conductor agents](https://github.com/conductor-oss/python-sdk/blob/main/docs/agents/concepts/agents.md),
[TypeSafe API](https://docs.typesafe.ai/introduction/quickstart),
[OpenRouter System One endpoint](https://openrouter.ai/docs/guides/community/typesafe-sdk).
