---
description: The nine ways Conductor orchestrates sub-agents, when to reach for each, and the runnable example in every SDK.
---

# Multi-Agent Architecture

A multi-agent system is one parent agent with a list of sub-agents and a **strategy** that decides how they run. The strategy is a single field. Everything else — durability, retries, visibility of each delegation — comes from Conductor compiling the whole thing into a workflow.

```python
support = Agent(
    name="support_supervisor",
    model="openai/gpt-4o-mini",
    instructions="Route each request to the right specialist.",
    agents=[billing, technical, sales],
    strategy=Strategy.HANDOFF,
)
```

## Choosing a strategy

The dividing question is **who decides**: the model, the graph, or you.

| Strategy | Who decides | Runs | Reach for it when |
|---|---|---|---|
| `handoff` | Model | One sub-agent, conversationally | A specialist should take over the conversation |
| `router` | Model | One sub-agent, no conversation | You just need classification and dispatch |
| `sequential` | Graph | All, in order | Each step builds on the previous output |
| `parallel` | Graph | All, at once | Independent opinions you want to compare |
| `swarm` | Sub-agents | Until one finishes | Agents should pass control between themselves |
| `round_robin` | Graph | Next in rotation | Spreading load or alternating reviewers |
| `random` | Graph | One at random | A/B comparison between agent versions |
| `plan_execute` | Model, then graph | A planned sequence, replanned as it goes | The steps aren't knowable up front |
| `manual` | You, in code | Whatever you select | Routing is a business rule, not a judgement call |

Two practical notes. **`router` is cheaper than `handoff`** — it classifies and dispatches without handing over the conversation, so use it when there's nothing to converse about. And **`plan_execute` is the only strategy that replans**; the others commit to their dispatch decision.

## The shapes

=== "Model picks one"

    `handoff` and `router`. Sub-agents are exposed to the parent's model as callable tools.

    ```python
    support = Agent(
        name="support",
        model=MODEL,
        instructions="Route to billing, technical, or sales.",
        agents=[billing, technical, sales],
        strategy=Strategy.HANDOFF,   # or Strategy.ROUTER
    )
    ```

=== "Graph runs them all"

    `sequential` and `parallel`. The model isn't consulted about ordering.

    ```python
    pipeline = Agent(
        name="review_pipeline",
        model=MODEL,
        agents=[researcher, writer, editor],
        strategy=Strategy.SEQUENTIAL,   # or Strategy.PARALLEL
    )
    ```

=== "Agents hand off to each other"

    `swarm`. Control passes between sub-agents until one produces a final answer.

    ```python
    swarm = Agent(
        name="triage_swarm",
        model=MODEL,
        agents=[intake, diagnosis, resolution],
        strategy=Strategy.SWARM,
    )
    ```

=== "Plan, execute, replan"

    `plan_execute`. The model produces a plan of sub-agent calls, runs it, and revises when results come back.

    ```python
    planner = Agent(
        name="incident_planner",
        model=MODEL,
        agents=[log_reader, metrics_reader, remediation_drafter],
        strategy=Strategy.PLAN_EXECUTE,
    )
    ```

## What Conductor adds

- **Each delegation is its own execution.** A specialist can retry without re-running the routing decision.
- **The choice is recorded.** Which sub-agent ran, and why, is in the execution — not just in a log line.
- **Sub-agents keep their own tools and guardrails,** so a billing agent can't reach fulfilment tools.
- **Parallel means actually parallel.** `parallel` and fan-out compile to `FORK_JOIN`, not a loop.

## Runnable examples in every SDK

Every strategy below is verified against `main` in all four SDKs.

