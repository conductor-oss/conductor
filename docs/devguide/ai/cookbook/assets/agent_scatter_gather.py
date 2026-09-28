"""Scatter-gather — one coordinator fans out to 100 parallel sub-agents.

Derived from sdk/python-sdk/examples/agents/58_scatter_gather.py.

scatter_gather() builds a coordinator that decomposes the request, dispatches
the worker agent N times through FORK_JOIN_DYNAMIC, and synthesizes the results.
N is decided by the model at runtime. Every sub-task is its own durable
sub-workflow, so one flaky worker retries on its own and the coordinator still
synthesizes partial results.
"""

from conductor.ai.agents import Agent, AgentRuntime, scatter_gather, tool

MODEL = "openai/gpt-4o-mini"
SYNTHESIS_MODEL = "openai/gpt-4o"  # larger context, it sees all 100 results


@tool
def search_knowledge_base(query: str) -> dict:
    """Look up a topic. Replace with a real search or vector-DB call."""
    return {
        "query": query,
        "results": [
            f"{query}: mid-sized economy with a services-led profile",
            f"{query}: population growth close to the regional average",
        ],
    }


researcher = Agent(
    name="country_researcher",
    model=MODEL,
    instructions=(
        "You profile one country. Call search_knowledge_base exactly once, then "
        "write 2-3 sentences covering economy, population and one distinctive fact. "
        "Do not call the tool more than once."
    ),
    tools=[search_knowledge_base],
    max_turns=5,
)

COUNTRIES = ['Afghanistan', 'Albania', 'Algeria', 'Andorra', 'Angola', 'Argentina', 'Armenia', 'Australia', 'Austria', 'Azerbaijan', 'Bahamas', 'Bahrain', 'Bangladesh', 'Barbados', 'Belarus', 'Belgium', 'Belize', 'Benin', 'Bhutan', 'Bolivia', 'Bosnia and Herzegovina', 'Botswana', 'Brazil', 'Brunei', 'Bulgaria', 'Burkina Faso', 'Burundi', 'Cambodia', 'Cameroon', 'Canada', 'Chad', 'Chile', 'China', 'Colombia', 'Congo', 'Costa Rica', 'Croatia', 'Cuba', 'Cyprus', 'Czech Republic', 'Denmark', 'Djibouti', 'Dominican Republic', 'Ecuador', 'Egypt', 'El Salvador', 'Estonia', 'Ethiopia', 'Fiji', 'Finland', 'France', 'Gabon', 'Georgia', 'Germany', 'Ghana', 'Greece', 'Guatemala', 'Guinea', 'Haiti', 'Honduras', 'Hungary', 'Iceland', 'India', 'Indonesia', 'Iran', 'Iraq', 'Ireland', 'Israel', 'Italy', 'Jamaica', 'Japan', 'Jordan', 'Kazakhstan', 'Kenya', 'Kuwait', 'Laos', 'Latvia', 'Lebanon', 'Libya', 'Lithuania', 'Luxembourg', 'Madagascar', 'Malaysia', 'Mali', 'Malta', 'Mexico', 'Mongolia', 'Morocco', 'Mozambique', 'Myanmar', 'Nepal', 'Netherlands', 'New Zealand', 'Nigeria', 'North Korea', 'Norway', 'Oman', 'Pakistan', 'Panama', 'Paraguay']

country_list = "\n".join(f"{i + 1}. {c}" for i, c in enumerate(COUNTRIES))

coordinator = scatter_gather(
    name="country_coordinator",
    worker=researcher,
    model=SYNTHESIS_MODEL,
    instructions=(
        f"Create EXACTLY {len(COUNTRIES)} country_researcher calls, one per country "
        f"below, passing just the country name. Issue ALL calls in a SINGLE response.\n\n"
        f"Countries:\n{country_list}\n\n"
        f"When all {len(COUNTRIES)} results are back, compile a short report grouped "
        f"by region."
    ),
    retry_count=3,
    retry_delay_seconds=5,
    timeout_seconds=900,
)


if __name__ == "__main__":
    with AgentRuntime() as runtime:
        result = runtime.run(
            coordinator,
            f"Profile all {len(COUNTRIES)} countries in the list.",
        )
        result.print_result()
        print("execution id:", result.execution_id)
