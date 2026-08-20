# The Workflow Inside Every Agent Harness

### Production AI begins when the agent loop becomes an execution model

An AI agent harness is the control layer that turns a language model into an agent capable of carrying out work over multiple steps. It assembles context for each model call, makes tools available, routes tool calls, records results, updates state, applies limits or policies and decides whether the loop should continue, stop or wait for outside input.

The model contributes judgment. The harness carries it through a stateful execution loop without granting the model unconditional authority.

“Harness” has no single industry-standard boundary. Some implementations include the sandbox, session store and model adapters. Others separate those systems from the loop itself. In this article, the harness is the control layer around the model: the machinery that coordinates a multi-step agent run.

By “workflow,” I do not mean a static DAG drawn in advance. I mean a stateful execution graph: nodes are units of work, edges are allowed transitions and execution state determines what may run next. The graph may be declared ahead of time, expanded at runtime and contain branches, loops, waits and nested executions.

Microsoft describes an [agent harness](https://learn.microsoft.com/en-us/agent-framework/concepts/harness) as the runtime around a model that manages its loop, tools, context and policies. OpenAI calls its Agents SDK [a harness for the agent loop](https://openai.com/index/the-next-evolution-of-the-agents-sdk/). Anthropic separates the [harness that calls the model and routes tool calls](https://www.anthropic.com/engineering/managed-agents) from the session and the environment in which work runs.

Cloudflare gives the harness responsibility for [prompt construction, memory, tool selection and continuation](https://developers.cloudflare.com/agents/harnesses/), while treating durable runtime infrastructure as a separate layer beneath it. The boundaries differ. The control structure does not.

Receive an objective. Assemble context. Call a model. Interpret the response. Select and invoke a tool. Record the observation. Decide whether to continue. Repeat until the work completes, stops or needs outside input.

Take away the model-specific vocabulary and the control structure becomes familiar.

At its control-flow core, it is a workflow.

Not every part of a harness is orchestration. But the harness encodes a workflow and advances an execution through it: deciding what runs, in which order, with which state and what happens next. A model may influence the next transition, but there is still control flow, task execution, failure handling and a terminal condition. The intelligence may be new. The execution problem is not.

Every agent harness contains a workflow. Production requires the execution semantics of that workflow to become explicit.

The model call is not the unit of production AI. The execution is.

## A probabilistic node changes the workflow, not the category

“Workflow” has accumulated unhelpful baggage. It often brings to mind a static diagram assembled in advance: a fixed sequence of boxes connected by deterministic arrows. An agent feels different because it can decide what to do next at runtime.

But runtime choice does not eliminate the workflow. It changes how the next edge is selected.

When the workflow is made explicit, familiar elements of an agent harness map cleanly onto execution concepts. A model turn becomes a task. A tool invocation becomes another unit of work. Its observation becomes task output. Tool selection becomes dynamic routing. Plan-act-observe becomes a loop. Parallel delegation can be expressed as a fork and join. Delegating to another agent can become a child execution. Guardrails become validation or policy transitions. Human interruption becomes a wait state that production software must persist. Session context is execution-scoped state; long-term memory is data the workflow reads and writes. A stopping rule becomes a terminal transition.

This is not the empty observation that any program can be drawn as a graph. A harness exists primarily to coordinate work across model, tool, process and human boundaries. Sequencing, routing, carrying state, waiting and handling failure are not incidental implementation details. They are its job.

None of this makes agent frameworks unnecessary. A harness may also provide model adapters, prompt construction, context compaction, tool registries, sandbox integration and framework-specific behavior. Those are real responsibilities. But the part that decides what runs, in which order, with which state and what happens next is workflow orchestration—whether or not the framework calls it that.

Many harnesses encode this workflow imperatively inside an event loop. That is a sensible place to start. It keeps the developer close to the model and makes the first working agent easy to understand. Nor does every local agent loop need an external orchestrator. Short-lived, single-process work may remain ordinary code. The distinction starts to matter when an execution must survive restarts, wait, cross service boundaries, retain its version or be repaired without starting over.

That is when the hidden workflow becomes something a team must operate rather than merely run.

## The hidden workflow works until somebody has to operate it

An in-memory loop can appear complete while its production contract remains undefined.

What is running right now? Which steps have committed? What survives a restart? Which operation may be repeated safely? What is waiting for outside input? Which version of the agent began this execution? Can a deployment change the behavior of work already in flight? Where should execution resume after a timeout? What evidence explains the transition that occurred?

These are not questions about the model's intelligence. They are questions about the workflow hidden inside the harness.

When control flow lives only in application code and conversation state, answering them means reconstructing execution from logs. A trace may show that the model requested a tool. It does not automatically establish whether the tool completed, whether its result was persisted, whether the next turn saw it or whether a retry repeated an external consequence.

This is the point where observability alone stops being enough. Observability can tell you what the process emitted. Durable execution must know what state the process reached and which transition is allowed next.

Production does not simply need a transcript of model interactions. It needs an operational model of the work.

## A harness carries two different kinds of state

Agent engineering has focused heavily on context state: messages, working context, plans, tool observations and the information that should enter the next model call. This state determines what the model can consider on its next turn.

Production systems also require execution state: task lifecycle, attempt identity, approval state, version and the distinction between an operation that failed and one whose result is unknown. They also need policies for retries, timeouts, authorization and compensation.

Confusing these forms of state creates subtle failures. Restoring the conversation does not establish what happened in an external system. Replaying a tool call does not necessarily recover the process. A timeout is not proof that an operation failed. A successful invocation is not proof that its response reached the caller.

Durable execution therefore does not promise that every external side effect happens exactly once. If a worker completes an operation and crashes before reporting success, an at-least-once runtime may deliver that work again. Safety comes from stable operation identity, idempotency where the receiving system supports it and reconciliation when the outcome is ambiguous.

A production harness must preserve both kinds of state without pretending they are interchangeable. The agent needs enough context continuity to continue. The execution system needs enough durable state to continue safely.

## A loop becomes a graph long before we call it one

The canonical harness begins as a loop: model, tool, observation, model. Real systems rarely remain that small.

The agent gains several tools. Tool use requires policy checks. Some decisions require authority outside the model. Work is delegated to specialist agents. Independent checks run in parallel. One result invalidates another branch. A transition waits on a person or an external system. A failure requires compensation or reconciliation rather than another attempt.

The loop has become a graph.

The harness had topology even when that topology was buried in code. Graph engineering begins when the topology and the contracts on its edges become first-class.

This is not a fashionable replacement for drawing a workflow. It is the discipline of making the relationships inside an adaptive execution system carry the correct meaning.

Evidence is not truth. A proposal is not permission. Approval is not proof of execution. An acknowledgement is not confirmed external state. Independent verification is different from asking several agents with the same context to repeat the same judgment. A veto, an advisory result and a retry cannot be treated as equivalent edges simply because each arrives as a message.

Those distinctions determine what the system may do next. Evidence can enter a planning step. A proposal can move to a policy boundary. An authorized intent can move to execution. An execution result can move to independent verification. An ambiguous result can move to reconciliation instead of returning to execution by default.

Some nodes in this graph will contain sophisticated agent loops. Others should remain deliberately boring: validate a schema, enforce a limit, wait for authority, persist a transition, verify external state. The graph is valuable because it lets probabilistic and deterministic work coexist without pretending they have the same semantics.

Once several agents participate, the system becomes a graph of harnesses. Each agent may own its local reasoning loop. The wider execution graph must still own delegation, lifecycle, authority and recovery across those boundaries.

## Harness engineering is workflow engineering under new constraints

Calling a harness a workflow does not mean nothing has changed.

Models introduce a different kind of control node. Their output is probabilistic. Their behavior changes with context. Tool descriptions influence routing. A model upgrade can alter execution without a conventional code change. The same prompt can take a different path on the next run. Evaluation therefore becomes part of release engineering, and traces must preserve enough information to explain failure and variation.

The executable version of an agent is therefore larger than its code. It includes the model, prompt, tool schemas, policies, routing rules and loop limits that shaped the run. An execution already in flight should remain attached to the version it started with; otherwise a deployment can change the meaning of its next transition halfway through the work.

These are meaningful additions to workflow engineering. They are also the reason to build on durable execution rather than reconstruct it inside every agent framework.

Retries, timeouts, state transitions, waits, versioning, fan-out, child execution, compensation and audit history did not become obsolete when the next action started coming from a model. They became more important because the path is now less predictable.

That pressure grows as models improve. More capable agents attempt longer work, cross more system boundaries and operate with less step-by-step supervision. Each increase in agency enlarges the execution surface that must be bounded, observed and recovered. Better reasoning does not reduce the need for orchestration. It raises the stakes of getting orchestration right.

The useful distinction is no longer agents versus workflows. It is local reasoning versus end-to-end execution. Agent frameworks should be free to innovate on reasoning, context, memory and tool use. The execution layer should preserve the lifecycle of the work those agents create.

## Why this is a natural next chapter for Conductor

Conductor originated at Netflix because distributed work does not stay inside one process or follow the happy path for long. Services time out. Workers disappear. Executions wait on people or external systems. Software changes while work remains in flight. A retry may repair a process or repeat a consequence.

The response was to make state durable, transitions visible and recovery part of the execution model.

Agent harnesses arrive at the same problem from a different starting point. They begin close to the model and expand outward as the agent acquires tools, memory, policies, delegation and longer-running work. Eventually the harness needs the same properties that production workflows have always needed. It must survive failure, expose state, bound retries, preserve identity, wait durably and compose with work outside itself.

In Conductor today, [an SDK-authored agent is compiled into an ordinary workflow definition](https://github.com/conductor-oss/conductor/blob/main/docs/devguide/concepts/agents.md), deployed as a versioned agent and invoked by name through an `AGENT` task. Model turns and tool calls become recorded task executions. The parent retains the agent's execution identity, leaving both the local agent run and the process around it separately inspectable.

The graph can remain adaptive without becoming unbounded. Model output may select from approved work, after which deterministic validation can [allowlist, deduplicate and cap dynamic fan-out](https://docs.conductor-oss.org/devguide/ai/dynamic-workflows.html). The engine [persists the workflow snapshot and task transitions](https://docs.conductor-oss.org/architecture/durable-execution.html), then requeues work according to configured failure and timeout policy.

Those mechanics do not make a model deterministic or turn a remote side effect into an exactly-once operation. They give the harness an execution model honest enough to deal with those realities.

This is why we are investing in agentic orchestration. We are not trying to replace agent frameworks or attach a new name to an old workflow diagram. We believe the orchestration hidden inside agent harnesses should have production semantics from the start.

The framework can own the local reasoning loop. Conductor can make the resulting workflow durable, inspectable and composable with everything around it.

## The workflow was there all along

The industry is not moving beyond workflows. It is encountering them again from inside the agent loop.

Every harness already has tasks, state, transitions, branches, loops and termination. The production question is whether that workflow remains implicit or becomes explicit; whether its state is ephemeral or durable; whether its behavior must be reconstructed from logs or can be inspected as a running system.

Models made the next step less predictable. That did not make orchestration less necessary. It made the quality of the orchestration part of the system's effective intelligence.

AI did not abolish the workflow. It put a probabilistic decision-maker inside it. Production begins when we engineer the rest of the system accordingly.
