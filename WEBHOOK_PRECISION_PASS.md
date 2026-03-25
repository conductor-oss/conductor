# WAIT_FOR_WEBHOOK Precision Pass — Session Notes

**Date**: 2026-03-25
**Branch**: `feat/wait-for-webhook-foundation`
**PR**: #894

---

## What this session did

This session was a systematic side-by-side comparison of the OSS `webhook-task` module against
the Orkes Enterprise implementation. Every class was read in both codebases and compared for
behavioral fidelity. Six real bugs/gaps were found and fixed across two commits.

---

## Files read from Orkes Enterprise

| File | Purpose |
|---|---|
| `webhooks/.../WebhookHashingService.java` | Hash computation — inbound event |
| `core/.../WebhookTaskService.java` | DAO interface for task registration |
| `core/.../WebhookDAO.java` | DAO interface for config + matchers |
| `redis-persistence/.../RedisWebhookTaskService.java` | Redis DAO impl — hash format source of truth |
| `postgres-persistence/.../PostgresWebhookTaskService.java` | Postgres DAO impl — confirms hash format |
| `postgres-persistence/.../PostgresWebhookDAO.java` | Postgres DAO impl — matcher key format |
| `webhooks/.../IncomingWebhookService.java` | Inbound event handling |
| `webhooks/.../WebhookWorker.java` | Async task dispatch (Orkes queue-based path) |
| `webhooks/.../Webhook.java` | System task (start/cancel) |
| `webhooks/.../WebhookConfigService.java` | Config CRUD |
| `webhooks/.../IncomingWebhookResource.java` | REST endpoint |
| `server/.../WebhooksConfigResource.java` | Config management REST endpoint |
| `webhooks/.../WebhookVerifier.java` | Verifier interface |
| `common/.../WebhookConfig.java` | Config model |
| `common/.../IncomingWebhookEvent.java` | Event model |

---

## Bugs and gaps found

### Fix 1 — Task output format (`putAll` vs `put("payload", ...)`)
**Commit**: `fix: align WAIT_FOR_WEBHOOK output format with Orkes Enterprise`

**What was wrong**: `IncomingWebhookService.completeTask()` was writing:
```java
result.getOutputData().put("payload", payload);
```
So task output was `{ "payload": { "event": { "type": "..." }, ... } }`.

**Orkes does**:
```java
taskResult.getOutputData().putAll(payload);
```
So task output is `{ "event": { "type": "..." }, ... }` — payload keys at the top level.

**Impact**: Workflow variable references like `${waitForPayment.output.event.type}` would not
work in OSS. Users would have to write `${waitForPayment.output.payload.event.type}` — a
convention that doesn't exist in Orkes and would break any workflow migrated from Orkes to OSS.

**Files changed**:
- `IncomingWebhookService.java` — `putAll(payload)`
- `WaitForWebhookTask.java` — `putAll(payload)` in `complete()`, changed parameter type to
  `Map<String, Object>` (was `Object`)
- `IncomingWebhookServiceTest.java` — assert on actual payload key not wrapper
- `WaitForWebhookTaskTest.java` — assert keys directly in outputData
- `README.md` — update task output section and example

---

### Fix 2 — Secret leak in `getWebhook(id)`
**Commit**: `fix: precision pass — four Orkes convergence gaps in webhook-task`

**What was wrong**: `WebhooksConfigResource.getWebhook()` returned the config object directly,
including plaintext `secretValue`. `getAllWebhooks()` already masked secrets via `shallowCopy()`,
but the single-get path was missed.

**Orkes does**: `webhookConfig.setSecretValue(SECRET)` before returning (mutates the fetched
object, safe because Postgres always returns a fresh deserialized instance).

**OSS fix**: `config.shallowCopy()` then `masked.setSecretValue("***")` — using a copy so the
stored in-memory instance is not mutated.

**Files changed**:
- `WebhooksConfigResource.java` — shallowCopy + mask before return
- `WebhooksConfigResourceTest.java` — new test asserting (a) secret is masked and (b) original
  stored config is NOT mutated

---

### Fix 3 — NPE on null matcher criteria
**Commit**: `fix: precision pass — four Orkes convergence gaps in webhook-task`

**What was wrong**: `IncomingWebhookService.dispatchToWaitingTasks()` passed `criteria`
directly to `computeInboundHash()` with no null check. If a matcher map entry has a null value
(e.g. from a misconfigured DAO), `matchCriteria.keySet()` in `computeInboundHash()` would NPE.

