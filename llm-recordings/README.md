# Shared SDK playback recordings

These 19 example folders contain 93 recordings shared by every SDK. Each file captures a normalized model request and its recorded response. Numbered filenames start at `1` within each folder and show save order; playback matches request content rather than file order.

Configure the server's `conductor.ai.recordings-directory` to this directory and enable `conductor.ai.enable-llm-mocks=true`. The mock provider loads JSON recordings recursively. SDK examples select `mock/mockLLM` and use the same prompts, tool definitions, and tool results as these recordings.

After the SDK runs its examples, use the [shared playback action](../.github/actions/check-playback/README.md) to verify that all these recordings were used. The action supplies its own file inventory, so SDK repositories do not need a separate example list or execution-ID manifest.
