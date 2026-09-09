Recording and playback are selected at server startup. SDKs keep using the existing workflow API.

| Property | Default | Behavior |
| --- | --- | --- |
| `conductor.ai.record-mode` | `false` | Creates a recorder that saves each supported real chat response to a separate JSON file. |
| `conductor.ai.enable-llm-mocks` | `false` | Registers `mockLLM`, which only plays back saved responses. |
| `conductor.ai.recordings-directory` | `./llm-recordings` | Recording output and playback input directory. |

AI workers also require the existing `conductor.integrations.ai.enabled=true` setting.

1. **Record:** keep the task's real `llmProvider`. [LLMs](ai/src/main/java/org/conductoross/conductor/ai/LLMs.java) passes the optional recorder to [LLMHelper](ai/src/main/java/org/conductoross/conductor/ai/LLMHelper.java). [JsonFileLlmCallRecorder](ai/src/main/java/org/conductoross/conductor/ai/testing/JsonFileLlmCallRecorder.java) wraps the real call and writes its normalized request and response before helper validation.
2. **Load:** copy the JSON files into the playback server's directory before startup. [MockLLMConfiguration](ai/src/main/java/org/conductoross/conductor/ai/providers/mock/MockLLMConfiguration.java) creates the provider when playback is enabled. It scans regular `*.json` files with `Files.newDirectoryStream` and deserializes each with the registered Spring `ObjectMapper`.
3. **Play back:** set the task's `llmProvider` to `mockLLM`, keeping its original `model` for the recorded history policy. [MockLLM](ai/src/main/java/org/conductoross/conductor/ai/providers/mock/MockLLM.java) looks up the normalized request and rebuilds a normal response with fresh tool-call IDs. It needs no real-provider credentials and has no live fallback.

[LlmRequestResponseConverter](ai/src/main/java/org/conductoross/conductor/ai/testing/LlmRequestResponseConverter.java) handles messages, tools, JSON-output constraints, and tool-ID normalization. [LlmJsonFiles](ai/src/main/java/org/conductoross/conductor/ai/testing/LlmJsonFiles.java) atomically writes files using that same mapper. [LlmSavedResponses](ai/src/main/java/org/conductoross/conductor/ai/testing/LlmSavedResponses.java) is the saved data structure; its constructors enforce the record invariants. File parsing uses the application mapper settings, without a separate JSON-schema pass or recording-specific size limit.

Matching includes the full normalized history and tool definitions. Requests may repeat or arrive in parallel or any order; there is no sequence cursor or lock around provider calls. Missing requests fail. Conflicting saved answers for the same request fail playback startup.

With both flags enabled, real calls are recorded and playback calls are excluded. Provider exceptions have no response to save; file-write failures surface to the caller. This format supports chat text and tool responses, not images, embeddings, media, or provider-native tools.
