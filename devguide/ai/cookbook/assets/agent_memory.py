"""Agent with memory — recall facts across sessions by similarity.

Derived from sdk/python-sdk/examples/agents/25_semantic_memory.py.

SemanticMemory stores facts and returns the most relevant ones for a query, so
the agent is primed with what it needs instead of the whole history. Swap the
store for your own backend; the agent contract does not change.
"""

from conductor.ai.agents import Agent, AgentRuntime, tool
from conductor.ai.agents.semantic_memory import SemanticMemory

MODEL = "openai/gpt-4o-mini"

memory = SemanticMemory(max_results=3)
memory.add("The customer's name is Alice and she prefers email.")
memory.add("Alice has been on the Enterprise plan since March 2021.")
memory.add("Alice reported a billing discrepancy on invoice #1042.")
memory.add("Alice's preferred language is English.")
memory.add("Enterprise customers get priority support with a 1-hour SLA.")
memory.add("Alice's timezone is US/Pacific.")


@tool
def recall(query: str) -> str:
    """Recall relevant context about the customer."""
    return memory.get_context(query)


agent = Agent(
    name="memory_support_agent",
    model=MODEL,
    tools=[recall],
    instructions=(
        "You are a support agent with a memory. Call recall before answering, "
        "then personalise the reply with what you find."
    ),
)


if __name__ == "__main__":
    with AgentRuntime() as runtime:
        result = runtime.run(agent, "I have a question about my last invoice.")
        result.print_result()
        print("execution id:", result.execution_id)
