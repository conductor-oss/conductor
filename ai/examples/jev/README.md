# Jev agent example

An `@agent` using `DecisionModelTool`. The server calls Jev; no Python worker is
needed. Configure [Jev](../../DECISION_MODELS.md) and an orchestration chat model
on the server first.

Use Python 3.10+ and the Python SDK checkout containing `DecisionModelTool`
(PR [#511](https://github.com/conductor-oss/python-sdk/pull/511)); the published
2.0.0 package does not include it.

From this directory:

```bash
python3 -m venv .venv
source .venv/bin/activate
python3 -m pip install -e '/path/to/python-sdk[agents]'
export CONDUCTOR_SERVER_URL=http://localhost:8080/api
export CONDUCTOR_AGENT_LLM_MODEL=openai/gpt-4o-mini
python3 jev_agent.py plan
python3 jev_agent.py run
```

Choose a chat model configured on your server. `plan` compiles without inference;
`run` reads `request.json`. Use `--request` for another file or `--model` to override
the chat model. Edit the tool's fixed questions and decision model in `jev_agent.py`.

The chat model calls the tool and summarizes its result; both models incur
inference costs. The `DECISION_MODEL` task output contains the exact Jev answers,
model revision, usage, and latency.
