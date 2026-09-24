# Jev typed decisions with Conductor

This standalone worker example connects Conductor to Jev's System One API.
It supports Choice, Score, and Noul questions through OpenRouter or TypeSafe,
with an optional Vercel Connect credential bridge. It uses a standard `SIMPLE`
task and does not require changes to Conductor's server or chat providers.

The worker uses Python 3.10+ and `conductor-python` 2.0.0. Run commands below
from this directory with a local Conductor server available at
`http://localhost:8080/api`.

```bash
python3 -m venv .venv
source .venv/bin/activate
python3 -m pip install -r requirements.txt
```

`JevWorker` implements the SDK's `WorkerInterface`; `TaskHandler` manages its
worker process, polling, and task-result delivery. The custom HTTP client is
used only for Jev's System One endpoint.

## Connect through OpenRouter

Set `OPENROUTER_API_KEY` in the worker environment, or supply `--key-file` with
the path to a file containing only the key. Keep the file outside the repository.
The endpoint is `https://openrouter.ai/api/v1/systemone`. `request.json` contains
a small support-routing question using `jev-1.13`; the actual model revision and
provider-reported usage/cost are preserved in the result.

```bash
python3 worker.py smoke --key-file /path/to/openrouter-key.txt
```

This sends one paid decision request. It does not register or start a workflow.
No authentication values or request state are printed. Keys are read locally,
never placed in Conductor workflow input. HTTP redirects and automatic inference
retries are disabled; provider error bodies are excluded from task errors.

## Register and run the Conductor example

Register once:

```bash
curl --fail-with-body -H 'Content-Type: application/json' \
  --data-binary @task-definitions.json http://localhost:8080/api/metadata/taskdefs
curl --fail-with-body -H 'Content-Type: application/json' \
  --data-binary @workflow.json http://localhost:8080/api/metadata/workflow
```

The workflow registration returns a conflict when that name/version already
exists. Use the existing definition or explicitly manage its version. Endpoint
paths and bodies were checked against `MetadataResource`, `TaskResource`, and
`WorkflowResource` in `rest/src/main/java/com/netflix/conductor/rest/controllers/`.

Start the worker in one terminal:

```bash
python3 worker.py worker --key-file /path/to/openrouter-key.txt
```

Submit the example in another terminal:

```bash
curl --fail-with-body -H 'Content-Type: application/json' \
  --data-binary @request.json http://localhost:8080/api/workflow/jev_decision_example
```

The start response is the workflow ID. Inspect its execution in Conductor;
`output.decision` contains `model`, `answers`, `usage`, provider metadata and
request latency. The worker validates answer types and allowed choices before
completing the task. A typed response is not proof that the decision is correct.

The worker runs until Ctrl-C or SIGTERM, which closes the SDK's `TaskHandler`.
It uses one execution thread and a one-second polling interval. Task retries
are zero, the provider request timeout is 20 seconds, and the workflow timeout is
120 seconds. The SDK handles Conductor transport retries and result delivery;
Jev calls have no automatic HTTP retries. This is not an exactly-once inference
guarantee: a worker crash or explicit workflow retry can cause another call.

Set `--api`, `--worker-id`, and `--domain` as needed. Without `--api`, SDK
configuration reads `CONDUCTOR_SERVER_URL`, defaulting to
`http://localhost:8080/api`. Authentication supports the SDK's
`CONDUCTOR_AUTH_KEY` / `CONDUCTOR_AUTH_SECRET` environment variables or an
existing token supplied through `CONDUCTOR_AUTH_TOKEN` (sent by the SDK in
`X-Authorization`). Configure
authentication on registration and start requests separately. Use TLS for remote
servers carrying credentials. SDK logging is set to WARNING to avoid logging
task payloads at DEBUG level.

## Direct TypeSafe access

Set `TYPESAFE_API_KEY`, select `--route typesafe`, and set the request's `model`
to a revision available on your TypeSafe account. This calls
`https://api.typesafe.ai/v1/systemone` using the same typed request shape.

```bash
python3 worker.py smoke --route typesafe --request request.json
python3 worker.py worker --route typesafe
```

The example deliberately accepts a nonempty text `state` and the question fields
`type`, `instructions`, and optional `criteria`. It does not implement every
provider extension. Credentials and endpoints are configured on the worker,
not accepted from workflow payloads.

## Optional Vercel Connect access

Vercel Connect obtains Jev credentials; it is distinct from AI Gateway chat
completions. Configure a linked Vercel project, its Jev connector, and development
OIDC access using the [Jev connector guide](https://vercel.com/connect/jev) and
[SDK authentication guide](https://vercel.com/docs/connect/ts-sdk-reference).
Set `JEV_CONNECTOR` to the actual `jev/<connector-name>` in the environment or
local environment file.

```bash
npm ci --ignore-scripts
node --env-file=.env.local with-vercel.mjs worker
```

Keep the Python virtual environment activated when running the bridge so its
`python3` child has the Conductor SDK installed.

The bridge uses `@vercel/connect` version 2.3.2, obtains one token, and passes it
to the Python worker's environment as `TYPESAFE_API_KEY`. It never prints the
token. Restart with fresh OIDC access when credentials expire. Select a
TypeSafe-supported model in workflow input. Live Vercel authentication has not
been verified; a connector/project must be configured by its owner.

## Verification and references

```bash
python3 -m unittest -v
node --check with-vercel.mjs
```

Tests use a real loopback HTTP server to check requests, bearer authentication,
typed responses, error redaction, redirect rejection, and the real SDK
`TaskRunner` polling and reporting completed, failed, and terminally failed tasks.
They make no paid model requests.

- [TypeSafe API quickstart](https://docs.typesafe.ai/introduction/quickstart)
- [OpenRouter TypeSafe-compatible API](https://openrouter.ai/docs/guides/community/typesafe-sdk)

The OpenRouter route has been exercised with a real Jev decision during development.
No API keys or account-specific configuration are included in this example.