**Orkes does**: explicit null check with log + `continue`.

**Files changed**:
- `IncomingWebhookService.java` — added `if (criteria == null) { LOGGER.warn...; continue; }`
- `IncomingWebhookServiceTest.java` — new regression test for null criteria

---

### Fix 4 — Task start order: DAO put before `setStatus(IN_PROGRESS)`
**Commit**: `fix: precision pass — four Orkes convergence gaps in webhook-task`

**What was wrong**: `WaitForWebhookTask.start()` set `task.setStatus(IN_PROGRESS)` first, then
called `webhookTaskDAO.put()`. If the DAO call throws (e.g. Redis is down), the task is already
marked `IN_PROGRESS` but is not registered in the DAO — it will never be completable by a webhook
event, and no error is visible.

**Orkes does**: `webhookTaskService.put(taskModel, workflow.getWorkflowVersion())` first, then
`taskModel.setStatus(IN_PROGRESS)`. If put fails, the exception propagates and the task status
is unchanged, allowing the engine to retry.

**Files changed**:
- `WaitForWebhookTask.java` — swapped order; comment explaining why

---

### Fix 5 — `isAsync()` returns `true` (should be `false`)
**Commit**: `fix: precision pass — four Orkes convergence gaps in webhook-task`

**What was wrong**: `WaitForWebhookTask.isAsync()` returned `true`, causing the Conductor decider
to periodically call `execute()` on every WAIT_FOR_WEBHOOK task. `execute()` always returns
`false` (the task is only completable via an external webhook event), so this polling is pure
overhead.

**Orkes does**: `Webhook.java` does not override `isAsync()`, so it inherits `false` from
`WorkflowSystemTask`. The task is never polled by the decider.

**Files changed**:
- `WaitForWebhookTask.java` — `isAsync()` returns `false` with explanatory Javadoc
- `WaitForWebhookTaskTest.java` — `isAsync_returnsTrue` renamed to `isAsync_returnsFalse`

---

## What was verified as correct (no change needed)

| Concern | Orkes source | OSS | Result |
|---|---|---|---|
| Hash format | `workflowName;version;taskRef;val1;val2` | identical | ✅ |
| Hash delimiter | `";"` constant in `WebhookTaskService.Constants` | `DELIMITER = ";"` | ✅ |
| Sort order | `new TreeSet<>(matches.keySet())` | identical | ✅ |
| JSONArray: first element only | `valueArray.get(0).toString()` | identical | ✅ |
| Wildcard: `expectedValue.startsWith("$")` | identical | ✅ | ✅ |
| Case-insensitive trim comparison | `equalsIgnoreCase + trim` | identical | ✅ |
| DO_WHILE suffix stripping | `TaskUtils.removeIterationFromTaskRefName()` | `stripIterationSuffix()` — same regex | ✅ |
| Matcher key: no strip in `computeMatchers()` | `task.getTaskReferenceName()` direct | identical (wf defs have no `__N` at definition time) | ✅ |
| Matcher key format | `workflowName;version;taskRefName` | identical | ✅ |
| DAO interfaces: no orgId params | Orkes impls read from `OrkesRequestContext` | same — isolation is impl's concern | ✅ |
| orgId call site | `OrkesRequestContext.get().setOrgId(TimeBasedUUIDGenerator.getOrgId(id))` | `orgContextProvider.applyContext(id)` before any DAO call | ✅ |
| `WebhookConfig` fields | all Orkes fields present | `name`, `id`, `receiverWorkflowNamesToVersions`, `workflowsToStart`, `urlVerified`, `sourcePlatform`, `verifier`, `headers`, `headerKey`, `secretKey`, `secretValue`, `createdBy`, `expression`, `evaluatorType` | ✅ |
| `@JsonIgnoreProperties(ignoreUnknown = true)` | present in Orkes | present in OSS | ✅ |
| `urlVerified` preserved on update | `webhookConfig.setUrlVerified(existing.isUrlVerified())` | identical | ✅ |
| Secret preserved when `"***"` sent on update | `webhookConfig.setSecretValue(existing.getSecretValue())` | identical | ✅ |
| Challenge/response — early return, skip dispatch | `if (webhookChallenge == null) { storeWebhook(...) }` | identical logic | ✅ |
| `workflowsToStart` reserved key removal | `wfsToStart.remove("idempotencyKey"); wfsToStart.remove("idempotencyStrategy")` | identical | ✅ |
| Non-integer version in `workflowsToStart` | skip with log | identical | ✅ |
| Workflow start failures isolated per-workflow | `catch(Exception e) { log; continue }` | identical | ✅ |
| `urlVerified` set on first successful verification | `if (!webhookConfig.isUrlVerified()) { ...; update = true }` | identical | ✅ |
| `urlVerified` set on successful ping | `if (response != null) { config.setUrlVerified(true) }` | identical | ✅ |
| `getWebhook(id)` masks secret | Orkes: `webhookConfig.setSecretValue(SECRET)` | OSS: shallowCopy + setSecretValue | ✅ (fixed) |
| getAllWebhooks masks secrets | Orkes: `webhookConfigs.forEach(w -> w.setSecretValue("***"))` | OSS: shallowCopy + setSecretValue per item | ✅ |

