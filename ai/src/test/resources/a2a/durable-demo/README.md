# Durable A2A — the money shot

**Kill Conductor mid-order. Restart it. The order finishes anyway.**

This demo shows the one thing an in-memory A2A host (the reference `a2a-samples` concierge, ADK,
CrewAI, LangGraph host loops) *cannot* do: survive a crash mid-orchestration. A Conductor workflow
that calls a remote A2A agent is a **durable** unit of work — its state is persisted, so a server
crash and restart resumes the in-flight agent interaction to completion.

## What runs

```
  ┌─────────────────────────────┐         A2A (JSON-RPC)        ┌──────────────────────────┐
  │ Conductor                   │   message/send → tasks/get    │ Restaurant agent         │
  │   durable_purchase workflow │ ────────────────────────────▶ │ (seller_agent.py)        │
  │     └─ AGENT  ──────────┘     (poll while "preparing")  │  separate process,       │
  │   state in SQLite (durable) │ ◀──────────────────────────── │  survives the restart    │
  └─────────────────────────────┘          receipt              └──────────────────────────┘
            ▲  💥 kill -9  +  restart (same SQLite store)
```

- **Concierge** = the `durable_purchase` Conductor workflow (`durable_purchase.json`): one
  `AGENT` task that places the order with the remote agent and polls until it's ready.
- **Seller** = `seller_agent.py`, a dependency-free A2A agent whose order stays *in preparation*
  for `SELLER_DELAY` seconds, giving us a window to crash Conductor. It's a separate process, so it
  keeps running across the Conductor restart.
- **Durable store** = SQLite (Conductor's zero-dependency standalone mode). The workflow + the
  `AGENT` task (with the remote agent's task id) are persisted here; on restart the engine
  reloads them and the system-task worker resumes polling.

## Run it

Requires **Java 21+**, **python3**, **curl** — no Docker, no Redis, no API keys.

```bash
./run-durable-demo.sh
```

It builds the server-lite jar (this branch), starts the seller + Conductor, places an order,
waits until it's in preparation, **`kill -9`s the server**, restarts it on the same SQLite store,
and waits for the order to complete.

Tunables: `SELLER_DELAY` (seconds the order stays in preparation; default 45).

## Expected output

```
▶ Placing an order (the concierge calls the restaurant agent via AGENT)
✓ Order started — workflowId=270df5c6-…
▶ Waiting for the order to be in preparation (workflow RUNNING, task IN_PROGRESS)
✓ Order in preparation. Status=RUNNING
▶ 💥 Killing the Conductor server MID-ORDER (kill -9)
✓ Conductor is DOWN. The order is in flight; an in-memory host would have just lost it.
▶ Restarting Conductor on the SAME persistent store
✓ Conductor is up on :7001
▶ Waiting for the order to COMPLETE after the restart
  workflow status : COMPLETED
  receipt         : Order ORD-BEA3FB24 confirmed: 1 large pepperoni pizza. Enjoy!
✓ ORDER SURVIVED THE CRASH AND COMPLETED. That is durable A2A.
```

## Why it matters

The A2A protocol is stateless request/response; **durability is a property of the orchestrator,
not the protocol**. Run the same concierge pattern on an in-memory host and the crash loses the
order — there is no resume. On Conductor, the agent interaction is a persisted, resumable task.
That is the "durable A2A" claim, demonstrated (see `design/a2a/09-durable-a2a.md`).

## Notes

- The concierge calls the seller over loopback, so the server runs with
  `conductor.a2a.client.allow-private-network=true` (the SSRF guard blocks private/loopback agent
  URLs by default). Use it only for agents on a trusted network.
- This demo doubles as the **full-process crash/restart proof** for A2A durability — a real
  `kill -9` of a persistent server with `AGENT` resuming — complementing the in-process
  proofs in `A2ADurabilityTest`.
- `seller_agent.py` here is intentionally dependency-free (stdlib). For an agent built on the
  official `a2a-sdk`, see `../echo_agent.py` and `../README.md`.
