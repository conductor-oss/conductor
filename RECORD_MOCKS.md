# Record and replay LLM responses

The server records every real LLM response as a JSON file while `conductor.ai.record-mode` is on, and plays those files back instead of calling a provider while `conductor.ai.enable-llm-mocks` is on. No SDK-side code changes are needed for either.

## 1. Enable recording on the server

Add these settings to the configuration your server loads. The paths are relative to the server's working directory; use an absolute path if you prefer.

```properties
conductor.integrations.ai.enabled=true
conductor.ai.record-mode=true
conductor.ai.enable-llm-mocks=false
conductor.ai.recordings-directory=./llm-recordings/my-agent-run-1
```

Use a new directory for each recording session. The server creates it.

The real provider still needs its credentials. For OpenAI that is `OPENAI_API_KEY` in the server's environment, which `application.properties` maps to `conductor.ai.openai.api-key`. Restart the server after changing the settings.

## 2. Point the SDK at the server

For the Python SDK:

```bash
export CONDUCTOR_SERVER_URL=http://localhost:8080/api
```

## 3. Run your agent against a real model

Set the agent's model in `provider/model` form, for example:

```python
model="openai/gpt-4o-mini",
```

Run the script normally. Keep its tool workers running and let the agent finish. Every LLM response the server receives is written to the recordings directory.

## 4. Check the recordings

```bash
ls -lh ./llm-recordings/my-agent-run-1/*.json
```

One file per LLM response, numbered in the order they were saved. Keep all of them. Avoid running unrelated agents while recording, because their responses are saved too.

## 5. Replay

Keep the same directory and flip the two mode settings:

```properties
conductor.ai.record-mode=false
conductor.ai.enable-llm-mocks=true
```

Restart the server. Change the agent's model to the mock provider:

```python
model="mock/mockLLM",
```

Use `mock/mockLLM` for every agent you replay, whatever provider recorded it. Run the same agent with the same prompt, instructions, tools, and starting conversation history. Tool workers still run, and their outputs must match the recorded run, including any dates or random values, because the server matches each LLM request against the recorded request content.

A request with no matching recording fails the LLM task with a non-retryable error instead of calling a provider. Two recordings with the same request but different responses stop the server from starting.

Separate per-step output assertions are unnecessary for playback. Request matching already validates the intermediate results that are passed into later LLM calls.
