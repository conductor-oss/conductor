"""Multi-agent handoff — a supervisor delegates to the specialist that fits.

Derived from sdk/python-sdk/examples/agents/05_handoffs.py.

With Strategy.HANDOFF the sub-agents are exposed to the supervisor's model as
callable tools, and the model picks one. Each delegation is its own durable
Conductor execution, so a specialist can retry without re-running the router.
"""

from conductor.ai.agents import Agent, AgentRuntime, Strategy, tool

MODEL = "openai/gpt-4o-mini"


@tool
def check_balance(account_id: str) -> dict:
    """Check the balance of a bank account."""
    return {"account_id": account_id, "balance": 5432.10, "currency": "USD"}


@tool
def lookup_order(order_id: str) -> dict:
    """Look up the status of an order."""
    return {"order_id": order_id, "status": "shipped", "eta": "2 days"}


@tool
def get_pricing(product: str) -> dict:
    """Get pricing information for a product."""
    return {"product": product, "price": 99.99, "discount": "10% off"}


billing = Agent(
    name="billing",
    model=MODEL,
    instructions="You handle billing questions: balances, payments, invoices.",
    tools=[check_balance],
)

technical = Agent(
    name="technical",
    model=MODEL,
    instructions="You handle technical questions: order status, shipping, returns.",
    tools=[lookup_order],
)

sales = Agent(
    name="sales",
    model=MODEL,
    instructions="You handle sales questions: pricing, products, promotions.",
    tools=[get_pricing],
)

support = Agent(
    name="support_supervisor",
    model=MODEL,
    instructions="Route each request to the right specialist: billing, technical, or sales.",
    agents=[billing, technical, sales],
    strategy=Strategy.HANDOFF,
)


if __name__ == "__main__":
    with AgentRuntime() as runtime:
        result = runtime.run(support, "What's the balance on account ACC-123?")
        result.print_result()
        print("execution id:", result.execution_id)
