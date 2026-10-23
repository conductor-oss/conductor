"""Agent guardrails — a regex rule on the server plus a Python check, both retrying.

Derived from sdk/python-sdk/examples/agents/36_simple_agent_guardrails.py.

RegexGuardrail compiles to a Conductor INLINE task and runs on the server, so it
costs nothing and needs no worker. A @guardrail function compiles to a worker
task. Both sit inside the same durable retry loop: on failure the message is fed
back to the model and the answer is regenerated, up to max_retries.
"""

from conductor.ai.agents import (
    Agent,
    AgentRuntime,
    Guardrail,
    GuardrailResult,
    OnFail,
    RegexGuardrail,
    guardrail,
)

MODEL = "openai/gpt-4o-mini"


# Runs on the server as an INLINE task — no Python process involved.
no_bullet_lists = RegexGuardrail(
    patterns=[r"^\s*[-*]\s", r"^\s*\d+\.\s"],
    mode="block",
    name="no_lists",
    message="Do not use bullet points or numbered lists. Write flowing prose instead.",
    on_fail=OnFail.RETRY,
    max_retries=3,
)


# Runs as a Conductor worker task.
@guardrail
def min_length(content: str) -> GuardrailResult:
    """Require at least 50 words."""
    words = len(content.split())
    if words < 50:
        return GuardrailResult(
            passed=False,
            message=f"Only {words} words. Give a fuller answer of at least 50 words.",
        )
    return GuardrailResult(passed=True)


agent = Agent(
    name="guarded_essay_writer",
    model=MODEL,
    instructions=(
        "Answer the question in well-structured prose paragraphs. "
        "Never use bullet points or numbered lists."
    ),
    guardrails=[
        no_bullet_lists,
        Guardrail(min_length, on_fail=OnFail.RETRY),
    ],
)


if __name__ == "__main__":
    with AgentRuntime() as runtime:
        result = runtime.run(agent, "Explain why the sky is blue.")
        result.print_result()
        print("execution id:", result.execution_id)
