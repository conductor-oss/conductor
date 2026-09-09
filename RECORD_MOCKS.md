# Record mocks with the Python SDK

Use `gpt-4o-mini` for the real run. The server saves the LLM responses automatically.

<!-- TODO: verify against live server -->

## 1. Enable recording on the server

Add these settings to the configuration your server loads:

```properties
conductor.integrations.ai.enabled=true
conductor.ai.record-mode=true
conductor.ai.enable-llm-mocks=false
conductor.ai.recordings-directory=/home/nicholascole/IdeaProjects/conductor/llm-recordings/my-agent-run-1
```

Use a new directory for each recording session. The server creates it for you.

Make sure `OPENAI_API_KEY` is set in the server's environment, then restart the server.

## 2. Point Python at the server

In the terminal where you run your Python agent:

```bash
export CONDUCTOR_SERVER_URL=http://localhost:8080/api
```

Change the address if your server runs elsewhere.

## 3. Run your agent

Set your agent's model to:

```python
model="openai/gpt-4o-mini",
```

Run your existing Python script normally. Keep its tool workers running and let the agent finish.

Recording makes real OpenAI calls. No special recording code is needed in Python.

## 4. Check the recordings

On the server machine, run:

```bash
ls -lh /home/nicholascole/IdeaProjects/conductor/llm-recordings/my-agent-run-1/*.json
```

Each returned LLM response creates one JSON file. Keep all the files from your run.
Avoid unrelated agent runs while recording, because their LLM responses are saved too.

## 5. Replay later (optional)

Keep the same directory and change these server settings:

```properties
conductor.ai.record-mode=false
conductor.ai.enable-llm-mocks=true
```

Restart the server. In Python, change the agent's model to:

```python
model="mockLLM/gpt-4o-mini",
```

Run the same agent with the same prompt, instructions, tools, and starting conversation history.
For multiple agents, change every model you want to replay.

Tool workers still run. Their outputs must match the recorded run, including any dates or random values.
Missing matches fail instead of calling OpenAI. Conflicting answers for the same request prevent playback startup.