| Strategy | Python | Java | TypeScript | C# |
|---|---|---|---|---|
| `handoff` | [`05_handoffs.py`](https://github.com/conductor-oss/python-sdk/blob/main/examples/agents/05_handoffs.py) | [`Example05Handoffs.java`](https://github.com/conductor-oss/java-sdk/blob/main/agent-examples/src/main/java/org/conductoross/conductor/ai/examples/Example05Handoffs.java) | [`05-handoffs.ts`](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/agents/05-handoffs.ts) | [`05_Handoffs`](https://github.com/conductor-oss/csharp-sdk/blob/main/Conductor.AI.Examples/05_Handoffs/Program.cs) |
| `router` | [`08_router_agent.py`](https://github.com/conductor-oss/python-sdk/blob/main/examples/agents/08_router_agent.py) | [`Example08RouterAgent.java`](https://github.com/conductor-oss/java-sdk/blob/main/agent-examples/src/main/java/org/conductoross/conductor/ai/examples/Example08RouterAgent.java) | [`08-router-agent.ts`](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/agents/08-router-agent.ts) | [`08_RouterAgent`](https://github.com/conductor-oss/csharp-sdk/blob/main/Conductor.AI.Examples/08_RouterAgent/Program.cs) |
| `sequential` | [`06_sequential_pipeline.py`](https://github.com/conductor-oss/python-sdk/blob/main/examples/agents/06_sequential_pipeline.py) | [`Example06SequentialPipeline.java`](https://github.com/conductor-oss/java-sdk/blob/main/agent-examples/src/main/java/org/conductoross/conductor/ai/examples/Example06SequentialPipeline.java) | [`06-sequential-pipeline.ts`](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/agents/06-sequential-pipeline.ts) | [`06_SequentialPipeline`](https://github.com/conductor-oss/csharp-sdk/blob/main/Conductor.AI.Examples/06_SequentialPipeline/Program.cs) |
| `parallel` | [`07_parallel_agents.py`](https://github.com/conductor-oss/python-sdk/blob/main/examples/agents/07_parallel_agents.py) | [`Example07ParallelAgents.java`](https://github.com/conductor-oss/java-sdk/blob/main/agent-examples/src/main/java/org/conductoross/conductor/ai/examples/Example07ParallelAgents.java) | [`07-parallel-agents.ts`](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/agents/07-parallel-agents.ts) | [`07_ParallelAgents`](https://github.com/conductor-oss/csharp-sdk/blob/main/Conductor.AI.Examples/07_ParallelAgents/Program.cs) |
| `swarm` | [`17_swarm_orchestration.py`](https://github.com/conductor-oss/python-sdk/blob/main/examples/agents/17_swarm_orchestration.py) | [`Example17SwarmOrchestration.java`](https://github.com/conductor-oss/java-sdk/blob/main/agent-examples/src/main/java/org/conductoross/conductor/ai/examples/Example17SwarmOrchestration.java) | [`17-swarm-orchestration.ts`](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/agents/17-swarm-orchestration.ts) | [`17_SwarmOrchestration`](https://github.com/conductor-oss/csharp-sdk/blob/main/Conductor.AI.Examples/17_SwarmOrchestration/Program.cs) |
| `random` | [`16_random_strategy.py`](https://github.com/conductor-oss/python-sdk/blob/main/examples/agents/16_random_strategy.py) | [`Example16RandomStrategy.java`](https://github.com/conductor-oss/java-sdk/blob/main/agent-examples/src/main/java/org/conductoross/conductor/ai/examples/Example16RandomStrategy.java) | [`16-random-strategy.ts`](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/agents/16-random-strategy.ts) | [`16_RandomStrategy`](https://github.com/conductor-oss/csharp-sdk/blob/main/Conductor.AI.Examples/16_RandomStrategy/Program.cs) |
| `manual` | [`18_manual_selection.py`](https://github.com/conductor-oss/python-sdk/blob/main/examples/agents/18_manual_selection.py) | [`Example18ManualSelection.java`](https://github.com/conductor-oss/java-sdk/blob/main/agent-examples/src/main/java/org/conductoross/conductor/ai/examples/Example18ManualSelection.java) | [`18-manual-selection.ts`](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/agents/18-manual-selection.ts) | [`18_ManualSelection`](https://github.com/conductor-oss/csharp-sdk/blob/main/Conductor.AI.Examples/18_ManualSelection/Program.cs) |
| `plan_execute` | [`108_plan_execute_refs.py`](https://github.com/conductor-oss/python-sdk/blob/main/examples/agents/108_plan_execute_refs.py) | [`Example108PlanExecuteRefs.java`](https://github.com/conductor-oss/java-sdk/blob/main/agent-examples/src/main/java/org/conductoross/conductor/ai/examples/Example108PlanExecuteRefs.java) | [`108-plan-execute-refs.ts`](https://github.com/conductor-oss/javascript-sdk/blob/main/examples/agents/108-plan-execute-refs.ts) | [`108_PlanExecuteRefs`](https://github.com/conductor-oss/csharp-sdk/blob/main/Conductor.AI.Examples/108_PlanExecuteRefs/Program.cs) |

`round_robin` has no dedicated example yet; it takes the same shape as `random`, swapping the strategy value.

## Next steps

- [Multi-agent handoff recipe](cookbook/agent-handoff.md) — a runnable supervisor with three specialists
- [Massively parallel agents](cookbook/agent-scatter-gather.md) — fan out to 100 sub-agents
- [Agent Configuration](agent-configuration.md) — what else you can set on an agent
