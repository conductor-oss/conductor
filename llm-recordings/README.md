# Shared SDK playback recordings

Each example folder holds the recordings for one shared SDK example. Each file captures a normalized model request and its recorded response. Numbered filenames show save order within a folder; playback matches request content rather than file order.

Configure the server's `conductor.ai.recordings-directory` to this directory and enable `conductor.ai.enable-llm-mocks=true`. The mock provider loads JSON recordings recursively. SDK examples select `mock/mockLLM` and use the same prompts, tool definitions, and tool results as these recordings. A request with no matching recording fails its LLM task with a non-retryable error instead of calling a real provider.

After the SDK runs its examples, use the [shared playback action](../.github/actions/check-playback/README.md) to confirm through the standard workflow API that every workflow completed.
