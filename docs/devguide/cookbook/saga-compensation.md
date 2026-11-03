---
description: "Conductor cookbook — saga pattern recipe: compensating a partially completed distributed transaction with failureWorkflow, reading the failed execution to undo only the steps that ran, in reverse order, idempotently."
---

# Saga: compensating a partial failure

Three services, one order. Inventory is reserved, the card is charged, and then the carrier returns 503. Two of the three steps already happened, and there is no transaction to roll back — each service owns its own data.

This recipe undoes exactly the work that completed, in reverse order, and nothing else.

## The shape

```text
reserve_inventory ──> charge_payment ──> book_shipment
                                              │ fails
                                              ▼
                                     failureWorkflow starts
                                              │
                     read the failed execution ──> refund_payment ──> release_inventory
```

The main workflow does not contain its own rollback branches. It declares a `failureWorkflow`, and Conductor starts that workflow when the main one fails after exhausting retries.

## Why compensation has to read the failed execution

The naive compensation workflow undoes every step. That is wrong: if `reserve_inventory` failed, there is no reservation to release and no charge to refund, and blindly calling refund produces a support ticket.

Conductor hands the failure workflow five inputs, and the last one is what makes this tractable:

| Input | What it gives you |
|---|---|
| `reason` | Why the workflow failed |
| `workflowId` | The failed execution's id |
| `failureStatus` | Its terminal status |
| `failureTaskId` | The id of the task that failed |
| `failedWorkflow` | **The entire failed execution**, including every task and its output |

So compensation starts by asking the execution what actually happened:

```json
{
  "name": "determine_what_completed",
  "taskReferenceName": "completed_steps",
  "type": "JSON_JQ_TRANSFORM",
  "inputParameters": {
    "failed": "${workflow.input.failedWorkflow}",
    "queryExpression": "((.failed.tasks // []) | map(select(.status == \"COMPLETED\")) | map(.referenceTaskName)) as $done | {done: $done, undoPayment: ($done | index(\"charge_payment\") != null), undoInventory: ($done | index(\"reserve_inventory\") != null)}"
  }
}
```

Each undo is then behind a `SWITCH` on that answer. Nothing gets undone that never happened.

## Prerequisites

A running Conductor server. The recipe calls three HTTP endpoints; a stub is included so you can run it without wiring real services.

Save this as `saga_stub_service.py` and leave it running:

```python
--8<-- "docs/devguide/cookbook/assets/saga_stub_service.py"
```

```bash
python3 saga_stub_service.py      # http://localhost:8088
```

It records every call at `GET /calls`, which is how you prove what the saga did.

## The main workflow

Save this as `saga-order-fulfillment.json`:

```json
--8<-- "docs/devguide/cookbook/assets/saga-order-fulfillment.json"
```

## The compensation workflow

Save this as `saga-order-compensation.json`:

```json
--8<-- "docs/devguide/cookbook/assets/saga-order-compensation.json"
```

## Register and run

```bash
conductor workflow create saga-order-compensation.json
conductor workflow create saga-order-fulfillment.json
```

Happy path — the carrier accepts the shipment:

```bash
conductor workflow start -w saga_order_fulfillment --sync \
  -i '{"orderId":"ORD-1","amount":49.00,"shipmentStatus":"200"}'
```

Failure path — the carrier is down, after the card has already been charged:

```bash
conductor workflow start -w saga_order_fulfillment \
  -i '{"orderId":"ORD-2","amount":49.00,"shipmentStatus":"503"}'
```

Open **[Executions](http://localhost:8080/executions)** in the Conductor UI and select the new execution to review the task graph, and each task's inputs and outputs.

The failed workflow's output carries `conductor.failure_workflow` — the id of the compensation run. Open it and you will see:

```text
completed_steps      JSON_JQ_TRANSFORM   COMPLETED
route_refund         SWITCH              COMPLETED
refund_payment       HTTP                COMPLETED
route_release        SWITCH              COMPLETED
release_inventory    HTTP                COMPLETED
```

with output:

```json
{
  "stepsCompleted": ["reserve_inventory", "charge_payment"],
  "paymentRefunded": true,
  "inventoryReleased": true,
  "compensatedOrder": "ORD-2"
}
```

Ask the stub what it actually received:

```bash
curl -s http://localhost:8088/calls
```

```text
1. /inventory/reserve   key=ORD-2-reserve
2. /payments/charge     key=ORD-2-charge
3. /shipping/book       key=ORD-2-ship
4. /shipping/book       key=ORD-2-ship
5. /shipping/book       key=ORD-2-ship
6. /shipping/book       key=ORD-2-ship
7. /payments/refund     key=ORD-2-refund
8. /inventory/release   key=ORD-2-release
```

Two things are worth staring at. The undo calls arrive **in reverse order** — refund before release. And `/shipping/book` was attempted **four times** before the workflow gave up, which is the whole argument for the next section.

## Production notes

- **Every write needs an idempotency key.** A failing endpoint gets called repeatedly by task retries. The stub replays the stored answer for a repeated `Idempotency-Key` instead of doing the work twice; your services must do the same.
- **Compensation must be idempotent too.** The failure workflow can itself be retried. `refund_payment` carries `ORD-2-refund` so a second attempt is a no-op, not a second refund.
- **Undo only what completed.** Drive each undo from the failed execution's task statuses, never from the assumption that everything ran.
- **Give compensation more retries than the forward path.** Here the forward shipment call retries once; refund and release retry five times with backoff. Failing to undo is worse than failing to do.
- **Compensation is not rollback.** A refund is a new transaction with its own ledger entry. Design for "eventually consistent and explainable", not "as if it never happened".
- **Alert when compensation fails.** A saga that cannot undo needs a human. Give the compensation workflow its own `failureWorkflow` or a status listener.
- **Keep the order id out of generated state.** Both workflows derive keys from `orderId` supplied by the caller, so a restart produces the same keys.

## Related

- [Handling workflow errors](../how-tos/Workflows/handling-errors.md) — retry strategies, timeout policies, and status listeners
- [Task timeouts and retries](task-timeouts-and-retries.md) — tuning the forward path
- [Microservice orchestration](microservice-orchestration.md) — the HTTP chain this builds on
