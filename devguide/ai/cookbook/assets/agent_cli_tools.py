"""Agent with CLI tools — a sandboxed shell, restricted to an allowlist.

Derived from the cli_commands support in sdk/python-sdk/src/conductor/ai/agents.

cli_commands=True attaches a run_command tool. cli_allowed_commands is the
allowlist: anything outside it is refused before execution. Shell mode is off,
so the model cannot chain commands with pipes or semicolons.
"""

from conductor.ai.agents import Agent, AgentRuntime

MODEL = "openai/gpt-4o-mini"

agent = Agent(
    name="repo_inspector",
    model=MODEL,
    instructions=(
        "You inspect a checked-out repository using shell commands. "
        "Use run_command for every fact you report. Never guess."
    ),
    cli_commands=True,
    cli_allowed_commands=["git", "ls", "wc", "cat"],
)


if __name__ == "__main__":
    with AgentRuntime() as runtime:
        result = runtime.run(agent, "How many files are in the current directory?")
        result.print_result()
        print("execution id:", result.execution_id)