---

## Intentional behavioral differences (documented on PR)

### 1. Synchronous vs async processing
- **OSS**: tasks completed inline within the `POST /webhook/{id}` HTTP request
- **Orkes**: event stored to DB, pushed to `_webhook_queue`, consumed by `WebhookWorker` thread pool
- **Rationale**: OSS simplicity; queue infrastructure not part of OSS core deployment

### 2. `POST /webhook/{id}` — body is optional
- **OSS**: `@RequestBody(required = false)` — body defaults to `"{}"` if absent
- **Orkes**: `@RequestBody String bodyStr` — body required (implicit `required = true`)
- **Rationale**: deliberate OSS improvement — some providers (Stripe, GitHub) send POST pings
  with no body; the lenient approach handles more real-world cases

### 3. GET null ping response — dispatched (fixed)
- **Orkes**: when `verifier.handlePing()` returns `null` (not a recognized ping), Orkes creates
  an `IncomingWebhookEvent` with `body="{}"` and routes it through the full task-dispatch
  pipeline
- **OSS fix**: when `handlePing()` gets null from the verifier, it now calls
  `dispatchToWaitingTasks()` and `startWorkflowsToStart()` with an empty body and the
  query parameters as payload — same semantics as Orkes, inline rather than queued
- **Files changed**: `IncomingWebhookService.java`, `IncomingWebhookServiceTest.java`

### 4. Config not found — returns null (fixed)
- **Orkes**: records to dead-letter queue, returns `null` → HTTP 200
- **OSS fix**: `handleWebhook()` now returns `null` when config not found instead of throwing
  `IllegalArgumentException`. Avoids retry storms when a webhook config is deleted while the
  provider still has the URL registered.
- **Files changed**: `IncomingWebhookService.java`, `IncomingWebhookServiceTest.java`

---

## What the Orkes adapter layer needs to provide

To use this OSS module in Orkes Enterprise, three beans need to be registered:

### 1. `OrkesWebhookOrgContextProvider implements WebhookOrgContextProvider`
```java
@Bean
public WebhookOrgContextProvider orkesWebhookOrgContextProvider() {
    return webhookId -> {
        String orgId = TimeBasedUUIDGenerator.getOrgId(webhookId);
        OrkesRequestContext.get().setOrgId(orgId);
    };
}
```
Called by `IncomingWebhookResource` before any DAO access, for both `POST` and `GET` endpoints.

### 2. `OrkesRedisWebhookTaskDAO implements WebhookTaskDAO`
```java
public void put(String hash, String taskId) {
    orkesJedisProxy.sadd(nsKey("WEBHOOK_TASKS", "hash" + hash), taskId);
}
public List<String> get(String hash) {
    return new ArrayList<>(orkesJedisProxy.smembers(nsKey("WEBHOOK_TASKS", "hash" + hash)));
}
public void remove(String hash, String taskId) {
    orkesJedisProxy.srem(nsKey("WEBHOOK_TASKS", "hash" + hash), taskId);
}
```
The Redis key format `NAMESPACE:WEBHOOK_TASKS:hash<hash>` matches the existing
`RedisWebhookTaskService` exactly — same data, same keys, no migration needed.

### 3. `OrkesPostgresWebhookConfigDAO implements WebhookConfigDAO`
Wraps the existing Postgres queries from `PostgresWebhookDAO`, adding
`org_id = OrkesRequestContext.get().getOrgId()` filtering internally. The DAO interface has
no orgId parameters.

---

## Commits made in this session

```
0c6fc873c  fix: align WAIT_FOR_WEBHOOK output format with Orkes Enterprise
340c2bb67  fix: precision pass — four Orkes convergence gaps in webhook-task
<pending>   fix: precision pass — fix GET event dispatch and config-not-found behaviour
```
