# LLM recording and mock playback

Record real chat completions once, then replay them in another Conductor server without real-provider credentials. Playback is deterministic: `mock/mockLLM` returns a saved response when the complete recorded request matches, and fails when none matches. It never calls a live provider.

## Configure the server

AI tasks require `conductor.integrations.ai.enabled=true`. These recording and playback properties are independent:

| Property | Default | Purpose |
| --- | --- | --- |
| `conductor.ai.record-mode` | `false` | Save each real chat-completion response as a JSON file. |
| `conductor.ai.enable-llm-mocks` | `false` | Register the `mock` provider for playback. |
| `conductor.ai.recordings-directory` | `./llm-recordings` | Directory used to write recordings and load them for playback. |

## Record and replay

1. Start a server with `conductor.ai.record-mode=true`. Keep each task's normal `llmProvider`, model, and generation settings. Each returned model response writes one JSON file to `conductor.ai.recordings-directory`, before task output validation.
2. Copy those JSON files to the directory configured on the playback server.
3. Restart the playback server with `conductor.ai.enable-llm-mocks=true`.
4. Set the task's `llmProvider` to `mock` and `model` to `mockLLM` (or `model="mock/mockLLM"` in the agent SDK). Use this same setting for recordings from any provider or model. Messages, tools, JSON-output constraints, and generation options must match the recorded request. Provider and model names are excluded from response matching.

The server reads recordings during startup. It rejects malformed files and conflicting responses for the same request; correct the files and restart. A missing matching recording fails the task. Recordings can be replayed in any order and reused concurrently. Playback matches the complete history first, then tries omitting automatically injected loop history for providers that suppress it. Explicit messages and participant history are preserved. Tool workers still run, and their outputs must match the recorded history.

Enabling both flags records real-provider calls while leaving `mock/mockLLM` playback calls unrecorded.

## Limits and compatibility

Recording and playback support chat completion only. Image generation and embeddings are not supported. Requests that use media, a previous-response ID, or provider-native tools such as web search, code interpreter, Google Search retrieval, or file search cannot be recorded.

Each JSON file contains one request and one response in recording schema version 3. Regenerate recordings created with earlier schema versions.
