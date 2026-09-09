**Goal:** wire recording and playback into the existing LLM execution path using startup configuration and conditional Spring beans. Recording writes JSON files. `MockLLM` only plays back recorded responses; it does not generate fake answers or accept task-output stubs.

Implemented with startup properties and conditional Spring beans.

**Startup configuration**

Use the existing `conductor.ai` namespace for the new properties:

| Property | Default | Effect |
| --- | --- | --- |
| `conductor.ai.record-mode` | `false` | Creates the recording bean. Every supported real LLM chat call passing through the helper is recorded automatically. |
| `conductor.ai.enable-llm-mocks` | `false` | Creates and registers the playback-only `MockLLM` provider. |
| `conductor.ai.recordings-directory` | `./llm-recordings` | Directory for writing recordings and loading playback data. |

Both flags are independent. If both are enabled, real providers are recorded and `MockLLM` calls are excluded from recording. Existing [AIIntegrationEnabledCondition](ai/src/main/java/org/conductoross/conductor/config/AIIntegrationEnabledCondition.java) still controls whether AI workers are enabled.

**How an SDK uses it**

The SDK starts a workflow through the existing workflow API. Recording is transparent: tasks keep their real provider and model settings. For playback, the task's existing `llmProvider` field selects `mockLLM`. The server must have playback enabled and the recorded JSON files installed in its configured directory.

Keep the original `model` field during playback so the mapper uses the recorded assistant-history policy. Response matching uses the normalized request, excluding provider/model names. Playback loads the directory's saved request/response pairs. Users copy or mount those files into the playback deployment before startup.

No new endpoints, workflow-start fields, SDK methods, database tables, or upload/download service are required.

**Implemented wiring**

1. **Separate recording from playback.** The combined recorder/playback wrapper has been removed. `LlmCallRecorder` has a JSON-file implementation enabled with `@ConditionalOnProperty` for `record-mode`. `MockLLM` implements the existing [AIModel](ai/src/main/java/org/conductoross/conductor/ai/AIModel.java) interface. Both reuse `LlmSavedResponses`, `LlmRequestResponseConverter`, and `LlmJsonFiles`.

2. **Inject the optional recorder into the real call path.** [LLMs](ai/src/main/java/org/conductoross/conductor/ai/LLMs.java) constructs [LLMHelper](ai/src/main/java/org/conductoross/conductor/ai/LLMHelper.java) directly. Pass the optional recorder through that construction path. Wrap the selected `ChatModel` before the helper builds and sends its effective prompt. Capture messages, resolved tools, JSON-output constraints, and the returned response. Write before normal helper response validation so invalid JSON answers are also captured. With the recording bean absent, retain the existing path.

3. **Write a separate JSON document for each returned response.** Use a generated filename and atomic file publication. Each document contains one normalized request/response entry. Extend the file writer to accept a generated destination name independently of the document's scenario label. This avoids a shared mutable recording file, response cursor, or lock around provider calls. Repeated calls may produce multiple files. Provider exceptions have no response to save; report file-write failures explicitly. Retain the current schema and size validation. The current format supports chat/text/tool responses; media and provider-native tools still need explicit format support before they can be recorded and replayed.

4. **Register playback through provider configuration.** Add a conditional `MockLLMConfiguration` implementing [ModelConfiguration](ai/src/main/java/org/conductoross/conductor/ai/ModelConfiguration.java). [AIModelProvider](ai/src/main/java/org/conductoross/conductor/ai/AIModelProvider.java) already collects these configurations and registers each provider by name. `MockLLM.getModelProvider()` returns `mockLLM`. Load and validate JSON files during bean initialization, building an immutable request-to-response map. Identical entries are deduplicated; conflicting responses for the same request fail playback initialization. Perform validation before the registry's catch-and-log initialization loop so an invalid playback configuration cannot silently disappear.

5. **Keep playback isolated per call.** Add a default `AIModel.getChatModel(ChatCompletion input)` overload delegating to the existing no-argument method, and use it in `LLMHelper`. `MockLLM` overrides it to capture the request's JSON-output constraints without storing mutable request data on a singleton bean. At `ChatModel.call(Prompt)`, convert the request, look up the saved response, and reconstruct a normal Spring AI response with fresh tool-call IDs. Missing requests fail with `NonRetryableException`. There is no live-provider fallback or synthesized response.

6. **Check history construction and finish the docs.** [ChatCompleteTaskMapper](ai/src/main/java/org/conductoross/conductor/ai/tasks/mapper/ChatCompleteTaskMapper.java) currently asks the provider about assistant-prefill support. Preserve the recording's history policy during playback without requiring real-provider credentials; include that policy in recorded metadata where necessary. Document the startup flags, file-copy workflow, and existing task provider selection after implementation. Keep all Java variable declarations explicitly typed.

**Focused verification**

- Spring context tests cover both flags off, recording only, playback only, and both enabled.
- Exercise a normal workflow through the existing HTTP API and worker path; verify recording happens without manually constructing the wrapper in the test.
- Start a fresh playback context with the generated files and no real-provider credentials. Replay a tool-calling workflow through normal helper validation.
- Verify out-of-order, repeated, and parallel calls; different physical tool-call IDs; conflicting recordings; missing requests; and invalid JSON responses.
- Confirm disabled playback does not register `mockLLM`, playback never calls a real provider, and recording never records playback calls.
- Run Spotless and only affected tests. No repository-wide test run by default.

Verified: focused AI tests pass (97 passed, one existing skip), and `LlmRecordingHttpIntegrationTest` passes recording and playback through the existing workflow API across separate server contexts. Spotless applied.
