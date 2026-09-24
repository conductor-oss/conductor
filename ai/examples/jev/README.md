# Jev server decision agent

This example uses `@agent` and the Python SDK's `DecisionModelTool`.
`AgentRuntime` handles compilation and execution; Conductor's Java server calls
Jev through `DECISION_MODEL`. There is no Python decision worker or provider
credential in this example.

## Setup

Build a Conductor server containing the [decision-model implementation](../../DECISION_MODELS.md).
Configure `JEV_API_KEY` on the server: use an OpenRouter key with the default
`JEV_ROUTE=openrouter`, or a TypeSafe key with `JEV_ROUTE=typesafe`. Configure an
orchestration chat model on the same server as usual.

`DecisionModelTool` is new SDK functionality and is not in the published 2.0.0
package. Install the matching Python SDK feature checkout with Python 3.10+:

```bash
python3 -m venv .venv
source .venv/bin/activate
python3 -m pip install -e '/path/to/python-sdk[agents]'
export CONDUCTOR_SERVER_URL=http://localhost:8080/api
export CONDUCTOR_AGENT_LLM_MODEL=openai/gpt-4o-mini
```

Replace the SDK path and orchestration model with your configured values. For
this change the SDK checkout is on `feature/agent_decisions`.

## Run

From this directory:

```bash
python3 jev_agent.py plan
python3 jev_agent.py run
```

`plan` asks the server to compile the agent without inference. `run` sends the
state from `request.json`; `--request` selects another file and `--model`
overrides the orchestration model. The CLI prints the execution ID, status, and
final conversational output. Inspect the `DECISION_MODEL` task in Conductor for
exact validated answers, actual Jev model revision, reported usage/cost, and
latency.

The chat model orchestrates the tool call and summarizes its result. Jev makes
the typed decision. Both have inference costs; the final summary is generated
text rather than the authoritative decision output.

## Extend

The example declares its decision tool directly in `@agent(tools=[...])`.
Change its fixed questions, decision provider/model, or add other tools. The
provider/model and fixed questions are server-side tool configuration, not
arguments controlled by the orchestration model. Only `state` is exposed in
this example.

Use the SDK's `Configuration` environment variables for Conductor connection and
authentication. Jev credentials belong on the server. Vercel Connect credentials,
if used, must be provisioned and refreshed there through the TypeSafe route.

## Local check

```bash
python3 -m unittest -v
```

This checks that the example serializes a server tool without credentials or a
Python worker. Provider and compiler behavior are covered in the Java modules;
the SDK wrapper has its own tests in the Python SDK repository.
