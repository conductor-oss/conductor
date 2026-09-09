**Saved responses** are a named collection of LLM requests and their recorded responses.

| Class | Plain meaning | What it does |
| --- | --- | --- |
| [LlmSavedResponses](ai/src/main/java/org/conductoross/conductor/ai/testing/LlmSavedResponses.java) | Saved responses | Holds a scenario name and request/response entries. Each request includes messages, tools, JSON-output mode, and output schema. |
| [LlmRecorderOrMock](ai/src/main/java/org/conductoross/conductor/ai/testing/LlmRecorderOrMock.java) | Recorder or mock | Wraps a Spring AI `ChatModel` and either records real calls or looks up saved responses. |
| [LlmRequestResponseConverter](ai/src/main/java/org/conductoross/conductor/ai/testing/LlmRequestResponseConverter.java) | Request/response converter | Converts Spring AI objects into the saved format. Replaces runtime tool-call IDs with logical references and rebuilds responses for replay. |
| [LlmJsonFiles](ai/src/main/java/org/conductoross/conductor/ai/testing/LlmJsonFiles.java) | JSON file reader/writer | Validates and loads recordings, or saves them as `<scenario>.json`. |

**Start with `LlmRecorderOrMock.invoke()`**—it contains the main behavior:

1. Normalize the incoming request, using a fresh converter for each call.
2. When recording, call the real model, save the normalized request/response pair, and return the original response.
3. When replaying, use `responses.get(request)` to find the saved answer. Rebuild a Spring AI response with fresh tool-call IDs. An unknown request throws an error.

The caller uses `recording(name).modelFor(input, realModel)` to create a recording wrapper. After calls finish, `savedResponses()` returns the saved data for writing to disk. For replay, load that data and use `replaying(savedResponses).modelFor(input, null)`.

Matching uses the full normalized request, including history and tool definitions. Provider name and model name are excluded. Requests can repeat or arrive in any order; there is no response cursor. Recording still calls the real model every time and rejects different responses for an identical request.
